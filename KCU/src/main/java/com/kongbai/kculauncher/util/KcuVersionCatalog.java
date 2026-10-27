package com.kongbai.kculauncher.util;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 版本清单：从镜像或官方取回可用版本列表，供导入页直接选择。
 *
 * 【为什么镜像在前】
 * 官方地址在国内直连经常超时或极慢，玩家点了导入却一直转圈，
 * 只会以为功能坏了。镜像优先、官方兜底，两头都能用；
 * 镜像落后于官方发布时，玩家仍可用本地文件导入兜底。
 *
 * 【为什么只解析需要的三个字段】
 * 完整清单字段很多且随版本变化，只取 id / type / url 三个，
 * 其余一概不读，上游改结构也不会崩。
 */
public final class KcuVersionCatalog {

    private static final String TAG = "KCU";

    /** 镜像优先，官方兜底 */
    private static final String[] MANIFEST_URLS = {
            "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json",
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    };

    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private KcuVersionCatalog() {
    }

    public static final class Entry {
        public final String id;
        public final String type;
        /** 该版本 version.json 的地址，取客户端下载地址时要再请求一次 */
        public final String url;

        Entry(String id, String type, String url) {
            this.id = id;
            this.type = type;
            this.url = url;
        }

        public boolean isRelease() {
            return "release".equals(type);
        }
    }

    /**
     * 取回版本清单。全部地址都失败时返回 null，由调用方提示。
     * 清单按时间倒序，最新的在最前。
     */
    public static List<Entry> fetch() {
        for (String address : MANIFEST_URLS) {
            String body = get(address);
            if (body == null || body.isEmpty()) {
                Log.w(TAG, "Version manifest unavailable: " + address);
                continue;
            }
            List<Entry> out = parseManifest(body);
            if (out != null && !out.isEmpty()) {
                return out;
            }
            Log.w(TAG, "Version manifest empty or unparsable: " + address);
        }
        return null;
    }

    /**
     * 取某版本的客户端 jar 下载地址。
     * 该版本确实提供 client 下载时返回地址，否则返回 null
     * （部分极旧版本没有 client 条目，不能让调用方拿到空地址去下载）。
     */
    public static String fetchClientJarUrl(String versionJsonUrl) {
        if (versionJsonUrl == null || versionJsonUrl.isEmpty()) {
            return null;
        }
        String body = get(versionJsonUrl);
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            JsonObject root = new Gson().fromJson(body, JsonObject.class);
            if (root == null) {
                return null;
            }
            JsonObject downloads = root.getAsJsonObject("downloads");
            if (downloads == null) {
                return null;
            }
            JsonObject client = downloads.getAsJsonObject("client");
            if (client == null) {
                return null;
            }
            return text(client, "url");
        } catch (Throwable e) {
            Log.w(TAG, "Failed to parse version json", e);
            return null;
        }
    }

    private static List<Entry> parseManifest(String body) {
        try {
            JsonObject root = new Gson().fromJson(body, JsonObject.class);
            if (root == null) {
                return null;
            }
            JsonElement arr = root.get("versions");
            if (arr == null || !arr.isJsonArray()) {
                return null;
            }
            List<Entry> out = new ArrayList<>();
            for (JsonElement one : arr.getAsJsonArray()) {
                if (!one.isJsonObject()) {
                    continue;
                }
                JsonObject o = one.getAsJsonObject();
                String id = text(o, "id");
                String url = text(o, "url");
                if (id == null || id.isEmpty() || url == null || url.isEmpty()) {
                    continue;
                }
                String type = text(o, "type");
                out.add(new Entry(id, type == null ? "release" : type, url));
            }
            return out;
        } catch (Throwable e) {
            Log.w(TAG, "Failed to parse version manifest", e);
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

    /** 只接受 200，其余一律当作不可用，交给下一个地址 */
    private static String get(String address) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            in = conn.getInputStream();
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
            return out.toString("UTF-8");
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
}
