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
import com.kongbai.kculauncher.util.KcuUi;
import com.kongbai.kculauncher.util.KcuVersionCatalog;
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

        loadCatalog();
    }

    private void loadCatalog() {
        worker.execute(() -> {
            List<KcuVersionCatalog.Entry> list = KcuVersionCatalog.fetch();
            runOnUiThread(() -> {
                if (list == null || list.isEmpty()) {
                    setStatus(getString(R.string.kcu_import_catalog_failed));
                    addLocalPick();
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
