package com.kongbai.kculauncher.ui.manage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialog;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.ui.TaskDialog;
import com.kongbai.kculauncher.ui.UIManager;
import com.kongbai.kculauncher.util.TaskCancellationAction;
import com.kongbai.kculauncher.core.fakefx.beans.binding.Bindings;
import com.kongbai.kculauncher.core.game.World;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.task.TaskExecutor;
import com.kongbai.kculauncher.core.task.TaskListener;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.core.util.platform.OperatingSystem;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUEditText;

import java.io.File;
import java.nio.file.Paths;

public class WorldExportDialog extends KCUDialog implements View.OnClickListener {

    private final World world;
    private final String parent;

    private KCUEditText editFileName;
    private KCUEditText editName;
    private KCUButton positive;
    private KCUButton negative;

    @SuppressLint("SetTextI18n")
    public WorldExportDialog(@NonNull Context context, World world, String parent) {
        super(context);
        this.world = world;
        this.parent = parent;
        setCancelable(false);
        setContentView(R.layout.dialog_world_export);

        editFileName = findViewById(R.id.file_name);
        editName = findViewById(R.id.name);
        editFileName.setStringValue(world.getWorldName() + ".zip");
        editName.setStringValue(world.getWorldName());
        editFileName.setText(world.getWorldName() + ".zip");
        editName.setText(world.getWorldName());
        positive = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);
        positive.setOnClickListener(this);
        negative.setOnClickListener(this);

        positive.disableProperty().bind(Bindings.createBooleanBinding(() ->
                        editName.getStringValue().isEmpty()
                                || StringUtils.isBlank(editFileName.getStringValue())
                                || !OperatingSystem.isNameValid(editFileName.getStringValue())
                                || new File(parent, editFileName.getStringValue()).exists(),
                editName.stringProperty().isEmpty(), editFileName.stringProperty()));
    }

    @Override
    public void onClick(View v) {
        if (v == positive) {
            TaskDialog taskDialog = new TaskDialog(getContext(), new TaskCancellationAction(AppCompatDialog::dismiss));
            taskDialog.setTitle(getContext().getString(R.string.message_doing));

            Task<?> task = Task.runAsync(getContext().getString(R.string.world_export_wizard, editName.getStringValue()), () -> world.export(Paths.get(new File(parent, editFileName.getText().toString()).getAbsolutePath()), editName.getStringValue()));
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
            dismiss();
        }
        if (v == negative) {
            dismiss();
        }
    }
}
