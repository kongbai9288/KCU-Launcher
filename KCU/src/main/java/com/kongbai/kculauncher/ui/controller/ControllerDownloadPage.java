package com.kongbai.kculauncher.ui.controller;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import com.kongbai.kculauncher.mio.download.DownloadManager;

import com.bumptech.glide.Glide;
import com.google.gson.GsonBuilder;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.control.download.ControllerIndex;
import com.kongbai.kculauncher.control.download.ControllerVersion;
import com.kongbai.kculauncher.databinding.PageControllerDownloadBinding;
import com.kongbai.kculauncher.setting.Controller;
import com.kongbai.kculauncher.setting.Controllers;
import com.kongbai.kculauncher.setting.DownloadProviders;
import com.kongbai.kculauncher.ui.UIManager;
import com.kongbai.kculauncher.launcher.utils.KCUPath;
import com.kongbai.kculauncher.core.task.FileDownloadTask;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.task.TaskExecutor;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.core.util.function.ExceptionalConsumer;
import com.kongbai.kculauncher.core.util.gson.JsonUtils;
import com.kongbai.kculauncher.core.util.gson.fakefx.factories.JavaFxPropertyTypeAdapterFactory;
import com.kongbai.kculauncher.core.util.io.FileUtils;
import com.kongbai.kculauncher.core.util.io.NetworkUtils;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;
import com.kongbai.kculauncher.library.component.ui.KCUPage;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CancellationException;

public class ControllerDownloadPage extends KCUPage implements View.OnClickListener {

    private final ArrayList<String> categories;
    private final ControllerIndex index;
    private final String url;
    private ControllerVersion controllerVersion;
    private PageControllerDownloadBinding binding;

    public ControllerDownloadPage(Context context, int id, int source, ArrayList<String> categories, ControllerIndex index) {
        super(context, id, R.layout.page_controller_download);
        this.categories = categories;
        this.index = index;
        this.url = (source == 0 ? ControllerRepoPage.CONTROLLER_GITHUB : ControllerRepoPage.CONTROLLER_GIT_CN) + "repo_json/" + index.getId() + "/";
        create();
    }

    private void create() {
        binding = PageControllerDownloadBinding.bind(getContentView());

        Glide.with(getContext()).load(url + "icon.png").into(binding.icon);
        binding.name.setText(index.getName());
        StringBuilder stringBuilder = new StringBuilder();
        categories.forEach(it -> stringBuilder.append(it).append("   "));
        String tagText = StringUtils.removeSuffix(stringBuilder.toString(), "   ");
        binding.tag.setText(tagText);
        binding.intro.setText(index.getIntroduction());

        binding.retry.setOnClickListener(this);
        binding.history.setOnClickListener(this);
        binding.latest.setOnClickListener(this);

        refresh();
    }

    private void refresh() {
        setLoading(true);
        String versionUrl = url + "version.json";
        String screenshotUrl = url + "screenshots/";
        Task.supplyAsync(() -> {
            String versionStr = NetworkUtils.doGet(NetworkUtils.toURL(versionUrl));
            return JsonUtils.GSON.fromJson(versionStr, ControllerVersion.class);
        }).thenAcceptAsync((ExceptionalConsumer<ControllerVersion, Exception>) controllerVersion -> {
            controllerVersion.getHistory().sort(Comparator.comparing(ControllerVersion.VersionInfo::getVersionCode).reversed());
            this.controllerVersion = controllerVersion;
            ArrayList<String> screenshotUrls = new ArrayList<>();
            for (int i = 1; i <= controllerVersion.getScreenshot(); i++) {
                String num = i + "";
                if (num.length() == 1)
                    num = "0" + num;
                screenshotUrls.add(screenshotUrl + num + ".png");
            }
            String[] allDevices = new String[]{
                    getContext().getString(R.string.control_download_device_phone),
                    getContext().getString(R.string.control_download_device_pad),
                    getContext().getString(R.string.control_download_device_other)
            };
            ArrayList<String> devices = new ArrayList<>();
            for (int d : index.getDevice()) {
                if (0 <= d && 2 >= d)
                    devices.add(allDevices[d]);
            }
            StringBuilder stringBuilder = new StringBuilder();
            devices.forEach(it -> stringBuilder.append(it).append("  "));
            String deviceText = StringUtils.removeSuffix(stringBuilder.toString(), "  ");
            binding.list.post(() -> {
                ControllerScreenshotAdapter adapter = new ControllerScreenshotAdapter(getContext(), screenshotUrls);
                binding.list.setAdapter(adapter);
            });
            binding.author.setText(controllerVersion.getAuthor());
            binding.version.setText(controllerVersion.getLatest().getVersionName());
            binding.device.setText(deviceText);
            binding.description.setText(controllerVersion.getDescription());
        }).whenComplete(Schedulers.androidUIThread(), exception -> {
            setLoading(false);
            if (exception != null) {
                setFailed();
            }
        }).start();
    }

    public void setLoading(boolean loading) {
        Schedulers.androidUIThread().execute(() -> {
            binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.layout.setVisibility(loading ? View.GONE : View.VISIBLE);
            if (loading) {
                binding.retry.setVisibility(View.GONE);
            }
        });
    }

    public void setFailed() {
        Schedulers.androidUIThread().execute(() -> {
            binding.retry.setVisibility(View.VISIBLE);
            binding.progress.setVisibility(View.GONE);
            binding.layout.setVisibility(View.GONE);
        });
    }

    private void download(int versionCode) {
        if (Controllers.findControllerById(index.getId()).getId().equals(index.getId())) {
            KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
            builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
            builder.setCancelable(false);
            builder.setMessage(getContext().getString(R.string.control_download_exist));
            builder.setPositiveButton(() -> downloadFile(versionCode));
            builder.setNegativeButton(null);
            builder.create().show();
        } else {
            downloadFile(versionCode);
        }
    }

    private void downloadFile(int versionCode) {
        FileUtils.deleteDirectoryQuietly(new File(KCUPath.CACHE_DIR + "/control"));
        String downloadUrl = url + "versions/" + versionCode + ".json";
        String destPath = KCUPath.CONTROLLER_DIR + "/" + index.getId() + ".json";
        String cache = KCUPath.CACHE_DIR + "/control/" + index.getId() + ".json";
        boolean exist = new File(destPath).exists();
        Controller old = exist ? Controllers.findControllerById(index.getId()) : null;
        FileDownloadTask fileTask = new FileDownloadTask(NetworkUtils.toURL(downloadUrl), new File(destPath));
        fileTask.setName(index.getName());
        Task<Void> downloadTask = Task.composeAsync(() -> {
            if (exist && old != null) {
                FileUtils.copyFile(new File(destPath), new File(cache));
                ((ControllerManagePage) UIManager.getInstance().getControllerUI().getPage(0)).removeController(old);
            }
            return fileTask;
        });
        TaskExecutor executor = downloadTask.whenComplete(Schedulers.defaultScheduler(), exception -> {
            if (exception != null) {
                if (new File(cache).exists()) {
                    FileUtils.copyFile(new File(cache), new File(destPath));
                    ((ControllerManagePage) UIManager.getInstance().getControllerUI().getPage(0)).addController(old);
                }
                Schedulers.androidUIThread().execute(() -> {
                    if (exception instanceof CancellationException) {
                        Toast.makeText(getContext(), getContext().getString(R.string.message_cancelled), Toast.LENGTH_SHORT).show();
                    } else {
                        KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
                        builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                        builder.setCancelable(false);
                        builder.setTitle(getContext().getString(R.string.install_failed_downloading));
                        builder.setMessage(DownloadProviders.localizeErrorMessage(getContext(), exception));
                        builder.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), null);
                        builder.create().show();
                    }
                });
            } else {
                FileUtils.deleteDirectoryQuietly(new File(KCUPath.CACHE_DIR + "/control"));
                Controller controller = new GsonBuilder()
                        .registerTypeAdapterFactory(new JavaFxPropertyTypeAdapterFactory(true, true))
                        .setPrettyPrinting()
                        .create().fromJson(FileUtils.readText(new File(destPath)), Controller.class);
                ((ControllerManagePage) UIManager.getInstance().getControllerUI().getPage(0)).addController(controller);
                Schedulers.androidUIThread().execute(() -> Toast.makeText(getContext(), getContext().getString(R.string.install_success), Toast.LENGTH_SHORT).show());
            }
        }).executor();
        DownloadManager.submit(index.getName(), fileTask, executor);
        executor.start();
    }




    @Override
    public void onClick(View view) {
        if (view == binding.retry) {
            refresh();
        }
        if (view == binding.history && controllerVersion != null) {
            if (controllerVersion.getHistory().isEmpty()) {
                Toast.makeText(getContext(), getContext().getString(R.string.control_download_history_empty), Toast.LENGTH_SHORT).show();
            } else {
                OldVersionDialog dialog = new OldVersionDialog(getContext(), controllerVersion.getHistory(), this::download);
                dialog.show();
            }
        }
        if (view == binding.latest && controllerVersion != null) {
            download(controllerVersion.getLatest().getVersionCode());
        }
    }
}
