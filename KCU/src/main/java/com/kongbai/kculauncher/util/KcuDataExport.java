package com.kongbai.kculauncher.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.kongbai.kculauncher.KCUApp;
import com.kongbai.kculauncher.launcher.utils.KCUPath;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 导出一份可读的数据摘要，供强制停止等场景下玩家留存。
 *
 * 【为什么只导出摘要而不是整个数据目录】
 * 数据目录里含有登录凭据一类的敏感内容，整体拷出去等于把账号交出去，
 * 风险远大于收益。这里只收集"有哪些东西"这类清单信息，
 * 不写入任何凭据、令牌或密码，玩家拿到后可以放心传阅。
 *
 * 【为什么导出到公共目录】
 * 导出就是为了让人能取走。放私有目录的话玩家还得想办法拿出来，
 * 而这里的内容已经确认不含敏感信息，放在公共目录更实用。
 */
public final class KcuDataExport {

    private KcuDataExport() {
    }

    /**
     * 生成导出文件。
     *
     * 返回成功写出的文件，失败返回 null。
     * 必须在后台线程调用：涉及文件遍历与写入。
     */
    public static File write() {
        try {
            StringBuilder text = new StringBuilder();
            appendLine(text, "KCU 数据摘要");
            appendLine(text, "导出时间 " + stamp());
            appendLine(text, "");

            appendLine(text, "【环境】");
            appendLine(text, "应用版本 " + orDefault(KcuTarget.currentVersion(), "未知"));
            appendLine(text, "客户端 " + KcuTarget.currentClient());
            appendLine(text, "系统 " + android.os.Build.VERSION.RELEASE
                    + " (API " + android.os.Build.VERSION.SDK_INT + ")");
            appendLine(text, "设备 " + android.os.Build.MANUFACTURER + " "
                    + android.os.Build.MODEL);
            appendLine(text, "");

            appendLine(text, "【实例】");
            List<String> instances = listNames(instanceDir());
            if (instances.isEmpty()) {
                appendLine(text, "（无）");
            } else {
                for (String name : instances) {
                    appendLine(text, "- " + name);
                }
            }
            appendLine(text, "");

            appendLine(text, "【按键布局】");
            List<String> layouts = listNames(layoutDir());
            if (layouts.isEmpty()) {
                appendLine(text, "（无）");
            } else {
                for (String name : layouts) {
                    appendLine(text, "- " + name);
                }
            }
            appendLine(text, "");

            appendLine(text, "【设置项】");
            appendSettings(text);
            appendLine(text, "");

            appendLine(text, "说明：本文件只记录清单与设置摘要，不包含任何登录凭据。");

            File dir = exportDir();
            if (dir == null) {
                return null;
            }
            File file = new File(dir, "kcu_export_" + stamp() + ".txt");
            File tmp = new File(dir, "kcu_export_" + stamp() + ".tmp");
            OutputStream out = new FileOutputStream(tmp, false);
            try {
                out.write(text.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
            } finally {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.delete();
                return null;
            }
            return file;
        } catch (Throwable e) {
            return null;
        }
    }

    private static void appendLine(StringBuilder text, String line) {
        text.append(line).append('\n');
    }

    private static String stamp() {
        try {
            return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        } catch (Throwable e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static File instanceDir() {
        try {
            return new File(KCUPath.KCU_SHARED_ROOT, "instances");
        } catch (Throwable e) {
            return null;
        }
    }

    private static File layoutDir() {
        try {
            Context context = KCUApp.getAppContext();
            if (context == null) {
                return null;
            }
            File dir = new File(context.getFilesDir(), "control");
            return dir.isDirectory() ? dir : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static File exportDir() {
        try {
            File dir = new File(KCUPath.KCU_SHARED_ROOT, "export");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            return dir;
        } catch (Throwable e) {
            return null;
        }
    }

    /** 列出目录下的子项名称；取不到就返回空列表 */
    private static List<String> listNames(File dir) {
        List<String> out = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) {
            return out;
        }
        try {
            File[] items = dir.listFiles();
            if (items == null) {
                return out;
            }
            for (File one : items) {
                out.add(one.getName());
            }
        } catch (Throwable e) {
            // 遍历失败返回已拿到的部分即可，这是清单不是判定依据
        }
        return out;
    }

    /**
     * 附加设置摘要。
     *
     * 只挑选通用且非敏感的开关项逐个记录，读不到就跳过。
     * 这里刻意不遍历全部键：设置里可能存有第三方服务的地址与标识，
     * 整体罗列会把不该外传的内容一并写出。
     */
    private static void appendSettings(StringBuilder text) {
        Context context = KCUApp.getAppContext();
        if (context == null) {
            appendLine(text, "（读不到）");
            return;
        }
        String[] files = {"launcher", "kcu_download", "menu_setting"};
        String[] keys = {"isAgree", "noticed", "source", "kcu_layout_enabled"};
        boolean any = false;
        for (String file : files) {
            try {
                SharedPreferences prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE);
                for (String key : keys) {
                    if (!prefs.contains(key)) {
                        continue;
                    }
                    Object value = prefs.getAll().get(key);
                    appendLine(text, "- " + file + "/" + key + " = " + value);
                    any = true;
                }
            } catch (Throwable e) {
                // 单个文件读不到不影响其他
            }
        }
        if (!any) {
            appendLine(text, "（无可导出项）");
        }
    }
}
