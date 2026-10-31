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

            // 按候选依次找能写的地方：这条路是强制停止页唯一的出口，
            // 公共目录写不进去就直接失败的话，等于把最后一条取回数据的路也堵死
            for (File dir : exportDirs()) {
                if (dir == null) {
                    continue;
                }
                File written = writeInto(dir, text.toString());
                if (written != null) {
                    return written;
                }
            }
            return null;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 往某个目录里写一份。写不进去返回 null，交给调用方换下一个候选。
     */
    private static File writeInto(File dir, String text) {
        String name = "kcu_export_" + stamp();
        File file = new File(dir, name + ".txt");
        File tmp = new File(dir, name + ".tmp");
        OutputStream out = null;
        boolean ok = false;
        try {
            out = new FileOutputStream(tmp, false);
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
            ok = true;
        } catch (Throwable e) {
            return null;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }
        if (!ok) {
            if (!tmp.delete()) {
                // 删不掉也无所谓，下次会覆盖同名临时文件
            }
            return null;
        }
        if (!tmp.renameTo(file)) {
            if (!tmp.delete()) {
                // 同上
            }
            return null;
        }
        return file;
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

    /**
     * 导出目录的候选，按理想程度从高到低排。
     *
     * 【为什么要有第二个候选】
     * 强制停止页是在启动的最前面弹出来的，早于权限申请。
     * 全新安装、权限被撤销、或者用户之前拒过，公共存储就写不进去，
     * 而导出是那个页面唯一的出口 —— 没有备选等于把人堵死。
     *
     * 【为什么第二候选是应用专属外部目录而不是私有目录】
     * 私有目录下的文件用户根本取不到，导出了等于没导出。
     * 应用专属外部目录不需要任何权限就能写，用户也还有办法取走。
     *
     * 【为什么不用内部目录兜底】
     * 落在内部目录里用户拿不到，给一条取不到的路径只会让人更困惑，
     * 两条都不通时宁可明确告知失败。
     */
    private static List<File> exportDirs() {
        List<File> out = new ArrayList<>();
        try {
            File dir = new File(KCUPath.KCU_SHARED_ROOT, "export");
            if (dir.isDirectory() || dir.mkdirs()) {
                out.add(dir);
            }
        } catch (Throwable ignored) {
            // 公共目录用不了就换下一个
        }
        try {
            Context context = KCUApp.getAppContext();
            if (context != null) {
                File external = context.getExternalFilesDir(null);
                if (external != null) {
                    File dir = new File(external, "export");
                    if (dir.isDirectory() || dir.mkdirs()) {
                        out.add(dir);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 同上
        }
        return out;
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
