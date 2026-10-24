package com.tungsten.fcl.activity;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mio.util.AndroidUtilKt;
import com.tungsten.fcl.R;
import com.tungsten.fcl.util.KcuUi;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 许可证全文。
 *
 * 文本取自各协议的官方发布版本并内置于 assets，读取过程不联网。
 * 进入时按 Intent 传入的文件名读取；文件缺失或读取异常时显示占位并给出官方链接。
 */
public class KcuLicenseTextActivity extends Activity {

    public static final String EXTRA_TITLE = "kcu_license_title";
    public static final String EXTRA_FILE = "kcu_license_file";
    public static final String EXTRA_URL = "kcu_license_url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String file = getIntent().getStringExtra(EXTRA_FILE);
        String url = getIntent().getStringExtra(EXTRA_URL);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = KcuUi.root(this);
        LinearLayout card = KcuUi.card(this);

        card.addView(KcuUi.header(this, "\u00A7", title == null ? "" : title));
        card.addView(KcuUi.gap(this, 16));

        String body = readAsset(file);
        if (body == null || body.isEmpty()) {
            card.addView(KcuUi.body(this, getString(R.string.kcu_license_text_empty)));
            if (url != null && !url.isEmpty()) {
                android.widget.Button open = KcuUi.secondaryButton(this, getString(R.string.kcu_license_open_official));
                open.setOnClickListener(v -> AndroidUtilKt.openLink(this, url));
                card.addView(open);
            }
        } else {
            TextView text = new TextView(this);
            text.setText(body);
            text.setTextColor(KcuUi.INK);
            text.setTextSize(11f);
            text.setTypeface(Typeface.MONOSPACE);
            text.setMovementMethod(new ScrollingMovementMethod());
            card.addView(text);
        }

        root.addView(card);
        scroll.addView(root);
        setContentView(scroll);
    }

    /** 读取 assets 内的许可证全文；任何异常返回 null，不抛出 */
    private String readAsset(String file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try (InputStream in = getAssets().open("licenses/text/" + file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        }
    }
}
