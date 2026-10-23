package com.tungsten.fcl.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tungsten.fcl.R;
import com.tungsten.fcl.util.KcuUi;
import com.tungsten.fcl.util.KcuVersionMeta;
import com.tungsten.fcl.setting.Profiles;
import com.tungsten.fclcore.util.Logging;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 本地导入：按用户给出的地址或本地文件写入版本目录。
 */
public class KcuImportActivity extends Activity {

    private static final int REQUEST_PICK = 9001;
    private static final int TIMEOUT_MS = 30000;

    private EditText editId;
    private EditText editUrl;
    private TextView status;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        LinearLayout root = KcuUi.root(this);
        LinearLayout card = KcuUi.card(this);

        card.addView(KcuUi.title(this, getString(R.string.kcu_import_title)));
        card.addView(KcuUi.gap(this, 10));
        card.addView(KcuUi.body(this, getString(R.string.kcu_import_hint)));
        card.addView(KcuUi.gap(this, 18));

        editId = KcuUi.field(this, getString(R.string.kcu_import_id_hint));
        card.addView(editId);

        editUrl = KcuUi.field(this, getString(R.string.kcu_import_url_hint));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = KcuUi.dp(this, 12);
        editUrl.setLayoutParams(lp);
        card.addView(editUrl);

        android.widget.Button btnUrl = KcuUi.primaryButton(this, getString(R.string.kcu_import_fetch));
        btnUrl.setOnClickListener(v -> startFetch());
        card.addView(btnUrl);

        android.widget.Button btnLocal = KcuUi.secondaryButton(this, getString(R.string.kcu_import_pick));
        btnLocal.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.kcu_import_pick)), REQUEST_PICK);
        });
        card.addView(btnLocal);

        status = KcuUi.status(this, "");
        status.setPadding(0, KcuUi.dp(this, 16), 0, 0);
        card.addView(status);

        root.addView(card);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void startFetch() {
        String id = editId.getText().toString().trim();
        String url = editUrl.getText().toString().trim();
        if (id.isEmpty()) {
            setStatus(getString(R.string.kcu_import_need_id));
            return;
        }
        if (url.isEmpty()) {
            setStatus(getString(R.string.kcu_import_need_url));
            return;
        }
        setStatus(getString(R.string.kcu_import_working));
        worker.execute(() -> {
            File target = targetFile(id);
            if (target == null) {
                setStatus(getString(R.string.kcu_import_no_dir));
                return;
            }
            boolean ok = download(url, target);
            finishImport(id, target, ok);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        String id = editId.getText().toString().trim();
        if (id.isEmpty()) {
            setStatus(getString(R.string.kcu_import_need_id));
            return;
        }
        Uri uri = data.getData();
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
     * 导入收尾：写最小版本 JSON 与元数据。
     * 没有 JSON 时上游枚举阶段会整份忽略该目录，版本不会出现在列表里。
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
        java.io.File file = new java.io.File(dir, id + ".json");
        if (file.isFile()) {
            return;
        }
        java.io.OutputStream out = null;
        try {
            out = new java.io.FileOutputStream(file, false);
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
            status.setText(text);
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }
}
