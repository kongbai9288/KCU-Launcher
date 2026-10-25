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
            // 版本 json 由清单地址直接落盘：它本身就是官方原样内容，
            // 比自行拼一个最小 json 更完整，后续依赖补全也能读到
            boolean ok = download(entry.url, new File(target.getParentFile(), entry.id + ".json"))
                    && download(jarUrl, target);
            finishImport(entry.id, target, ok);
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
        if (id == null || id.isEmpty()) {
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

    /** 取路径最后一段并去掉 .jar；取不到就回退空串由调用方提示 */
    private String deriveId(Uri uri) {
        try {
            String path = uri.getPath();
            if (path == null) {
                return "";
            }
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (name.toLowerCase().endsWith(".jar")) {
                name = name.substring(0, name.length() - 4);
            }
            return name.trim();
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
        OutputStream out = null;
        try {
            out = new FileOutputStream(file, false);
            String json = "{\n  \"id\": \"" + id + "\",\n  \"jar\": \"" + id + "\"\n}\n";
            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
        } catch (Throwable ignored) {
            // 写不出来不影响 jar 已就位，仅版本不会出现在列表中
        } finally {
            close(out);
        }
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

    private boolean download(String address, File target) {
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
            while ((n = in.read(buf)) > 0) {
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

    private boolean copy(Uri uri, File target) {
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
            while ((n = in.read(buf)) > 0) {
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
