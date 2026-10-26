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

    /** 请求开启的文件名：外部把内容改成 true 即表示"我要开开发者模式" */
    private static final String REQUEST_FILE_NAME = "dev_request";
    /** 授权标记的文件名：位于应用私有目录，公共目录无法伪造 */
    private static final String AUTH_FILE_NAME = ".kcu_dev_authorized";
    /** 授权标记的内容：只作存在性之外的第二道校验，避免空白文件被当作已授权 */
    private static final String AUTH_CONTENT = "kcu.dev.authorized.v1";
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

    /** 写入请求文件。连点达标时调用，之后玩家下次进入应用会被要求填令牌 */
    public static void markRequested() {
        try {
            File file = getRequestFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            java.io.OutputStream out = null;
            try {
                out = new java.io.FileOutputStream(file, false);
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
            while ((n = in.read(buf)) > 0) {
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
        // 授权标记优先：它由 PAT 校验通过后写入私有目录，是正规途径。
        // 仍保留对旧开关文件的识别，避免此前已开启的玩家被意外关掉
        boolean result = hasAuthorization() || matchFile();
        cached = result;
        return result;
    }

    /** 清除缓存，下次调用重新读取文件 */
    public static void refresh() {
        cached = null;
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
     * 校验令牌归属。仅在返回 true 时才允许执行敏感操作。
     * 网络不可用、非 200、响应中不含预期账号名，一律视为不通过。
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
            return body.contains("\"login\"") && body.contains(EXPECTED_LOGIN);
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

    private static String readAll(InputStream in) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
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
