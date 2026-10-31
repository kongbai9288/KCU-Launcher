package com.kongbai.kculauncher.util;

import android.util.Log;

import com.kongbai.kculauncher.core.task.Task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多源竞速下载：同一个文件同时从几个地址拉，谁先下完用谁的，其余立刻停掉。
 *
 * 【为什么不用"一个失败了再换下一个"】
 * 顺序试错要等前一个彻底失败（通常是超时）才开始下一个，
 * 玩家看到的可能是几十秒毫无动静。并发的话，慢的那个自然被比下去，
 * 不需要先失败一次。
 *
 * 【只用于下载页那几个下载】
 * 我的世界本体与依赖库有缓存、断点续传、依赖顺序，这里一概没有，
 * 所以不能拿它替换那些下载。
 *
 * 【关于"不超出手机最大下载速度"】
 * 手机的带宽上限没有一个可靠接口能读到，这里做不到精确控制。
 * 能做的是限制同时下几个源（默认最多两个），把额外占用的带宽压到可控范围。
 * 这一点如实说明：它不是按带宽判断的，是按并发数限制的。
 */
public class KcuRaceDownloadTask extends Task<Void> {

    private static final String TAG = "KCU";

    /** 同时进行的源数量上限：多了既抢带宽，收益也快速递减 */
    public static final int MAX_PARALLEL = 2;

    private static final int TIMEOUT_MS = 15000;
    private static final int BUFFER = 64 * 1024;

    private final List<String> urls;
    private final File target;
    /** 期望的 SHA-1；为空表示不校验 */
    private final String expectSha1;

    private volatile boolean stopped = false;

    public KcuRaceDownloadTask(List<String> urls, File target) {
        this(urls, target, null);
    }

    public KcuRaceDownloadTask(List<String> urls, File target, String expectSha1) {
        this.urls = urls == null ? new ArrayList<>() : new ArrayList<>(urls);
        this.target = target;
        this.expectSha1 = expectSha1;
    }

    @Override
    public void execute() throws Exception {
        // 每次执行都从"未停止"开始：
        // 这个标记是本实例的字段，收尾时会被置为停止。
        // 万一同一个实例被再执行一次，残留的停止标记会让所有源一开始就放弃，
        // 表现出来就是点了下载毫无动静。
        stopped = false;
        List<String> usable = new ArrayList<>();
        for (String one : urls) {
            if (one != null && !one.isEmpty() && usable.size() < MAX_PARALLEL) {
                usable.add(one);
            }
        }
        if (usable.isEmpty()) {
            throw new java.io.IOException("no available source");
        }
        // 只有一个源时没什么可比，直接单源下载，省掉并发的开销
        if (usable.size() == 1) {
            downloadSingle(usable.get(0));
            return;
        }

        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new java.io.IOException("unable to make directory " + parent);
        }

        List<File> temps = new ArrayList<>();
        for (int i = 0; i < usable.size(); i++) {
            temps.add(new File(parent, target.getName() + ".race" + i + ".tmp"));
        }

        AtomicInteger winner = new AtomicInteger(-1);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicLong bestBytes = new AtomicLong(0);
        // 服务器声明的文件总长。进度要拿它当分母：
        // 拿已下载字节数当分母算出来恒等于 100%，界面上根本看不出下到哪了
        AtomicLong declaredTotal = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(usable.size());
        List<Thread> threads = new ArrayList<>();

        try {
            for (int i = 0; i < usable.size(); i++) {
                final int index = i;
                final String address = usable.get(i);
                final File temp = temps.get(i);
                Thread thread = new Thread(() -> {
                    try {
                        fetch(address, temp, winner, index, bestBytes, declaredTotal);
                        // 第一个完整下完的记下来；后面下完的即使成功也不用，
                        // 此时已经有可用的完整文件了
                        if (winner.compareAndSet(-1, index)) {
                            // 已经有人赢了，其余源没必要再占带宽
                            stopped = true;
                        }
                    } catch (Throwable e) {
                        // 单个源失败只记一笔，不影响其他源 —— 玩家要的是快，
                        // 不是每个源都必须成功
                        failed.incrementAndGet();
                        Log.w(TAG, "Race source failed: " + address + " " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                }, "kcu-race-" + i);
                thread.setDaemon(true);
                threads.add(thread);
                thread.start();
            }

            // 等所有源结束。被取消时不再等，直接退出并清理
            while (!latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                // 只在知道总长、且已下载不超过总长时才上报：
                // 拿已下载字节数当分母算出来恒等于 100%，界面上永远满格；
                // 而基类对大于 1 的进度会直接抛异常，这里必须自己挡住
                long total = declaredTotal.get();
                long done = bestBytes.get();
                if (total > 0 && done <= total) {
                    updateProgress(done, total);
                }
                if (stopped && winner.get() >= 0) {
                    // 已经有人下完了，其余的能停就停，不必干等
                    break;
                }
                if (Thread.currentThread().isInterrupted()) {
                    stopped = true;
                    break;
                }
            }
        } finally {
            stopped = true;
            for (Thread thread : threads) {
                thread.interrupt();
            }
        }

        int won = winner.get();
        if (won < 0) {
            cleanup(temps);
            // 全部源都失败时才算失败，且把原因留给上层提示
            throw new java.io.IOException("all sources failed: " + failed.get() + "/" + usable.size());
        }

        File wonFile = temps.get(won);
        if (!wonFile.isFile()) {
            cleanup(temps);
            throw new java.io.IOException("winner file missing");
        }
        if (expectSha1 != null && !expectSha1.isEmpty()) {
            String actual = sha1(wonFile);
            if (actual == null || !actual.equalsIgnoreCase(expectSha1)) {
                cleanup(temps);
                throw new java.io.IOException("checksum mismatch");
            }
        }
        // 直接改名，不先删同名文件：
        // 改名本身就是覆盖式的，先删是多余的；更关键的是先删后移一旦改名失败，
        // 旧文件已经没了、新文件也没换上，玩家会凭空丢一个文件。
        // 直接改名的话，要么换上新的，要么保留旧的，两个方向都不会丢东西。
        if (!wonFile.renameTo(target)) {
            cleanup(temps);
            throw new java.io.IOException("unable to move downloaded file");
        }
        cleanup(temps);
    }

    private void downloadSingle(String address) throws Exception {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new java.io.IOException("unable to make directory " + parent);
        }
        File temp = new File(parent, target.getName() + ".race0.tmp");
        try {
            fetch(address, temp, new AtomicInteger(-1), 0, new AtomicLong(0), new AtomicLong(0));
            if (expectSha1 != null && !expectSha1.isEmpty()) {
                String actual = sha1(temp);
                if (actual == null || !actual.equalsIgnoreCase(expectSha1)) {
                    throw new java.io.IOException("checksum mismatch");
                }
            }
            // 同上：直接改名覆盖，不先删同名文件，避免改名失败时丢文件
            if (!temp.renameTo(target)) {
                throw new java.io.IOException("unable to move downloaded file");
            }
        } finally {
            if (temp.exists() && !temp.delete()) {
                Log.w(TAG, "unable to remove " + temp);
            }
        }
    }

    /**
     * 从单个地址下载到临时文件。
     *
     * 每读一块都看一次"是不是已经有别的源下完了 / 是不是被取消了"，
     * 有就立刻停手 —— 明知用不上还把流量吃完，只会拖慢赢家。
     */
    private void fetch(String address, File temp, AtomicInteger winner, int index, AtomicLong bestBytes,
                       AtomicLong declaredTotal)
            throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new java.io.IOException("http " + code);
            }
            long total = conn.getContentLengthLong();
            // 记下第一个拿到长度的那个源即可：几个源下的是同一个文件，
            // 声明的长度理论上一致，反复覆盖反而让进度在几个值之间跳
            if (total > 0 && declaredTotal.get() == 0) {
                declaredTotal.set(total);
            }
            in = conn.getInputStream();
            out = new FileOutputStream(temp, false);
            byte[] buf = new byte[BUFFER];
            int n;
            long done = 0;
            while ((n = in.read(buf)) != -1) {
                if (winner.get() >= 0 && winner.get() != index) {
                    throw new java.io.IOException("another source won");
                }
                if (stopped || Thread.currentThread().isInterrupted()) {
                    throw new java.io.IOException("cancelled");
                }
                out.write(buf, 0, n);
                done += n;
                if (done > bestBytes.get()) {
                    bestBytes.set(done);
                }
                // 只在读到的字节数还没超过声明长度时才报进度：
                // 服务器声明的长度可能小于实际内容（压缩传输、分块、或声明值本身有误），
                // 这时算出来的进度会大于 1，而基类会直接抛异常把这次下载判成失败
                if (total > 0 && done <= total) {
                    updateProgress(done, total);
                }
            }
            out.flush();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void cleanup(List<File> temps) {
        for (File one : temps) {
            if (one != null && one.exists() && !one.delete()) {
                Log.w(TAG, "unable to remove " + one);
            }
        }
    }

    private static String sha1(File file) {
        InputStream in = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            in = new java.io.FileInputStream(file);
            byte[] buf = new byte[BUFFER];
            int n;
            while ((n = in.read(buf)) != -1) {
                digest.update(buf, 0, n);
            }
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() < 2) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Throwable e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
