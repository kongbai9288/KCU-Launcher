package com.kongbai.kculauncher.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.util.KcuDataExport;
import com.kongbai.kculauncher.util.KcuPolicy;
import com.kongbai.kculauncher.util.KcuUi;

import java.io.File;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 强制停止与强制更新的落地页。
 *
 * 【为什么单独一个页面】
 * 这两种状态都要把整个应用挡住，不能让任何常规界面出现在它上面。
 * 放在独立页面里，进入就直接显示，没有继续操作的空间，
 * 也不会被后续跳转覆盖掉。
 *
 * 【为什么这两个页面不提供"继续"】
 * 停就是停。给了继续按钮就等于没停，规则形同虚设。
 * 但导出数据的口子要留着 —— 那是玩家自己的东西，不该跟着一起锁住。
 *
 * 【为什么只在停用时才显示导出】
 * 强制更新意味着还能用新版本，数据不会丢，不需要导出；
 * 强制停止则不知道什么时候恢复，必须给玩家留一条取回数据的路。
 */
public class KcuGateActivity extends Activity {

    private static final String EXTRA_MODE = "mode";
    private static final String MODE_STOP = "stop";
    private static final String MODE_UPDATE = "update";

    private static final String ORG_URL = "https://github.com/KCU-Studios";
    private static final String DEV_URL = "https://github.com/kongbai9288";

    private LinearLayout card;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public static Intent stopped(android.content.Context context) {
        Intent intent = new Intent(context, KcuGateActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_STOP);
        return intent;
    }

    public static Intent update(android.content.Context context) {
        Intent intent = new Intent(context, KcuGateActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_UPDATE);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = KcuUi.root(this);
        card = KcuUi.card(this);
        root.addView(card);
        scroll.addView(root);
        setContentView(scroll);

        if (MODE_UPDATE.equals(getIntent().getStringExtra(EXTRA_MODE))) {
            renderUpdate();
        } else {
            renderStopped();
        }
    }

    /** 强制停止：说明原因，提供导出与两个主页入口 */
    private void renderStopped() {
        KcuPolicy.Shutdown shutdown = KcuPolicy.shutdown();
        card.addView(KcuUi.header(this, "!", titleOf(shutdown == null ? "" : shutdown.title,
                getString(R.string.kcu_gate_stopped_title))));
        card.addView(KcuUi.gap(this, 12));
        card.addView(KcuUi.body(this, bodyOf(shutdown == null ? "" : shutdown.body,
                getString(R.string.kcu_gate_stopped_body))));
        card.addView(KcuUi.gap(this, 18));

        // 导出默认允许：规则没写就按允许，玩家的东西不该被一起锁住
        boolean allowExport = shutdown == null || shutdown.allowExport;
        if (allowExport) {
            android.widget.Button export = KcuUi.primaryButton(this,
                    getString(R.string.kcu_gate_export));
            export.setOnClickListener(v -> startExport());
            card.addView(export);
            card.addView(KcuUi.gap(this, 10));
        }

        card.addView(KcuUi.title(this, getString(R.string.kcu_gate_links)));
        card.addView(KcuUi.gap(this, 8));

        android.widget.Button org = KcuUi.secondaryButton(this,
                getString(R.string.kcu_gate_org));
        org.setOnClickListener(v -> openPage(ORG_URL));
        card.addView(org);
        card.addView(KcuUi.gap(this, 8));

        android.widget.Button dev = KcuUi.secondaryButton(this,
                getString(R.string.kcu_gate_dev));
        dev.setOnClickListener(v -> openPage(DEV_URL));
        card.addView(dev);
    }

    /** 强制更新：说明需要新版本，给出获取入口 */
    private void renderUpdate() {
        KcuPolicy.ForceUpdate force = KcuPolicy.forceUpdate();
        card.addView(KcuUi.header(this, "\u2191",
                titleOf(force == null ? "" : force.title,
                        getString(R.string.kcu_gate_update_title))));
        card.addView(KcuUi.gap(this, 12));
        card.addView(KcuUi.body(this, bodyOf(force == null ? "" : force.body,
                getString(R.string.kcu_gate_update_body))));
        card.addView(KcuUi.gap(this, 18));

        String url = force == null ? "" : force.url;
        if (url != null && !url.trim().isEmpty()) {
            android.widget.Button go = KcuUi.primaryButton(this,
                    getString(R.string.kcu_gate_get_update));
            go.setOnClickListener(v -> openPage(url));
            card.addView(go);
            card.addView(KcuUi.gap(this, 10));
        }

        android.widget.Button org = KcuUi.secondaryButton(this,
                getString(R.string.kcu_gate_org));
        org.setOnClickListener(v -> openPage(ORG_URL));
        card.addView(org);
    }

    /**
     * 导出数据。
     *
     * 【为什么导出过程不能被打断】
     * 这是被停用时唯一能取回数据的路。按钮按下后若因为配置回调又重建界面，
     * 任务还在跑但结果无从告知，玩家会以为导出没生效。
     * 这里只写文字不重建结构，任务完成后再改文字。
     */
    private void startExport() {
        Toast.makeText(this, R.string.kcu_gate_exporting, Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            File result = KcuDataExport.write();
            final String message = result == null
                    ? getString(R.string.kcu_gate_export_failed)
                    : getString(R.string.kcu_gate_export_done) + " " + result.getAbsolutePath();
            runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
        });
    }

    private void openPage(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable e) {
            Toast.makeText(this, R.string.kcu_gate_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private String titleOf(String remote, String fallback) {
        return remote == null || remote.trim().isEmpty() ? fallback : remote.trim();
    }

    private String bodyOf(String remote, String fallback) {
        return remote == null || remote.trim().isEmpty() ? fallback : remote.trim();
    }

    @Override
    protected void onDestroy() {
        // 页面销毁时关闭后台线程：导出任务可能还在跑，
        // 不关的话线程会一直持有本页面，页面无法回收
        worker.shutdownNow();
        super.onDestroy();
    }
}
