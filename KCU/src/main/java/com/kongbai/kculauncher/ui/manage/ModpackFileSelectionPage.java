package com.kongbai.kculauncher.ui.manage;

import static com.kongbai.kculauncher.core.util.Lang.mapOf;
import static com.kongbai.kculauncher.core.util.Pair.pair;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatDialog;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.setting.VersionSetting;
import com.kongbai.kculauncher.ui.TaskDialog;
import com.kongbai.kculauncher.ui.UIManager;
import com.kongbai.kculauncher.util.TaskCancellationAction;
import com.kongbai.kculauncher.core.fakefx.collections.FXCollections;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableList;
import com.kongbai.kculauncher.core.mod.ModAdviser;
import com.kongbai.kculauncher.core.mod.ModpackExportInfo;
import com.kongbai.kculauncher.core.mod.curse.CurseForgeModpackExportTask;
import com.kongbai.kculauncher.core.mod.mcbbs.McbbsModpackExportTask;
import com.kongbai.kculauncher.core.mod.modrinth.ModrinthModpackExportTask;
import com.kongbai.kculauncher.core.mod.multimc.MultiMCInstanceConfiguration;
import com.kongbai.kculauncher.core.mod.multimc.MultiMCModpackExportTask;
import com.kongbai.kculauncher.core.mod.server.ServerModpackExportTask;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.task.TaskExecutor;
import com.kongbai.kculauncher.core.task.TaskListener;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.core.util.io.FileUtils;
import com.kongbai.kculauncher.library.component.KCUCheckBoxTreeAdapter;
import com.kongbai.kculauncher.library.component.KCUCheckBoxTreeItem;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.ui.KCUPage;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUProgressBar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ModpackFileSelectionPage extends KCUPage implements View.OnClickListener {

    private final Profile profile;
    private final String version;
    private final String modpackType;
    private final ModAdviser adviser;
    private final ModpackExportInfo exportInfo;
    private final File modpackFile;

    private KCUCheckBoxTreeItem<String> rootItem;

    private KCUProgressBar progressBar;
    private ListView listView;
    private KCUButton next;

    public ModpackFileSelectionPage(Context context, int id, Profile profile, String version, String type, ModAdviser adviser, ModpackExportInfo exportInfo, File file) {
        super(context, id, R.layout.page_modpack_file);
        this.profile = profile;
        this.version = version;
        this.modpackType = type;
        this.adviser = adviser;
        this.exportInfo = exportInfo;
        this.modpackFile = file;

        // 原 onCreate 逻辑：绑定控件
        progressBar = findViewById(R.id.progress);
        listView = findViewById(R.id.list);
        ThemeEngine.getInstance().registerEvent(listView, () -> listView.setBackgroundTintList(new ColorStateList(new int[][]{{}}, new int[]{ThemeEngine.getInstance().getTheme().getLtColor()})));
        next = findViewById(R.id.next);
        next.setOnClickListener(this);

        // 原 onStart 逻辑：页面构造即加载文件树
        progressBar.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        next.setVisibility(View.GONE);

        new Thread(() -> {
            this.rootItem = getTreeItem(profile.getRepository().getRunDirectory(version), "minecraft");

            Schedulers.androidUIThread().execute(() -> {
                progressBar.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
                next.setVisibility(View.VISIBLE);

                ObservableList<KCUCheckBoxTreeItem<String>> list = FXCollections.observableArrayList();
                list.add(rootItem);
                KCUCheckBoxTreeAdapter<String> adapter = new KCUCheckBoxTreeAdapter<>(getContext(), list);
                listView.setAdapter(adapter);
            });
        }).start();
    }

    private void finish() {
        ArrayList<String> list = new ArrayList<>();
        getFilesNeeded(rootItem, "minecraft", list);
        exportInfo.setWhitelist(list);

        TaskDialog taskDialog = new TaskDialog(getContext(), new TaskCancellationAction(AppCompatDialog::dismiss));
        taskDialog.setTitle(getContext().getString(R.string.message_doing));

        Task<?> task = getExportTask(modpackType, exportInfo, modpackFile);
        TaskExecutor executor = task.executor(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                Schedulers.androidUIThread().execute(() -> {
                    if (success) {
                        KCUAlertDialog.Builder builder1 = new KCUAlertDialog.Builder(getContext());
                        builder1.setAlertLevel(KCUAlertDialog.AlertLevel.INFO);
                        builder1.setCancelable(false);
                        builder1.setMessage(getContext().getString(R.string.message_success));
                        builder1.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), () -> UIManager.getInstance().getManageUI().dismissAllTempPages());
                        builder1.create().show();
                    } else {
                        if (executor.getException() == null)
                            return;
                        String appendix = StringUtils.getStackTrace(executor.getException());
                        KCUAlertDialog.Builder builder1 = new KCUAlertDialog.Builder(getContext());
                        builder1.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                        builder1.setCancelable(false);
                        builder1.setTitle(getContext().getString(R.string.message_failed));
                        builder1.setMessage(appendix);
                        builder1.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
                        builder1.create().show();
                    }

                });
            }
        });
        taskDialog.setExecutor(executor);
        taskDialog.show();
        executor.start();
    }

    private KCUCheckBoxTreeItem<String> getTreeItem(File file, String basePath) {
        if (!file.exists())
            return null;

        ModAdviser.ModSuggestion state = ModAdviser.ModSuggestion.SUGGESTED;
        if (basePath.length() > "minecraft/".length()) {
            state = adviser.advise(StringUtils.substringAfter(basePath, "minecraft/") + (file.isDirectory() ? "/" : ""), file.isDirectory());
            if (file.isFile() && Objects.equals(FileUtils.getNameWithoutExtension(file), version)) // Ignore <version>.json, <version>.jar
                state = ModAdviser.ModSuggestion.HIDDEN;
            if (file.isDirectory() && Objects.equals(file.getName(), version + "-natives")) // Ignore <version>-natives
                state = ModAdviser.ModSuggestion.HIDDEN;
            if (state == ModAdviser.ModSuggestion.HIDDEN)
                return null;
        }

        ObservableList<KCUCheckBoxTreeItem<String>> list = FXCollections.observableArrayList();
        KCUCheckBoxTreeItem<String> item = new KCUCheckBoxTreeItem<>(StringUtils.substringAfterLast(basePath, "/"), null, list);
        if (state == ModAdviser.ModSuggestion.SUGGESTED)
            item.setSelected(true);

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File it : files) {
                    KCUCheckBoxTreeItem<String> subItem = getTreeItem(it, basePath + "/" + it.getName());
                    if (subItem != null) {
                        item.setSelected(subItem.isSelected() || item.isSelected());
                        if (!subItem.isSelected()) {
                            item.setIndeterminate(true);
                        }
                        subItem.selectedProperty().addListener(observable -> item.checkProperty());
                        subItem.indeterminateProperty().addListener(observable -> item.checkProperty());
                        item.getSubItem().add(subItem);
                    }
                }
            }
            if (!item.isSelected()) item.setIndeterminate(false);

            // Empty folder need not to be displayed.
            if (item.getSubItem().size() == 0) {
                return null;
            }
        }

        if (TRANSLATION.containsKey(basePath)) {
            item.setComment(TRANSLATION.get(basePath));
        }
        item.setExpanded("minecraft".equals(basePath));

        return item;
    }

    private void getFilesNeeded(KCUCheckBoxTreeItem<String> item, String basePath, List<String> list) {
        if (item == null) return;
        if (item.isSelected() || item.isIndeterminate()) {
            if (basePath.length() > "minecraft/".length())
                list.add(StringUtils.substringAfter(basePath, "minecraft/"));
            item.getSubItem().forEach(it -> getFilesNeeded(it, basePath + "/" + it.getData(), list));
        }
    }

    private Task<?> getExportTask(String modpackType, ModpackExportInfo exportInfo, File modpackFile) {
        return new Task<Object>() {
            Task<?> exportTask;

            @Override
            public boolean doPreExecute() {
                return true;
            }

            @Override
            public void preExecute() throws Exception {
                switch (modpackType) {
                    case ModpackTypeSelectionPage.MODPACK_TYPE_MCBBS:
                        exportTask = exportAsMcbbs(exportInfo, modpackFile);
                        break;
                    case ModpackTypeSelectionPage.MODPACK_TYPE_MULTIMC:
                        exportTask = exportAsMultiMC(exportInfo, modpackFile);
                        break;
                    case ModpackTypeSelectionPage.MODPACK_TYPE_SERVER:
                        exportTask = exportAsServer(exportInfo, modpackFile);
                        break;
                    case ModpackTypeSelectionPage.MODPACK_TYPE_CURSEFORGE:
                        exportTask = exportAsCurseForge(exportInfo, modpackFile);
                        break;
                    case ModpackTypeSelectionPage.MODPACK_TYPE_MODRINTH:
                        exportTask = exportAsModrinth(exportInfo, modpackFile);
                        break;
                    default:
                        throw new IllegalStateException("Unrecognized modpack type " + modpackType);
                }

            }

            @Override
            public Collection<Task<?>> getDependents() {
                return Collections.singleton(exportTask);
            }

            @Override
            public void execute() throws Exception {

            }
        }.setSignificance(Task.TaskSignificance.MINOR);
    }

    private Task<?> exportAsMcbbs(ModpackExportInfo exportInfo, File modpackFile) {
        return new Task<Void>() {
            Task<?> dependency = null;

            @Override
            public void execute() {
                dependency = new McbbsModpackExportTask(profile.getRepository(), version, exportInfo, modpackFile);
            }

            @Override
            public Collection<Task<?>> getDependencies() {
                return Collections.singleton(dependency);
            }
        }.setSignificance(Task.TaskSignificance.MINOR);
    }

    private Task<?> exportAsMultiMC(ModpackExportInfo exportInfo, File modpackFile) {
        return new Task<Void>() {
            Task<?> dependency;

            @Override
            public void execute() {
                VersionSetting vs = profile.getVersionSetting(version);
                dependency = new MultiMCModpackExportTask(profile.getRepository(), version, exportInfo.getWhitelist(),
                        new MultiMCInstanceConfiguration(
                                "OneSix",
                                exportInfo.getName() + "-" + exportInfo.getVersion(),
                                null,
                                null,
                                "",
                                "",
                                null,
                                exportInfo.getDescription(),
                                null,
                                exportInfo.getJavaArguments(),
                                false,
                                854,
                                480,
                                vs.getMaxMemory(),
                                exportInfo.getMinMemory(),
                                false,
                                /* showConsoleOnError */ true,
                                /* autoCloseConsole */ false,
                                /* overrideMemory */ true,
                                /* overrideJavaLocation */ false,
                                /* overrideJavaArgs */ true,
                                /* overrideConsole */ true,
                                /* overrideCommands */ true,
                                /* overrideWindow */ true,
                                /* iconKey */ null // TODO
                        ), modpackFile);
            }

            @Override
            public Collection<Task<?>> getDependencies() {
                return Collections.singleton(dependency);
            }
        }.setSignificance(Task.TaskSignificance.MINOR);
    }

    private Task<?> exportAsServer(ModpackExportInfo exportInfo, File modpackFile) {
        return new Task<Void>() {
            Task<?> dependency;

            @Override
            public void execute() {
                dependency = new ServerModpackExportTask(profile.getRepository(), version, exportInfo, modpackFile);
            }

            @Override
            public Collection<Task<?>> getDependencies() {
                return Collections.singleton(dependency);
            }
        }.setSignificance(Task.TaskSignificance.MINOR);
    }

    private Task<?> exportAsCurseForge(ModpackExportInfo exportInfo, File modpackFile) {
        return new Task<Void>() {
            Task<?> dependency;

            @Override
            public void execute() {
                dependency = new CurseForgeModpackExportTask(profile.getRepository(), version, exportInfo, modpackFile);
            }

            @Override
            public Collection<Task<?>> getDependencies() {
                return Collections.singleton(dependency);
            }
        }.setSignificance(Task.TaskSignificance.MINOR);
    }

    private Task<?> exportAsModrinth(ModpackExportInfo exportInfo, File modpackFile) {
        return new Task<Void>() {
            Task<?> dependency;

            @Override
            public void execute() {
                dependency = new ModrinthModpackExportTask(profile.getRepository(), version, exportInfo, modpackFile);
            }

            @Override
            public Collection<Task<?>> getDependencies() {
                return Collections.singleton(dependency);
            }
        }.setSignificance(Task.TaskSignificance.MINOR);
    }


    @Override
    public void onClick(View v) {
        if (v == next) {
            finish();
        }
    }

    private final Map<String, String> TRANSLATION = mapOf(
            pair("minecraft/fclversion.cfg", getContext().getString(R.string.modpack_files_fclversion_cfg)),
            pair("minecraft/servers.dat", getContext().getString(R.string.modpack_files_servers_dat)),
            pair("minecraft/saves", getContext().getString(R.string.modpack_files_saves)),
            pair("minecraft/mods", getContext().getString(R.string.modpack_files_mods)),
            pair("minecraft/config", getContext().getString(R.string.modpack_files_config)),
            pair("minecraft/liteconfig", getContext().getString(R.string.modpack_files_liteconfig)),
            pair("minecraft/resourcepacks", getContext().getString(R.string.modpack_files_resourcepacks)),
            pair("minecraft/resources", getContext().getString(R.string.modpack_files_resourcepacks)),
            pair("minecraft/options.txt", getContext().getString(R.string.modpack_files_options_txt)),
            pair("minecraft/optionsshaders.txt", getContext().getString(R.string.modpack_files_optionsshaders_txt)),
            pair("minecraft/mods/VoxelMods", getContext().getString(R.string.modpack_files_mods_voxelmods)),
            pair("minecraft/dumps", getContext().getString(R.string.modpack_files_dumps)),
            pair("minecraft/blueprints", getContext().getString(R.string.modpack_files_blueprints)),
            pair("minecraft/scripts", getContext().getString(R.string.modpack_files_scripts))
    );
}
