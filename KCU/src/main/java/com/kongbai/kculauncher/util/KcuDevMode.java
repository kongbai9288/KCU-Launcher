package com.kongbai.kculauncher.util;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.launcher.utils.KCUPath;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 开发者模式。
 *
 * 开关文件位于公共目录下，与 .minecraft 同级。
 * 文件内容的 SHA3-256 必须与内置值完全一致才会开启，仅存在不足以开启。
 */
public final class KcuDevMode {

    private static final String FILE_NAME = "dev_mode";
    private static final String EXPECTED_SHA3 = "4c238702173b7a01d082f2c6f840d8f149e28e6b081a60e54b452d7c9c5453e0";
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final String EXPECTED_LOGIN = "kongbai9288";
    private static final String API_USER = "https://api.github.com/user";
    private static final int TIMEOUT_MS = 15000;

    /**
     * 网络参照站点：用于确认"网络真的通着"，而不只是这一个接口有回应。
     *
     * 【为什么用专门的连通性检测点，而不是普通网站首页】
     * 这类检测点是各系统厂商专门部署、只用来回答"网络通不通"的地址，
     * 多数固定返回 204 且不带任何正文：只要状态码不对，就能立刻判定链路上有人插了东西。
     * 普通网站首页会返回大段内容、可能跳转、也可能因地区或风控返回不同页面，
     * 用它来判断连通性既不干净也不稳定。
     * 少数厂商的检测点返回 200 并带一小段固定正文（例如苹果用的是 html 页），
     * 因此下方判定同时接受 200 与 204。
     *
     * 【为什么国内与海外各放三组】
     * 判据是"只要有一个站点回应就算网络正常"，因此站点必须覆盖两种网络环境：
     * 只放海外站点的话，国内用户（该接口还能访问，但海外站点普遍不通）会被
     * 判成可疑，等于把真正的主人锁在门外；只放国内站点则海外用户遭遇同样问题。
     * 两边各放三组，任一边通着就放行。
     */
    private static final String[] NET_PROBES = {
            "https://connect.rom.miui.com/generate_204",                       // 小米 国内
            "https://connectivitycheck.platform.hicloud.com/generate_204",      // 华为 国内
            "https://connect.qq.com/generate_204",                              // 腾讯 国内
            "https://cp.cloudflare.com/generate_204",                           // Cloudflare 海外
            "https://captive.apple.com/hotspot-detect.html",                    // Apple 海外
            "https://connectivitycheck.gstatic.com/generate_204"                // Google 海外
    };
    private static final int PROBE_TIMEOUT_MS = 6000;

    /** 请求开启的文件名：外部把内容改成 true 即表示"我要开开发者模式" */
    private static final String REQUEST_FILE_NAME = "dev_request";
    /** 授权标记的文件名：位于应用私有目录，公共目录无法伪造 */
    private static final String AUTH_FILE_NAME = ".kcu_dev_authorized";
    /** 授权标记的内容：只作存在性之外的第二道校验，避免空白文件被当作已授权 */
    private static final String AUTH_CONTENT = "kcu.dev.authorized.v1";
    /** 许可标识单独存放，不并入授权标记，理由见 {@link #setGrantId(String)} */
    private static final String GRANT_ID_FILE_NAME = ".kcu_dev_grant_id";
    /** 触发所需的连点次数 */
    public static final int UNLOCK_TAPS = 5;

    private static volatile Boolean cached;

    /**
     * 本次进程是否已经弹过令牌框。
     *
     * 没有它会出现这样的死循环：玩家连点五次写入请求文件，
     * 进入应用弹出令牌框，取消或校验失败后流程停在启动页，
     * 下次进入又弹 —— 玩家连点之后就再也进不了启动器。
     * 因此无论校验结果如何都必须放行，且本次进程内只提示一次。
     */
    private static volatile boolean unlockPrompted;

    /**
     * 私有目录写入需要 Context，而判断是否开启的调用点分布在各处 UI，
     * 逐个传参会牵动过多文件。这里只保存 Application 的上下文，
     * 它与进程同生命周期，不会持有 Activity，不构成泄漏。
     */
    private static volatile Context appContext;

    private KcuDevMode() {
    }

    public static File getFile() {
        return new File(KCUPath.KCU_SHARED_ROOT, FILE_NAME);
    }

    /**
     * 保存 Application 上下文，供私有目录读写使用。
     * 应在应用启动早期调用一次。
     */
    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    /** 请求开启的文件：位于公共目录，便于玩家用任意文件管理器改成 true */
    public static File getRequestFile() {
        return new File(KCUPath.KCU_SHARED_ROOT, REQUEST_FILE_NAME);
    }

    /** 已请求但尚未授权：此时应当弹出令牌输入框 */
    public static boolean isRequested() {
        try {
            File file = getRequestFile();
            if (!file.isFile()) {
                return false;
            }
            long len = file.length();
            if (len <= 0 || len > 64) {
                return false;
            }
            byte[] raw = new byte[(int) len];
            InputStream in = null;
            try {
                in = new FileInputStream(file);
                int off = 0;
                while (off < raw.length) {
                    int n = in.read(raw, off, raw.length - off);
                    if (n <= 0) {
                        break;
                    }
                    off += n;
                }
                if (off != raw.length) {
                    return false;
                }
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
            return "true".equalsIgnoreCase(new String(raw, StandardCharsets.UTF_8).trim());
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 写入请求文件。连点达标时调用，之后玩家下次进入应用会被要求填令牌。
     *
     * 先写临时文件再改名：直接截断写若中途出问题，留在磁盘上的可能是
     * 既非旧值也非 true 的半截内容，本次连点就白按了，
     * 而界面上刚提示过可以填令牌，看上去自相矛盾。
     */
    public static void markRequested() {
        try {
            File file = getRequestFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            File tmp = new File(parent, REQUEST_FILE_NAME + ".tmp");
            java.io.OutputStream out = null;
            try {
                out = new java.io.FileOutputStream(tmp, false);
                out.write("true".getBytes(StandardCharsets.UTF_8));
                out.flush();
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.delete();
            }
        } catch (Throwable ignored) {
            // 写不出来只是无法触发解锁流程，不应影响当前界面
        }
    }

    /**
     * 授权：写入私有目录标记并清掉请求文件。
     *
     * 【为什么授权标记放私有目录】
     * 请求文件在公共目录，任何应用都能写成 true；若开启与否只认它，
     * 等于任何人都能开开发者模式。真正的授权凭据必须落在别的应用写不到的地方。
     *
     * @return 写入成功返回 true
     */
    public static boolean grant() {
        Context ctx = appContext;
        if (ctx == null) {
            return false;
        }
        boolean written = false;
        java.io.OutputStream out = null;
        try {
            out = ctx.openFileOutput(AUTH_FILE_NAME, Context.MODE_PRIVATE);
            out.write(AUTH_CONTENT.getBytes(StandardCharsets.UTF_8));
            out.flush();
            written = true;
        } catch (Throwable ignored) {
            written = false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }
        if (written) {
            try {
                File req = getRequestFile();
                if (req.isFile()) {
                    req.delete();
                }
            } catch (Throwable ignored) {
            }
        }
        refresh();
        return written;
    }

    /** 私有目录中的授权标记是否存在且内容正确 */
    private static boolean hasAuthorization() {
        Context ctx = appContext;
        if (ctx == null) {
            return false;
        }
        InputStream in = null;
        try {
            in = ctx.openFileInput(AUTH_FILE_NAME);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            // 以 -1 为结束：个别流会返回 0，按 > 0 判结束会提前截断。
            // 这里截断的后果是已授权用户被判成未授权，开发者模式静默失效
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return AUTH_CONTENT.equals(new String(out.toByteArray(), StandardCharsets.UTF_8).trim());
        } catch (Throwable e) {
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 结果会缓存；需要重新判定时调用 {@link #refresh()} */
    public static boolean isEnabled() {
        Boolean v = cached;
        if (v != null) {
            return v;
        }
        // 远程开关先判：它管的就是"这个版本的开发者模式不许开"，
        // 优先级高于本机已经开过 —— 本机授权是本地文件，
        // 真到需要紧急收回的时候，等本地那个慢慢失效来不及
        boolean result = !KcuDevPolicy.isDisabled()
                && (hasAuthorization() || matchFile());
        cached = result;
        return result;
    }

    /** 清除缓存，下次调用重新读取文件 */
    public static void refresh() {
        cached = null;
    }

    /**
     * 记下本次授权用的是哪一条许可，供日后判断是否已被收回。
     *
     * 【为什么单独放一个文件，而不是把标识写进授权标记里】
     * 授权标记的内容是固定值，已开启过的人（包括已经开启的本人）都靠它识别。
     * 一旦改变写法，判断就会因为"内容对不上"而把人判成未授权 ——
     * 那等于把已经开着的人一并关掉，而且他自己也未必知道是怎么关的。
     * 所以这里另开一个文件，读不到只是"没有可收回的信息"，
     * 对既有授权没有任何影响。
     */
    public static void setGrantId(String id) {
        Context ctx = appContext;
        if (ctx == null || id == null || id.isEmpty()) {
            return;
        }
        java.io.OutputStream out = null;
        try {
            out = ctx.openFileOutput(GRANT_ID_FILE_NAME, Context.MODE_PRIVATE);
            out.write(id.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Throwable ignored) {
            // 记不下就记不下：它只用于日后收回，不影响本次已经完成的授权
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 取本次授权用的许可标识；没有返回空串 */
    public static String grantId() {
        Context ctx = appContext;
        if (ctx == null) {
            return "";
        }
        InputStream in = null;
        try {
            in = ctx.openFileInput(GRANT_ID_FILE_NAME);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (Throwable e) {
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 收回本次授权：清掉标记与标识，并让缓存失效 */
    public static void revokeGrant() {
        Context ctx = appContext;
        if (ctx != null) {
            try {
                ctx.deleteFile(AUTH_FILE_NAME);
            } catch (Throwable ignored) {
            }
            try {
                ctx.deleteFile(GRANT_ID_FILE_NAME);
            } catch (Throwable ignored) {
            }
        }
        refresh();
    }

    /** 本次进程是否已提示过解锁。提示过就不再重复弹，避免锁死启动流程 */
    public static boolean isUnlockPrompted() {
        return unlockPrompted;
    }

    public static void markUnlockPrompted() {
        unlockPrompted = true;
    }

    private static boolean matchFile() {
        InputStream in = null;
        try {
            File file = getFile();
            if (!file.isFile()) {
                return false;
            }
            long len = file.length();
            if (len <= 0 || len > MAX_BYTES) {
                return false;
            }
            byte[] raw = new byte[(int) len];
            in = new FileInputStream(file);
            int off = 0;
            while (off < raw.length) {
                int n = in.read(raw, off, raw.length - off);
                if (n <= 0) {
                    break;
                }
                off += n;
            }
            if (off != raw.length) {
                return false;
            }
            String actual = Sha3.sha3256Hex(raw);
            return !actual.isEmpty() && actual.equals(EXPECTED_SHA3);
        } catch (Throwable e) {
            return false;
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
     * 单个参照站点是否给出了明确回应。
     *
     * 只认 200 与 204：204 正是连通性检测点的标准回答（无正文），
     * 200 用于兼容少数返回正文的检测点。抓包工具可能返回伪造的错误页或跳转，
     * 只有明确的成功状态才说明这一路请求确实到达了对方。
     * 不读取响应内容 —— 只需要知道有没有回应，读内容既慢又无必要。
     */
    private static boolean reachable(String address) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(PROBE_TIMEOUT_MS);
            conn.setReadTimeout(PROBE_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            return code == 200 || code == 204;
        } catch (Throwable e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 网络是否看起来是真的通着。
     *
     * 【为什么需要这一步】
     * 抓包改包类工具通常先接管全部流量并拦下所有请求，再由使用者挑出要放行的那几个。
     * 这种情况下会出现一种很不寻常的组合：目标接口有回应，而同一时刻其它任何站点
     * 都没有回应。真实网络不会这样 —— 能访问到该接口，就说明链路是通的，
     * 参照站点不该集体沉默。出现这种组合时，目标接口的回应极可能是本地伪造的。
     *
     * 【为什么不要求多数站点回应】
     * 只要求至少一个：区域网络差异是常态而非异常，海外站点在国内不通、
     * 国内站点在海外也可能不通。要求多数会把大量正常环境判成可疑，
     * 而这一步的目的是拦住"全部沉默"这种极端情况，不是做严格的网络体检。
     *
     * 【为什么探测本身失败时返回 true】
     * 探测机制自身出故障（线程池建不起来等）时不能据此拒绝 ——
     * 那会把真正的主人锁在门外，而真正的网络不通已经在令牌请求上体现过了。
     * 这里是旁路的补充确认，不是主要判据。
     */
    public static boolean networkLooksReal() {
        final String[] targets = NET_PROBES;
        ExecutorService pool = null;
        try {
            pool = Executors.newFixedThreadPool(targets.length);
            List<Future<Boolean>> futures = new ArrayList<>(targets.length);
            for (String address : targets) {
                futures.add(pool.submit(() -> reachable(address)));
            }
            int ok = 0;
            for (Future<Boolean> one : futures) {
                try {
                    if (Boolean.TRUE.equals(
                            one.get(PROBE_TIMEOUT_MS + 2000L, TimeUnit.MILLISECONDS))) {
                        ok++;
                    }
                } catch (Throwable ignored) {
                    // 单个站点取不到结果按没回应处理
                }
            }
            return ok >= 1;
        } catch (Throwable e) {
            return true;
        } finally {
            // 这些线程不是守护线程，不关闭就会一直挂着并随重试次数累积
            if (pool != null) {
                pool.shutdownNow();
            }
        }
    }

    /**
     * 校验令牌归属。仅在返回 true 时才允许执行敏感操作。
     * 网络不可用、非 200、返回的登录名与预期不一致，一律视为不通过。
     *
     * 【为什么必须精确取 login 字段的值，不能用包含判断】
     * 包含判断下，登录名只要"含有"预期字符串就会通过：
     * 别人注册一个名为 kongbai9288abc 的账号，用它自己的令牌也能通过校验，
     * 等于开发者模式对任何有心人开放。取字段值再整体比对才没有这种绕过。
     */
    public static boolean verifyOwner(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(API_USER).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token.trim());
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            int code = conn.getResponseCode();
            if (code != 200) {
                return false;
            }
            in = conn.getInputStream();
            String body = readAll(in);
            if (body == null) {
                return false;
            }
            String login = readLogin(body);
            if (login == null) {
                return false;
            }
            // 登录名整体比对。GitHub 不允许仅大小写不同的用户名并存，
            // 因此忽略大小写不会放过他人，只是避免本人因大小写差异被锁在门外
            if (!EXPECTED_LOGIN.equalsIgnoreCase(login.trim())) {
                return false;
            }
            // 令牌本身没问题，再确认一下网络是不是真的通着。
            // 只对"令牌看起来有效"这一种少见情况做这一步：
            // 令牌填错时直接返回，不必让正常玩家多等这几秒
            if (!networkLooksReal()) {
                android.util.Log.w("KCU",
                        "Dev unlock rejected: token valid but no network probe answered");
                return false;
            }
            return true;
        } catch (Throwable e) {
            return false;
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

    /**
     * 清除全部标记。调用前必须先通过 {@link #verifyOwner}。
     * 返回被清除的项数量。
     */
    public static int clearMarks() {
        int cleared = 0;
        try {
            File dev = getFile();
            if (dev.exists() && dev.delete()) {
                cleared++;
            }
        } catch (Throwable ignored) {
        }
        try {
            File marker = new File(KCUPath.KCU_SHARED_ROOT, KCUPath.KCU_ROOT_MARKER);
            if (marker.exists() && marker.delete()) {
                cleared++;
            }
        } catch (Throwable ignored) {
        }
        try {
            KcuAgeGate.reset();
            cleared++;
        } catch (Throwable ignored) {
        }
        try {
            Context ctx = appContext;
            if (ctx != null) {
                ctx.deleteFile(AUTH_FILE_NAME);
                cleared++;
            }
        } catch (Throwable ignored) {
        }
        try {
            File req = getRequestFile();
            if (req.isFile() && req.delete()) {
                cleared++;
            }
        } catch (Throwable ignored) {
        }
        refresh();
        return cleared;
    }

    /**
     * 取响应里的 login 字段值。
     *
     * 只认这个字段：其余字段（昵称、简介、公司等）由账号持有者自行填写，
     * 不能作为归属依据。解析失败返回 null 而不是回退其他匹配方式。
     */
    private static String readLogin(String body) {
        android.util.JsonReader reader = null;
        try {
            reader = new android.util.JsonReader(new java.io.StringReader(body));
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("login".equals(key)) {
                    return reader.nextString();
                }
                reader.skipValue();
            }
            reader.endObject();
        } catch (Throwable e) {
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static String readAll(InputStream in) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            // 同上：按 > 0 判结束会截断，截断后 JSON 解析失败，
            // 结果是本人拿着有效令牌也通过不了校验
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 受限状态下的提示界面 */
    public static android.view.View buildBlockedView(Context context) {
        LinearLayout root = KcuUi.root(context);
        LinearLayout card = KcuUi.card(context);
        card.addView(KcuUi.title(context, context.getString(R.string.kcu_blocked_title)));
        card.addView(KcuUi.gap(context, 12));
        card.addView(KcuUi.body(context, context.getString(R.string.kcu_blocked_body)));
        root.addView(card);
        return root;
    }
}
