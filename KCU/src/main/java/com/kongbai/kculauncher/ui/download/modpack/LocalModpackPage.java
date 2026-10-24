package com.kongbai.kculauncher.ui.download.modpack;

import static com.kongbai.kculauncher.core.util.Logging.LOG;

import android.content.Context;
import android.text.Html;
import android.view.View;
import android.widget.Toast;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.game.KCUGameRepository;
import com.kongbai.kculauncher.game.ManuallyCreatedModpackException;
import com.kongbai.kculauncher.game.ModpackHelper;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.setting.Profiles;
import com.kongbai.kculauncher.ui.UIManager;
import com.kongbai.kculauncher.core.fakefx.beans.property.BooleanProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleBooleanProperty;
import com.kongbai.kculauncher.core.mod.Modpack;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.core.util.io.CompressingUtils;
import com.kongbai.kculauncher.core.util.io.FileUtils;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class LocalModpackPage extends ModpackPage implements View.OnClickListener {

    private final String updateVersion;
    private final File modpackFile;

    private final BooleanProperty installAsVersion = new SimpleBooleanProperty(true);
    private boolean isManuallyCreated = false;
    private Modpack manifest = null;
    private Charset charset;

    public LocalModpackPage(Context context, int id, Profile profile, String updateVersion, File modpackFile) {
        super(context, id, R.layout.page_modpack, profile);
        this.updateVersion = updateVersion;
        this.modpackFile = modpackFile;

        // 原 onStart 逻辑：页面构造即读取模组包 manifest
        if (updateVersion != null) {
            editText.setText(updateVersion);
            editText.setEnabled(false);
        }

        progressBar.setVisibility(View.VISIBLE);
        layout.setVisibility(View.GONE);

        Task.supplyAsync(() -> {
                    if (!ModpackHelper.isFileModpackByExtension(modpackFile)) {
                        return StandardCharsets.UTF_8;
                    }
                    return CompressingUtils.findSuitableEncoding(modpackFile.toPath());
                })
                .thenApplyAsync(encoding -> {
                    charset = encoding;
                    manifest = ModpackHelper.readModpackManifest(modpackFile.toPath(), encoding);
                    return manifest;
                })
                .whenComplete(Schedulers.androidUIThread(), (manifest, exception) -> {
                    if (exception instanceof ManuallyCreatedModpackException) {
                        progressBar.setVisibility(View.GONE);
                        layout.setVisibility(View.VISIBLE);
                        name.setText(modpackFile.getName());
                        installAsVersion.set(false);

                        if (updateVersion == null) {
                            editText.setText(FileUtils.getNameWithoutExtension(modpackFile));
                        }

                        KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
                        builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                        builder.setCancelable(false);
                        builder.setTitle(getContext().getString(R.string.install_modpack));
                        builder.setMessage(getContext().getString(R.string.modpack_type_manual_warning));
                        builder.setPositiveButton(null);
                        builder.setNegativeButton(() -> {
                            if (updateVersion == null) {
                                UIManager.getInstance().getDownloadUI().dismissCurrentTempPage();
                            } else {
                                UIManager.getInstance().getManageUI().dismissCurrentTempPage();
                            }
                        });
                        builder.create().show();

                        this.manifest = null;
                        isManuallyCreated = true;
                        describe.setVisibility(View.GONE);
                    } else if (exception != null) {
                        LOG.log(Level.WARNING, "Failed to read modpack manifest", exception);
                        KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
                        builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                        builder.setCancelable(false);
                        builder.setTitle(getContext().getString(R.string.message_error));
                        builder.setMessage(getContext().getString(R.string.modpack_task_install_error));
                        builder.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), () -> {
                            if (updateVersion == null) {
                                UIManager.getInstance().getDownloadUI().dismissCurrentTempPage();
                            } else {
                                UIManager.getInstance().getManageUI().dismissCurrentTempPage();
                            }
                        });
                        builder.create().show();
                    } else {
                        progressBar.setVisibility(View.GONE);
                        layout.setVisibility(View.VISIBLE);
                        name.setText(manifest.getName());
                        version.setText(manifest.getVersion());
                        author.setText(manifest.getAuthor());

                        if (updateVersion == null) {
                            editText.setText(manifest.getName().trim());
                        }
                        describe.setVisibility(View.VISIBLE);
                    }
                }).start();
    }

    @Override
    protected void onInstall() {
        String name;
        if (updateVersion != null) {
            name = updateVersion;
        } else {
            String str = editText.getText().toString();
            if (installAsVersion.get()) {
                if (StringUtils.isBlank(str)) {
                    Toast.makeText(getContext(), getContext().getString(R.string.input_not_empty), Toast.LENGTH_SHORT).show();
                    return;
                } else if (profile.getRepository().versionIdConflicts(str)) {
                    Toast.makeText(getContext(), getContext().getString(R.string.install_new_game_already_exists), Toast.LENGTH_SHORT).show();
                    return;
                } else if (!KCUGameRepository.isValidVersionId(str)) {
                    Toast.makeText(getContext(), getContext().getString(R.string.install_new_game_malformed), Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                if (StringUtils.isBlank(str)) {
                    Toast.makeText(getContext(), getContext().getString(R.string.input_not_empty), Toast.LENGTH_SHORT).show();
                    return;
                } else if (ModpackHelper.isExternalGameNameConflicts(str) || Profiles.getProfiles().stream().anyMatch(p -> p.getName().equals(str))) {
                    Toast.makeText(getContext(), getContext().getString(R.string.install_new_game_already_exists), Toast.LENGTH_SHORT).show();
                    return;
                } else if (!KCUGameRepository.isValidVersionId(str)) {
                    Toast.makeText(getContext(), getContext().getString(R.string.install_new_game_malformed), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            name = str;
        }
        Task<?> task;
        if (isManuallyCreated) {
            task = ModpackInstaller.getModpackInstallTask(profile, modpackFile, name, charset);
        } else {
            if (updateVersion == null) {
                task = ModpackInstaller.getModpackInstallTask(getContext(), profile, modpackFile, manifest, name);
            } else {
                task = ModpackInstaller.getModpackInstallTask(getContext(), profile, updateVersion, modpackFile, manifest, name);
            }
        }
        if (task != null)
            ModpackInstaller.installModpack(getContext(), task, updateVersion != null);
    }

    @Override
    protected void onDescribe() {
        if (manifest != null) {
            KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
            builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
            builder.setCancelable(false);
            builder.setTitle(getContext().getString(R.string.modpack_description));
            CharSequence charSequence = Html.fromHtml(manifest.getDescription(), 0);
            builder.setMessage(charSequence);
            builder.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
            builder.create().show();
        }
    }
}
