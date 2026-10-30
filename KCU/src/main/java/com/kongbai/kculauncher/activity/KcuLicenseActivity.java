package com.kongbai.kculauncher.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.util.KcuUi;
import com.kongbai.kculauncher.mio.util.AndroidUtilKt;

import org.json.JSONArray;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 开源许可清单。
 *
 * 数据全部来自本地 assets/licenses/licenses.json，展示过程不发起任何网络请求，
 * 图标用字符徽章而非远程图片。
 * SPDX 标识取自各项目官方仓库的 license 字段；官方未声明的如实标注，不推测填充。
 */
public class KcuLicenseActivity extends Activity {

    private static final String ASSET = "licenses/licenses.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = KcuUi.root(this);
        LinearLayout card = KcuUi.card(this);

        card.addView(KcuUi.header(this, "\u00A7", getString(R.string.kcu_license_title)));
        card.addView(KcuUi.gap(this, 12));
        card.addView(KcuUi.body(this, getString(R.string.kcu_license_hint)));
        card.addView(KcuUi.gap(this, 18));

        JSONArray items = readItems();
        if (items == null || items.length() == 0) {
            card.addView(KcuUi.hint(this, getString(R.string.kcu_license_empty)));
        } else {
            for (int i = 0; i < items.length(); i++) {
                card.addView(buildRow(items.optJSONObject(i), i < items.length() - 1));
            }
        }

        root.addView(card);
        scroll.addView(root);
        setContentView(scroll);
    }

    /** 读取本地清单；任何异常都返回 null 由上层显示占位，不抛出 */
    private JSONArray readItems() {
        try (InputStream in = getAssets().open(ASSET)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            // 以 -1 为结束：个别输入流会返回 0，按 > 0 判结束会提前截断内容。
            // 截断后解析不出合法内容，页面会显示"清单为空"，
            // 而文件明明就在安装包里，看起来像是项目没写许可信息
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            return new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable e) {
            return null;
        }
    }

    private LinearLayout buildRow(org.json.JSONObject item, boolean addGap) {
        if (item == null) {
            return new LinearLayout(this);
        }
        // 行本身不再套一层 card：card 带 22dp 内边距，嵌套在外层 card 内会过挤
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        android.widget.TextView badge = KcuUi.iconBadge(this, item.optString("glyph", "?"));
        row.addView(badge);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = KcuUi.dp(this, 14);
        text.setLayoutParams(lp);

        text.addView(KcuUi.title(this, item.optString("name", "")));
        text.addView(KcuUi.hint(this, item.optString("spdx", "")));
        String note = item.optString("note", "");
        if (!note.isEmpty()) {
            text.addView(KcuUi.hint(this, note));
        }
        row.addView(text);

        final String url = item.optString("url", "");
        final String file = item.optString("licenseFile", "");
        if (!file.isEmpty()) {
            // 有内置全文时优先打开本地全文页，避免直接跳外部链接
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, KcuLicenseTextActivity.class);
                intent.putExtra(KcuLicenseTextActivity.EXTRA_TITLE, item.optString("name", ""));
                intent.putExtra(KcuLicenseTextActivity.EXTRA_FILE, file);
                intent.putExtra(KcuLicenseTextActivity.EXTRA_URL, url);
                startActivity(intent);
            });
        } else if (!url.isEmpty()) {
            row.setOnClickListener(v -> AndroidUtilKt.openLink(this, url));
        }

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row);
        if (addGap) wrapper.addView(KcuUi.gap(this, 10));
        return wrapper;
    }
}
