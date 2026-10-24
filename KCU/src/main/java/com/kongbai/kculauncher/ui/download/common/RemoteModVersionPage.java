package com.kongbai.kculauncher.ui.download.common;

import android.content.Context;
import android.widget.ListView;

import com.kongbai.kculauncher.mio.download.DownloadManager;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.activity.MainActivity;
import com.kongbai.kculauncher.ui.UIManager;
import com.kongbai.kculauncher.ui.download.DownloadUI;
import com.kongbai.kculauncher.core.mod.RemoteMod;
import com.kongbai.kculauncher.core.task.FileDownloadTask;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.task.TaskExecutor;
import com.kongbai.kculauncher.core.util.io.NetworkUtils;
import com.kongbai.kculauncher.library.component.ui.KCUPage;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class RemoteModVersionPage extends KCUPage {

    private final RemoteModVersionPage.DownloadCallback callback;

    /**
     * @param pinnedPageId 本页内容所属的下载模式（上游详情页创建时固定）。
     *                     不能用 {@code downloadPage.getPageId()} 现取：共享页模式可能在别处被切换，
     *                     而本页回调（安装目录）固定，现取会让两者不一致——
     *                     例如光影模式下现取到 MOD 而回调仍是光影，下载落错目录。
     */
    public RemoteModVersionPage(Context context, int id, List<RemoteMod.Version> list, @Nullable RemoteModVersionPage.DownloadCallback callback, DownloadPage downloadPage, int pinnedPageId) {
        super(context, id, R.layout.page_download_addon_version);
        this.callback = callback;

        // 原 onStart 逻辑：页面构造即初始化列表
        ListView listView = findViewById(R.id.list);
        ModVersionAdapter adapter = new ModVersionAdapter(getContext(), list, modVersion -> {
            // 模组模式先出下载选项页（可加前置），其余模式直接下载；判定与回调同源
            if (pinnedPageId == DownloadUI.PAGE_ID_DOWNLOAD_MOD) {
                RemoteModDownloadPage page = new RemoteModDownloadPage(getContext(), KCUPage.PAGE_ID_TEMP, modVersion, callback, this, downloadPage);
                UIManager.getInstance().getDownloadUI().showTempPage(page);
            } else {
                download(modVersion);
            }
        });
        listView.setAdapter(adapter);
    }

    public void download(RemoteMod.Version file) {
        if (this.callback == null) {
            saveAs(file);
        } else {
            this.callback.download(file);
        }
    }

    public void saveAs(RemoteMod.Version file) {
        MainActivity.getInstance().fileLauncher.launchSingleSelection(null, null, true, files -> {
            if (files == null) return;
            String folder = files.get(0).getPath();
            FileDownloadTask fileTask = new FileDownloadTask(NetworkUtils.toURL(file.file().url()), new File(folder, file.file().filename()), file.file().getIntegrityCheck());
            fileTask.setName(file.name());
            Task<Void> downloadTask = Task.composeAsync(() -> fileTask);
            TaskExecutor executor = downloadTask.executor();
            DownloadManager.submit(file.file().filename(), fileTask, executor);
            executor.start();
        });
    }


    public interface DownloadCallback {
        void download(RemoteMod.Version file);
    }
}
