/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.kongbai.kculauncher.game;

import static android.content.Context.MODE_PRIVATE;
import static com.kongbai.kculauncher.mio.util.AndroidUtilKt.getLocalizedText;
import static com.kongbai.kculauncher.mio.util.AndroidUtilKt.hasStringId;
import static com.kongbai.kculauncher.core.util.Logging.LOG;
import static java.util.stream.Collectors.toList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.GsonBuilder;
import com.kongbai.kculauncher.mio.JavaManager;
import com.kongbai.kculauncher.mio.data.Renderer;
import com.kongbai.kculauncher.mio.manager.RendererManager;
import com.kongbai.kculauncher.mio.minecraft.ModCheckException;
import com.kongbai.kculauncher.mio.minecraft.ModChecker;
import com.kongbai.kculauncher.mio.plugin.NativeLibPlugin;
import com.kongbai.kculauncher.mio.util.LoginProgressKt;
import com.kongbai.kculauncher.mio.util.ParseUtil;
import com.kongbai.kculauncher.KCUApp;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.activity.JVMActivity;
import com.kongbai.kculauncher.activity.MainActivity;
import com.kongbai.kculauncher.control.MenuType;
import com.kongbai.kculauncher.setting.GameOption;
import com.kongbai.kculauncher.setting.MenuSetting;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.setting.Profiles;
import com.kongbai.kculauncher.setting.VersionSetting;
import com.kongbai.kculauncher.ui.TaskDialog;
import com.kongbai.kculauncher.ui.UIManager;
import com.kongbai.kculauncher.util.TaskCancellationAction;
import com.kongbai.kculauncher.launcher.bridge.KCUBridge;
import com.kongbai.kculauncher.launcher.utils.KCUPath;
import com.kongbai.kculauncher.core.auth.Account;
import com.kongbai.kculauncher.core.auth.AuthInfo;
import com.kongbai.kculauncher.core.auth.AuthenticationException;
import com.kongbai.kculauncher.core.auth.CharacterDeletedException;
import com.kongbai.kculauncher.core.auth.CredentialExpiredException;
import com.kongbai.kculauncher.core.auth.authlibinjector.AuthlibInjectorDownloadException;
import com.kongbai.kculauncher.core.auth.microsoft.MicrosoftAccount;
import com.kongbai.kculauncher.core.download.DefaultDependencyManager;
import com.kongbai.kculauncher.core.download.LibraryAnalyzer;
import com.kongbai.kculauncher.core.download.MaintainTask;
import com.kongbai.kculauncher.core.download.game.GameAssetIndexDownloadTask;
import com.kongbai.kculauncher.core.download.game.GameVerificationFixTask;
import com.kongbai.kculauncher.core.download.game.LibraryDownloadException;
import com.kongbai.kculauncher.core.game.JavaVersion;
import com.kongbai.kculauncher.core.game.LaunchOptions;
import com.kongbai.kculauncher.core.game.Library;
import com.kongbai.kculauncher.core.game.Version;
import com.kongbai.kculauncher.core.mod.LocalModFile;
import com.kongbai.kculauncher.core.mod.ModpackCompletionException;
import com.kongbai.kculauncher.core.mod.ModpackConfiguration;
import com.kongbai.kculauncher.core.mod.ModpackProvider;
import com.kongbai.kculauncher.core.task.DownloadException;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.task.TaskExecutor;
import com.kongbai.kculauncher.core.task.TaskListener;
import com.kongbai.kculauncher.core.util.Lang;
import com.kongbai.kculauncher.core.util.LibFilter;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.core.util.io.FileUtils;
import com.kongbai.kculauncher.core.util.io.ResponseCodeException;
import com.kongbai.kculauncher.core.util.versioning.GameVersionNumber;
import com.kongbai.kculauncher.core.util.versioning.VersionNumber;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUTabLayout;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class LauncherHelper {

    private final Context context;
    private final Profile profile;
    private final Account account;
    private final String selectedVersion;
    private final VersionSetting setting;
    private final TaskDialog launchingStepsPane;
    private double scaleFactor;
    private double cursorOffset;

    public LauncherHelper(Context context, Profile profile, Account account, String selectedVersion) {
        this.context = Objects.requireNonNull(context);
        this.profile = Objects.requireNonNull(profile);
        this.account = Objects.requireNonNull(account);
        this.selectedVersion = Objects.requireNonNull(selectedVersion);
        this.setting = profile.getVersionSetting(selectedVersion);
        this.launchingStepsPane = new TaskDialog(context, TaskCancellationAction.NORMAL);
        this.launchingStepsPane.setTitle(context.getString(R.string.version_launch));
    }

    public void launch() {
        LOG.info("Launching game version: " + selectedVersion);

        launchingStepsPane.show();
        // 版本解析与 mod 全量扫描较重，挪到后台执行，避免阻塞主线程导致弹窗延迟显示
        Task.runAsync(this::launch0).whenComplete(Schedulers.androidUIThread(), e -> {
            // 任务链尚未构建时（版本解析/mod 扫描准备段）失败不会触发链内 onStop，在此兜底
            if (e != null) {
                launchingStepsPane.dismiss();
                showLaunchErrorMessage(e);
            }
        }).start();
    }

    private void launch0() {
        KCUGameRepository repository = profile.getRepository();
        DefaultDependencyManager dependencyManager = profile.getDependency();
        AtomicReference<Version> version = new AtomicReference<>();
        AtomicReference<List<LocalModFile>> scannedModsRef = new AtomicReference<>();
        AtomicReference<Optional<String>> gameVersionRef = new AtomicReference<>();
        AtomicBoolean integrityCheckRef = new AtomicBoolean();

        AtomicReference<JavaVersion> javaVersionRef = new AtomicReference<>();

        // 版本解析与 mod 扫描较重，作为链首阶段任务执行，弹窗内可实时显示进度
        TaskExecutor executor = Task.runAsync(() -> version.set(MaintainTask.maintain(repository, repository.getResolvedVersion(selectedVersion))))
                .withStage("launch.state.version")
                .thenComposeAsync(() -> {
                    // 全量 mod 扫描一次，供 lwjgl3ify 补丁、modloader 与渲染器检查共用
                    List<LocalModFile> scannedMods = scanMods(repository, selectedVersion);
                    scannedModsRef.set(scannedMods);
                    // GTNH/lwjgl3ify 兼容：mods 目录存在 lwjgl3ify 时在内存中合并 RFB 启动清单（不落盘），以 RFB + Java17+ 启动
                    Lwjgl3ifyPatcher.patchIfNeeded(repository, selectedVersion, scannedMods, version);
                    gameVersionRef.set(repository.getGameVersion(version.get()));
                    integrityCheckRef.set(repository.unmarkVersionLaunchedAbnormally(selectedVersion));
                    return null;
                }).withStage("launch.state.mods")
                .thenComposeAsync(() -> checkGameState(context, setting, version.get()))
                .thenComposeAsync(javaVersion -> {
                    javaVersionRef.set(Objects.requireNonNull(javaVersion));
                    version.set(LibFilter.filter(version.get(), false));
                    if (setting.isNotCheckGame())
                        return null;
                    return Task.allOf(
                            dependencyManager.checkGameCompletionAsync(version.get(), integrityCheckRef.get()),
                            Task.composeAsync(() -> {
                                try {
                                    ModpackConfiguration<?> configuration = ModpackHelper.readModpackConfiguration(repository.getModpackConfiguration(selectedVersion));
                                    ModpackProvider provider = ModpackHelper.getProviderByType(configuration.getType());
                                    if (provider == null)
                                        return null;
                                    else
                                        return provider.createCompletionTask(dependencyManager, selectedVersion);
                                } catch (IOException ignore) {
                                }
                                return null;
                            }),
                            Task.composeAsync(() -> null)
                    );
                }).withStage("launch.state.dependencies")
                .thenComposeAsync(() -> {
                    try (InputStream input = LauncherHelper.class.getResourceAsStream("/assets/game/MioLibPatcher.jar")) {
                        Files.copy(input, new File(KCUPath.LIB_PATCHER_PATH).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Unable to unpack MioLibFixer.jar", e);
                    }
                    return null;
                })
                .thenComposeAsync(() -> {
                    try (InputStream input = LauncherHelper.class.getResourceAsStream("/assets/game/MioLaunchWrapper.jar")) {
                        Files.copy(input, new File(KCUPath.MIO_LAUNCH_WRAPPER).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Unable to unpack MioLaunchWrapper.jar", e);
                    }
                    return null;
                })
                .thenComposeAsync(() -> gameVersionRef.get().map(s -> new GameVerificationFixTask(dependencyManager, s, version.get())).orElse(null))
                .thenComposeAsync(() -> logIn(context, account,
                        // 微软登录阶段实时写入启动过程的日志区
                        text -> Schedulers.androidUIThread().execute(() -> launchingStepsPane.appendLog(text)))
                        .withStage("launch.state.logging_in"))
                .thenComposeAsync(authInfo -> Task.supplyAsync(() -> {
                            MenuSetting menuSetting = null;
                            try {
                                menuSetting = new GsonBuilder()
                                        .setPrettyPrinting()
                                        .create()
                                        .fromJson(FileUtils.readText(new File(KCUPath.FILES_DIR + "/menu_setting.json")), MenuSetting.class);
                            } catch (Throwable ignore) {
                            }
                            if (menuSetting != null) {
                                scaleFactor = menuSetting.getWindowScale();
                                cursorOffset = menuSetting.getCursorOffset();
                            } else {
                                scaleFactor = 1d;
                                cursorOffset = 0d;
                            }
                            // 强制分辨率已迁移到游戏内菜单（MenuSetting），启动前同步进静态配置
                            KCUBridge.initForceResolution(menuSetting);
                            LaunchOptions launchOptions = repository.getLaunchOptions(selectedVersion, javaVersionRef.get(), profile.getGameDir(), scaleFactor, cursorOffset);
                            KCUGameLauncher launcher = new KCUGameLauncher(
                                    context,
                                    repository,
                                    version.get(),
                                    authInfo,
                                    launchOptions
                            );
                            String jnaVersion = null;
                            String lwjglVersion = null;
                            VersionNumber lwjglMax = null;
                            for (Library library : version.get().getLibraries()) {
                                String name = library.getName();
                                if (name.startsWith("net.java.dev.jna:jna:")) {
                                    jnaVersion = library.getVersion();
                                } else if (name.startsWith("org.lwjgl.lwjgl:lwjgl:") || name.startsWith("org.lwjgl:lwjgl:")) {
                                    // 新旧 LWJGL 声明可能并存(如 lwj3ify 的 3.x 与基础版本的 2.x),取最高版本
                                    VersionNumber current = VersionNumber.asVersion(library.getVersion());
                                    if (lwjglMax == null || current.compareTo(lwjglMax) > 0) {
                                        lwjglMax = current;
                                        lwjglVersion = library.getVersion();
                                    }
                                }
                            }
                            if (jnaVersion != null) {
                                launcher.setJnaVersion(jnaVersion);
                            }
                            if (lwjglVersion != null) {
                                launcher.setLwjglVersion(lwjglVersion);
                            }
                            return launcher;
                        }).thenComposeAsync(launcher -> { // launcher is prev task's result
                            return Task.supplyAsync(launcher::launch);
                        }).thenComposeAsync(fclBridge -> checkPathValid(fclBridge, repository))
                        .thenComposeAsync(fclBridge -> {
                            Renderer renderer = RendererManager.getRenderer(repository.getVersionSetting(selectedVersion).getRenderer());
                            fclBridge.setRenderer(renderer.getName());
                            return checkRenderer(fclBridge, renderer, repository.getGameVersion(selectedVersion).orElse(""), scannedModsRef.get());
                        }).thenComposeAsync(fclBridge -> checkNativeLibPlugin(fclBridge, repository.getGameVersion(selectedVersion).orElse("")))
                        .thenComposeAsync(fclBridge -> {
                            boolean skip = repository.getVersionSetting(selectedVersion).isNotCheckMod();
                            if (skip) return Task.supplyAsync(() -> fclBridge);
                            return checkModLoader(fclBridge, repository, scannedModsRef.get());
                        }).thenComposeAsync(fclBridge -> {
                            boolean skip = repository.getVersionSetting(selectedVersion).isNotCheckMod();
                            return checkMod(fclBridge, repository.getGameVersion(selectedVersion).orElse(""), skip, scannedModsRef.get());
                        }).thenComposeAsync(fclBridge -> {
                            GameOption gameOption = new GameOption(repository.getRunDirectory(selectedVersion).getAbsolutePath());
                            gameOption.set("preferredGraphicsBackend", setting.getGraphicsBackend());
                            gameOption.set("startedCleanly", "true");
                            gameOption.save();
                            return Task.completed(fclBridge);
                        }).thenAcceptAsync(fclBridge -> Schedulers.androidUIThread().execute(() -> {
                            CallbackBridge.nativeSetUseInputStackQueue(version.get().getArguments().isPresent());
                            Intent intent = new Intent(context, JVMActivity.class);
                            fclBridge.setScaleFactor(scaleFactor);
                            fclBridge.setController(repository.getVersionSetting(selectedVersion).getController());
                            fclBridge.setGameDir(repository.getRunDirectory(selectedVersion).getAbsolutePath());
                            fclBridge.setJava(Integer.toString(javaVersionRef.get().getVersion()));
                            JVMActivity.setKCUBridge(fclBridge, MenuType.GAME);
                            Bundle bundle = new Bundle();
                            bundle.putString("controller", repository.getVersionSetting(selectedVersion).getController());
                            bundle.putString("TERRACOTTA_PLAYER", account.getUsername());
                            intent.putExtras(bundle);
                            LOG.log(Level.INFO, "Start JVMActivity!");
                            context.startActivity(intent);
                            if (MainActivity.getInstance().shouldPlayVideo()) {
                                MainActivity.getInstance().setMediaPlayer(null);
                                MainActivity.getInstance().binding.videoView.stopPlayback();
                            }
                            if (context.getSharedPreferences("launcher", MODE_PRIVATE).getBoolean("autoExitLauncher", false)) {
                                Activity activity = KCUApp.getActivity();
                                if (activity != null)
                                    activity.finish();
                            }
                        }))
                        .withStage("launch.state.waiting_launching"))
                .withStagesHint(Lang.immutableListOf(
                        "launch.state.version",
                        "launch.state.mods",
                        "launch.state.java",
                        "launch.state.dependencies",
                        "launch.state.logging_in",
                        "launch.state.waiting_launching"))
                .executor();
        // setExecutor 内部更新视图，且须先于 start 装配，整体切回主线程执行
        Schedulers.androidUIThread().execute(() -> {
            launchingStepsPane.setExecutor(executor, false);
            executor.addTaskListener(new TaskListener() {

                @Override
                public void onStop(boolean success, TaskExecutor executor) {
                    launchingStepsPane.dismiss();
                    if (!success) {
                        Exception ex = executor.getException();
                        if (ex != null && !(ex instanceof CancellationException)) {
                            showLaunchErrorMessage(ex);
                        }
                    }
                }
            });

            executor.start();
        });
    }

    /** 主线程展示启动失败原因（按异常类型整理文案） */
    private void showLaunchErrorMessage(Exception ex) {
        Schedulers.androidUIThread().execute(() -> {
            String message;
            if (ex instanceof ModpackCompletionException) {
                if (ex.getCause() instanceof FileNotFoundException)
                    message = context.getString(R.string.modpack_type_curse_not_found);
                else
                    message = context.getString(R.string.modpack_type_curse_error);
            } else if (ex instanceof LibraryDownloadException) {
                message = context.getString(R.string.launch_failed_download_library, ((LibraryDownloadException) ex).getLibrary().getName()) + "\n";
                if (ex.getCause() instanceof ResponseCodeException rce) {
                    int responseCode = rce.getResponseCode();
                    URL url = rce.getUrl();
                    if (responseCode == 404)
                        message += context.getString(R.string.download_code_404, url);
                    else
                        message += context.getString(R.string.download_failed, url, responseCode);
                } else {
                    message += StringUtils.getStackTrace(ex.getCause());
                }
            } else if (ex instanceof DownloadException) {
                URL url = ((DownloadException) ex).getUrl();
                if (ex.getCause() instanceof SocketTimeoutException) {
                    message = context.getString(R.string.install_failed_downloading_timeout, url);
                } else if (ex.getCause() instanceof ResponseCodeException responseCodeException) {
                    if (hasStringId(context, "download_code_" + responseCodeException.getResponseCode())) {
                        message = getLocalizedText(context, "download_code_" + responseCodeException.getResponseCode(), url);
                    } else {
                        message = context.getString(R.string.install_failed_downloading_detail, url) + "\n" + StringUtils.getStackTrace(ex.getCause());
                    }
                } else {
                    message = context.getString(R.string.install_failed_downloading_detail, url) + "\n" + StringUtils.getStackTrace(ex.getCause());
                }
            } else if (ex instanceof GameAssetIndexDownloadTask.GameAssetIndexMalformedException) {
                message = context.getString(R.string.assets_index_malformed);
            } else if (ex instanceof AuthlibInjectorDownloadException) {
                message = context.getString(R.string.account_failed_injector_download_failure);
            } else if (ex instanceof CharacterDeletedException) {
                message = context.getString(R.string.account_failed_character_deleted);
            } else if (ex instanceof ResponseCodeException rce) {
                int responseCode = rce.getResponseCode();
                URL url = rce.getUrl();
                if (responseCode == 404)
                    message = context.getString(R.string.download_code_404, url);
                else
                    message = context.getString(R.string.download_failed, url, responseCode);
            } else if (ex instanceof AccessDeniedException) {
                message = context.getString(R.string.exception_access_denied, ((AccessDeniedException) ex).getFile());
            } else if (ex instanceof ModCheckException) {
                message = ((ModCheckException) ex).getReason();
            } else if (ex instanceof IllegalArgumentException) {
                message = context.getString(R.string.exception_no_suitable_java);
            } else {
                message = StringUtils.getStackTrace(ex);
            }

            KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(context);
            builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
            builder.setCancelable(false);
            builder.setTitle(context.getString(R.string.launch_failed));
            builder.setMessage(message);
            builder.setNegativeButton(context.getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
            builder.create().show();
        });
    }

    private Task<KCUBridge> checkPathValid(KCUBridge bridge, KCUGameRepository repository) {
        return Task.composeAsync(() -> {
            try {
                CompletableFuture<Task<KCUBridge>> future = new CompletableFuture<>();
                String path = repository.getVersionJar(selectedVersion).getAbsolutePath();
                if (ParseUtil.isValidCharacters(path)) {
                    return Task.completed(bridge);
                } else {
                    Schedulers.androidUIThread().execute(() -> new KCUAlertDialog.Builder(context)
                            .setCancelable(false)
                            .setMessage(context.getString(R.string.message_check_path_valid, path))
                            .setPositiveButton(context.getString(R.string.button_cancel), () -> future.completeExceptionally(new CancellationException()))
                            .setNegativeButton(context.getString(R.string.mod_check_continue), () -> future.complete(Task.completed(bridge))).create().show());
                    return Task.fromCompletableFuture(future).thenComposeAsync(task -> task);
                }
            } catch (Throwable e) {
                return Task.completed(bridge);
            }
        });
    }

    /** 全量扫描 mod 列表，失败返回 null（调用方按需回退） */
    @Nullable
    private static List<LocalModFile> scanMods(KCUGameRepository repository, String versionId) {
        try {
            return repository.getModManager(versionId).getMods();
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to scan mods before launch", e);
            return null;
        }
    }

    private Task<KCUBridge> checkRenderer(KCUBridge bridge, Renderer renderer, String version, List<LocalModFile> mods) {
        return Task.composeAsync(() -> {
            if (Lwjgl3ifyPatcher.shouldSkipRendererCheck(mods)) {
                LOG.log(Level.INFO, "Angelica + lwjgl3ify detected, skip renderer version warning");
                return Task.completed(bridge);
            }
            try {
                CompletableFuture<Task<KCUBridge>> future = new CompletableFuture<>();
                if (!version.isEmpty()) {
                    if (!renderer.getMinMCver().isEmpty()) {
                        if (GameVersionNumber.compare(version, renderer.getMinMCver()) < 0) {
                            Schedulers.androidUIThread().execute(() -> new KCUAlertDialog.Builder(context)
                                    .setCancelable(false)
                                    .setMessage(context.getString(R.string.message_check_renderer, renderer.getName()))
                                    .setPositiveButton(context.getString(R.string.button_cancel), () -> future.completeExceptionally(new CancellationException()))
                                    .setNegativeButton(context.getString(R.string.mod_check_continue), () -> future.complete(Task.completed(bridge))).create().show());
                            return Task.fromCompletableFuture(future).thenComposeAsync(task -> task);
                        }
                    }
                    if (!renderer.getMaxMCver().isEmpty()) {
                        if (GameVersionNumber.compare(version, renderer.getMaxMCver()) > 0) {
                            Schedulers.androidUIThread().execute(() -> new KCUAlertDialog.Builder(context)
                                    .setCancelable(false)
                                    .setMessage(context.getString(R.string.message_check_renderer, renderer.getName()))
                                    .setPositiveButton(context.getString(R.string.button_cancel), () -> future.completeExceptionally(new CancellationException()))
                                    .setNegativeButton(context.getString(R.string.mod_check_continue), () -> future.complete(Task.completed(bridge))).create().show());
                            return Task.fromCompletableFuture(future).thenComposeAsync(task -> task);
                        }
                    }
                }
                return Task.completed(bridge);
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "checkRenderer() failed", e);
                return Task.completed(bridge);
            }
        });
    }

    private Task<KCUBridge> checkNativeLibPlugin(KCUBridge bridge, String version) {
        return Task.composeAsync(() -> {
            try {
                CompletableFuture<Task<KCUBridge>> future = new CompletableFuture<>();
                List<NativeLibPlugin.@NotNull NativePlugin> pluginList = NativeLibPlugin.getPluginList();
                List<String> unsupportedPlugins = pluginList.stream().filter(plugin -> {
                            String minVer = plugin.getMinMCVer();
                            String maxVer = plugin.getMaxMCVer();
                            if (!minVer.isEmpty() && GameVersionNumber.compare(version, minVer) < 0) {
                                return true;
                            }
                            return !maxVer.isEmpty() && GameVersionNumber.compare(version, maxVer) > 0;
                        }).map(NativeLibPlugin.NativePlugin::getAppName)
                        .collect(toList());
                if (!unsupportedPlugins.isEmpty()) {
                    String fullString = String.join(", ", unsupportedPlugins);
                    Schedulers.androidUIThread().execute(() -> new KCUAlertDialog.Builder(context)
                            .setCancelable(false)
                            .setMessage(context.getString(R.string.message_check_plugin, fullString))
                            .setPositiveButton(context.getString(R.string.button_cancel), () -> future.completeExceptionally(new CancellationException()))
                            .setNegativeButton(context.getString(R.string.mod_check_continue), () -> future.complete(Task.completed(bridge))).create().show());
                    return Task.fromCompletableFuture(future).thenComposeAsync(task -> task);
                }
                return Task.completed(bridge);
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "checkNativeLibPlugin() failed", e);
                return Task.completed(bridge);
            }
        });
    }

    private Task<KCUBridge> checkModLoader(KCUBridge bridge, KCUGameRepository repository, List<LocalModFile> mods) {
        return Task.composeAsync(() -> {
            try {
                CompletableFuture<Task<KCUBridge>> future = new CompletableFuture<>();
                boolean modded = LibraryAnalyzer.isModded(repository, repository.getVersion(selectedVersion));
                if (mods != null && !mods.isEmpty() && !modded) {
                    Schedulers.androidUIThread().execute(() -> new KCUAlertDialog.Builder(context)
                            .setCancelable(false)
                            .setMessage(context.getString(R.string.message_check_has_modloader))
                            .setPositiveButton(context.getString(R.string.button_cancel), () -> future.completeExceptionally(new CancellationException()))
                            .setNeutralButton(context.getString(R.string.button_install), () -> {
                                future.completeExceptionally(new CancellationException());
                                UIManager manager = UIManager.getInstance();
                                MainActivity.getInstance().binding.manage.setSelected(true);
                                KCUTabLayout tabLayout = manager.getManageUI().tabLayout;
                                tabLayout.selectTab(tabLayout.getTabAt(2));
                            })
                            .setNegativeButton(context.getString(R.string.mod_check_continue), () -> future.complete(Task.completed(bridge))).create().show());
                    return Task.fromCompletableFuture(future).thenComposeAsync(task -> task);
                } else {
                    return Task.completed(bridge);
                }
            } catch (Throwable e) {
                return Task.completed(bridge);
            }
        });
    }

    private Task<KCUBridge> checkMod(KCUBridge bridge, String version, boolean skip, List<LocalModFile> mods) {
        return Task.composeAsync(() -> {
            try {
                List<LocalModFile> modList = mods;
                if (modList == null) {
                    // 启动链扫描失败时回退为自行获取（getModManager 每次返回新实例，此处为全量重扫）
                    modList = Profiles.getSelectedProfile().getRepository().getModManager(Profiles.getSelectedVersion()).getMods();
                }
                StringBuilder modCheckerInfo = new StringBuilder();
                StringBuilder modSummary = new StringBuilder();
                ModChecker modChecker = new ModChecker(context, version);
                int count = 0;
                for (LocalModFile mod : modList) {
                    if (!mod.isActive()) {
                        continue;
                    }
                    modSummary.append(mod.getFileName());
                    modSummary.append(" | ");
                    modSummary.append(mod.getId());
                    modSummary.append(" | ");
                    modSummary.append(mod.getVersion());
                    modSummary.append(" | ");
                    modSummary.append(mod.getModLoaderType());
                    modSummary.append("\n");
                    try {
                        modChecker.check(bridge, mod);
                    } catch (ModCheckException e) {
                        count++;
                        modCheckerInfo.append(count).append(".").append(e.getReason()).append("\n\n");
                    }
                }
                bridge.setModSummary(modSummary.toString());
                if (!skip && !modCheckerInfo.toString().trim().isEmpty()) {
                    CompletableFuture<Task<KCUBridge>> future = new CompletableFuture<>();
                    Schedulers.androidUIThread().execute(() -> {
                        KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(context);
                        builder.setCancelable(false);
                        builder.setMessage(modCheckerInfo.toString());
                        builder.setPositiveButton(context.getString(R.string.button_cancel), () -> future.completeExceptionally(new CancellationException()));
                        builder.setNegativeButton(context.getString(R.string.mod_check_continue), () -> future.complete(Task.completed(bridge)));
                        builder.create().show();
                    });
                    return Task.fromCompletableFuture(future).thenComposeAsync(task -> task);
                }
                return Task.completed(bridge);
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "CheckMod() failed", e);
                return Task.completed(bridge);
            }
        });
    }

    private static Task<JavaVersion> checkGameState(Context context, VersionSetting setting, Version version) {
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(version, null);
        boolean isCleanroom = analyzer.has(LibraryAnalyzer.LibraryType.CLEANROOM);
        VersionNumber cleanroomVersion;
        if (isCleanroom) {
            Optional<String> optional = analyzer.getVersion(LibraryAnalyzer.LibraryType.CLEANROOM);
            cleanroomVersion = optional.map(s -> VersionNumber.asVersion(StringUtils.removeSuffix(s, "-alpha"))).orElse(null);
        } else {
            cleanroomVersion = null;
        }
        Task<JavaVersion> task = Task.supplyAsync(Schedulers.androidUIThread(), () -> {
            if (setting.getJava().equals("Auto")) {
                if (isCleanroom) {
                    if (cleanroomVersion != null && cleanroomVersion.compareTo("0.5.0") >= 0) {
                        return JavaManager.getSuitableJavaVersion(25);
                    } else {
                        return JavaManager.getJavaFromVersionName("jre21");
                    }
                } else {
                    return JavaManager.getSuitableJavaVersion(version);
                }
            } else {
                return JavaManager.getJavaFromVersionName(setting.getJava());
            }
        });

        if (setting.isNotCheckJVM()) {
            return task.withStage("launch.state.java");
        }

        return task.thenComposeAsync(Schedulers.androidUIThread(), javaVersion -> {
            JavaVersion suggestedJavaVersion;
            if (isCleanroom) {
                if (cleanroomVersion != null && cleanroomVersion.compareTo("0.5.0") >= 0) {
                    suggestedJavaVersion = JavaManager.getSuitableJavaVersion(25);
                } else {
                    suggestedJavaVersion = JavaManager.getJavaFromVersionName("jre21");
                }
            } else {
                suggestedJavaVersion = JavaManager.getSuitableJavaVersion(version);
            }
            if (suggestedJavaVersion.getVersion() != -1) {
                if (setting.getJava().equals("Auto") || javaVersion.getVersion() == suggestedJavaVersion.getVersion()) {
                    return Task.completed(setting.getJava().equals("Auto") ? suggestedJavaVersion : javaVersion);
                }
            }

            CompletableFuture<JavaVersion> future = new CompletableFuture<>();
            Runnable continueAction = () -> future.complete(javaVersion);
            new KCUAlertDialog.Builder(context)
                    .setCancelable(false)
                    .setMessage(context.getString(R.string.launch_error_java))
                    .setPositiveButton(context.getString(R.string.launch_error_java_auto), () -> {
                        setting.setJava(JavaVersion.JAVA_AUTO.getName());
                        if (suggestedJavaVersion == JavaManager.NO_JAVA_FOUND) {
                            future.completeExceptionally(new IllegalArgumentException("Failed to find a suitable Java!"));
                        } else {
                            future.complete(suggestedJavaVersion);
                        }
                    })
                    .setNegativeButton(context.getString(R.string.launch_error_java_continue), continueAction::run)
                    .setNeutralButton(context.getString(R.string.launch_error_java_continue_disable), () -> {
                        setting.setNotCheckJVM(true);
                        continueAction.run();
                    })
                    .create()
                    .show();
            return Task.fromCompletableFuture(future);
        }).withStage("launch.state.java");
    }

    /**
     * 登录账户；progressConsumer 用于接收微软账户的实时登录阶段文字（可为 null）。
     */
    private static Task<AuthInfo> logIn(Context context, Account account, Consumer<String> progressConsumer) {
        return Task.composeAsync(() -> {
            // 微软账户登录期间上报各认证阶段，进度回调在后台登录线程触发
            boolean withProgress = account instanceof MicrosoftAccount && progressConsumer != null;
            if (withProgress) {
                ((MicrosoftAccount) account).setProgressCallback(
                        stage -> progressConsumer.accept(LoginProgressKt.loginStageText(context, stage)));
            }
            try {
                return Task.completed(account.logIn());
            } catch (CredentialExpiredException e) {
                LOG.log(Level.INFO, "Credential has expired", e);

                CompletableFuture<Task<AuthInfo>> future = new CompletableFuture<>();
                Schedulers.androidUIThread().execute(() -> {
                    TipReLoginLoginDialog dialog = new TipReLoginLoginDialog(context, account, future);
                    dialog.show();
                });
                return Task.fromCompletableFuture(future).thenComposeAsync(task -> task);
            } catch (AuthenticationException e) {
                LOG.log(Level.WARNING, "Authentication failed, try skipping refresh", e);

                CompletableFuture<Task<AuthInfo>> future = new CompletableFuture<>();
                Schedulers.androidUIThread().execute(() -> {
                    SkipLoginDialog dialog = new SkipLoginDialog(context, account, future);
                    dialog.show();
                });
                return Task.fromCompletableFuture(future).thenComposeAsync(task -> task);
            } finally {
                if (withProgress) {
                    ((MicrosoftAccount) account).setProgressCallback(null);
                }
            }
        });
    }

    static class SkipLoginDialog extends KCUDialog implements View.OnClickListener {

        private final Account account;
        private final CompletableFuture<Task<AuthInfo>> future;

        private final KCUButton retry;
        private final KCUButton skip;
        private final KCUButton cancel;

        public SkipLoginDialog(@NonNull Context context, Account account, CompletableFuture<Task<AuthInfo>> future) {
            super(context);
            this.account = account;
            this.future = future;
            setContentView(R.layout.dialog_skip_login);
            setCancelable(false);

            retry = findViewById(R.id.retry);
            skip = findViewById(R.id.skip);
            cancel = findViewById(R.id.cancel);
            retry.setOnClickListener(this);
            skip.setOnClickListener(this);
            cancel.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (view == retry) {
                future.complete(logIn(getContext(), account, null));
            }
            if (view == skip) {
                try {
                    future.complete(Task.completed(account.playOffline()));
                } catch (AuthenticationException e2) {
                    future.completeExceptionally(e2);
                }
            }
            if (view == cancel) {
                future.completeExceptionally(new CancellationException());
            }
            dismiss();
        }
    }

    static class TipReLoginLoginDialog extends KCUDialog implements View.OnClickListener {

        private final Account account;
        private final CompletableFuture<Task<AuthInfo>> future;

        private final KCUButton skip;
        private final KCUButton ok;

        public TipReLoginLoginDialog(@NonNull Context context, Account account, CompletableFuture<Task<AuthInfo>> future) {
            super(context);
            this.account = account;
            this.future = future;
            setContentView(R.layout.dialog_tip_relogin);
            setCancelable(false);

            skip = findViewById(R.id.skip);
            ok = findViewById(R.id.ok);
            skip.setOnClickListener(this);
            ok.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (view == skip) {
                try {
                    future.complete(Task.completed(account.playOffline()));
                } catch (AuthenticationException e2) {
                    future.completeExceptionally(e2);
                }
            }
            if (view == ok) {
                future.completeExceptionally(new CancellationException());
            }
            dismiss();
        }
    }

}
