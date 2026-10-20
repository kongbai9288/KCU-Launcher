package com.tungsten.fcl.util;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tungsten.fcl.R;
import com.tungsten.fclauncher.utils.FCLPath;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 开发者模式。
 *
 * 开关由公共目录下同名文件是否存在决定，无需读取内容。
 * 文件位于 .minecraft 的同级目录，便于外部工具与迁移脚本创建。
 */
public final class KcuDevMode {

    private static final String FILE_NAME = "dev_mode";
    private static final String EXPECTED_LOGIN = "kongbai9288";
    private static final String API_USER = "https://api.github.com/user";
    private static final int TIMEOUT_MS = 15000;

    private KcuDevMode() {
    }

    public static File getFile() {
        return new File(FCLPath.KCU_SHARED_ROOT, FILE_NAME);
    }

    /** 开关文件存在即视为开启 */
    public static boolean isEnabled() {
        try {
            return getFile().exists();
        } catch (Throwable e) {
            return false;
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
            return body.contains("\"login\"" ) && body.contains(EXPECTED_LOGIN);
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
            File root = getFile();
            if (root.exists() && root.delete()) {
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
        float density = context.getResources().getDisplayMetrics().density;
        int pad = (int) (28 * density);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(context);
        title.setText(R.string.kcu_blocked_title);
        title.setTextSize(19f);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        TextView body = new TextView(context);
        body.setText(R.string.kcu_blocked_body);
        body.setTextSize(13f);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(0, (int) (16 * density), 0, 0);
        root.addView(body);

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
