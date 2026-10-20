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
import java.nio.charset.StandardCharsets;

/**
 * 扩展登录方式开关。
 *
 * 开关文件位于公共目录下，便于外部工具读写；文件不存在或内容无法解析时一律视为关闭。
 */
public final class KcuDevMode {

    private static final String FILE_NAME = "dev_mode";
    private static final String KEY = "developerMode";

    private KcuDevMode() {
    }

    public static File getFile() {
        return new File(FCLPath.KCU_SHARED_ROOT, FILE_NAME);
    }

    /**
     * 是否开放全部登录方式。
     * 只在文件存在且内容明确为开启时返回 true。
     */
    public static boolean isEnabled() {
        try {
            File file = getFile();
            if (!file.isFile()) {
                return false;
            }
            String text = readAll(file);
            if (text == null) {
                return false;
            }
            String lower = text.toLowerCase();
            if (lower.contains("\"" + KEY + "\"") && lower.contains("true")) {
                return true;
            }
            return lower.trim().equals("true");
        } catch (Throwable e) {
            return false;
        }
    }

    private static String readAll(File file) {
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
            return new String(raw, 0, off, StandardCharsets.UTF_8);
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
}
