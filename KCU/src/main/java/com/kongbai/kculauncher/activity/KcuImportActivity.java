package com.kongbai.kculauncher.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.util.Logging;
import com.kongbai.kculauncher.library.browser.SelectedFile;
import com.kongbai.kculauncher.setting.Profiles;
import com.kongbai.kculauncher.util.KcuPolicy;
import com.kongbai.kculauncher.util.KcuUi;
import com.kongbai.kculauncher.util.KcuVersionCatalog;
import java.util.Map;
import java.util.LinkedHashMap;
import com.kongbai.kculauncher.util.KcuUrlFormat;
import com.kongbai.kculauncher.util.KcuDownloadPrefs;
import com.kongbai.kculauncher.util.KcuNetwork;
import com.kongbai.kculauncher.util.KcuVersionMeta;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 导入游戏：列出可选版本让玩家点选，而不是要求手填版本 id。
 *
 * 【为什么不填 id】
 * 版本 id 是玩家记不住也无从猜测的一串字符，填错就导入失败，
 * 且失败原因（目录整份被忽略）在界面上完全看不出来。
 * 改为像下载游戏一样列出来点选，玩家只需要认识版本号。
 */
public class KcuImportActivity extends Activity {

    private static final int REQUEST_PICK = 9001;
    private static final int TIMEOUT_MS = 60000;

    /** 渲染上限：清单按时间倒序，取最新的若干条足够，避免界面塞入上千个按钮 */
    private static final int MAX_RELEASE_ROWS = 80;
    private static final int MAX_SNAPSHOT_ROWS = 40;

    private LinearLayout card;
    private TextView status;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private List<KcuVersionCatalog.Entry> catalog;
    private boolean showSnapshots = false;

    /** 格式链接下载：当前用途，false=版本 true=模组 */
    private boolean customForMod = false;
    private LinearLayout placeholderBox;
    private TextView previewText;
    private android.widget.EditText formatEdit;
    private android.widget.EditText nameEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = KcuUi.root(this);
        card = KcuUi.card(this);

        card.addView(KcuUi.header(this, "\u2193", getString(R.string.kcu_import_title)));
        card.addView(KcuUi.gap(this, 12));
        card.addView(KcuUi.body(this, getString(R.string.kcu_import_hint)));
        card.addView(KcuUi.gap(this, 18));

        status = KcuUi.status(this, getString(R.string.kcu_import_loading));
        card.addView(status);

        root.addView(card);
        scroll.addView(root);
        setContentView(scroll);

        startWithPolicyCheck();
    }

    /**
     * 进入流程：先判可用性与联网，都不发无效请求。
     *
     * 【为什么联网检查要放在最前面】
     * 后面的每一步都要发起网络请求。没网时若照常发起，
     * 请求要么等超时才失败、要么直接抛错，界面长时间没有结果，
     * 玩家只会以为功能坏了。先本地判一次，没网就立刻说明，一步请求都不发。
     *
     * 【为什么没网时仍要看一眼本地缓存的规则】
     * 限制是必须生效的，不能因为断网就放行。缓存里若有限制就照常挡住，
     * 只有在缓存没有限制时，才把"没网"作为进不去的原因告诉玩家。
     */
    private void startWithPolicyCheck() {
        KcuPolicy.loadCached();
        if (!KcuNetwork.isOnline()) {
            if (KcuPolicy.isBlocked(KcuPolicy.FEATURE_DOWNLOAD)) {
                showBlocked();
            } else {
                showOffline();
            }
            return;
        }
        if (KcuPolicy.isPrepared()) {
            decide();
            return;
        }
        // 未就绪时必须先在后台完成准备再判断，不能在界面线程直接判断：
        // 判断要用到地区，地区判定是网络操作，在界面线程上必然失败
        setStatus(getString(R.string.kcu_policy_checking));
        worker.execute(() -> {
            KcuPolicy.prepare();
            runOnUiThread(this::decide);
        });
    }

    /**
     * 没网时的页面：说明是哪种情况没网，不提供任何需要联网的入口。
     *
     * 【为什么分开说 WiFi 与移动数据】
     * 只说一句"没连接网络"，玩家不知道该去开哪一个：
     * 有人是忘了开 WiFi，有人是流量被禁用了。
     * 把两条路都点出来，玩家一眼就知道该去做什么。
     */
    private void showOffline() {
        card.removeAllViews();
        card.addView(KcuUi.header(this, "!", getString(R.string.kcu_net_offline_title)));
        card.addView(KcuUi.gap(this, 12));
        card.addView(KcuUi.body(this, getString(R.string.kcu_net_offline)));
        card.addView(KcuUi.gap(this, 8));
        card.addView(KcuUi.body(this, getString(R.string.kcu_net_none)));
        card.addView(KcuUi.gap(this, 18));
        android.widget.Button retry = KcuUi.primaryButton(this, getString(R.string.kcu_import_loading));
        retry.setOnClickListener(v -> {
            // 重新走一遍：联网状态随时可变，这里只重判，不做任何网络请求
            startWithPolicyCheck();
        });
        card.addView(retry);
    }

    /** 当前网络类型的一句话说明，用于让联网提示更具体 */
    private String networkKindText() {
        KcuNetwork.Kind kind = KcuNetwork.kind();
        if (kind == KcuNetwork.Kind.WIFI) {
            return getString(R.string.kcu_net_kind_wifi);
        }
        if (kind == KcuNetwork.Kind.MOBILE) {
            return getString(R.string.kcu_net_kind_mobile);
        }
        return "";
    }

    /**
     * 首次进入下载功能时的使用须知。
     *
     * 【为什么必须先看这个】
     * 这个功能本质上是在下载外部内容，玩家需要清楚后果由谁承担。
     * 只在第一次进入时提示，之后记住不再打扰。
     */
    private void showDisclaimer() {
        card.removeAllViews();
        card.addView(KcuUi.header(this, "!", getString(R.string.kcu_disclaimer_title)));
        card.addView(KcuUi.gap(this, 12));
        card.addView(KcuUi.body(this, getString(R.string.kcu_disclaimer_body)));
        card.addView(KcuUi.gap(this, 18));

        android.widget.Button agree = KcuUi.primaryButton(this, getString(R.string.kcu_disclaimer_agree));
        agree.setOnClickListener(v -> {
            KcuDownloadPrefs.markNoticed();
            loadCatalog();
        });
        card.addView(agree);
        card.addView(KcuUi.gap(this, 10));

        android.widget.Button decline = KcuUi.secondaryButton(this, getString(R.string.kcu_disclaimer_cancel));
        decline.setOnClickListener(v -> finish());
        card.addView(decline);
    }

    private void decide() {
        if (KcuPolicy.isBlocked(KcuPolicy.FEATURE_DOWNLOAD)) {
            showBlocked();
            return;
        }
        // 没网时不再往下走：后面的清单与下载都需要联网
        if (!KcuNetwork.isOnline()) {
            showOffline();
            return;
        }
        if (!KcuDownloadPrefs.isNoticed()) {
            showDisclaimer();
            return;
        }
        loadCatalog();
    }

    /** 被限制时显示的页面：只说明原因，不提供任何继续使用的入口 */
    private void showBlocked() {
        card.removeAllViews();
        card.addView(KcuUi.header(this, "!", KcuPolicy.titleFor(this, KcuPolicy.FEATURE_DOWNLOAD)));
        card.addView(KcuUi.gap(this, 12));
        card.addView(KcuUi.body(this, KcuPolicy.bodyFor(this, KcuPolicy.FEATURE_DOWNLOAD)));
    }

    private void loadCatalog() {
        // 必须把玩家选的源带进去：
        // 不带的话固定按镜像优先去取，切换源就只是把按钮高亮挪了个位置，
        // 取回来的还是同一个列表。玩家正是想在镜像不通时改用官方，
        // 那时发现自己换了个寂寞，而界面上没有任何迹象
        final KcuVersionCatalog.Source source = KcuDownloadPrefs.source();
        worker.execute(() -> {
            List<KcuVersionCatalog.Entry> list = KcuVersionCatalog.fetch(source);
            runOnUiThread(() -> {
                if (list == null || list.isEmpty()) {
                    setStatus(getString(R.string.kcu_import_catalog_failed));
                    addLocalPick();
                    // 清单取不到时工具区仍可用：格式链接不依赖清单
                    addTools();
                    return;
                }
                catalog = list;
                renderList();
            });
        });
    }

    /** 按类型分组渲染；快照默认折叠，展开后才渲染，避免首屏过长 */
    private void renderList() {
        card.removeAllViews();
        card.addView(KcuUi.header(this, "\u2193", getString(R.string.kcu_import_title)));
        card.addView(KcuUi.gap(this, 12));
        card.addView(KcuUi.body(this, getString(R.string.kcu_import_hint)));
        card.addView(KcuUi.gap(this, 18));

        status = KcuUi.status(this, "");
        card.addView(status);

        int releaseShown = 0;
        for (KcuVersionCatalog.Entry entry : catalog) {
            if (!entry.isRelease()) {
                continue;
            }
            if (releaseShown >= MAX_RELEASE_ROWS) {
                break;
            }
            releaseShown++;
            card.addView(versionButton(entry));
        }

        if (hasSnapshots()) {
            android.widget.Button toggle = KcuUi.secondaryButton(this, getString(
                    showSnapshots ? R.string.kcu_import_hide_snapshot
                            : R.string.kcu_import_show_snapshot));
            toggle.setOnClickListener(v -> {
                showSnapshots = !showSnapshots;
                renderList();
            });
            card.addView(KcuUi.gap(this, 10));
            card.addView(toggle);

            if (showSnapshots) {
                int snapshotShown = 0;
                for (KcuVersionCatalog.Entry entry : catalog) {
                    if (entry.isRelease()) {
                        continue;
                    }
                    if (snapshotShown >= MAX_SNAPSHOT_ROWS) {
                        break;
                    }
                    snapshotShown++;
                    card.addView(versionButton(entry));
                }
            }
        }

        card.addView(KcuUi.gap(this, 18));
        addLocalPick();
        addTools();
    }

    /**
     * 下载源切换与格式链接下载。
     *
     * 【为什么切换源后要重新取清单】
     * 两个源的内容与可用性都不同，只改标记而不重取，
     * 玩家会看到上一个源的列表，以为切换没生效。
     */
    private void addTools() {
        card.addView(KcuUi.gap(this, 20));

        card.addView(KcuUi.title(this, getString(R.string.kcu_source_title)));
        card.addView(KcuUi.gap(this, 8));

        KcuVersionCatalog.Source current = KcuDownloadPrefs.source();
        android.widget.Button mirror = current == KcuVersionCatalog.Source.MIRROR
                ? KcuUi.primaryButton(this, getString(R.string.kcu_source_mirror))
                : KcuUi.secondaryButton(this, getString(R.string.kcu_source_mirror));
        mirror.setOnClickListener(v -> {
            KcuDownloadPrefs.setSource(KcuVersionCatalog.Source.MIRROR);
            Toast.makeText(this, R.string.kcu_source_switched, Toast.LENGTH_SHORT).show();
            loadCatalog();
        });
        card.addView(mirror);
        card.addView(KcuUi.gap(this, 8));

        android.widget.Button official = current == KcuVersionCatalog.Source.OFFICIAL
                ? KcuUi.primaryButton(this, getString(R.string.kcu_source_official))
                : KcuUi.secondaryButton(this, getString(R.string.kcu_source_official));
        official.setOnClickListener(v -> {
            KcuDownloadPrefs.setSource(KcuVersionCatalog.Source.OFFICIAL);
            Toast.makeText(this, R.string.kcu_source_switched, Toast.LENGTH_SHORT).show();
            loadCatalog();
        });
        card.addView(official);

        card.addView(KcuUi.gap(this, 20));
        card.addView(KcuUi.title(this, getString(R.string.kcu_dl_custom)));
        card.addView(KcuUi.gap(this, 8));
        card.addView(KcuUi.hint(this, getString(R.string.kcu_format_hint)));
        card.addView(KcuUi.gap(this, 8));

        formatEdit = KcuUi.field(this, getString(R.string.kcu_format_hint));
        formatEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable editable) {
                rebuildPlaceholders();
            }
        });
        card.addView(formatEdit);
        card.addView(KcuUi.gap(this, 8));

        placeholderBox = new LinearLayout(this);
        placeholderBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(placeholderBox);

        previewText = KcuUi.hint(this, "");
        card.addView(previewText);
        card.addView(KcuUi.gap(this, 10));

        // 用途：版本还是模组，决定保存到哪个目录
        LinearLayout kindRow = new LinearLayout(this);
        kindRow.setOrientation(LinearLayout.HORIZONTAL);
        // 选中态用文字前缀标记，不改动按钮外观：
        // 重建整个工具区会清掉已填的占位符，改背景又需要新的绘制资源，
        // 用前缀最省事且不会丢内容
        final android.widget.Button asVersion = KcuUi.secondaryButton(this, markOf(false) + getString(R.string.kcu_dl_version));
        final android.widget.Button asMod = KcuUi.secondaryButton(this, markOf(true) + getString(R.string.kcu_dl_mod));
        asVersion.setOnClickListener(v -> {
            customForMod = false;
            asVersion.setText(markOf(false) + getString(R.string.kcu_dl_version));
            asMod.setText(markOf(true) + getString(R.string.kcu_dl_mod));
        });
        asMod.setOnClickListener(v -> {
            customForMod = true;
            asVersion.setText(markOf(false) + getString(R.string.kcu_dl_version));
            asMod.setText(markOf(true) + getString(R.string.kcu_dl_mod));
        });
        kindRow.addView(asVersion);
        kindRow.addView(KcuUi.gap(this, 8));
        kindRow.addView(asMod);
        card.addView(kindRow);
        card.addView(KcuUi.gap(this, 8));

        nameEdit = KcuUi.field(this, getString(R.string.kcu_dl_target));
        card.addView(nameEdit);
        card.addView(KcuUi.gap(this, 10));

        android.widget.Button start = KcuUi.primaryButton(this, getString(R.string.kcu_dl_start));
        start.setOnClickListener(v -> startCustomDownload());
        card.addView(start);

        rebuildPlaceholders();
    }

    /** 选中标记：当前用途前面加一个勾，未选中的留空保持对齐 */
    private String markOf(boolean forMod) {
        return customForMod == forMod ? "✓ " : "　　";
    }

    /**
     * 按当前格式重建占位符输入框。
     *
     * 已填过的值要保留：玩家改动格式时若把已填内容清空，
     * 等于让他重新填一遍，格式一复杂就很难用。
     */
    private void rebuildPlaceholders() {
        if (placeholderBox == null) {
            return;
        }
        java.util.Map<String, String> kept = new LinkedHashMap<>();
        for (int i = 0; i < placeholderBox.getChildCount(); i++) {
            android.view.View child = placeholderBox.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof String && child instanceof LinearLayout) {
                android.widget.EditText edit = KcuUi.editOf((LinearLayout) child);
                if (edit != null) {
                    kept.put((String) tag, edit.getText().toString());
                }
            }
        }
        placeholderBox.removeAllViews();
        String format = formatEdit == null ? "" : formatEdit.getText().toString();
        List<String> names = KcuUrlFormat.placeholders(format);
        if (names.isEmpty()) {
            return;
        }
        for (String name : names) {
            LinearLayout row = KcuUi.fieldRow(this, name, "§");
            row.setTag(name);
            android.widget.EditText edit = KcuUi.editOf(row);
            if (edit != null) {
                String old = kept.get(name);
                if (old != null) {
                    edit.setText(old);
                }
                edit.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(android.text.Editable editable) {
                        updatePreview();
                    }
                });
            }
            placeholderBox.addView(row);
        }
        updatePreview();
    }

    private void updatePreview() {
        if (previewText == null || formatEdit == null) {
            return;
        }
        String format = formatEdit.getText().toString();
        if (format.trim().isEmpty()) {
            previewText.setText("");
            return;
        }
        previewText.setText(getString(R.string.kcu_format_preview) + " " + KcuUrlFormat.apply(format, collectValues()));
    }

    private java.util.Map<String, String> collectValues() {
        java.util.Map<String, String> values = new LinkedHashMap<>();
        if (placeholderBox == null) {
            return values;
        }
        for (int i = 0; i < placeholderBox.getChildCount(); i++) {
            android.view.View child = placeholderBox.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof String && child instanceof LinearLayout) {
                android.widget.EditText edit = KcuUi.editOf((LinearLayout) child);
                if (edit != null) {
                    values.put((String) tag, edit.getText().toString().trim());
                }
            }
        }
        return values;
    }

    /**
     * 按格式链接下载。
     *
     * 【为什么没填完的占位符要拦下来】
     * 未替换的占位符留在地址里，请求多半会失败或取回错误内容，
     * 而玩家看不出是地址错了还是网络问题。先明确说是哪一处没填。
     */
    private void startCustomDownload() {
        if (!KcuNetwork.isOnline()) {
            setStatus(getString(R.string.kcu_net_offline));
            return;
        }
        String format = formatEdit == null ? "" : formatEdit.getText().toString().trim();
        if (format.isEmpty()) {
            setStatus(getString(R.string.kcu_format_empty));
            return;
        }
        List<String> names = KcuUrlFormat.placeholders(format);
        if (names.isEmpty()) {
            // 没有占位符就是一条完整地址，按原样使用
            startCustomWithUrl(format);
            return;
        }
        java.util.Map<String, String> values = collectValues();
        StringBuilder missing = new StringBuilder();
        for (String name : names) {
            String value = values.get(name);
            if (value == null || value.isEmpty()) {
                if (missing.length() > 0) {
                    missing.append("、");
                }
                missing.append("§").append(name).append("§");
            }
        }
        if (missing.length() > 0) {
            setStatus(getString(R.string.kcu_format_need) + " " + missing);
            return;
        }
        startCustomWithUrl(KcuUrlFormat.apply(format, values));
    }

    private void startCustomWithUrl(String url) {
        String name = nameEdit == null ? "" : nameEdit.getText().toString().trim();
        if (name.isEmpty()) {
            // 没填文件名时从地址末段取，取不到再提示
            int slash = url.lastIndexOf('/');
            name = slash >= 0 ? url.substring(slash + 1) : "";
            name = name.trim();
        }
        if (name.isEmpty()) {
            setStatus(getString(R.string.kcu_dl_target));
            return;
        }
        final String finalName = name;
        setStatus(getString(R.string.kcu_import_downloading));
        worker.execute(() -> {
            File target = customTarget(finalName, customForMod);
            if (target == null) {
                setStatus(getString(R.string.kcu_import_no_dir));
                return;
            }
            boolean ok = download(url, target);
            if (!ok) {
                removeIfEmpty(target.getParentFile());
            }
            setStatus(ok
                    ? getString(R.string.kcu_import_done) + " " + target.getAbsolutePath()
                    : getString(R.string.kcu_import_failed));
        });
    }

    /** 格式链接的保存位置：版本进 versions/<名>/，模组进 mods/ */
    private File customTarget(String name, boolean forMod) {
        try {
            File gameDir = Profiles.getSelectedProfile().getGameDir();
            if (forMod) {
                File dir = new File(gameDir, "mods");
                if (!dir.exists() && !dir.mkdirs()) {
                    return null;
                }
                return new File(dir, name);
            }
            File dir = new File(gameDir, "versions/" + name);
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            return new File(dir, name + ".jar");
        } catch (Throwable e) {
            return null;
        }
    }

    private boolean hasSnapshots() {
        if (catalog == null) {
            return false;
        }
        for (KcuVersionCatalog.Entry entry : catalog) {
            if (!entry.isRelease()) {
                return true;
            }
        }
        return false;
    }

    private android.widget.Button versionButton(KcuVersionCatalog.Entry entry) {
        android.widget.Button button = KcuUi.secondaryButton(this, entry.id);
        button.setOnClickListener(v -> startDownload(entry));
        return button;
    }

    private void addLocalPick() {
        android.widget.Button local = KcuUi.secondaryButton(this, getString(R.string.kcu_import_pick_local));
        local.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.kcu_import_pick_local)),
                    REQUEST_PICK);
        });
        card.addView(local);
    }

    /** 选中版本：先取该版本 json 里的 client 地址，再下载 jar */
    private void startDownload(KcuVersionCatalog.Entry entry) {
        // 下载前先判联网：没网时不发请求，直接说明原因，免得干等超时
        if (!KcuNetwork.isOnline()) {
            setStatus(getString(R.string.kcu_net_offline));
            return;
        }
        setStatus(getString(R.string.kcu_import_resolving));
        worker.execute(() -> {
            String jarUrl = KcuVersionCatalog.fetchClientJarUrl(entry.url);
            if (jarUrl == null || jarUrl.isEmpty()) {
                setStatus(getString(R.string.kcu_import_no_client));
                return;
            }
            setStatus(getString(R.string.kcu_import_downloading));
            File target = targetFile(entry.id);
            if (target == null) {
                setStatus(getString(R.string.kcu_import_no_dir));
                return;
            }
            File dir = target.getParentFile();

            // 先落 jar 再落 json：顺序反了会在 jar 失败时留下一个只有 json 的版本目录，
            // 它照样出现在版本列表里，玩家点开却是坏的，而界面刚刚才提示过导入失败
            if (!download(jarUrl, target)) {
                removeIfEmpty(dir);
                setStatus(getString(R.string.kcu_import_failed));
                return;
            }

            // 版本 json 优先取官方原样内容，比自行拼一个最小 json 更完整，
            // 后续依赖补全也能读到；取不到时退回最小 json，版本依然可用
            File json = new File(dir, entry.id + ".json");
            if (!download(entry.url, json)) {
                writeVersionJson(dir, entry.id);
            }
            if (!json.isFile()) {
                removeIfEmpty(dir);
                setStatus(getString(R.string.kcu_import_failed));
                return;
            }
            finishImport(entry.id, target, true);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        // 从文件名推导 id：玩家不需要知道 id 是什么
        String id = deriveId(uri);
        // 文件名可能含引号、路径分隔符等不适合当标识的字符：
        // 拿去拼 JSON 会拼出非法内容，拿去当目录名会写到目录之外
        if (id == null || id.isEmpty() || !isSafeId(id)) {
            setStatus(getString(R.string.kcu_import_need_id));
            return;
        }
        setStatus(getString(R.string.kcu_import_working));
        worker.execute(() -> {
            File target = targetFile(id);
            if (target == null) {
                setStatus(getString(R.string.kcu_import_no_dir));
                return;
            }
            boolean ok = copy(uri, target);
            finishImport(id, target, ok);
        });
    }

    /**
     * 取文件的显示名并去掉 .jar；取不到就回退空串由调用方提示。
     *
     * 【为什么不能取路径最后一段】
     * 系统文件选择器返回的 content:// 地址，路径末段常常是 "msf:12345"
     * 这类与文件名无关的标识串，直接拿来当版本 id 会得到一个毫无意义的名字，
     * 玩家看列表根本认不出这是哪个版本。改按显示名取，拿不到才回退空串提示。
     */
    private String deriveId(Uri uri) {
        try {
            String name = new SelectedFile(uri).fileName(this);
            if (name == null) {
                return "";
            }
            name = name.trim();
            if (name.toLowerCase().endsWith(".jar")) {
                name = name.substring(0, name.length() - 4).trim();
            }
            return name;
        } catch (Throwable e) {
            return "";
        }
    }

    /**
     * 导入收尾：写元数据。
     * 版本 json 已由清单原样落盘；本地文件导入时这里补一份最小 json，
     * 否则上游枚举阶段会整份忽略该目录，版本不会出现在列表里。
     */
    private void finishImport(String id, File jar, boolean ok) {
        if (!ok) {
            setStatus(getString(R.string.kcu_import_failed));
            return;
        }
        File dir = jar.getParentFile();
        writeVersionJson(dir, id);
        KcuVersionMeta.Meta meta = new KcuVersionMeta.Meta();
        meta.known = false;
        meta.display = KcuVersionMeta.UNKNOWN;
        meta.java = "";
        KcuVersionMeta.write(dir, meta);
        setStatus(getString(R.string.kcu_import_done) + " " + jar.getAbsolutePath());
    }

    /** 最小可被解析的版本 JSON：validate 只要求 id 非空 */
    private void writeVersionJson(File dir, String id) {
        File file = new File(dir, id + ".json");
        if (file.isFile()) {
            return;
        }
        File tmp = new File(dir, id + ".json.kcupart");
        boolean ok = false;
        OutputStream out = null;
        try {
            out = new FileOutputStream(tmp, false);
            // id 来自玩家选的文件名，可能带引号或反斜杠；直接拼进 JSON 会拼出非法内容，
            // 解析失败后这个版本不会出现在列表里，而 jar 已经就位，
            // 玩家看到"已写入"却找不到版本。这里必须按 JSON 字符串规则转义
            String safe = jsonEscape(id);
            String json = "{\n  \"id\": \"" + safe + "\",\n  \"jar\": \"" + safe + "\"\n}\n";
            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
            ok = true;
        } catch (Throwable ignored) {
            // 写不出来不影响 jar 已就位，仅版本不会出现在列表中
        } finally {
            close(out);
            if (!ok && !tmp.delete()) {
                Logging.LOG.warning("KcuImport: unable to delete partial " + tmp);
            }
        }
        if (ok && !tmp.renameTo(file) && !tmp.delete()) {
            Logging.LOG.warning("KcuImport: unable to delete " + tmp);
        }
    }

    /** 按 JSON 字符串规则转义：引号、反斜杠与控制字符都必须转义 */
    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * 版本标识的字符集限制。
     *
     * 标识会被用作目录名，也会被拼进版本 JSON。文件名里允许出现的字符远多于
     * 这里允许的范围，包含引号会破坏 JSON，包含路径分隔符会让文件写到目录之外。
     * 因此只放行版本号实际用到的那几种字符。
     */
    private static boolean isSafeId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        if (id.contains("..")) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-' || c == '+';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private File targetFile(String id) {
        try {
            File gameDir = Profiles.getSelectedProfile().getGameDir();
            File dir = new File(gameDir, "versions/" + id);
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            return new File(dir, id + ".jar");
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 先下载到临时文件，只有完整写完才改名为目标文件。
     *
     * 【为什么不能直接写目标文件】
     * 直接写的话，中途失败会在目标路径留下半截文件。清单路径是先落盘版本 json
     * 再下载 jar 的，半截 jar 配上完整 json 会让这个版本照样出现在版本列表里，
     * 玩家一点开就是坏的，而界面上早就提示过导入失败，看上去自相矛盾。
     * 先写临时文件、成功再改名，目标路径要么没有文件，要么是完整内容。
     */
    private boolean download(String address, File target) {
        File tmp = new File(target.getParentFile(), target.getName() + ".kcupart");
        boolean ok = false;
        try {
            ok = downloadTo(address, tmp);
        } finally {
            if (!ok && !tmp.delete()) {
                Logging.LOG.warning("KcuImport: unable to delete partial " + tmp);
            }
        }
        return ok && place(tmp, target);
    }

    private boolean downloadTo(String address, File target) {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            int code = conn.getResponseCode();
            if (code != 200) {
                return false;
            }
            in = conn.getInputStream();
            out = new FileOutputStream(target, false);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
            return true;
        } catch (Throwable e) {
            Logging.LOG.warning("KcuImport: " + e);
            return false;
        } finally {
            close(out);
            close(in);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 同 {@link #download}：先写临时文件，完整才改名，避免失败留下半截文件 */
    private boolean copy(Uri uri, File target) {
        File tmp = new File(target.getParentFile(), target.getName() + ".kcupart");
        boolean ok = false;
        try {
            ok = copyTo(uri, tmp);
        } finally {
            if (!ok && !tmp.delete()) {
                Logging.LOG.warning("KcuImport: unable to delete partial " + tmp);
            }
        }
        return ok && place(tmp, target);
    }

    private boolean copyTo(Uri uri, File target) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) {
                return false;
            }
            out = new FileOutputStream(target, false);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
            return true;
        } catch (Throwable e) {
            Logging.LOG.warning("KcuImport: " + e);
            return false;
        } finally {
            close(out);
            close(in);
        }
    }

    /** 导入失败时清掉空目录，避免留下一个不会被识别、也不会被清理的版本目录 */
    private void removeIfEmpty(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        String[] items = dir.list();
        if (items != null && items.length == 0 && !dir.delete()) {
            Logging.LOG.warning("KcuImport: unable to remove empty " + dir);
        }
    }

    /** 同目录内改名就位；改名失败则清掉临时文件并返回失败 */
    private boolean place(File tmp, File target) {
        if (tmp.renameTo(target)) {
            return true;
        }
        if (!tmp.delete()) {
            Logging.LOG.warning("KcuImport: unable to delete " + tmp);
        }
        return false;
    }

    private void close(Object stream) {
        if (stream == null) {
            return;
        }
        try {
            if (stream instanceof InputStream) {
                ((InputStream) stream).close();
            } else if (stream instanceof OutputStream) {
                ((OutputStream) stream).close();
            }
        } catch (Throwable ignored) {
        }
    }

    private void setStatus(String text) {
        // 界面被替换成限制页后 status 已失效，此时不应再写入
        if (status == null) {
            return;
        }
        runOnUiThread(() -> {
            if (status != null) {
                status.setText(text);
            }
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }
}
