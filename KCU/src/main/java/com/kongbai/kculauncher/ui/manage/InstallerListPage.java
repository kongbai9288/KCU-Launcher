package com.kongbai.kculauncher.ui.manage;

import static com.kongbai.kculauncher.ui.download.version.VersionInstallInfoPage.alertFailureMessage;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.activity.MainActivity;
import com.kongbai.kculauncher.setting.DownloadProviders;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.ui.InstallerItem;
import com.kongbai.kculauncher.ui.TaskDialog;
import com.kongbai.kculauncher.ui.UIManager;
import com.kongbai.kculauncher.mio.util.AndroidUtilKt;
import com.kongbai.kculauncher.util.TaskCancellationAction;
import com.kongbai.kculauncher.launcher.utils.KCUPath;
import com.kongbai.kculauncher.core.download.LibraryAnalyzer;
import com.kongbai.kculauncher.core.download.ComponentRemoteVersion;
import com.kongbai.kculauncher.core.event.Event;
import com.kongbai.kculauncher.core.game.Version;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.task.TaskExecutor;
import com.kongbai.kculauncher.core.task.TaskListener;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;
import com.kongbai.kculauncher.library.component.ui.KCUPage;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.util.ConvertUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class InstallerListPage extends KCUPage implements ManageUI.VersionLoadable, View.OnClickListener {

    private Profile profile;
    private String versionId;
    private Version version;
    private String gameVersion;

    private ScrollView scrollView;
    private LinearLayoutCompat parent;

    private KCUButton installOfflineButton;

    public InstallerListPage(Context context, int id) {
        super(context, id, R.layout.page_manage_auto_install);
        create();
    }

    private void create() {
        scrollView = findViewById(R.id.scroll);
        installOfflineButton = findViewById(R.id.install_offline);
        installOfflineButton.setOnClickListener(this);
    }


    @Override
    public void loadVersion(Profile profile, String versionId) {
        // null 是合法分发（切到未选版本 Profile 的瞬态）：getVersion(null) 会抛
        // VersionNotFoundException，本页内容依赖具体版本，保持现状等下一个真实版本分发
        if (versionId == null) {
            return;
        }
        this.profile = profile;
        this.versionId = versionId;
        this.version = profile.getRepository().getVersion(versionId);
        this.gameVersion = null;

        CompletableFuture.supplyAsync(() -> {
            gameVersion = profile.getRepository().getGameVersion(version).orElse(null);

            return LibraryAnalyzer.analyze(profile.getRepository().getResolvedPreservingPatchesVersion(versionId), gameVersion);
        }).thenAcceptAsync(analyzer -> {
            Function<String, Runnable> removeAction = libraryId -> () -> profile.getDependency().removeLibraryAsync(version, libraryId)
                    .thenComposeAsync(profile.getRepository()::saveAsync)
                    .withComposeAsync(profile.getRepository().refreshVersionsAsync())
                    .withRunAsync(Schedulers.androidUIThread(), () -> {
                        loadVersion(this.profile, this.versionId);
                        profile.getRepository().onVersionIconChanged.fireEvent(new Event(this));
                    })
                    .start();

            clear();

            InstallerItem.InstallerItemGroup group = new InstallerItem.InstallerItemGroup(getContext(), gameVersion);

            // Conventional libraries: game, fabric, quilt, forge, neoforge, liteloader, optifine, cleanroom
            for (InstallerItem installerItem : group.getLibraries()) {
                String libraryId = installerItem.getLibraryId();
                String libraryVersion = analyzer.getVersion(libraryId).orElse(null);
                boolean libraryConfigurable = libraryVersion != null && analyzer.getLibraryStatus(libraryId) == LibraryAnalyzer.LibraryMark.LibraryStatus.CLEAR;

                // Skip fabric-api and quilt-api
                if (libraryId.contains("fabric-api") || libraryId.contains("quilt-api")) {
                    continue;
                }

                installerItem.libraryVersion.set(libraryVersion);
                installerItem.upgradable.set(libraryConfigurable);
                installerItem.installable.set(true);
                installerItem.action.set(() -> {
                    com.kongbai.kculauncher.ui.download.version.InstallerListPage page = new com.kongbai.kculauncher.ui.download.version.InstallerListPage(getContext(), KCUPage.PAGE_ID_TEMP, gameVersion, libraryId, remoteVersion -> {
                        if (libraryVersion == null) {
                            finish(profile, remoteVersion);
                        } else {
                            KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
                            builder.setCancelable(false);
                            builder.setAlertLevel(KCUAlertDialog.AlertLevel.INFO);
                            builder.setTitle(getContext().getString(R.string.install_change_version));
                            builder.setMessage(getContext().getString(R.string.install_change_version_confirm, AndroidUtilKt.getLocalizedText(getContext(), "install_installer_" + libraryId), libraryVersion, remoteVersion.getSelfVersion()));
                            builder.setPositiveButton(() -> finish(profile, remoteVersion));
                            builder.setNegativeButton(null);
                            builder.create().show();
                        }
                    });
                    UIManager.getInstance().getManageUI().showTempPage(page);
                });
                boolean removable = !LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId().equals(libraryId) && libraryConfigurable;
                installerItem.removable.set(removable);
                if (removable) {
                    Runnable action = removeAction.apply(libraryId);
                    installerItem.removeAction.set(action);
                }
                addView(installerItem);
            }
        }, Schedulers.androidUIThread());
    }

    public void installOffline() {
        ArrayList<String> suffix = new ArrayList<>();
        suffix.add(".jar");
        MainActivity.getInstance().fileLauncher.launchSingleSelection(null, suffix, files -> {
            if (files == null) return;
            File file = files.get(0).toFile(getActivity(), new File(KCUPath.CACHE_DIR));
            if (file.exists()) {
                doInstallOffline(file);
            }
        });
    }

    private void doInstallOffline(File file) {
        Task<?> task = profile.getDependency().installLibraryAsync(version, file.toPath())
                .thenComposeAsync(profile.getRepository()::saveAsync)
                .thenComposeAsync(profile.getRepository().refreshVersionsAsync());
        task.setName(getContext().getString(R.string.install_installer_install_offline));
        TaskExecutor executor = task.executor(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                Schedulers.androidUIThread().execute(() -> {
                    if (success) {
                        loadVersion(profile, versionId);
                        KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
                        builder.setAlertLevel(KCUAlertDialog.AlertLevel.INFO);
                        builder.setCancelable(false);
                        builder.setMessage(getContext().getString(R.string.install_success));
                        builder.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), () -> profile.getRepository().onVersionIconChanged.fireEvent(new Event(this)));
                        builder.create().show();
                    } else {
                        if (executor.getException() == null)
                            return;
                        alertFailureMessage(getContext(), executor.getException(), () -> {
                        });
                    }
                    loadVersion(InstallerListPage.this.profile, InstallerListPage.this.versionId);
                });
            }
        });
        TaskDialog dialog = new TaskDialog(getContext(), TaskCancellationAction.NORMAL);
        dialog.setTitle(getContext().getString(R.string.install_installer_install_offline));
        dialog.setExecutor(executor);
        dialog.show();
        executor.start();
    }

    private void clear() {
        if (parent == null) {
            parent = new LinearLayoutCompat(getContext());
            parent.setOrientation(LinearLayoutCompat.VERTICAL);
            scrollView.addView(parent);
            ViewGroup.LayoutParams layoutParams = scrollView.getChildAt(0).getLayoutParams();
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            scrollView.getChildAt(0).setLayoutParams(layoutParams);
        }
        parent.removeAllViews();
    }

    private void addView(InstallerItem installerItem) {
        if (parent == null) {
            parent = new LinearLayoutCompat(getContext());
            parent.setOrientation(LinearLayoutCompat.VERTICAL);
            scrollView.addView(parent);
            ViewGroup.LayoutParams layoutParams = scrollView.getChildAt(0).getLayoutParams();
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            scrollView.getChildAt(0).setLayoutParams(layoutParams);
        }
        View view = installerItem.createView();
        if (parent.getChildCount() > 0) {
            view.setPadding(0, ConvertUtils.dip2px(getContext(), 10), 0, 0);
        }
        parent.addView(view);
    }

    private void finish(Profile profile, ComponentRemoteVersion remoteVersion) {
        // We remove library but not save it,
        // so if installation failed will not break down current version.
        Task<Version> ret = Task.supplyAsync(() -> version);
        List<String> stages = new ArrayList<>();
        ret = ret.thenComposeAsync(version -> profile.getDependency(DownloadProviders.getDownloadProvider()).installLibraryAsync(version, remoteVersion));
        stages.add(String.format("fcl.install.%s:%s", remoteVersion.getComponentType().getPatchId(), remoteVersion.getSelfVersion()));

        Task<?> task = ret.thenComposeAsync(profile.getRepository()::saveAsync).thenComposeAsync(profile.getRepository().refreshVersionsAsync()).withStagesHint(stages);

        TaskDialog pane = new TaskDialog(getContext(), new TaskCancellationAction(AppCompatDialog::dismiss));

        pane.setTitle(getContext().getString(R.string.install_change_version));

        Schedulers.androidUIThread().execute(() -> {
            TaskExecutor executor = task.executor(new TaskListener() {
                @Override
                public void onStop(boolean success, TaskExecutor executor) {
                    Schedulers.androidUIThread().execute(() -> {
                        if (success) {
                            KCUAlertDialog.Builder builder1 = new KCUAlertDialog.Builder(getContext());
                            builder1.setAlertLevel(KCUAlertDialog.AlertLevel.INFO);
                            builder1.setCancelable(false);
                            builder1.setMessage(getContext().getString(R.string.install_success));
                            builder1.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), () -> {
                                UIManager.getInstance().getManageUI().dismissCurrentTempPage();
                                profile.getRepository().onVersionIconChanged.fireEvent(new Event(this));
                            });
                            builder1.create().show();
                        } else {
                            if (executor.getException() == null)
                                return;
                            alertFailureMessage(getContext(), executor.getException(), () -> {
                            });
                        }
                        loadVersion(InstallerListPage.this.profile, InstallerListPage.this.versionId);
                    });
                }
            });
            pane.setExecutor(executor);
            pane.show();
            executor.start();
        });
    }

    @Override
    public void onClick(View view) {
        if (view == installOfflineButton) {
            installOffline();
        }
    }
}
