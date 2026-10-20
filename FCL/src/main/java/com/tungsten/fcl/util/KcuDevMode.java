package com.tungsten.fcl.util;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tungsten.fcl.R;
import com.tungsten.fclauncher.utils.FCLPath;

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

    private static volatile Boolean cached;

    private KcuDevMode() {
    }

    public static File getFile() {
        return new File(FCLPath.KCU_SHARED_ROOT, FILE_NAME);
    }

    /** 结果会缓存；需要重新判定时调用 {@link #refresh()} */
    public static boolean isEnabled() {
        Boolean v = cached;
        if (v != null) {
            return v;
        }
        boolean result = matchFile();
        cached = result;
        return result;
    }

    /** 清除缓存，下次调用重新读取文件 */
    public static void refresh() {
        cached = null;
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
            File marker = new File(FCLPath.KCU_SHARED_ROOT, FCLPath.KCU_ROOT_MARKER);
            if (marker.exists() && marker.delete()) {
                cleared++;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (com.tungsten.fcl.setting.ConfigHolder.config().isKcuAgePrompted()) {
                com.tungsten.fcl.setting.ConfigHolder.config().setKcuAgePrompted(false);
                com.tungsten.fcl.setting.ConfigHolder.config().setKcuAgeDeclared(0);
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

    /** 供调试：写入开关文件 */
    public static boolean create(File target) {
        OutputStream out = null;
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            out = new java.io.FileOutputStream(target, false);
            out.write("# developer mode\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (Throwable e) {
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
