package com.kongbai.kculauncher.ui.setting;

import static android.content.Context.MODE_PRIVATE;
import static com.kongbai.kculauncher.setting.ConfigHolder.config;
import static com.kongbai.kculauncher.core.util.Lang.thread;
import static com.kongbai.kculauncher.core.util.Logging.LOG;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

import android.net.Uri;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.kongbai.kculauncher.mio.ui.adapter.SpacingItemDecoration;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.activity.MainActivity;
import com.kongbai.kculauncher.databinding.PageSettingListBinding;
import com.kongbai.kculauncher.setting.DownloadSource;
import com.kongbai.kculauncher.upgrade.UpdateChecker;
import com.kongbai.kculauncher.core.mod.RemoteModCache;
import com.kongbai.kculauncher.launcher.utils.KCUPath;
import com.kongbai.kculauncher.core.task.FetchTask;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.util.Logging;
import com.kongbai.kculauncher.core.util.io.FileUtils;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;

import com.kongbai.kculauncher.library.component.theme.ThemeData;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.ui.KCUPage;
import com.kongbai.kculauncher.library.util.LocaleUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import java.util.logging.Level;

/**
 * 启动器设置页。设置项由 {@link LauncherSettingAdapter} 以 RecyclerView 行级复用渲染，
 * 页面只负责对话框、文件选择与权限等业务逻辑。
 */
public class LauncherSettingPage extends KCUPage implements LauncherSettingAdapter.Listener {

    private SharedPreferences sharedPreferences;

    public LauncherSettingPage(Context context, int id) {
        super(context, id, R.layout.page_setting_list);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences = getActivity().getSharedPreferences("launcher", MODE_PRIVATE);
        PageSettingListBinding binding = PageSettingListBinding.bind(getContentView());
        LauncherSettingAdapter adapter = new LauncherSettingAdapter(getContext(), this);
        binding.settingList.setLayoutManager(new LinearLayoutManager(getContext()));
        // 行间用间距分隔（ItemDecoration），最后一行不加；同组相邻行留 1dp 缝并绘制次要色分割线
        int rowSpacing = (int) (8 * getContext().getResources().getDisplayMetrics().density);
        int groupDivider = (int) (1 * getContext().getResources().getDisplayMetrics().density);
        final int finalRowSpacing = rowSpacing;
        final int finalGroupDivider = groupDivider;
        binding.settingList.addItemDecoration(new SpacingItemDecoration(rowSpacing,
                (parent, position) -> ((LauncherSettingAdapter) parent.getAdapter()).isNextInSameGroup(position)
                        ? finalGroupDivider : finalRowSpacing,
                true,
                () -> ThemeEngine.getInstance().getTheme().getColor()));
        // 主题切换时重绘分割线颜色
        ThemeEngine.getInstance().registerEvent(
                binding.settingList, binding.settingList::invalidate);
        binding.settingList.setAdapter(adapter);
        adapter.rebuild();
    }

    @Override
    public void onButtonClick(LauncherSettingTag tag) {
        switch (tag) {
            case CHECK_UPDATE:
                if (!UpdateChecker.getInstance().isChecking()) {
                    UpdateChecker.getInstance().checkManually(getContext()).whenComplete(Schedulers.androidUIThread(), e -> {
                        if (e != null) {
                            KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
                            builder.setCancelable(false);
                            builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                            builder.setMessage(getContext().getString(R.string.update_check_failed) + "\n" + e);
                            builder.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
                            builder.create().show();
                        }
                    }).start();
                }
                break;
            case SHOW_CHANGELOG:
                UpdateChecker.getInstance().showCurrentChangelog(getContext()).whenComplete(Schedulers.androidUIThread(), e -> {
                    if (e != null) {
                        KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
                        builder.setCancelable(false);
                        builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                        builder.setMessage(getContext().getString(R.string.update_check_failed) + "\n" + e);
                        builder.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
                        builder.create().show();
                    }
                }).start();
                break;
            case EXPORT_LOG:
                exportLog();
                break;
            case CLEAR_MOD_CACHE:
                Task.runAsync(RemoteModCache::clear).whenComplete(Schedulers.androidUIThread(), e -> {
                    Toast.makeText(getContext(), getContext().getString(R.string.settings_launcher_mod_cache_cleared), Toast.LENGTH_SHORT).show();
                }).start();
                break;
            case REQUEST_AUDIO:
                if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.getInstance(), Manifest.permission.RECORD_AUDIO)) {
                        MainActivity.getInstance().permissionResultLauncher.launch(Manifest.permission.RECORD_AUDIO);
                    } else {
                        try {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
                            intent.setData(uri);
                            getContext().startActivity(intent);
                        } catch (Exception ignored) {
                        }
                    }
                }
                break;
            case BACKGROUND_LIVE_SET:
                selectLiveBackground();
                break;
            case CURSOR_SET:
                selectCursor();
                break;
            case MENU_ICON_SET:
                selectMenuIcon();
                break;
            case BACKGROUND_LIVE_RESET:
                try {
                    FileUtils.forceDelete(new File(KCUPath.LIVE_BACKGROUND_PATH));
                    MainActivity.getInstance().setupLiveBackground();
                } catch (IOException ignore) {
                }
                break;
            case CURSOR_RESET:
                deleteCursorFile();
                break;
            case MENU_ICON_RESET:
                deleteMenuIconFile();
                break;
            default:
                break;
        }
    }

    private void exportLog() {
        thread(() -> {
            Path logFile = new File(new File(KCUPath.SHARED_COMMON_DIR).getParent(), "fcl-exported-logs-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")) + ".log").toPath().toAbsolutePath();
            LOG.info("Exporting logs to " + logFile);
            try {
                Files.write(logFile, Logging.getRawLogs());
            } catch (IOException e) {
                Schedulers.androidUIThread().execute(() -> {
                    KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
                    builder.setCancelable(false);
                    builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                    builder.setMessage(getContext().getString(R.string.settings_launcher_launcher_log_export_failed) + "\n" + e);
                    builder.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
                    builder.create().show();
                });
                LOG.log(Level.WARNING, "Failed to export logs", e);
                return;
            }
            Schedulers.androidUIThread().execute(() -> {
                KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
                builder.setCancelable(false);
                builder.setAlertLevel(KCUAlertDialog.AlertLevel.INFO);
                builder.setMessage(getContext().getString(R.string.settings_launcher_launcher_log_export_success, logFile));
                builder.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
                builder.create().show();
            });
        });
    }

    private void selectLiveBackground() {
        ArrayList<String> suffix = new ArrayList<>();
        suffix.add(".mp4");
        MainActivity.getInstance().fileLauncher.launchSingleSelection(null, suffix, files -> {
            if (files == null) return;
            files.get(0).copyTo(getActivity(), new File(KCUPath.LIVE_BACKGROUND_PATH));
            MainActivity.getInstance().setupLiveBackground();
        });
    }

    private void selectCursor() {
        ArrayList<String> suffix = new ArrayList<>();
        suffix.add(".png");
        suffix.add(".gif");
        MainActivity.getInstance().fileLauncher.launchSingleSelection(null, suffix, files -> {
            if (files == null) return;
            String type;
            if (files.get(0).fileName(getContext()).endsWith(".gif")) {
                type = "gif";
            } else {
                type = "png";
            }
            deleteCursorFile();
            files.get(0).copyTo(getActivity(), new File(KCUPath.FILES_DIR, "cursor." + type));
        });
    }

    private void selectMenuIcon() {
        ArrayList<String> suffix = new ArrayList<>();
        suffix.add(".png");
        suffix.add(".gif");
        MainActivity.getInstance().fileLauncher.launchSingleSelection(null, suffix, files -> {
            if (files == null) return;
            String type;
            if (files.get(0).fileName(getContext()).endsWith(".gif")) {
                type = "gif";
            } else {
                type = "png";
            }
            deleteMenuIconFile();
            files.get(0).copyTo(getActivity(), new File(KCUPath.FILES_DIR, "menu_icon." + type));
        });
    }

    /**
     * 从指定背景提取主要主题色（muted；与当前色相同时换 lightVibrant 保证可见变化）
     */
    /**
     * 从指定背景提取次要主题色（vibrant）
     */
    private static void deleteMenuIconFile() {
        try {
            Files.deleteIfExists(Paths.get(KCUPath.FILES_DIR, "menu_icon.png"));
            Files.deleteIfExists(Paths.get(KCUPath.FILES_DIR, "menu_icon.gif"));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to delete menu icon", e);
        }
    }

    private void deleteCursorFile() {
        try {
            Files.deleteIfExists(Paths.get(KCUPath.FILES_DIR, "cursor.png"));
            Files.deleteIfExists(Paths.get(KCUPath.FILES_DIR, "cursor.gif"));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to delete cursor", e);
        }
    }

    @Override
    public void onSwitchToggle(LauncherSettingTag tag, boolean checked) {
        switch (tag) {
            case SWITCH_AUTO_EXIT:
                sharedPreferences.edit().putBoolean("autoExitLauncher", checked).apply();
                break;
            case SWITCH_IGNORE_NOTCH:
                ThemeEngine.getInstance().applyAndSave(getContext(), getActivity().getWindow(), checked);
                getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
                break;
            case SWITCH_DISABLE_FULLSCREEN_INPUT:
                sharedPreferences.edit().putBoolean("disableFullscreenInput", checked).apply();
                break;
            case SWITCH_ALLOW_SCREENSHOTS:
                sharedPreferences.edit().putBoolean("allowScreenshots", checked).apply();
                break;
            default:
                break;
        }
    }

    @Override
    public void onSpinnerSelect(LauncherSettingTag tag, int position) {
        switch (tag) {
            case SPINNER_LANGUAGE:
                // 初始化/复用 bind 的回调与当前值相同，忽略；实际切换才生效
                if (position == LocaleUtils.getLanguage(getContext())) return;
                LocaleUtils.changeLanguage(getContext(), position);
                // 立即生效：重建 Activity 重新走 attachBaseContext 应用新语言
                getActivity().recreate();
                break;
            case SPINNER_THEME_MODE:
                // 初始化/复用 bind 的回调与当前值相同，忽略；实际切换才生效
                if (position == sharedPreferences.getInt("themeMode", 0)) return;
                sharedPreferences.edit().putInt("themeMode", position).apply();
                int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                if (position != 0) {
                    mode = position == 1 ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
                }
                AppCompatDelegate.setDefaultNightMode(mode);
                // configChanges 含 uiMode 时 AppCompat 不重建 Activity 也不更新 Resources 配置，
                // 亮暗切换需显式刷新主题控件与背景（ThemeEngine.isNightMode 读 themeMode 设置）
                ThemeEngine.getInstance().refreshTheme();
                break;
            case SPINNER_VERSION_LIST_SOURCE:
                config().setVersionListSource(toDownloadSourceName(position));
                break;
            case SPINNER_FILE_DOWNLOAD_SOURCE:
                config().setFileDownloadSource(toDownloadSourceName(position));
                break;
            default:
                break;
        }
    }

    @Override
    public void onSeekBarChange(LauncherSettingTag tag, int progress) {
        switch (tag) {
            case SEEKBAR_VIDEO_VOLUME:
                sharedPreferences.edit().putInt("videoBackgroundVolume", progress).apply();
                MainActivity.getInstance().setLiveBackgroundVolume();
                break;
            case SEEKBAR_ANIMATION_SPEED:
                ThemeEngine.getInstance().setAnimationSpeed(progress);
                ThemeData.saveTheme(getContext(), ThemeEngine.getInstance().getTheme());
                break;
            case SEEKBAR_VIBRATION:
                sharedPreferences.edit().putInt("vibrationDuration", progress).apply();
                break;
            case SEEKBAR_THREADS:
                config().downloadThreadsProperty().set(progress);
                break;
            default:
                break;
        }
    }

    private static String toDownloadSourceName(int position) {
        return switch (position) {
            case 1 -> DownloadSource.OFFICIAL.name();
            case 2 -> DownloadSource.MIRROR.name();
            default -> DownloadSource.DEFAULT.name();
        };
    }

    @Override
    public void onCheckToggle(LauncherSettingTag tag, boolean checked) {
        switch (tag) {
            case CHECK_AUTO_THREADS:
                config().autoDownloadThreadsProperty().set(checked);
                if (checked) {
                    config().downloadThreadsProperty().set(FetchTask.DEFAULT_CONCURRENCY);
                }
                break;
            default:
                break;
        }
    }
}
