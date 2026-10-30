package com.kongbai.kculauncher.util;

import android.util.JsonReader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 远端内容的镜像选择。
 *
 * 【为什么要有镜像】
 * 直连某些境外代码托管服务在国内经常超时或极慢。规则本体很小，
 * 但如果在启动路径上一直等一个连不上的地址，启动就会被拖住。
 * 走镜像可以显著提高成功率。
 *
 * 【为什么镜像地址不能写死】
 * 镜像站的可用性会变，有的今天能用明天就关了。写死的话，
 * 一旦镜像失效，只能靠重新发版来换地址，而已经发出去的旧版本会一直卡着。
 * 因此镜像列表本身也放在远端：启动时先取一次镜像清单，按清单里的地址去取内容。
 *
 * 【为什么仍保留一个内置地址】
 * 第一次启动时本地什么都没有，必须有一个能用的起点，否则永远拿不到清单。
 * 这个内置地址只是起点，一旦取到清单就以清单为准，之后可以随时更换。
 *
 * 【镜像清单的写法】
 * 每一条是一个带占位符的模板，取内容时把占位符换成完整的原始地址：
 *   https://某镜像/{url}
 * 这样不论镜像是前缀型还是改写型都能适应，不必为每种镜像单独适配。
 */
public final class KcuMirror {

    private static final String TAG = "KCU";
    private static final String PLACEHOLDER = "{url}";

    /** 内置起点：仅在本地还没有清单时使用，取到清单后即被覆盖 */
    private static final String BOOTSTRAP =
            "https://gitee.com/kongbai9288/kcu-mirrors/raw/main/mirrors.json";

    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 65536;

    private static volatile List<String> templates = new ArrayList<>();
    private static volatile boolean loaded = false;
    /** 引导地址也可被清单覆盖，避免起点失效后无法自救 */
    private static volatile String bootstrap = BOOTSTRAP;

    private KcuMirror() {
    }

    /**
     * 载入镜像清单。
     *
     * 必须在后台线程调用。
     *
     * 【为什么失败时保持原样】
     * 取不到新清单就用上一次的，也没有就空着走直连。
     * 绝不因为取不到清单而清空已有清单 —— 那会让本来能用的镜像突然失效。
     */
    public static void load() {
        String body = get(bootstrap);
        if (body == null || body.isEmpty()) {
            return;
        }
        List<String> parsed = parse(body);
        if (parsed == null || parsed.isEmpty()) {
            return;
        }
        templates = parsed;
        loaded = true;
    }

    /** 当前生效的镜像数量，供排查用 */
    public static int count() {
        return templates.size();
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * 按镜像清单展开出候选地址，原始地址排在最后兜底。
     *
     * 【为什么原始地址要保留在最后】
     * 镜像是为了提速和提成功率，不是唯一途径。
     * 所有镜像都不可用时，直连至少还有机会成功。
     */
    public static List<String> candidates(String origin) {
        List<String> out = new ArrayList<>();
        if (origin == null || origin.isEmpty()) {
            return out;
        }
        for (String template : templates) {
            try {
                if (template.contains(PLACEHOLDER)) {
                    out.add(template.replace(PLACEHOLDER, origin));
                } else {
                    // 没写占位符就按前缀拼接处理
                    out.add(template + origin);
                }
            } catch (Throwable e) {
                // 单条模板有问题不影响其他
            }
        }
        out.add(origin);
        return out;
    }

    /**
     * 按顺序尝试各镜像，取回内容。
     *
     * 必须在后台线程调用。全部失败时返回 null。
     */
    public static String fetch(String origin) {
        List<String> urls = candidates(origin);
        for (String address : urls) {
            String body = get(address);
            if (body != null && !body.isEmpty()) {
                return body;
            }
        }
        return null;
    }

    private static List<String> parse(String body) {
        JsonReader reader = null;
        try {
            reader = new JsonReader(new InputStreamReader(
                    new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8));
            List<String> out = new ArrayList<>();
            String newBootstrap = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("mirrors".equals(key)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        String one = reader.nextString();
                        if (one != null && !one.trim().isEmpty()) {
                            out.add(one.trim());
                        }
                    }
                    reader.endArray();
                } else if ("bootstrap".equals(key)) {
                    newBootstrap = reader.nextString();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            // 清单里给了新的引导地址就换掉，起点失效时也能自救
            if (newBootstrap != null && !newBootstrap.trim().isEmpty()) {
                bootstrap = newBootstrap.trim();
            }
            return out;
        } catch (Throwable e) {
            return null;
        } finally {
            close(reader);
        }
    }

    private static String get(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int total = 0;
            int n;
            // 用 != -1 判结束：个别流会返回 0，按 > 0 判会提前结束导致内容被截断
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BYTES) {
                    return null;
                }
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        } finally {
            close(in);
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void close(Object closeable) {
        if (closeable == null) {
            return;
        }
        try {
            if (closeable instanceof InputStream) {
                ((InputStream) closeable).close();
            } else if (closeable instanceof JsonReader) {
                ((JsonReader) closeable).close();
            }
        } catch (Throwable ignored) {
        }
    }
}
