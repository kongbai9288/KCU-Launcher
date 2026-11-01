package com.kongbai.kculauncher.library.browser;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.kongbai.kculauncher.mio.dialog.ItemSelectionDialog;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.util.Logging;
import com.kongbai.kculauncher.library.browser.adapter.FileBrowserAdapter;
import com.kongbai.kculauncher.library.browser.adapter.FileBrowserListener;
import com.kongbai.kculauncher.library.browser.options.LibMode;
import com.kongbai.kculauncher.library.browser.options.SelectionMode;
import com.kongbai.kculauncher.library.browser.options.SortMode;
import com.kongbai.kculauncher.library.component.KCUActivity;
import com.kongbai.kculauncher.library.component.dialog.EditDialog;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUTextView;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class FileBrowserActivity extends KCUActivity implements View.OnClickListener {

    private static final String PREFERENCES_NAME = "launcher";
    private static final String KEY_SORT_MODE = "file_browser_sort_mode";

    private FileBrowser fileBrowser;
    private FileBrowserAdapter adapter;

    private KCUButton back;
    private KCUButton close;
    private KCUTextView mode;
    private KCUTextView type;

    private KCUTextView currentText;
    private ListView listView;

    private KCUButton sharedDir;
    private KCUButton privateDir;
    private KCUButton manage;
    private KCUButton openExternal;
    private KCUButton selectExternal;
    private KCUButton confirm;

    private Path currentPath;
    private SortMode sortMode;

    /**
     * 每次列目录的序号：只有最新一次的结果允许写进界面，
     * 避免连续切换目录时，先发起的慢请求覆盖后发起的结果
     */
    private long listLoadToken;
    /** 目录内容正在后台读取：此时列表显示的还是上一个目录，先不响应点击 */
    private volatile boolean listLoading;
    /** 主题回调按控件登记即可，重复登记会白做，故只登记一次 */
    private boolean themeRegistered;

    private ArrayList<String> selectedFiles;
    private ArrayList<Uri> extSelected;

    private final DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private final ActivityResultLauncher<Object> launcher = registerForActivityResult(new ActivityResultContract<Object, Uri>() {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Object o) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, fileBrowser.getSelectionMode() == SelectionMode.MULTIPLE_SELECTION);
            return intent;
        }

        @Override
        public Uri parseResult(int resultCode, @Nullable Intent data) {
            if (data == null || resultCode != Activity.RESULT_OK) {
                return null;
            }
            ClipData clipData = data.getClipData();
            if (clipData != null && clipData.getItemCount() > 0) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    if (uri != null) {
                        extSelected.add(uri);
                    }
                }
            } else {
                // 有些文件管理器点了返回却仍给 RESULT_OK，此时 getData() 是 null。
                // 直接加进去会让后面 new SelectedFile(null) 抛异常，整个导入流程崩掉
                Uri single = data.getData();
                if (single != null) {
                    extSelected.add(single);
                }
            }
            return null;
        }
    }, result -> {
        if (!extSelected.isEmpty()) {
            Intent intent = new Intent();
            intent.putParcelableArrayListExtra(FileBrowser.SELECTED_FILES, extSelected);
            FileBrowserActivity.this.setResult(Activity.RESULT_OK, intent);
            FileBrowserActivity.this.finish();
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);

        fileBrowser = (FileBrowser) getIntent().getExtras().getSerializable("config");

        SharedPreferences preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        sortMode = parseSortMode(preferences.getString(KEY_SORT_MODE, null));

        mode = findViewById(R.id.mode);
        type = findViewById(R.id.type);
        mode.setText(getMode());
        type.setText(getType());
        back = findViewById(R.id.back);
        close = findViewById(R.id.close);
        back.setOnClickListener(this);
        close.setOnClickListener(this);

        sharedDir = findViewById(R.id.shared_dir);
        privateDir = findViewById(R.id.private_dir);
        manage = findViewById(R.id.manage);
        openExternal = findViewById(R.id.open_external);
        selectExternal = findViewById(R.id.select_external);
        confirm = findViewById(R.id.confirm);
        sharedDir.setOnClickListener(this);
        privateDir.setOnClickListener(this);
        manage.setOnClickListener(this);
        openExternal.setOnClickListener(this);
        selectExternal.setOnClickListener(this);
        confirm.setOnClickListener(this);

        selectedFiles = new ArrayList<>();
        extSelected = new ArrayList<>();
        currentText = findViewById(R.id.current_folder);
        listView = findViewById(R.id.list);
        refreshList(currentPath != null ? currentPath : new File(fileBrowser.getInitDir()).toPath());

        if (fileBrowser.getLibMode() != LibMode.FILE_CHOOSER) {
            selectExternal.setVisibility(View.GONE);
        }
        if (fileBrowser.getLibMode() != LibMode.FILE_BROWSER) {
            openExternal.setVisibility(View.GONE);
        }
        switch (fileBrowser.getCode()) {
            case 100:
            case 150:
            case 200:
            case 500:
            case 600:
            case 700:
            case 750:
                selectExternal.setVisibility(View.GONE);
                break;
                default:
        }
        if (!fileBrowser.isExternalSelection()) {
            selectExternal.setVisibility(View.GONE);
            openExternal.setVisibility(View.GONE);
        }

        currentText.setOnClickListener(v -> {
            new EditDialog(this, path -> {
                File file = new File(path);
                if (file.exists() && file.isDirectory()) {
                    refreshList(file.toPath());
                }
            }).show();
        });
    }

    private SortMode parseSortMode(String value) {
        if (value != null) {
            try {
                return SortMode.valueOf(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return SortMode.NAME;
    }

    private String getMode() {
        return switch (fileBrowser.getLibMode()) {
            case FILE_CHOOSER -> getString(R.string.file_browser_mode_file);
            case FOLDER_CHOOSER -> getString(R.string.file_browser_mode_folder);
            default -> getString(R.string.file_browser_mode_browse);
        };
    }

    private String getType() {
        if (fileBrowser.getSelectionMode() == SelectionMode.SINGLE_SELECTION) {
            return getString(R.string.file_browser_selection_simple);
        }
        return getString(R.string.file_browser_selection_multiple);
    }

    private void refreshList(Path path) {
        if (fileBrowser.getLibMode() == LibMode.FOLDER_CHOOSER && !selectedFiles.contains(path.toString())) {
            selectedFiles = new ArrayList<>();
            selectedFiles.add(path.toString());
        }
        // 同目录刷新（增删改名、排序）要保住滚动位置，换目录则回到顶部
        boolean sameDir = currentPath != null && currentPath.equals(path);
        int firstVisible = listView.getFirstVisiblePosition();
        View firstChild = listView.getChildAt(0);
        int topOffset = firstChild != null ? firstChild.getTop() : 0;

        currentPath = path;
        currentText.setText(path.toString());
        if (!themeRegistered) {
            ThemeEngine.getInstance().registerEvent(currentText, () -> currentText.setBackgroundColor(ThemeEngine.getInstance().getTheme().getColor()));
            themeRegistered = true;
        }

        final long token = ++listLoadToken;
        final FileBrowser config = fileBrowser;
        final SortMode sort = sortMode;
        final ArrayList<String> selected = selectedFiles;
        listLoading = true;
        // 列目录是磁盘操作，文件多时会卡住界面线程，放到 IO 线程做
        Schedulers.io().execute(() -> {
            // 内层 lambda 只能捕获不再改写的变量，故先算完再定死
            List<File> loaded;
            try {
                loaded = FileOperator.getFileList(path, config, sort);
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "Failed to list directory " + path, e);
                loaded = new ArrayList<>();
            }
            final List<File> files = loaded;
            // 读不出来的目录（无权限 / 不是目录）listFiles 返回 null，列表会是空的。
            // 不说一声的话，玩家只会以为这个文件夹本来就是空的
            final boolean listable = path.toFile().isDirectory() && path.toFile().canRead();
            Schedulers.androidUIThread().execute(() -> {
                if (isDestroyed() || isFinishing() || token != listLoadToken) {
                    return;
                }
                adapter = new FileBrowserAdapter(this, config, files, selected, createListener());
                listView.setAdapter(adapter);
                listLoading = false;
                if (files.isEmpty() && !listable) {
                    toast(R.string.file_browser_unreadable);
                }
                if (sameDir) {
                    listView.setSelectionFromTop(firstVisible, topOffset);
                } else {
                    listView.setSelection(0);
                }
            });
        });
    }

    private FileBrowserListener createListener() {
        return new FileBrowserListener() {
            @Override
            public void onEnterDir(String path) {
                // 不再屏蔽：点的条目来自当前显示的列表，路径是绝对路径，
                // 跳过去一定跳得对。屏蔽只会让这一下轻点毫无反应
                refreshList(new File(path).toPath());
            }

            @Override
            public void onSelect(FileBrowserAdapter adapter1, String path) {
                if (selectedFiles.stream().anyMatch(s -> s.equals(path))) {
                    selectedFiles.remove(path);
                } else {
                    if (fileBrowser.getSelectionMode() == SelectionMode.SINGLE_SELECTION) {
                        selectedFiles = new ArrayList<>();
                    }
                    selectedFiles.add(path);
                }
                adapter1.setSelectedFiles(selectedFiles);
                adapter1.notifyDataSetChanged();
                // 单选文件：点中即返回，不必再点一次确认
                if (fileBrowser.getLibMode() == LibMode.FILE_CHOOSER
                        && fileBrowser.getSelectionMode() == SelectionMode.SINGLE_SELECTION
                        && selectedFiles.contains(path)) {
                    confirmSelection();
                }
            }

            @Override
            public void onItemLongClick(File file) {
                showItemMenu(file);
            }
        };
    }

    /**
     * 把当前勾选作为结果返回给调用方
     */
    private void confirmSelection() {
        Intent intent = new Intent();
        intent.putParcelableArrayListExtra(FileBrowser.SELECTED_FILES, (ArrayList<? extends Parcelable>) selectedFiles.stream().map(path -> Uri.fromFile(new File(path))).collect(Collectors.toList()));
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (currentPath.getParent() != null && !currentPath.toString().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
            refreshList(currentPath.getParent());
        } else {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public void onClick(View view) {
        if (view == back) {
            if (currentPath.getParent() != null && !currentPath.toString().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
                refreshList(currentPath.getParent());
            } else {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        }
        if (view == close) {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
        if (view == sharedDir) {
            refreshList(Environment.getExternalStorageDirectory().toPath());
        }
        if (view == privateDir) {
            if (getExternalCacheDir().getParent() != null) {
                refreshList(new File(getExternalCacheDir().getParent()).toPath());
            } else {
                Toast.makeText(this, getString(R.string.file_browser_private_alert), Toast.LENGTH_SHORT).show();
            }
        }
        if (view == manage) {
            showManageMenu();
        }
        if (view == openExternal) {
            if (currentPath.toFile().getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
                currentPath = currentPath.resolve("KCU");
            }
            Uri uri = FileProvider.getUriForFile(this, getApplication().getPackageName() + ".provider", currentPath.toFile());
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setDataAndType(uri, "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.file_browser_open_external)));
        }
        if (view == selectExternal) {
            launcher.launch(null);
        }
        if (view == confirm) {
            if (selectedFiles.isEmpty()) {
                if (fileBrowser.getLibMode() == LibMode.FILE_BROWSER) {
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                    return;
                }
                Toast.makeText(this, getString(R.string.file_browser_positive_alert), Toast.LENGTH_SHORT).show();
            } else {
                confirmSelection();
            }
        }
    }

    private void toast(int resId) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show();
    }

    /**
     * 允许抛出异常的文件操作，交由 {@link #runFileOperation} 统一处理失败
     */
    private interface FileOperation {
        void run() throws Exception;
    }

    /**
     * 在 IO 线程执行文件操作，成功后回主线程刷新列表，失败时提示
     */
    private void runFileOperation(FileOperation operation) {
        Schedulers.io().execute(() -> {
            try {
                operation.run();
                Schedulers.androidUIThread().execute(() -> {
                    if (!isDestroyed() && !isFinishing()) {
                        refreshList(currentPath);
                    }
                });
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "File browser operation failed", e);
                Schedulers.androidUIThread().execute(() -> {
                    if (!isDestroyed() && !isFinishing()) {
                        toast(R.string.file_browser_operation_failed);
                    }
                });
            }
        });
    }

    /**
     * 保存排序方式并刷新列表
     */
    private void setSortMode(SortMode sortMode) {
        this.sortMode = sortMode;
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit().putString(KEY_SORT_MODE, sortMode.name()).apply();
        refreshList(currentPath);
    }

    private void showSortMenu() {
        List<String> items = Arrays.asList(
                getString(R.string.file_browser_sort_name),
                getString(R.string.file_browser_sort_size),
                getString(R.string.file_browser_sort_date));
        ItemSelectionDialog.show(this, getString(R.string.file_browser_sort), items, true, sortMode.ordinal(),
                (position, item) -> setSortMode(SortMode.values()[position]));
    }

    /**
     * 管理菜单：新建、粘贴、排序、批量操作与刷新
     */
    private void showManageMenu() {
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(getString(R.string.file_browser_new_folder));
        actions.add(() -> showCreateDialog(true));
        items.add(getString(R.string.file_browser_new_file));
        actions.add(() -> showCreateDialog(false));
        items.add(getString(R.string.file_browser_paste));
        actions.add(this::pasteClipboard);
        items.add(getString(R.string.file_browser_sort));
        actions.add(this::showSortMenu);
        // 批量操作会向调用方返回多个路径：单选的文件选择模式（launchSingleSelection）下不提供
        // 入口，否则全选后确认会破坏调用方的单选约定
        boolean singleSelectionChooser = fileBrowser.getLibMode() == LibMode.FILE_CHOOSER
                && fileBrowser.getSelectionMode() == SelectionMode.SINGLE_SELECTION;
        if (!singleSelectionChooser && fileBrowser.getLibMode() != LibMode.FOLDER_CHOOSER
                && !listLoading && adapter != null && adapter.getCount() > 0) {
            items.add(getString(R.string.file_browser_batch));
            actions.add(this::showBatchMenu);
        }
        items.add(getString(R.string.file_browser_refresh));
        actions.add(() -> refreshList(currentPath));
        ItemSelectionDialog.show(this, getString(R.string.file_browser_manage), items, false, -1,
                (position, item) -> actions.get(position).run());
    }

    /**
     * 批量操作菜单：作用于当前勾选的文件
     */
    private void showBatchMenu() {
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(getString(R.string.file_browser_select_all));
        actions.add(this::selectAll);
        items.add(getString(R.string.file_browser_copy));
        actions.add(() -> clipboardSelected(false));
        items.add(getString(R.string.file_browser_cut));
        actions.add(() -> clipboardSelected(true));
        items.add(getString(R.string.file_browser_delete));
        actions.add(this::confirmDeleteSelected);
        ItemSelectionDialog.show(this, getString(R.string.file_browser_batch), items, false, -1,
                (position, item) -> actions.get(position).run());
    }

    /**
     * 单个文件/文件夹的操作菜单
     */
    private void showItemMenu(File file) {
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(getString(R.string.file_browser_details));
        actions.add(() -> showDetails(file));
        items.add(getString(R.string.file_browser_rename));
        actions.add(() -> showRenameDialog(file));
        items.add(getString(R.string.file_browser_delete));
        actions.add(() -> confirmDelete(List.of(file)));
        items.add(getString(R.string.file_browser_copy));
        actions.add(() -> FileClipboard.copy(List.of(file)));
        items.add(getString(R.string.file_browser_cut));
        actions.add(() -> FileClipboard.cut(List.of(file)));
        if (file.isFile()) {
            items.add(getString(R.string.file_browser_share));
            actions.add(() -> shareFile(file));
        }
        ItemSelectionDialog.show(this, file.getName(), items, false, -1,
                (position, item) -> actions.get(position).run());
    }

    private void showCreateDialog(boolean directory) {
        File dir = currentPath.toFile();
        EditDialog dialog = new EditDialog(this, "", name -> {
            if (!FileOperator.isValidFileName(name)) {
                toast(R.string.file_browser_invalid_name);
                return;
            }
            if (new File(dir, name).exists()) {
                toast(R.string.file_browser_exists);
                return;
            }
            runFileOperation(() -> {
                if (directory) {
                    FileOperator.createDirectory(dir, name);
                } else {
                    FileOperator.createFile(dir, name);
                }
            });
        });
        dialog.setTitle(getString(directory ? R.string.file_browser_new_folder : R.string.file_browser_new_file));
        dialog.show();
    }

    private void showRenameDialog(File file) {
        EditDialog dialog = new EditDialog(this, file.getName(), name -> {
            if (!FileOperator.isValidFileName(name)) {
                toast(R.string.file_browser_invalid_name);
                return;
            }
            File target = new File(file.getParentFile(), name);
            if (target.exists()) {
                toast(R.string.file_browser_exists);
                return;
            }
            runFileOperation(() -> FileOperator.rename(file, name));
        });
        dialog.setTitle(getString(R.string.file_browser_rename));
        dialog.show();
    }

    private void confirmDelete(List<File> files) {
        KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(this);
        builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
        builder.setTitle(getString(R.string.file_browser_delete));
        builder.setMessage(getString(R.string.file_browser_delete_message));
        builder.setPositiveButton(() -> {
            List<String> paths = files.stream().map(File::getAbsolutePath).collect(Collectors.toList());
            runFileOperation(() -> {
                for (File file : files) {
                    FileOperator.delete(file);
                }
                Schedulers.androidUIThread().execute(() -> selectedFiles.removeAll(paths));
            });
        });
        builder.setNegativeButton(null);
        builder.create().show();
    }

    private void confirmDeleteSelected() {
        if (selectedFiles.isEmpty()) {
            toast(R.string.file_browser_no_selection);
            return;
        }
        confirmDelete(selectedFiles.stream().map(File::new).collect(Collectors.toList()));
    }

    private void pasteClipboard() {
        if (FileClipboard.isEmpty()) {
            toast(R.string.file_browser_clipboard_empty);
            return;
        }
        List<File> files = new ArrayList<>(FileClipboard.getFiles());
        boolean cut = FileClipboard.isCut();
        File dir = currentPath.toFile();
        for (File source : files) {
            if (isSelfOrDescendant(source, dir)) {
                toast(R.string.file_browser_paste_recursive);
                return;
            }
        }
        runFileOperation(() -> {
            if (cut) {
                FileOperator.moveTo(files, dir);
                FileClipboard.clear();
            } else {
                FileOperator.copyTo(files, dir);
            }
        });
    }

    /**
     * 判断目标目录是否为来源本身或其子目录：是则粘贴会造成无限递归
     */
    private static boolean isSelfOrDescendant(File source, File targetDir) {
        try {
            String src = source.getCanonicalPath();
            String dst = targetDir.getCanonicalPath();
            return dst.equals(src) || dst.startsWith(src + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private void clipboardSelected(boolean cut) {
        if (selectedFiles.isEmpty()) {
            toast(R.string.file_browser_no_selection);
            return;
        }
        List<File> files = selectedFiles.stream().map(File::new).collect(Collectors.toList());
        if (cut) {
            FileClipboard.cut(files);
        } else {
            FileClipboard.copy(files);
        }
    }

    private void selectAll() {
        if (adapter == null) {
            return;
        }
        selectedFiles = new ArrayList<>();
        for (File file : adapter.getFiles()) {
            if (file.isFile()) {
                selectedFiles.add(file.getAbsolutePath());
            }
        }
        adapter.setSelectedFiles(selectedFiles);
        adapter.notifyDataSetChanged();
    }

    private void showDetails(File file) {
        Schedulers.io().execute(() -> {
            String detail = getString(R.string.file_browser_name) + ": " + file.getName() + "\n"
                    + getString(R.string.file_browser_type) + ": "
                    + getString(file.isDirectory() ? R.string.file_browser_type_folder : R.string.file_browser_type_file) + "\n"
                    + getString(R.string.file_browser_size) + ": " + FileUtils.byteCountToDisplaySize(FileUtils.sizeOf(file)) + "\n"
                    + getString(R.string.file_browser_last_modified) + ": " + formatter.format(new Date(file.lastModified())) + "\n"
                    + getString(R.string.file_browser_path) + ": " + file.getAbsolutePath();
            Schedulers.androidUIThread().execute(() -> {
                if (isDestroyed() || isFinishing()) {
                    return;
                }
                KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(this);
                builder.setAlertLevel(KCUAlertDialog.AlertLevel.INFO);
                builder.setTitle(getString(R.string.file_browser_details));
                builder.setMessage(detail);
                builder.setPositiveButton(() -> { });
                builder.create().show();
            });
        });
    }

    @Override
    protected void onDestroy() {
        // 主题引擎按控件持有回调，不摘掉就会一直攥着本页，关闭后无法回收
        if (currentText != null) {
            ThemeEngine.getInstance().unregisterEvent(currentText);
        }
        super.onDestroy();
    }

    private void shareFile(File file) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        Uri uri = FileProvider.getUriForFile(this, getApplication().getPackageName() + ".provider", file);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.file_browser_share_title)));
    }

}
