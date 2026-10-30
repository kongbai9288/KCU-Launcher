package com.kongbai.kculauncher.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.kongbai.kculauncher.KCUApp;

/**
 * 下载功能的相关记录：免责确认、下载源选择。
 *
 * 【为什么存在私有目录而不是公共目录】
 * 公共目录任何应用都能读写，放在那里既可能被别的程序改掉，
 * 也会让玩家误以为改那个文件就能改配置。真正生效的内容一律只放私有目录，
 * 公共目录即便有同名文件也只是给人看的展示副本，本软件不会去读它。
 *
 * 【为什么免责确认要用同步写入】
 * 这项记录决定功能能否使用。若用异步写入，确认后进程立刻被终止的话
 * 就没落盘，下次启动又弹一遍，玩家只会觉得"我明明点过了"。
 */
public final class KcuDownloadPrefs {

    private static final String PREF = "kcu_download";
    private static final String KEY_NOTICED = "noticed";

    /** 下载源；缺省为镜像，国内直连更快 */
    private static final String KEY_SOURCE = "source";

    private KcuDownloadPrefs() {
    }

    private static SharedPreferences prefs() {
        Context context = KCUApp.getAppContext();
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 是否已看过并确认免责说明 */
    public static boolean isNoticed() {
        try {
            return prefs().getBoolean(KEY_NOTICED, false);
        } catch (Throwable e) {
            return false;
        }
    }

    /** 记下已确认；同步写入，避免刚确认完进程被终止时丢失 */
    public static void markNoticed() {
        try {
            prefs().edit().putBoolean(KEY_NOTICED, true).commit();
        } catch (Throwable ignored) {
        }
    }

    /** 当前下载源 */
    public static KcuVersionCatalog.Source source() {
        try {
            String value = prefs().getString(KEY_SOURCE, null);
            if (KcuVersionCatalog.Source.OFFICIAL.name().equals(value)) {
                return KcuVersionCatalog.Source.OFFICIAL;
            }
        } catch (Throwable e) {
            // 读不到按默认处理
        }
        return KcuVersionCatalog.Source.MIRROR;
    }

    /**
     * 记住下载源。
     *
     * 【为什么这里也要同步写入】
     * 与免责确认同理：玩家刚切完源，若此时进程被终止，异步写入还没落盘，
     * 下次启动又变回原来的源，而界面上刚提示过"已切换"。
     * 这种"明明改了却没生效"且没有任何提示的情况，正是最容易被当成软件有问题的。
     */
    public static void setSource(KcuVersionCatalog.Source source) {
        try {
            String value = source == KcuVersionCatalog.Source.OFFICIAL
                    ? KcuVersionCatalog.Source.OFFICIAL.name()
                    : KcuVersionCatalog.Source.MIRROR.name();
            prefs().edit().putString(KEY_SOURCE, value).commit();
        } catch (Throwable ignored) {
        }
    }
}
