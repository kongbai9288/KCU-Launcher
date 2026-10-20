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
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (22 * density);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText(R.string.kcu_import_title);
        title.setTextSize(19f);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText(R.string.kcu_import_hint);
        hint.setTextSize(12f);
        hint.setPadding(0, (int) (10 * density), 0, (int) (18 * density));
        root.addView(hint);

        editId = new EditText(this);
        editId.setHint(R.string.kcu_import_id_hint);
        editId.setSingleLine(true);
        root.addView(editId);

        editUrl = new EditText(this);
        editUrl.setHint(R.string.kcu_import_url_hint);
        editUrl.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (12 * density);
        editUrl.setLayoutParams(lp);
        root.addView(editUrl);

        Button btnUrl = new Button(this);
        btnUrl.setText(R.string.kcu_import_fetch);
        btnUrl.setOnClickListener(v -> startFetch());
        root.addView(btnUrl);

        Button btnLocal = new Button(this);
        btnLocal.setText(R.string.kcu_import_pick);
        btnLocal.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.kcu_import_pick)), REQUEST_PICK);
        });
        root.addView(btnLocal);

        status = new TextView(this);
        status.setTextSize(12f);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(0, (int) (18 * density), 0, 0);
        root.addView(status);

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
            setStatus(ok
                    ? getString(R.string.kcu_import_done) + " " + target.getAbsolutePath()
                    : getString(R.string.kcu_import_failed));
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
            setStatus(ok
                    ? getString(R.string.kcu_import_done) + " " + target.getAbsolutePath()
                    : getString(R.string.kcu_import_failed));
        });
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
