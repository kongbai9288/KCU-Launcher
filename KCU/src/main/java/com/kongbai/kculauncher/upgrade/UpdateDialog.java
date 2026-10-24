package com.kongbai.kculauncher.upgrade;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.content.FileProvider;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.ui.TaskDialog;
import com.kongbai.kculauncher.mio.util.AndroidUtilKt;
import com.kongbai.kculauncher.util.TaskCancellationAction;
import com.kongbai.kculauncher.launcher.bridge.KCUBridge;
import com.kongbai.kculauncher.launcher.utils.Architecture;
import com.kongbai.kculauncher.launcher.utils.KCUPath;
import com.kongbai.kculauncher.core.task.FileDownloadTask;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.task.TaskExecutor;
import com.kongbai.kculauncher.core.util.io.NetworkUtils;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCULinearLayout;
import com.kongbai.kculauncher.library.component.view.KCUTextView;
import com.kongbai.kculauncher.library.util.ConvertUtils;

import java.io.File;
import java.util.concurrent.CancellationException;

public class UpdateDialog extends KCUDialog implements View.OnClickListener {

    private final RemoteVersion version;

    private View parent;
    private ScrollView scrollView;
    private KCULinearLayout layout;

    private KCUTextView versionName;
    private KCUTextView date;
    private KCUTextView type;
    private KCUTextView description;

    private KCUButton ignore;
    private KCUButton positive;
    private KCUButton negative;
    private KCUButton netdisk;

    public UpdateDialog(@NonNull Context context, RemoteVersion version) {
        super(context);
        this.version = version;
        setCancelable(false);
        setContentView(R.layout.dialog_update);

        init();
    }

    private void init() {
        parent = findViewById(R.id.parent);
        scrollView = findViewById(R.id.text_scroll);
        layout = findViewById(R.id.layout);

        versionName = findViewById(R.id.version);
        date = findViewById(R.id.date);
        type = findViewById(R.id.type);
        description = findViewById(R.id.description);

        versionName.setText(String.format(getContext().getString(R.string.update_version), version.getVersionName()));
        date.setText(String.format(getContext().getString(R.string.update_date), version.getDate()));
        type.setText(String.format(getContext().getString(R.string.update_type), version.getDisplayType(getContext())));
        description.setText(String.format(getContext().getString(R.string.update_description), version.getDisplayDescription(getContext())));

        ignore = findViewById(R.id.ignore);
        positive = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);
        netdisk = findViewById(R.id.netdisk);
        ignore.setOnClickListener(this);
        positive.setOnClickListener(this);
        negative.setOnClickListener(this);
        netdisk.setOnClickListener(this);

        positive.setOnLongClickListener(view -> {
            AndroidUtilKt.openLink(getContext(), "https://github.com/kongbai9288/KCU-Launcher/releases/latest");
            return true;
        });

        checkHeight();
    }

    private void checkHeight() {
        parent.post(() -> layout.post(() -> {
            int maxHeight = AndroidUtilKt.getScreenHeight() - ConvertUtils.dip2px(getContext(), 30);
            if (parent.getMeasuredHeight() < maxHeight) {
                ViewGroup.LayoutParams layoutParams = scrollView.getLayoutParams();
                layoutParams.height = layout.getMeasuredHeight();
                scrollView.setLayoutParams(layoutParams);
                getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            } else {
                getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT, maxHeight);
            }
        }));
    }

    @Override
    public void onClick(View v) {
        if (v == ignore) {
            UpdateChecker.setIgnore(getContext(), version.getVersionCode());
            dismiss();
        }
        if (v == positive) {
            TaskDialog dialog = new TaskDialog(getContext(), new TaskCancellationAction(AppCompatDialog::dismiss));
            dialog.setTitle(getContext().getString(R.string.update_launcher));
            Schedulers.androidUIThread().execute(() -> {
                TaskExecutor executor = Task.composeAsync(() -> {
                    FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(getTargetArchUrl()), new File(KCUPath.CACHE_DIR, "KCU-Launcher.apk"));
                    task.setName("KCU-Launcher");
                    return task.whenComplete(Schedulers.androidUIThread(), exception -> {
                        if (exception == null) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            Uri apkUri = FileProvider.getUriForFile(getContext(), getContext().getString(com.kongbai.kculauncher.R.string.file_browser_provider), new File(KCUPath.CACHE_DIR, "KCU-Launcher.apk"));
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                            getContext().startActivity(intent);
                        } else if (!(exception instanceof CancellationException)) {
                            KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
                            builder.setCancelable(false);
                            builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                            builder.setMessage(getContext().getString(R.string.update_failed) + "\n" + exception.getMessage());
                            builder.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
                            builder.setPositiveButton(getContext().getString(R.string.update_netdisk), ()->{
                                AndroidUtilKt.openLink(getContext(), version.getNetdiskUrl());
                            });
                            builder.create().show();
                        }
                    });
                }).executor();
                dialog.setExecutor(executor);
                dialog.show();
                executor.start();
            });
            dismiss();
        }
        if (v == negative) {
            dismiss();
        }
        if (v == netdisk) {
            AndroidUtilKt.openLink(getContext(), version.getNetdiskUrl());
            dismiss();
        }
    }

    @NonNull
    private String getTargetArchUrl() {
        String url = version.getUrl();
        String arch = switch (Architecture.getDeviceArchitecture()) {
            case Architecture.ARCH_ARM -> "armeabi-v7a";
            case Architecture.ARCH_ARM64 -> "arm64-v8a";
            case Architecture.ARCH_X86 -> "x86";
            case Architecture.ARCH_X86_64 -> "x86_64";
            default -> "all";
        };
        url = url.replace("-all", "-" + arch);
        return url;
    }
}
