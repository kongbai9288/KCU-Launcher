package com.kongbai.kculauncher.library.browser.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.appcompat.widget.LinearLayoutCompat;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.library.browser.FileBrowser;
import com.kongbai.kculauncher.library.browser.FileOperator;
import com.kongbai.kculauncher.library.browser.options.LibMode;
import com.kongbai.kculauncher.library.browser.options.SortMode;
import com.kongbai.kculauncher.library.component.KCUAdapter;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.view.KCUTextView;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.List;

public class FileBrowserAdapter extends KCUAdapter {

    private final FileBrowser fileBrowser;
    private final FileBrowserListener listener;
    private final List<File> list;

    private ArrayList<String> selectedFiles;

    /**
     * 每一行的显示信息（时间、大小、是否目录），由调用方在 IO 线程预先算好。
     * 没有它的时候才会现算——那会在界面线程查磁盘，只作为兜底
     */
    private final Map<File, FileOperator.Row> rows;

    public FileBrowserAdapter(Context context, FileBrowser fileBrowser, Path path, ArrayList<String> selectedFiles, SortMode sortMode, FileBrowserListener listener) {
        // 这个入口在界面线程列目录，本来就慢；至少把每行的显示信息一次算好，
        // 免得滚动时还要一遍遍查磁盘
        this(context, fileBrowser, FileOperator.getFileList(path, fileBrowser, sortMode), selectedFiles, listener,
                FileOperator.buildRows(FileOperator.getFileList(path, fileBrowser, sortMode)));
    }

    /**
     * 目录内容已在 IO 线程算好时走这里：避免在界面线程列目录，文件多时会卡住界面
     */
    public FileBrowserAdapter(Context context, FileBrowser fileBrowser, List<File> files, ArrayList<String> selectedFiles,
                              FileBrowserListener listener, Map<File, FileOperator.Row> rows) {
        super(context);
        this.fileBrowser = fileBrowser;
        this.selectedFiles = selectedFiles;
        this.listener = listener;
        this.list = files != null ? files : new ArrayList<>();
        this.rows = rows;
    }

    public void setSelectedFiles(ArrayList<String> selectedFiles) {
        this.selectedFiles = selectedFiles;
    }

    public ArrayList<String> getSelectedFiles() {
        return selectedFiles;
    }

    /**
     * 当前目录下展示的文件列表
     */
    public List<File> getFiles() {
        return list;
    }

    private static class ViewHolder {
        LinearLayoutCompat parent;
        ImageView icon;
        KCUTextView name;
        KCUTextView description;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int i) {
        return list.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        final ViewHolder viewHolder;
        if (view == null){
            viewHolder = new ViewHolder();
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_file_browser, null);
            viewHolder.parent = view.findViewById(R.id.parent);
            viewHolder.icon = view.findViewById(R.id.icon);
            viewHolder.name = view.findViewById(R.id.name);
            viewHolder.description = view.findViewById(R.id.description);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        File file = list.get(i);
        // 优先用预先算好的那一份：现算会在界面线程查磁盘，滚一屏就重查一遍，
        // 大目录滚动会一顿一顿的。表是 IO 线程一次算好的，查不到才兜底现算
        FileOperator.Row row = rows != null ? rows.get(file) : null;
        String description;
        boolean directory;
        if (row != null) {
            description = row.description;
            directory = row.directory;
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            try {
                long time = FileUtils.lastModified(file);
                stringBuilder.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(time)));
            } catch (IOException e) {
                stringBuilder.append("Unknown");
            }
            directory = file.isDirectory();
            if (!directory) {
                // 文件可能在列目录之后被删掉：sizeOf 遇到不存在的路径会抛异常，
                // 滚动到那一行时整个界面崩掉。普通文件取长度即可，目录本来就跳过
                stringBuilder.append("    ").append(FileUtils.byteCountToDisplaySize(file.length()));
            }
            description = stringBuilder.toString();
        }
        @SuppressLint("UseCompatLoadingForDrawables") Drawable drawable = directory ? getContext().getDrawable(R.drawable.ic_baseline_folder_24) : getContext().getDrawable(R.drawable.ic_baseline_file_24);
        drawable.setTint(ThemeEngine.getInstance().getTheme().getColor());
        viewHolder.icon.setImageDrawable(drawable);
        viewHolder.name.setText(file.getName());
        viewHolder.description.setText(description);
        if (selectedFiles.contains(file.getAbsolutePath())) {
            viewHolder.parent.setBackgroundColor(Color.GRAY);
        } else {
            viewHolder.parent.setBackground(getContext().getDrawable(R.drawable.clickable_parent));
        }
        viewHolder.parent.setOnClickListener(view1 -> {
            if (directory) {
                listener.onEnterDir(file.getAbsolutePath());
            }
            if (!directory && fileBrowser.getLibMode() != LibMode.FOLDER_CHOOSER) {
                listener.onSelect(this, file.getAbsolutePath());
            }
        });
        viewHolder.parent.setOnLongClickListener(view12 -> {
            listener.onItemLongClick(file);
            return true;
        });
        return view;
    }

}
