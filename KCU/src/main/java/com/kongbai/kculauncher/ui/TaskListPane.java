package com.kongbai.kculauncher.ui;

import static com.kongbai.kculauncher.mio.util.AndroidUtilKt.getLocalizedText;
import static com.kongbai.kculauncher.core.util.Lang.tryCast;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.game.HMCLModpackInstallTask;
import com.kongbai.kculauncher.core.download.cleanroom.CleanroomInstallTask;
import com.kongbai.kculauncher.core.download.fabric.FabricAPIInstallTask;
import com.kongbai.kculauncher.core.download.fabric.FabricInstallTask;
import com.kongbai.kculauncher.core.download.forge.ForgeNewInstallTask;
import com.kongbai.kculauncher.core.download.forge.ForgeOldInstallTask;
import com.kongbai.kculauncher.core.download.game.GameAssetDownloadTask;
import com.kongbai.kculauncher.core.download.game.GameInstallTask;
import com.kongbai.kculauncher.core.download.legacyfabric.LegacyFabricAPIInstallTask;
import com.kongbai.kculauncher.core.download.legacyfabric.LegacyFabricInstallTask;
import com.kongbai.kculauncher.core.download.liteloader.LiteLoaderInstallTask;
import com.kongbai.kculauncher.core.download.neoforge.NeoForgeInstallTask;
import com.kongbai.kculauncher.core.download.neoforge.NeoForgeOldInstallTask;
import com.kongbai.kculauncher.core.download.optifine.OptiFineInstallTask;
import com.kongbai.kculauncher.core.mod.MinecraftInstanceTask;
import com.kongbai.kculauncher.core.mod.ModpackInstallTask;
import com.kongbai.kculauncher.core.mod.ModpackUpdateTask;
import com.kongbai.kculauncher.core.mod.curse.CurseCompletionTask;
import com.kongbai.kculauncher.core.mod.curse.CurseForgeModpackExportTask;
import com.kongbai.kculauncher.core.mod.curse.CurseInstallTask;
import com.kongbai.kculauncher.core.mod.mcbbs.McbbsModpackCompletionTask;
import com.kongbai.kculauncher.core.mod.mcbbs.McbbsModpackExportTask;
import com.kongbai.kculauncher.core.mod.modrinth.ModrinthCompletionTask;
import com.kongbai.kculauncher.core.mod.modrinth.ModrinthModpackExportTask;
import com.kongbai.kculauncher.core.mod.modrinth.ModrinthInstallTask;
import com.kongbai.kculauncher.core.mod.multimc.MultiMCModpackExportTask;
import com.kongbai.kculauncher.core.mod.multimc.MultiMCModpackInstallTask;
import com.kongbai.kculauncher.core.mod.server.ServerModpackCompletionTask;
import com.kongbai.kculauncher.core.mod.server.ServerModpackExportTask;
import com.kongbai.kculauncher.core.mod.server.ServerModpackLocalInstallTask;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.task.TaskExecutor;
import com.kongbai.kculauncher.core.task.TaskListener;
import com.kongbai.kculauncher.core.util.Lang;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.library.component.KCUAdapter;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.view.KCUImageView;
import com.kongbai.kculauncher.library.component.view.KCUProgressBar;
import com.kongbai.kculauncher.library.component.view.KCUTextView;
import com.kongbai.kculauncher.library.util.ConvertUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TaskListPane extends KCUAdapter {

    private final TaskExecutor executor;
    private final TaskListener taskListener;
    private final Map<Task<?>, ProgressListNode> nodes = new HashMap<>();
    private final List<StageNode> stageNodes = new ArrayList<>();
    private final ArrayList<View> listBox = new ArrayList<>();

    public TaskListPane(Context context, TaskExecutor taskExecutor) {
        super(context);
        this.executor = taskExecutor;
        this.taskListener = createTaskListener();
        executor.addTaskListener(taskListener);
    }

    /** 解除对 executor 的监听并释放全部行 View，对话框关闭时调用，避免 View 树随长命 executor 泄漏 */
    public void release() {
        executor.removeTaskListener(taskListener);
        nodes.forEach((task, node) -> node.unbind());
        nodes.clear();
        stageNodes.clear();
        listBox.clear();
    }

    @Override
    public int getCount() {
        return listBox.size();
    }

    @Override
    public Object getItem(int i) {
        return listBox.get(i);
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        return listBox.get(i);
    }

    private TaskListener createTaskListener() {
        List<String> stages = Lang.removingDuplicates(executor.getStages());
        return new TaskListener() {
            @Override
            public void onStart() {
                Schedulers.androidUIThread().execute(() -> {
                    stageNodes.clear();
                    stageNodes.addAll(stages.stream().map(it -> new StageNode(getContext(), it)).collect(Collectors.toList()));
                    for (StageNode stageNode : stageNodes) {
                        listBox.add(stageNode.getView());
                    }
                    notifyDataSetChanged();
                });
            }

            @Override
            public void onReady(Task<?> task) {
                if (task.getStage() != null) {
                    Schedulers.androidUIThread().execute(() -> stageNodes.stream().filter(x -> x.stage.equals(task.getStage())).findAny().ifPresent(StageNode::begin));
                }
            }

            @Override
            public void onRunning(Task<?> task) {
                if (!task.getSignificance().shouldShow() || task.getName() == null)
                    return;

                if (task instanceof GameAssetDownloadTask) {
                    task.setName(getContext().getString(R.string.assets_download_all));
                } else if (task instanceof GameInstallTask) {
                    task.setName(getContext().getString(R.string.install_installer_install, getContext().getString(R.string.install_installer_game)));
                } else if (task instanceof CleanroomInstallTask) {
                    task.setName(getContext().getString(R.string.install_installer_install, getContext().getString(R.string.install_installer_cleanroom)));
                } else if (task instanceof ForgeNewInstallTask || task instanceof ForgeOldInstallTask) {
                    task.setName(getContext().getString(R.string.install_installer_install, getContext().getString(R.string.install_installer_forge)));
                } else if (task instanceof NeoForgeInstallTask || task instanceof NeoForgeOldInstallTask) {
                    task.setName(getContext().getString(R.string.install_installer_install, getContext().getString(R.string.install_installer_neoforge)));
                } else if (task instanceof LiteLoaderInstallTask) {
                    task.setName(getContext().getString(R.string.install_installer_install, getContext().getString(R.string.install_installer_liteloader)));
                } else if (task instanceof OptiFineInstallTask) {
                    task.setName(getContext().getString(R.string.install_installer_install, getContext().getString(R.string.install_installer_optifine)));
                } else if (task instanceof FabricInstallTask) {
                    task.setName(getContext().getString(R.string.install_installer_install, getContext().getString(R.string.install_installer_fabric)));
                } else if (task instanceof FabricAPIInstallTask) {
                    task.setName(getContext().getString(R.string.install_installer_install, getContext().getString(R.string.install_installer_fabric_api)));
                } else if (task instanceof LegacyFabricInstallTask) {
                    task.setName(getContext().getString(R.string.install_installer_install, getContext().getString(R.string.install_installer_legacyfabric)));
                } else if (task instanceof LegacyFabricAPIInstallTask) {
                    task.setName(getContext().getString(R.string.install_installer_install, getContext().getString(R.string.install_installer_legacyfabric_api)));
                } else if (task instanceof CurseCompletionTask || task instanceof ModrinthCompletionTask || task instanceof ServerModpackCompletionTask || task instanceof McbbsModpackCompletionTask) {
                    task.setName(getContext().getString(R.string.modpack_completion));
                } else if (task instanceof ModpackInstallTask) {
                    task.setName(getContext().getString(R.string.modpack_installing));
                } else if (task instanceof ModpackUpdateTask) {
                    task.setName(getContext().getString(R.string.modpack_update));
                } else if (task instanceof CurseInstallTask) {
                    task.setName(getContext().getString(R.string.modpack_install, getContext().getString(R.string.modpack_type_curse)));
                } else if (task instanceof MultiMCModpackInstallTask) {
                    task.setName(getContext().getString(R.string.modpack_install, getContext().getString(R.string.modpack_type_multimc)));
                } else if (task instanceof ModrinthInstallTask) {
                    task.setName(getContext().getString(R.string.modpack_install, getContext().getString(R.string.modpack_type_modrinth)));
                } else if (task instanceof ServerModpackLocalInstallTask) {
                    task.setName(getContext().getString(R.string.modpack_install, getContext().getString(R.string.modpack_type_server)));
                } else if (task instanceof HMCLModpackInstallTask) {
                    task.setName(getContext().getString(R.string.modpack_install, getContext().getString(R.string.modpack_type_hmcl)));
                } else if (task instanceof McbbsModpackExportTask || task instanceof MultiMCModpackExportTask || task instanceof ServerModpackExportTask
                        || task instanceof CurseForgeModpackExportTask || task instanceof ModrinthModpackExportTask) {
                    task.setName(getContext().getString(R.string.modpack_export));
                } else if (task instanceof MinecraftInstanceTask) {
                    task.setName(getContext().getString(R.string.modpack_scan));
                }

                Schedulers.androidUIThread().execute(() -> {
                    StageNode stageNode = stageNodes.stream().filter(x -> x.stage.equals(task.getInheritedStage())).findAny().orElse(null);
                    ProgressListNode node = new ProgressListNode(getContext(), stageNode != null && stageNodes.contains(stageNode), task);
                    nodes.put(task, node);
                    listBox.add(stageNode == null || !stageNodes.contains(stageNode) ? 0 : listBox.indexOf(stageNode.getView()) + 1, node.getView());
                    notifyDataSetChanged();
                });
            }

            @Override
            public void onFinished(Task<?> task) {
                if (task.getStage() != null) {
                    Schedulers.androidUIThread().execute(() -> stageNodes.stream().filter(x -> x.stage.equals(task.getStage())).findAny().ifPresent(StageNode::succeed));
                }

                Schedulers.androidUIThread().execute(() -> {
                    ProgressListNode node = nodes.remove(task);
                    if (node == null)
                        return;
                    node.unbind();
                    listBox.remove(node.getView());
                    notifyDataSetChanged();
                });
            }

            @Override
            public void onFailed(Task<?> task, Throwable throwable) {
                if (task.getStage() != null) {
                    Schedulers.androidUIThread().execute(() -> stageNodes.stream().filter(x -> x.stage.equals(task.getStage())).findAny().ifPresent(StageNode::fail));
                }
                ProgressListNode node = nodes.remove(task);
                if (node == null)
                    return;
                Schedulers.androidUIThread().execute(() -> node.setThrowable(throwable));
            }

            @Override
            public void onPropertiesUpdate(Task<?> task) {
                if (task instanceof Task.CountTask) {
                    Schedulers.androidUIThread().execute(() -> stageNodes.stream()
                            .filter(x -> x.stage.equals(((Task<?>.CountTask) task).getCountStage()))
                            .findAny()
                            .ifPresent(StageNode::count));

                    return;
                }

                if (task.getStage() != null) {
                    Schedulers.androidUIThread().execute(() -> {
                        int total = tryCast(task.getProperties().get("total"), Integer.class).orElse(0);
                        stageNodes.stream()
                                .filter(x -> x.stage.equals(task.getStage()))
                                .findAny()
                                .ifPresent(stageNode -> stageNode.setTotal(total));
                    });
                }
            }
        };
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private static class StageNode {
        private final Context context;
        private final String stage;
        private final String message;
        private int count = 0;
        private int total = 0;
        private boolean started = false;

        private final View parent;
        private final KCUTextView title;
        private final KCUImageView icon;

        public StageNode(Context context, String stage) {
            this.context = context;
            this.stage = stage;

            parent = LayoutInflater.from(context).inflate(R.layout.item_task_stage, null);
            title = parent.findViewById(R.id.title);
            icon = parent.findViewById(R.id.icon);

            int[][] state = {
                    {

                    }
            };
            int[] color = {
                    ThemeEngine.getSystemAutoTint(context)
            };
            icon.setImageTintList(new ColorStateList(state, color));

            String stageKey = StringUtils.substringBefore(stage, ':');
            String stageValue = StringUtils.substringAfter(stage, ':');

            // @formatter:off
            switch (stageKey) {
                case "fcl.modpack": message = context.getString(R.string.install_modpack); break;
                case "fcl.modpack.download": message = context.getString(R.string.launch_state_modpack); break;
                case "fcl.install.assets": message = context.getString(R.string.assets_download); break;
                case "fcl.install.game": message = context.getString(R.string.install_installer_install, context.getString(R.string.install_installer_game) + " " + stageValue); break;
                case "fcl.install.forge": message = context.getString(R.string.install_installer_install, context.getString(R.string.install_installer_forge) + " " + stageValue); break;
                case "fcl.install.cleanroom": message = context.getString(R.string.install_installer_install, context.getString(R.string.install_installer_cleanroom) + " " + stageValue); break;
                case "fcl.install.neoforge": message = context.getString(R.string.install_installer_install, context.getString(R.string.install_installer_neoforge) + " " + stageValue); break;
                case "fcl.install.liteloader": message = context.getString(R.string.install_installer_install, context.getString(R.string.install_installer_liteloader) + " " + stageValue); break;
                case "fcl.install.optifine": message = context.getString(R.string.install_installer_install, context.getString(R.string.install_installer_optifine) + " " + stageValue); break;
                case "fcl.install.fabric": message = context.getString(R.string.install_installer_install, context.getString(R.string.install_installer_fabric) + " " + stageValue); break;
                case "fcl.install.fabric-api": message = context.getString(R.string.install_installer_install, context.getString(R.string.install_installer_fabric_api) + " " + stageValue); break;
                case "fcl.install.quilt": message = context.getString(R.string.install_installer_install, context.getString(R.string.install_installer_quilt) + " " + stageValue); break;
                default: message = getLocalizedText(context, stageKey.replace(".", "_").replace("-", "_")); break;
            }
            // @formatter:on

            title.setText(message);
            icon.setImageDrawable(context.getDrawable(R.drawable.ic_baseline_more_horiz_24));
        }

        public void begin() {
            if (started) return;
            started = true;
            icon.setImageDrawable(context.getDrawable(R.drawable.ic_baseline_arrow_forward_24));
        }

        public void fail() {
            icon.setImageDrawable(context.getDrawable(R.drawable.ic_baseline_close_24));
        }

        public void succeed() {
            icon.setImageDrawable(context.getDrawable(R.drawable.ic_baseline_done_24));
        }

        public void count() {
            updateCounter(++count, total);
        }

        public void setTotal(int total) {
            this.total = total;
            updateCounter(count, total);
        }

        @SuppressLint("DefaultLocale")
        public void updateCounter(int count, int total) {
            if (total > 0)
                title.setText(String.format("%s - %d/%d", message, count, total));
            else
                title.setText(message);
        }

        public View getView() {
            return parent;
        }
    }

    private static class ProgressListNode {
        private final View parent;

        private final KCUProgressBar bar;
        private final KCUTextView state;

        public ProgressListNode(Context context, boolean padding, Task<?> task) {
            parent = LayoutInflater.from(context).inflate(R.layout.item_task_progress, null);
            if (padding) {
                parent.setPadding(ConvertUtils.dip2px(context, 31), 0, 0, ConvertUtils.dip2px(context, 8));
            }

            bar = parent.findViewById(R.id.progress);
            KCUTextView title = parent.findViewById(R.id.name);
            state = parent.findViewById(R.id.state);

            bar.percentProgressProperty().bind(task.progressProperty());
            title.setText(task.getName());
            state.stringProperty().bind(task.messageProperty());
        }

        public void unbind() {
            bar.percentProgressProperty().unbind();
            state.stringProperty().unbind();
        }

        public void setThrowable(Throwable throwable) {
            unbind();
            state.setText(throwable.getLocalizedMessage());
            bar.setProgress(0);
        }

        public View getView() {
            return parent;
        }
    }
}