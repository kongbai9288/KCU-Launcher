package com.kongbai.kculauncher.util;

import android.util.JsonReader;
import android.util.JsonToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 地区检测：判定设备当前出口网络所属的国家或地区。
 *
 * 只取国家或地区代码（如 CN、US），不取城市等细粒度信息 —— 判断是否需要限制某个功能，
 * 只需要知道国家或地区，更细的信息既无必要也涉及更多隐私。
 *
 * 【为什么必须多个服务冗余】
 * 判定地区这件事本身要发出网络请求，而请求能否成功取决于用户所在的网络环境。
 * 只用单一服务时，一旦该服务在当前网络不可达（这在跨境、校园网、企业网络里很常见），
 * 就完全无法判定。多放几个，任一可达即判定成功，可大幅降低"判不出来"的比例。
 *
 * 【为什么全都用 HTTPS】
 * 部分系统版本默认禁止明文 HTTP 请求。若某个检测服务是 HTTP，
 * 在这类系统上它永远不会成功，等于白放一个。全部选 HTTPS 可避开这个问题。
 *
 * 【判定不出来时怎么办】
 * 返回 {@link #UNKNOWN}，由调用方按规则本身的语义决定 —— 见 KcuPolicy 的说明。
 * 这里不擅自假设地区，因为猜错的代价比判不出来更大。
 */
public final class KcuRegion {

    /** 判定失败时的返回值：表示地区未知 */
    public static final String UNKNOWN = "";

    /**
     * 三个不同的归属服务，字段结构各不相同，由 parse 按字段名统一处理。
     * 都是无需密钥的公开接口。
     */
    private static final String[] ENDPOINTS = {
            "https://ipwho.is/",
            "https://ipinfo.io/json",
            "https://ip9.com.cn/get"
    };

    private static final int TIMEOUT_MS = 6000;

    private static volatile String cached = null;

    private KcuRegion() {
    }

    /**
     * 取当前国家或地区代码；判定不出来返回 {@link #UNKNOWN}。
     *
     * 结果在进程内缓存：同一进程内多次判断不需要重复发起请求。
     * 这是网络操作，调用方应自行确保不在主线程直接调用。
     *
     * 【为什么只缓存成功的结果】
     * 若把"没判出来"也当成结果缓存，那么一次失败的判定会把未知状态固定下来：
     * 后续即便网络恢复、即便在正确的线程上重新判定，拿到的仍是上一次的空值，
     * 功能会一直表现得像是判定不出来。失败的判定必须允许重试，只有真正
     * 拿到有效结果才值得缓存下来复用。
     */
    public static String detect() {
        String v = cached;
        if (v != null) {
            return v;
        }
        String result = detectOnce();
        if (result != null && !result.isEmpty()) {
            cached = result;
        }
        return result;
    }

    /** 清除缓存，下次 detect 重新判定 */
    public static void refresh() {
        cached = null;
    }

    private static String detectOnce() {
        ExecutorService pool = null;
        try {
            pool = Executors.newFixedThreadPool(ENDPOINTS.length);
            List<Future<String>> futures = new ArrayList<>(ENDPOINTS.length);
            for (String endpoint : ENDPOINTS) {
                final String address = endpoint;
                futures.add(pool.submit(() -> query(address)));
            }
            // 任一服务给出结果即采用；全部没有结果才判为未知
            for (Future<String> one : futures) {
                try {
                    String code = one.get(TIMEOUT_MS + 2000L, TimeUnit.MILLISECONDS);
                    if (code != null && !code.isEmpty()) {
                        return code;
                    }
                } catch (Throwable ignored) {
                    // 单个服务取不到结果按没结果处理
                }
            }
        } catch (Throwable e) {
            return UNKNOWN;
        } finally {
            // 这些线程不是守护线程，不关闭就会一直挂着并随调用次数累积
            if (pool != null) {
                pool.shutdownNow();
            }
        }
        return UNKNOWN;
    }

    /**
     * 请求单个服务并解析国家或地区代码。
     *
     * 只认 200：非 200 时内容可能是错误页或跳转页，解析它没有意义。
     */
    private static String query(String address) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != 200) {
                return UNKNOWN;
            }
            in = conn.getInputStream();
            return parse(in);
        } catch (Throwable e) {
            return UNKNOWN;
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

    private static String parse(InputStream in) {
        JsonReader reader = null;
        try {
            reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String value = walk(reader);
            if (value == null || value.trim().isEmpty()) {
                return UNKNOWN;
            }
            return value.trim().toUpperCase(Locale.ROOT);
        } catch (Throwable e) {
            return UNKNOWN;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * 按字段名取国家或地区代码。
     *
     * 【为什么不按服务分支】
     * 三个服务的字段结构不同，还有的把字段放在嵌套对象里（例如放在 data 中）。
     * 按字段名处理比按网址分支更稳：将来换服务不必改这里，格式变了也能继续工作。
     * 遇到嵌套对象会递归进去找，因此无论字段在哪一层都能取到。
     */
    private static String walk(JsonReader reader) throws Exception {
        reader.beginObject();
        String code = null;
        String country = null;
        String nested = null;
        while (reader.hasNext()) {
            String key = reader.nextName();
            JsonToken token = reader.peek();
            if (token == JsonToken.BEGIN_OBJECT) {
                String inner = walk(reader);
                if (nested == null && inner != null && !inner.isEmpty()) {
                    nested = inner;
                }
            } else if (token == JsonToken.STRING) {
                String v = reader.nextString();
                if ("country_code".equalsIgnoreCase(key) || "countryCode".equalsIgnoreCase(key)) {
                    code = v;
                } else if ("country".equalsIgnoreCase(key)) {
                    country = v;
                }
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        // 国家码优先：country 字段是完整国名，长度和写法都不统一，
        // 而国家码是固定两位的标准编码，更适合用来匹配规则
        if (code != null && !code.trim().isEmpty()) {
            return code;
        }
        if (country != null && !country.trim().isEmpty()) {
            return country;
        }
        return nested;
    }
}
