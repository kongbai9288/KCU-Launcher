package com.kongbai.kculauncher.upgrade;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;

import java.util.concurrent.atomic.AtomicBoolean;

public class UpdateChecker {

    private static UpdateChecker instance;

    public static UpdateChecker getInstance() {
        if (instance == null) {
            instance = new UpdateChecker();
        }
        return instance;
    }

    /** 检查更新进行中：同步 CAS 置位，快速连点在异步任务起跑前即被忽略 */
    private final AtomicBoolean checking = new AtomicBoolean(false);

    /** 拉取当前版本更新内容进行中：同上防抖 */
    private final AtomicBoolean changelogLoading = new AtomicBoolean(false);

    public boolean isChecking() {
        return checking.get();
    }

    public UpdateChecker() {

    }

    public Task<?> checkManually(Context context) {
        return check(context, true, true);
    }

    /**
     * 拉取版本表并弹出当前版本的更新内容（设置页"查看更新内容"入口）。
     * 版本表中没有当前版本条目（官方下架或自定义构建）时给出提示。
     */
    /**
     * 上游更新源已停用。
     * 原实现向 KCU 官方仓库（GitHub raw / Gitee）拉取版本表。KCU 版本号与 KCU 不同源，
     * 直接比较会让用户被提示去下载 KCU 的 APK，属错误行为。
     * KCU 尚未建立自有更新源（产物目前仅为 Actions artifacts，无稳定下载直链），故此处不联网。
     */
    public Task<?> showCurrentChangelog(Context context) {
        return Task.runAsync(() -> {
        });
    }

    public Task<?> checkAuto(Context context) {
        return check(context, false, false);
    }

    /** 上游更新源已停用，原因见 {@link #showCurrentChangelog}。此处不联网、不弹窗。 */
    public Task<?> check(Context context, boolean showBeta, boolean showAlert) {
        return Task.runAsync(() -> {
        });
    }

    private void showUpdateDialog(Context context, RemoteVersion version) {
        Schedulers.androidUIThread().execute(() -> {
            UpdateDialog dialog = new UpdateDialog(context, version);
            dialog.show();
        });
    }

    public static boolean isIgnore(Context context, int code) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        return sharedPreferences.getInt("ignore_update", -1) == code;
    }

    public static void setIgnore(Context context, int code) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        @SuppressLint("CommitPrefEdits") SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("ignore_update", code);
        editor.apply();
    }

}
