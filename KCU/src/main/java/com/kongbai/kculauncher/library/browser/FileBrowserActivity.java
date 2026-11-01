package com.kongbai.kculauncher.library.browser;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.OpenableColumns;
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
    /** 重建时恢复现场用的键：目录、排序、已勾选的文件 */
    private static final String STATE_PATH = "kcu_fb_path";
    private static final String STATE_SORT = "kcu_fb_sort";
    private static final String STATE_SELECTED = "kcu_fb_selected";

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
    /** 有文件操作正在 IO 线程执行 */
    private volatile boolean operationBusy;

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
            // 外部选择走的是系统选择器，它并不认识这里配的文件类型：不过一遍后缀，
            // 什么文件都能选进来，后面要么静默失败、要么把错误内容当成要的文件
            ArrayList<Uri> accepted = new ArrayList<>();
            int rejected = 0;
            for (Uri uri : extSelected) {
                if (matchesConfiguredSuffix(uri)) {
                    accepted.add(uri);
                } else {
                    rejected++;
                }
            }
            if (accepted.isEmpty()) {
                // 全部不符就留在本页：直接返回等于选完了却毫无反应
                toast(R.string.file_browser_external_type_mismatch);
                extSelected.clear();
                return;
            }
            extSelected = accepted;
            if (rejected > 0) {
                Toast.makeText(this, getString(R.string.file_browser_external_type_skipped, rejected), Toast.LENGTH_SHORT).show();
            }
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
        // 被系统回收后重建时先恢复原来的目录与勾选：
        // 玩家翻到深层目录、勾好几个模组，接个电话回来界面回到根目录、勾选全没，
        // 只得重新翻一遍——这正是最让人觉得"不像正常软件"的地方
        if (savedInstanceState != null) {
            String savedPath = savedInstanceState.getString(STATE_PATH);
            if (savedPath != null && !savedPath.isEmpty()) {
                currentPath = new File(savedPath).toPath();
            }
            String savedSort = savedInstanceState.getString(STATE_SORT);
            if (savedSort != null) {
                try {
                    // 只在这里直接解析而不用 parseSortMode：
                    // 那个方法解析不出来会退回默认，会把从偏好里读出来的排序覆盖掉
                    sortMode = SortMode.valueOf(savedSort);
                } catch (IllegalArgumentException ignored) {
                    // 认不出就保持偏好里读到的那个
                }
            }
            ArrayList<String> savedSelected = savedInstanceState.getStringArrayList(STATE_SELECTED);
            if (savedSelected != null) {
                // 只保留还在的：回收期间文件可能被别的应用删掉或移动走，
                // 留着不存在的路径会让后面按它去操作时直接失败
                ArrayList<String> alive = new ArrayList<>();
                for (String one : savedSelected) {
                    if (one != null && new File(one).exists()) {
                        alive.add(one);
                    }
                }
                selectedFiles = alive;
            }
        }
        currentText = findViewById(R.id.current_folder);
        listView = findViewById(R.id.list);
        // 现场恢复之后才知道勾了几个，所以放在刷新之后更新
        refreshList(currentPath != null ? currentPath : new File(fileBrowser.getInitDir()).toPath());
        updateSelectionHint();

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
            // 预填当前路径：点路径栏是要改路径，打开却是空的，
            // 每次都得从头敲一遍完整路径。预填后配合输入框的打开即全选，
            // 直接输入就替换，想改末尾也可以在原路径上改
            new EditDialog(this, currentPath != null ? currentPath.toString() : "", path -> {
                File file = new File(path);
                if (file.exists() && file.isDirectory()) {
                    refreshList(file.toPath());
                } else {
                    // 原来什么都不做：点路径栏输了个不存在的路径，界面毫无反应，
                    // 玩家只会以为这个输入框是摆设
                    toast(R.string.file_browser_path_invalid);
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

    /**
     * 多选时把已选数量写在模式那一行：勾了几个在界面上没有任何体现，
     * 跨目录勾了之后更是数不清，只有点确认才知道勾了什么
     */
    private void updateSelectionHint() {
        if (type == null) {
            return;
        }
        if (fileBrowser.getSelectionMode() == SelectionMode.MULTIPLE_SELECTION
                && selectedFiles != null && !selectedFiles.isEmpty()) {
            type.setText(getType() + " (" + selectedFiles.size() + ")");
        } else {
            type.setText(getType());
        }
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
                if (files.isEmpty()) {
                    // 读不出来的目录和本来就空的目录，列表长得一模一样，
                    // 都不说一声的话玩家分不清是没加载出来还是真没有文件
                    toast(listable ? R.string.file_browser_empty_folder : R.string.file_browser_unreadable);
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
                updateSelectionHint();
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
        // 显式构造 ArrayList：Collectors.toList() 只保证返回 List，
        // 直接强转是赌它恰好是 ArrayList，换实现就会崩
        ArrayList<Uri> uris = new ArrayList<>();
        for (String path : selectedFiles) {
            uris.add(Uri.fromFile(new File(path)));
        }
        Intent intent = new Intent();
        intent.putParcelableArrayListExtra(FileBrowser.SELECTED_FILES, uris);
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
            // 用局部变量，不改写 currentPath：
            // 原来直接改写字段却不刷新界面，路径栏还显示着旧目录，
            // 之后一刷新或进出目录就会突然跳到另一个地方，而界面上毫无提示
            File externalTarget = currentPath.toFile().getAbsolutePath()
                    .equals(Environment.getExternalStorageDirectory().getAbsolutePath())
                    ? currentPath.resolve("KCU").toFile() : currentPath.toFile();
            // 这个目录可能还没建过。交给外部应用一个不存在的路径，对方打开是空的，
            // 看不出是自己没建还是启动器给错了
            if (!externalTarget.exists() && !externalTarget.mkdirs()) {
                toast(R.string.file_browser_operation_failed);
                return;
            }
            Uri uri = FileProvider.getUriForFile(this, getApplication().getPackageName() + ".provider", externalTarget);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setDataAndType(uri, "*/*");
            // 只给 setDataAndType 的话不少外部应用收不到，EXTRA_STREAM 是通用做法
            intent.putExtra(Intent.EXTRA_STREAM, uri);
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

    /**
     * 外部选择拿到的文件是否符合配置的后缀。
     * 认不出文件名时一律放行：误拒会让玩家根本选不了文件，比多选进来几个更糟
     */
    private boolean matchesConfiguredSuffix(Uri uri) {
        List<String> suffix = fileBrowser.getSuffix();
        if (suffix == null || suffix.isEmpty()) {
            return true;
        }
        String name = displayNameOf(uri);
        if (name == null || name.isEmpty()) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String item : suffix) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            if (lower.endsWith(item.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取外部选择结果的文件名。content 下取不到显示名时返回 null，表示认不出来
     */
    private String displayNameOf(Uri uri) {
        if (uri == null) {
            return null;
        }
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        return cursor.getString(index);
                    }
                }
                return null;
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "Failed to query display name: " + uri, e);
                return null;
            }
        }
        String path = uri.getPath();
        if (path != null) {
            return new File(path).getName();
        }
        return null;
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
     * 在 IO 线程执行文件操作，成功后回主线程刷新列表，失败时提示。
     * 操作期间不接受新的提交：复制大目录要跑很久，期间界面没有任何变化，
     * 不挡住的话玩家以为没反应就会再点一次，两个操作撞在同一批文件上
     */
    private void runFileOperation(FileOperation operation) {
        if (operationBusy) {
            toast(R.string.file_browser_operation_busy);
            return;
        }
        operationBusy = true;
        toast(R.string.file_browser_operation_running);
        Schedulers.io().execute(() -> {
            try {
                operation.run();
                Schedulers.androidUIThread().execute(() -> {
                    operationBusy = false;
                    if (!isDestroyed() && !isFinishing()) {
                        refreshList(currentPath);
                    }
                });
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "File browser operation failed", e);
                Schedulers.androidUIThread().execute(() -> {
                    operationBusy = false;
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
     * 当前目录里的文件是否已被全部勾选（决定菜单里写"全选"还是"取消全选"）
     */
    private boolean allFilesSelected() {
        if (adapter == null) {
            return false;
        }
        boolean any = false;
        for (File file : adapter.getFiles()) {
            if (file.isFile()) {
                any = true;
                if (!selectedFiles.contains(file.getAbsolutePath())) {
                    return false;
                }
            }
        }
        return any;
    }

    /**
     * 批量操作菜单：作用于当前勾选的文件
     */
    private void showBatchMenu() {
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(getString(allFilesSelected() ? R.string.file_browser_deselect_all : R.string.file_browser_select_all));
        actions.add(this::selectAll);
        items.add(getString(R.string.file_browser_copy));
        actions.add(() -> {
            // 只在真的放进去了才提示：没勾东西时那边已经提示过"没有选中"，
            // 再说一句已放入剪贴板就自相矛盾了
            if (clipboardSelected(false)) {
                toast(R.string.file_browser_clipboard_set);
            }
        });
        items.add(getString(R.string.file_browser_cut));
        actions.add(() -> {
            if (clipboardSelected(true)) {
                toast(R.string.file_browser_clipboard_set);
            }
        });
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
        // 复制/剪切只是把路径放进剪贴板，界面上没有任何变化。
        // 不给反馈的话玩家点了以后看不出成没成，往往会再点一次
        actions.add(() -> {
            FileClipboard.copy(List.of(file));
            toast(R.string.file_browser_clipboard_set);
        });
        items.add(getString(R.string.file_browser_cut));
        actions.add(() -> {
            FileClipboard.cut(List.of(file));
            toast(R.string.file_browser_clipboard_set);
        });
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
        // 只说"删了不能恢复"却不说是删什么：多选删八个文件时，
        // 玩家无法从弹窗确认自己勾的到底是哪几个
        builder.setMessage(getString(R.string.file_browser_delete_message)
                + "\n\n" + describeFiles(files));
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

    /**
     * 把要删除的文件说清楚：只一个就直接给名字，多个给首个名字加总数
     */
    private String describeFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        if (files.size() == 1) {
            return files.get(0).getName();
        }
        return files.get(0).getName() + " " + getString(R.string.file_browser_items_count, files.size());
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
        final int total = files.size();
        runFileOperation(() -> {
            // 逐个文件时有的会失败（权限、被占用、空间不够），只刷新的话
            // 玩家看不出少了几个，只会以为全成了，之后才发现文件不见了
            int done;
            if (cut) {
                done = FileOperator.moveTo(files, dir);
                FileClipboard.clear();
            } else {
                done = FileOperator.copyTo(files, dir);
            }
            final int ok = done;
            Schedulers.androidUIThread().execute(() -> {
                if (isDestroyed() || isFinishing()) {
                    return;
                }
                Toast.makeText(this, getString(cut ? R.string.file_browser_move_result : R.string.file_browser_copy_result,
                        ok, total - ok), Toast.LENGTH_SHORT).show();
            });
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

    /** 把当前勾选放进剪贴板，返回是否真的放了（没勾任何东西时返回 false） */
    private boolean clipboardSelected(boolean cut) {
        if (selectedFiles.isEmpty()) {
            toast(R.string.file_browser_no_selection);
            return false;
        }
        List<File> files = selectedFiles.stream().map(File::new).collect(Collectors.toList());
        if (cut) {
            FileClipboard.cut(files);
        } else {
            FileClipboard.copy(files);
        }
        return true;
    }

    private void selectAll() {
        if (adapter == null) {
            return;
        }
        // 已经全选了就取消，否则勾上之后没有任何入口能一次性清掉，
        // 只能一个个点回去
        boolean allSelected = true;
        for (File file : adapter.getFiles()) {
            if (file.isFile() && !selectedFiles.contains(file.getAbsolutePath())) {
                allSelected = false;
                break;
            }
        }
        if (allSelected) {
            selectedFiles = new ArrayList<>();
        } else {
            for (File file : adapter.getFiles()) {
                if (file.isFile() && !selectedFiles.contains(file.getAbsolutePath())) {
                    selectedFiles.add(file.getAbsolutePath());
                }
            }
        }
        adapter.setSelectedFiles(selectedFiles);
        adapter.notifyDataSetChanged();
        updateSelectionHint();
    }

    private void showDetails(File file) {
        Schedulers.io().execute(() -> {
            String detail;
            try {
                // 文件可能刚好被删掉：sizeOf 遇到不存在的路径会抛异常，
                // 整段没有兜底的话弹窗直接崩，且看不到任何原因
                // 目录不能递归算大小：实例目录里有成千上万个文件，
                // 遍历一遍要几十秒，这期间弹窗迟迟不出，看起来就是点了没反应。
                // 目录改为直接子项数量，只看一层，很快
                String sizeText;
                if (file.isDirectory()) {
                    File[] children = file.listFiles();
                    sizeText = children == null
                            ? getString(R.string.file_browser_size_unknown)
                            : getString(R.string.file_browser_items, children.length);
                } else {
                    sizeText = FileUtils.byteCountToDisplaySize(file.length());
                }
                detail = getString(R.string.file_browser_name) + ": " + file.getName() + "\n"
                        + getString(R.string.file_browser_type) + ": "
                        + getString(file.isDirectory() ? R.string.file_browser_type_folder : R.string.file_browser_type_file) + "\n"
                        + getString(R.string.file_browser_size) + ": " + sizeText + "\n"
                        + getString(R.string.file_browser_last_modified) + ": " + formatter.format(new Date(file.lastModified())) + "\n"
                        + getString(R.string.file_browser_path) + ": " + file.getAbsolutePath();
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "Failed to read file details " + file.getAbsolutePath(), e);
                Schedulers.androidUIThread().execute(() -> {
                    if (!isDestroyed() && !isFinishing()) {
                        toast(R.string.file_browser_operation_failed);
                    }
                });
                return;
            }
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentPath != null) {
            outState.putString(STATE_PATH, currentPath.toString());
        }
        if (sortMode != null) {
            outState.putString(STATE_SORT, sortMode.name());
        }
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            outState.putStringArrayList(STATE_SELECTED, new ArrayList<>(selectedFiles));
        }
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
