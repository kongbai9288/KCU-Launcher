package com.kongbai.kculauncher.ui.download.modpack;

import android.content.Context;

import androidx.appcompat.app.AppCompatDialog;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.game.ManuallyCreatedModpackException;
import com.kongbai.kculauncher.game.ModpackHelper;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.ui.TaskDialog;
import com.kongbai.kculauncher.ui.UIManager;
import com.kongbai.kculauncher.ui.download.version.VersionInstallInfoPage;
import com.kongbai.kculauncher.util.TaskCancellationAction;
import com.kongbai.kculauncher.core.mod.MismatchedModpackTypeException;
import com.kongbai.kculauncher.core.mod.Modpack;
import com.kongbai.kculauncher.core.mod.ModpackCompletionException;
import com.kongbai.kculauncher.core.mod.UnsupportedModpackException;
import com.kongbai.kculauncher.core.mod.server.ServerModpackManifest;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.task.TaskExecutor;
import com.kongbai.kculauncher.core.task.TaskListener;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

public class ModpackInstaller {

    public static void installModpack(Context context, Task<?> task, boolean update) {
        TaskDialog pane = new TaskDialog(context, new TaskCancellationAction(AppCompatDialog::dismiss));
        pane.setTitle(context.getString(R.string.install_modpack));
        Schedulers.androidUIThread().execute(() -> {
            TaskExecutor executor = task.executor(new TaskListener() {
                @Override
                public void onStop(boolean success, TaskExecutor executor) {
                    Schedulers.androidUIThread().execute(() -> {
                        if (success) {
                            KCUAlertDialog.Builder builder1 = new KCUAlertDialog.Builder(context);
                            builder1.setAlertLevel(KCUAlertDialog.AlertLevel.INFO);
                            builder1.setCancelable(false);
                            builder1.setMessage(context.getString(R.string.install_success));
                            builder1.setNegativeButton(context.getString(com.kongbai.kculauncher.R.string.dialog_positive), () -> {
                                if (update) {
                                    UIManager.getInstance().getManageUI().dismissCurrentTempPage();
                                } else {
                                    UIManager.getInstance().getDownloadUI().dismissCurrentTempPage();
                                }
                            });
                            builder1.create().show();
                        } else {
                            if (executor.getException() == null)
                                return;
                            if (executor.getException() instanceof ModpackCompletionException) {
                                if (executor.getException().getCause() instanceof FileNotFoundException) {
                                    KCUAlertDialog.Builder builder1 = new KCUAlertDialog.Builder(context);
                                    builder1.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                                    builder1.setCancelable(false);
                                    builder1.setTitle(context.getString(R.string.install_failed));
                                    builder1.setMessage(context.getString(R.string.modpack_type_curse_not_found));
                                    builder1.setNegativeButton(context.getString(com.kongbai.kculauncher.R.string.dialog_positive), () -> {
                                        if (update) {
                                            UIManager.getInstance().getManageUI().dismissCurrentTempPage();
                                        } else {
                                            UIManager.getInstance().getDownloadUI().dismissCurrentTempPage();
                                        }
                                    });
                                    builder1.create().show();
                                } else {
                                    KCUAlertDialog.Builder builder1 = new KCUAlertDialog.Builder(context);
                                    builder1.setAlertLevel(KCUAlertDialog.AlertLevel.INFO);
                                    builder1.setCancelable(false);
                                    builder1.setMessage(context.getString(R.string.install_success));
                                    builder1.setNegativeButton(context.getString(com.kongbai.kculauncher.R.string.dialog_positive), () -> {
                                        if (update) {
                                            UIManager.getInstance().getManageUI().dismissCurrentTempPage();
                                        } else {
                                            UIManager.getInstance().getDownloadUI().dismissCurrentTempPage();
                                        }
                                    });
                                    builder1.create().show();
                                }
                            } else {
                                VersionInstallInfoPage.alertFailureMessage(context, executor.getException(), () -> {
                                });
                            }
                        }

                    });
                }
            });
            pane.setExecutor(executor);
            pane.show();
            executor.start();
        });
    }

    public static Task<?> getModpackInstallTask(Profile profile, File selected, String name, Charset charset) {
        return getModpackInstallTaskAsync(null, profile, null, selected, null, null, name, charset, true);
    }

    public static Task<?> getModpackInstallTask(Context context, Profile profile, ServerModpackManifest serverModpackManifest, Modpack modpack, String name) {
        return getModpackInstallTask(context, profile, null, null, serverModpackManifest, modpack, name);
    }

    public static Task<?> getModpackInstallTask(Context context, Profile profile, File selected, Modpack modpack, String name) {
        return getModpackInstallTask(context, profile, null, selected, null, modpack, name);
    }

    public static Task<?> getModpackInstallTask(Context context, Profile profile, String updateVersion, File selected, Modpack modpack, String name) {
        return getModpackInstallTask(context, profile, updateVersion, selected, null, modpack, name);
    }

    public static Task<?> getModpackInstallTask(Context context, Profile profile, String updateVersion, File selected, ServerModpackManifest serverModpackManifest, Modpack modpack, String name) {
        return getModpackInstallTaskAsync(context, profile, updateVersion, selected, serverModpackManifest, modpack, name, null, false);
    }

    private static Task<?> getModpackInstallTaskAsync(Context context, Profile profile, String updateVersion, File selected, ServerModpackManifest serverModpackManifest, Modpack modpack, String name, Charset charset, boolean isManuallyCreated) {
        if (isManuallyCreated) {
            return ModpackHelper.getInstallManuallyCreatedModpackTask(profile, selected, name, charset);
        }

        if ((selected == null && serverModpackManifest == null) || modpack == null || name == null)
            return null;

        if (updateVersion != null) {
            if (selected == null) {
                KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(context);
                builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                builder.setCancelable(false);
                builder.setTitle(context.getString(R.string.message_error));
                builder.setMessage(context.getString(R.string.modpack_unsupported));
                builder.setNegativeButton(context.getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
                builder.create().show();
                return null;
            }
            try {
                if (serverModpackManifest != null) {
                    return ModpackHelper.getUpdateTask(profile, serverModpackManifest, modpack.getEncoding(), name, ModpackHelper.readModpackConfiguration(profile.getRepository().getModpackConfiguration(name)));
                } else {
                    return ModpackHelper.getUpdateTask(profile, selected, modpack.getEncoding(), name, ModpackHelper.readModpackConfiguration(profile.getRepository().getModpackConfiguration(name)));
                }
            } catch (UnsupportedModpackException | ManuallyCreatedModpackException e) {
                KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(context);
                builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                builder.setCancelable(false);
                builder.setTitle(context.getString(R.string.message_error));
                builder.setMessage(context.getString(R.string.modpack_unsupported));
                builder.setNegativeButton(context.getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
                builder.create().show();
            } catch (MismatchedModpackTypeException e) {
                KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(context);
                builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                builder.setCancelable(false);
                builder.setTitle(context.getString(R.string.message_error));
                builder.setMessage(context.getString(R.string.modpack_mismatched_type));
                builder.setNegativeButton(context.getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
                builder.create().show();
            } catch (IOException e) {
                KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(context);
                builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                builder.setCancelable(false);
                builder.setTitle(context.getString(R.string.message_error));
                builder.setMessage(context.getString(R.string.modpack_invalid));
                builder.setNegativeButton(context.getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
                builder.create().show();
            }
            return null;
        } else {
            if (serverModpackManifest != null) {
                return ModpackHelper.getInstallTask(profile, serverModpackManifest, name, modpack)
                        .thenRunAsync(Schedulers.androidUIThread(), () -> profile.setSelectedVersion(name));
            } else {
                return ModpackHelper.getInstallTask(profile, selected, name, modpack)
                        .thenRunAsync(Schedulers.androidUIThread(), () -> profile.setSelectedVersion(name));
            }
        }
    }

}
