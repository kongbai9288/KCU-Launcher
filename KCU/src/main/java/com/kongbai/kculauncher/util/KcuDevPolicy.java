package com.kongbai.kculauncher.util;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kongbai.kculauncher.KCUApp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 开发者模式的远程开关。
 *
 * 这个文件管的是"哪些版本的开发者模式不许开"。
 * 默认状态是什么都不禁用 —— 也就是开发者模式照常能用。
 * 只有仓库里明确写了某条规则、且当前版本与客户端都匹配上时，才禁用。
 *
 * 【为什么默认是不禁用】
 * 网络随时可能不通，配置文件也可能还没建。
 * 如果把"取不到配置"当成"禁用"，一次网络波动就会把开发者模式关掉；
 * 反过来把"取不到"当成"不禁用"，最坏只是这次停用没生效，方向安全得多。
 *
 * 【为什么它优先于本机已解锁】
 * 本机解锁状态是本地文件，这个开关是仓库里的。
 * 真遇到需要紧急收回的情况，等本地那个慢慢失效是来不及的，
 * 所以只要远程说禁用，本机已经开着的也要失效。
 */
public final class KcuDevPolicy {

    private static final String TAG = "KCU";

    private static final String BASE =
            "https://raw.githubusercontent.com/kongbai9288/KCU-Policy/main/";
    private static final String INDEX_URL = BASE + "dev/index.json";
    private static final String RULES_URL = BASE + "dev/disable.json";

    private static final String CACHE_NAME = "kcu_dev_policy.json";

    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_BYTES = 256 * 1024;

    private static final Object LOCK = new Object();

    private static volatile List<Rule> rules = new ArrayList<>();
    private static volatile long updatedAt = 0;
    private static volatile boolean loaded = false;

    private KcuDevPolicy() {
    }

    public static final class Rule {
        /** 版本写法：1.20.1 / 1.20.x / 1.20.0-1.21.4 / 空或 * 表示全部 */
        public final String versions;
        /** 客户端写法：android-arm64 / android / 空或 * 表示全部 */
        public final String clients;

        Rule(String versions, String clients) {
            this.versions = versions == null ? "" : versions.trim();
            this.clients = clients == null ? "" : clients.trim();
        }
    }

    /**
     * 当前版本的开发者模式是否被禁用。
     *
     * 判断本身出任何问题都按"没禁用"处理：
     * 这是停用措施，宁可这次没生效，也不能因为读错了把人整个锁在外面。
     */
    public static boolean isDisabled() {
        List<Rule> list = rules;
        if (list == null || list.isEmpty()) {
            return false;
        }
        try {
            String version = KcuTarget.currentVersion();
            String client = KcuTarget.currentClient();
            for (Rule rule : list) {
                boolean versionOk;
                try {
                    versionOk = KcuTarget.versionMatches(rule.versions, version);
                } catch (Throwable e) {
                    // 版本写法不认识时不匹配：
                    // 一条看不懂的规则如果当成匹配，会把停用套到所有人头上
                    Log.w(TAG, "Dev policy: bad version pattern " + rule.versions);
                    continue;
                }
                if (!versionOk) {
                    continue;
                }
                try {
                    if (!KcuTarget.clientMatches(rule.clients)) {
                        continue;
                    }
                } catch (Throwable e) {
                    continue;
                }
                return true;
            }
        } catch (Throwable e) {
            return false;
        }
        return false;
    }

    /** 启动时先读上次缓存，不发请求，不拖慢启动 */
    public static void loadCached() {
        long[] stamp = new long[1];
        String body = readCache(stamp);
        if (body == null || body.isEmpty()) {
            return;
        }
        // 缓存里记下的更新时间要一并恢复：
        // 只恢复规则而恢复不了时间戳的话，本次启动仍会去拉一次全文
        synchronized (LOCK) {
            if (stamp[0] > updatedAt) {
                updatedAt = stamp[0];
            }
        }
        apply(parse(body));
    }

    /** 后台取远端配置；必须在非界面线程调用 */
    public static void refresh() {
        try {
            KcuMirror.load();
        } catch (Throwable ignored) {
            // 镜像清单取不到不影响后续，会自动退回直连
        }
        try {
            sync();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 先看更新时间，变了才取正文。
     *
     * 没有新内容就到此为止，不再往下请求 —— 每次启动都拉全文没必要，
     * 也让仓库那边没有更新时不产生多余流量。
     */
    private static void sync() {
        String indexBody = KcuMirror.fetch(INDEX_URL);
        long remote = 0;
        if (indexBody != null && !indexBody.isEmpty()) {
            try {
                JsonObject o = new Gson().fromJson(indexBody, JsonObject.class);
                if (o != null && o.has("updatedAt")) {
                    JsonElement e = o.get("updatedAt");
                    if (e != null && e.isJsonPrimitive()) {
                        remote = e.getAsLong();
                    }
                }
            } catch (Throwable e) {
                // 索引读不出来：拿不到更新时间，按没有新内容处理，
                // 本次不拉正文，沿用上次缓存
                Log.w(TAG, "Dev policy: unreadable index");
                return;
            }
        } else {
            return;
        }
        if (remote <= 0) {
            return;
        }
        synchronized (LOCK) {
            if (loaded && remote <= updatedAt) {
                return;
            }
        }
        String body = KcuMirror.fetch(RULES_URL);
        if (body == null || body.isEmpty()) {
            return;
        }
        List<Rule> parsed = parse(body);
        if (parsed == null) {
            return;
        }
        // 先落盘再改内存：缓存没写成功就不算这次同步过，
        // 下次仍会重试，而不是在内存里假装已经拿到过
        if (writeCache(remote, body)) {
            synchronized (LOCK) {
                updatedAt = remote;
            }
        }
        apply(parsed);
    }

    private static void apply(List<Rule> parsed) {
        synchronized (LOCK) {
            rules = parsed == null ? new ArrayList<Rule>() : parsed;
            loaded = true;
        }
    }

    /**
     * 解析规则。解析不出来返回 null —— 不用已经读到的那部分，
     * 半截规则造成的误判比没有规则更麻烦。
     */
    private static List<Rule> parse(String body) {
        try {
            JsonObject root = new Gson().fromJson(body, JsonObject.class);
            if (root == null) {
                return null;
            }
            List<Rule> out = new ArrayList<>();
            JsonElement disabled = root.get("disabled");
            if (disabled == null || !disabled.isJsonArray()) {
                // 没有 disabled 字段也是合法状态（尚未停用任何版本），
                // 返回空列表：空表示"没有规则"，与"读不出来"不是一回事
                return out;
            }
            JsonArray arr = disabled.getAsJsonArray();
            for (JsonElement one : arr) {
                if (!one.isJsonObject()) {
                    continue;
                }
                JsonObject o = one.getAsJsonObject();
                out.add(new Rule(text(o, "versions"), text(o, "clients")));
            }
            return out;
        } catch (Throwable e) {
            Log.w(TAG, "Dev policy: failed to parse", e);
            return null;
        }
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return null;
        }
        return e.getAsString();
    }

    /** 生效内容存私有目录：公有目录任何应用都能改，停用措施不能放在那里 */
    private static File cacheFile() {
        Context ctx = KCUApp.getAppContext();
        if (ctx == null) {
            return null;
        }
        File dir = ctx.getFilesDir();
        if (dir == null) {
            return null;
        }
        return new File(dir, CACHE_NAME);
    }

    /**
     * 先写临时文件再整体替换。
     *
     * 直接截断写的话，写一半出问题会留下半截内容，
     * 下次读出来既不是新规则也不是旧规则，停用范围会莫名其妙地变。
     *
     * 【为什么把更新时间一起写进去】
     * 只存规则正文、不存时间戳的话，进程重启后更新时间无从恢复（内存字段归零），
     * "没有新内容就关闭请求"这个设计在跨启动时完全失效 —— 每次启动都会重拉全文。
     * 返回是否写成功，写失败就不算这次同步过。
     */
    private static boolean writeCache(long remote, String body) {
        File file = cacheFile();
        if (file == null) {
            return false;
        }
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("updatedAt", remote);
        wrapper.addProperty("body", body);
        byte[] bytes;
        try {
            bytes = wrapper.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return false;
        }
        File tmp = new File(file.getParentFile(), CACHE_NAME + ".tmp");
        boolean ok = false;
        try {
            FileOutputStream out = new FileOutputStream(tmp, false);
            try {
                out.write(bytes);
                ok = true;
            } finally {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "Dev policy: failed to write cache");
        }
        if (!ok) {
            if (!tmp.delete()) {
                Log.w(TAG, "Dev policy: unable to remove " + tmp);
            }
            return false;
        }
        if (!tmp.renameTo(file)) {
            if (!tmp.delete()) {
                Log.w(TAG, "Dev policy: unable to remove " + tmp);
            }
            return false;
        }
        return true;
    }

    /**
     * 读缓存，更新时间通过 stampOut[0] 带出。
     *
     * 兼容旧格式：早期缓存只写了规则正文（顶层就是 disabled 数组所在的那个对象），
     * 没有 updatedAt / body 这两层。认不出来时把整段当正文、时间戳按 0 处理 ——
     * 这样升级上来的老缓存仍然生效，不会因为读不懂就退回默认（默认是不禁用，
     * 那等于这次停用直接失效）。
     */
    private static String readCache(long[] stampOut) {
        File file = cacheFile();
        if (file == null || !file.isFile()) {
            return null;
        }
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            // 以 -1 为结束：个别输入流会返回 0，按 > 0 判结束会提前截断内容
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BYTES) {
                    return null;
                }
                out.write(buf, 0, n);
            }
            String text = out.toString("UTF-8");
            if (text == null || text.isEmpty()) {
                return null;
            }
            try {
                JsonObject wrapper = new Gson().fromJson(text, JsonObject.class);
                if (wrapper != null && wrapper.has("body")) {
                    JsonElement stamp = wrapper.get("updatedAt");
                    if (stamp != null && stamp.isJsonPrimitive()) {
                        try {
                            stampOut[0] = stamp.getAsLong();
                        } catch (Throwable ignored) {
                            stampOut[0] = 0;
                        }
                    }
                    JsonElement body = wrapper.get("body");
                    if (body != null && body.isJsonPrimitive()) {
                        return body.getAsString();
                    }
                    return null;
                }
            } catch (Throwable ignored) {
                // 解析不出来就按旧格式处理
            }
            return text;
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
