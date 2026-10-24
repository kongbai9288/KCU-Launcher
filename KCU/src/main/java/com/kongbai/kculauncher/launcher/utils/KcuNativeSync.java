package com.kongbai.kculauncher.launcher.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 玩家自带原生库同步。
 *
 * 公共目录（/sdcard/KCU/natives/&lt;abi&gt;/）挂载为 noexec，不能直接 dlopen；
 * 启动时把 .so 校验后复制到私有 MOD_RUNTIME_DIR，该目录由 KCULauncher
 * 无条件拼进 LD_LIBRARY_PATH，因此不需要改动任何已有的加载逻辑。
 *
 * 设计前提：玩家什么都不放时，本类不产生任何副作用。
 */
public final class KcuNativeSync {

    private static final String SUBDIR = "natives";
    private static final String RECORD = ".kcu_synced";
    private static final byte[] ELF_MAGIC = {0x7F, 'E', 'L', 'F'};

    private KcuNativeSync() {
    }

    public static File getExternalDir() {
        return new File(KCUPath.KCU_SHARED_ROOT + "/" + SUBDIR + "/"
                + Architecture.archAsStringAndroid(Architecture.getDeviceArchitecture()));
    }

    public static void sync() {
        try {
            File srcDir = getExternalDir();
            if (!srcDir.isDirectory()) {
                return;
            }
            File dstDir = KCUPath.MOD_RUNTIME_DIR == null ? null : new File(KCUPath.MOD_RUNTIME_DIR);
            if (dstDir == null) {
                return;
            }
            if (!dstDir.exists() && !dstDir.mkdirs()) {
                return;
            }

            File[] files = srcDir.listFiles();
            if (files == null) {
                return;
            }

            List<String> kept = new ArrayList<>();
            for (File src : files) {
                if (!src.isFile()) {
                    continue;
                }
                String name = src.getName();
                if (!name.endsWith(".so") || name.startsWith(".")) {
                    continue;
                }
                if (!isElf(src)) {
                    continue;
                }
                File dst = new File(dstDir, name);
                if (needsCopy(src, dst) && copy(src, dst)) {
                    kept.add(name);
                } else if (dst.isFile()) {
                    kept.add(name);
                }
            }
            prune(dstDir, new HashSet<>(kept));
        } catch (Throwable ignored) {
            // 同步失败不影响启动
        }
    }

    private static boolean isElf(File file) {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] head = new byte[4];
            if (in.read(head) != 4) {
                return false;
            }
            for (int i = 0; i < 4; i++) {
                if (head[i] != ELF_MAGIC[i]) {
                    return false;
                }
            }
            return true;
        } catch (Throwable e) {
            return false;
        } finally {
            close(in);
        }
    }

    private static boolean needsCopy(File src, File dst) {
        return !dst.isFile() || src.length() != dst.length() || src.lastModified() > dst.lastModified();
    }

    private static boolean copy(File src, File dst) {
        InputStream in = null;
        OutputStream out = null;
        try {
            // 上一次同步已把目标置为只读，覆盖前必须先放开写权限，
            // 否则更新过的 .so 永远写不进去，玩家改了文件却不生效
            if (dst.isFile()) {
                dst.setWritable(true);
            }
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            dst.setLastModified(src.lastModified());
            // Android 14 起动态加载的文件必须只读，只保留读与执行
            dst.setReadOnly();
            return true;
        } catch (Throwable e) {
            return false;
        } finally {
            close(out);
            close(in);
        }
    }

    /**
     * 仅清理本类上次同步过、如今源已不存在的文件。
     * 不清理其他机制放入该目录的文件，避免误删。
     */
    private static void prune(File dstDir, Set<String> kept) {
        // 记录文件按名排序，避免集合遍历顺序导致每次写入内容抖动
        File record = new File(dstDir, RECORD);
        List<String> previous = readLines(record);
        for (String name : previous) {
            if (kept.contains(name)) {
                continue;
            }
            File stale = new File(dstDir, name);
            if (stale.isFile()) {
                stale.setWritable(true);
                stale.delete();
            }
        }
        List<String> sorted = new ArrayList<>(kept);
        java.util.Collections.sort(sorted);
        writeLines(record, sorted);
    }

    private static List<String> readLines(File file) {
        List<String> lines = new ArrayList<>();
        if (!file.isFile()) {
            return lines;
        }
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] raw = new byte[(int) file.length()];
            int off = 0;
            while (off < raw.length) {
                int n = in.read(raw, off, raw.length - off);
                if (n <= 0) {
                    break;
                }
                off += n;
            }
            String text = new String(raw, 0, off, StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                String t = line.trim();
                if (!t.isEmpty()) {
                    lines.add(t);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            close(in);
        }
        return lines;
    }

    private static void writeLines(File file, List<String> lines) {
        OutputStream out = null;
        try {
            file.setWritable(true);
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(line).append('\n');
            }
            out = new FileOutputStream(file, false);
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Throwable ignored) {
        } finally {
            close(out);
        }
    }

    private static void close(Object stream) {
        if (stream == null) {
            return;
        }
        try {
            if (stream instanceof InputStream) {
                ((InputStream) stream).close();
            } else if (stream instanceof OutputStream) {
                ((OutputStream) stream).close();
            }
        } catch (IOException ignored) {
        }
    }
}
