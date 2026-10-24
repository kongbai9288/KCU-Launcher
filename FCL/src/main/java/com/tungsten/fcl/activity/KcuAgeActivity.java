package com.tungsten.fcl.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.tungsten.fcl.R;
import com.tungsten.fcl.util.KcuAgeGate;
import com.tungsten.fcl.util.KcuDevMode;
import com.tungsten.fcl.util.KcuUi;

import android.widget.ScrollView;

/**
 * 年龄声明，以及受限状态下的说明。
 * 界面一律经 KcuUi 构建。
 */
public class KcuAgeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (KcuAgeGate.isBlocked()) {
            setContentView(KcuDevMode.buildBlockedView(this));
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = KcuUi.root(this);

        LinearLayout card = KcuUi.card(this);
        // 标题与说明由 KcuAgeGate.buildInto 统一构建，此处不再重复添加
        final int[] selected = new int[]{KcuAgeGate.THRESHOLD};
        KcuAgeGate.buildInto(this, card, selected, () -> {
            KcuAgeGate.declare(selected[0]);
            if (KcuAgeGate.isBlocked()) {
                setContentView(KcuDevMode.buildBlockedView(KcuAgeActivity.this));
                return;
            }
            Intent intent = new Intent(KcuAgeActivity.this, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        root.addView(card);
        scroll.addView(root);
        setContentView(scroll);
    }
}
