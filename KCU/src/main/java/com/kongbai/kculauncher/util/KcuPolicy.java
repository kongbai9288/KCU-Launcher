package com.kongbai.kculauncher.util;

import android.util.JsonReader;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.launcher.utils.KCUPath;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 远程策略：决定某个功能在当前地区是否可用。
 *
 * 【为什么要有这个】
 * 有些功能的可用性会随外部要求变化，而这种变化无法提前预知。
 * 若把规则写死在安装包里，一旦需要调整就只能重新发版，
 * 而旧版本的安装包会一直按老规则运行。把规则放在可以随时更新的远端，
 * 需要调整时改远端即可，已发出的安装包也会跟着变。
 *
 * 【拉取流程：先看有没有新内容，没有就关闭请求】
 * 1. 拉更新时间索引（很小）
 * 2. 与本地缓存里记录的时间比对
 * 3. 远端更新了才去拉规则正文，否则到此为止，不再发起请求
 * 这样绝大多数启动只会产生一个极小请求，规则没有变动时不会重复下载正文。
 *
 * 【拉不到远端时怎么办】
 * 用本地缓存；本地也没有才用内置默认。
 * 拉取失败绝不视为"禁止"，也绝不视为"全部允许"后清空已有缓存 ——
 * 拿不到新信息时保持现有判断，是最不容易出意外的做法。
 */
public final class KcuPolicy {

    /** 版本导入与下载 */
    public static final String FEATURE_DOWNLOAD = "download";
    /** 统一登录 */
    public static final String FEATURE_LOGIN = "login";
    /** 存档检测与提示 */
    public static final String FEATURE_WORLD_SCAN = "world_scan";
    /** 布局与按键自定义 */
    public static final String FEATURE_CONTROLLER = "controller";

    /**
     * 更新时间索引与规则正文分放在两个不同目录。
     * 索引很小，每次都可以拉；正文只在索引显示有更新时才拉。
     */
    private static final String BASE =
            "https://raw.githubusercontent.com/kongbai9288/KCU-Policy/main/";
    private static final String INDEX_URL = BASE + "index/index.json";
    private static final String RULES_URL = BASE + "policy/rules.json";

    private static final String CACHE_NAME = "kcu_policy.json";
    private static final int TIMEOUT_MS = 10000;

    /**
     * 内置默认规则：仅在远端和本地缓存都拿不到时使用。
     *
     * 默认只限制下载功能在中国大陆地区，其余功能默认不限制 ——
     * 限制是例外而非常态，正常情况下这些限制都不该出现。
     */
    private static final String FALLBACK =
            "{\"updatedAt\":0,\"blocks\":[{\"feature\":\"download\",\"regions\":[\"CN\"],"
                    + "\"unknown\":\"allow\"}]}";

    private static volatile List<Block> blocks = new ArrayList<>();
    private static volatile boolean prepared = false;

    private KcuPolicy() {
    }

    /** 一条限制规则 */
    private static final class Block {
        String feature = "";
        List<String> regions = new ArrayList<>();
        /** 地区判定不出来时的取向；缺省按 allow */
        boolean blockOnUnknown = false;
        String title = "";
        String body = "";
    }

    /**
     * 准备：拉取远端规则并判定地区。
     *
     * 这是网络操作，必须在后台线程调用。
     * 未调用过本方法时，isBlocked 按"地区未知 + 内置默认"判断，
     * 因此不会因为没有准备好而误伤任何功能。
     */
    public static void prepare() {
        try {
            syncRules();
        } catch (Throwable ignored) {
        }
        try {
            KcuRegion.detect();
        } catch (Throwable ignored) {
        }
        prepared = true;
    }

    /** 在后台线程准备一次；重复调用无副作用 */
    public static void prepareAsync() {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            try {
                prepare();
            } finally {
                worker.shutdownNow();
            }
        });
    }

    /** 规则是否已就绪 */
    public static boolean isPrepared() {
        return prepared;
    }

    /**
     * 该功能当前是否不可用。
     *
     * 判定顺序：先找匹配功能的规则，再看地区。
     * 规则里地区列表为空表示所有地区都限制；非空则只有列出的地区受限。
     */
    public static boolean isBlocked(String feature) {
        if (feature == null || feature.isEmpty()) {
            return false;
        }
        String region = KcuRegion.detect();
        boolean unknown = region == null || region.isEmpty();
        for (Block block : blocks) {
            if (!feature.equals(block.feature)) {
                continue;
            }
            // 地区列表为空 = 全部地区
            if (block.regions.isEmpty()) {
                return true;
            }
            if (unknown) {
                // 判定不出地区时按规则自身的取向处理，默认不限制：
                // 猜错的代价（正常用户用不了）比放过的大
                return block.blockOnUnknown;
            }
            for (String one : block.regions) {
                if (one != null && one.trim().equalsIgnoreCase(region)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 被限制时显示的标题；规则没写则回退到内置文案 */
    public static String titleFor(android.content.Context context, String feature) {
        Block hit = find(feature);
        if (hit != null && !hit.title.isEmpty()) {
            return hit.title;
        }
        return context.getString(R.string.kcu_policy_blocked_title);
    }

    /** 被限制时显示的说明；规则没写则回退到内置文案 */
    public static String bodyFor(android.content.Context context, String feature) {
        Block hit = find(feature);
        if (hit != null && !hit.body.isEmpty()) {
            return hit.body;
        }
        return context.getString(R.string.kcu_policy_blocked_body);
    }

    private static Block find(String feature) {
        if (feature == null) {
            return null;
        }
        for (Block block : blocks) {
            if (feature.equals(block.feature)) {
                return block;
            }
        }
        return null;
    }

    /** 首次使用或缓存为空时载入：优先本地缓存，其次内置默认 */
    public static void loadCached() {
        if (!blocks.isEmpty()) {
            return;
        }
        File file = getCacheFile();
        if (file != null && file.isFile()) {
            List<Block> parsed = parse(readFile(file));
            if (parsed != null) {
                blocks = parsed;
                return;
            }
        }
        List<Block> parsed = parse(FALLBACK);
        blocks = parsed != null ? parsed : new ArrayList<>();
    }

    private static void syncRules() {
        loadCached();
        String index = fetch(INDEX_URL);
        if (index == null) {
            return;
        }
        long remote = readUpdatedAt(index);
        if (remote <= 0) {
            return;
        }
        long local = readUpdatedAt(readFile(getCacheFile()));
        // 远端没有比本地更新就到此为止，不再请求规则正文。
        // 这一步是"没有新内容就关闭请求"的关键：绝大多数启动只产生一个索引请求
        if (local > 0 && remote <= local) {
            return;
        }
        String rules = fetch(RULES_URL);
        if (rules == null || rules.trim().isEmpty()) {
            return;
        }
        List<Block> parsed = parse(rules);
        if (parsed == null) {
            return;
        }
        // 先落盘成功再替换内存：否则进程内认为已生效、磁盘上却没有，
        // 下次启动又会重新拉一遍
        if (writeCache(rules)) {
            blocks = parsed;
        }
    }

    private static File getCacheFile() {
        try {
            return new File(KCUPath.KCU_SHARED_ROOT, CACHE_NAME);
        } catch (Throwable e) {
            return null;
        }
    }

    private static String readFile(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
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

    /**
     * 先写临时文件再改名。
     *
     * 规则文件决定功能是否可用，写坏一半的后果是解析失败，
     * 判断结果会整个退回默认，与该次更新的意图可能完全相反。
     */
    private static boolean writeCache(String content) {
        File target = getCacheFile();
        if (target == null) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        File tmp = new File(parent, CACHE_NAME + ".tmp");
        try {
            OutputStream out = new FileOutputStream(tmp, false);
            try {
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } finally {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.delete();
                return false;
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static String fetch(String address) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            in = conn.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        } finally {
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

    private static long readUpdatedAt(String json) {
        if (json == null || json.trim().isEmpty()) {
            return 0;
        }
        JsonReader reader = null;
        try {
            reader = new JsonReader(new java.io.StringReader(json));
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("updatedAt".equals(key)) {
                    return reader.nextLong();
                }
                reader.skipValue();
            }
            reader.endObject();
        } catch (Throwable e) {
            return 0;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return 0;
    }

    private static List<Block> parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        List<Block> list = new ArrayList<>();
        JsonReader reader = null;
        try {
            reader = new JsonReader(new java.io.StringReader(json));
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("blocks".equals(key)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        Block block = readBlock(reader);
                        if (block != null) {
                            list.add(block);
                        }
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return list;
        } catch (Throwable e) {
            // 解析失败返回 null 而不是已经读到的那部分：
            // 部分规则会让判断结果不完整，而"缺一条限制"和"多一条限制"
            // 相比，前者更安全，且下次启动会重新拉取
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Block readBlock(JsonReader reader) throws Exception {
        Block block = new Block();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if ("feature".equals(key)) {
                block.feature = reader.nextString();
            } else if ("regions".equals(key)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    String one = reader.nextString();
                    if (one != null && !one.trim().isEmpty()) {
                        block.regions.add(one.trim().toUpperCase(Locale.ROOT));
                    }
                }
                reader.endArray();
            } else if ("unknown".equals(key)) {
                block.blockOnUnknown = "block".equalsIgnoreCase(reader.nextString());
            } else if ("title".equals(key)) {
                block.title = reader.nextString();
            } else if ("body".equals(key)) {
                block.body = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (block.feature.isEmpty()) {
            return null;
        }
        return block;
    }

    /** 把当前规则导出为 JSON 文本，便于排查实际生效的内容 */
    public static String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("updatedAt=").append(readUpdatedAt(readFile(getCacheFile())));
        sb.append(" region=");
        String region = KcuRegion.detect();
        sb.append(region == null || region.isEmpty() ? "unknown" : region);
        sb.append(" blocks=").append(blocks.size());
        return sb.toString();
    }
}
