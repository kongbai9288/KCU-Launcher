package com.tungsten.fcl.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

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
        card.addView(KcuUi.title(this, getString(R.string.kcu_age_title)));
        card.addView(KcuUi.gap(this, 10));
        card.addView(KcuUi.body(this, getString(R.string.kcu_age_prompt)));
        card.addView(KcuUi.gap(this, 18));

        final int[] selected = new int[]{KcuAgeGate.THRESHOLD};
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        int[] options = {6, 12, 16, 18, 20};
        String unit = getString(R.string.kcu_age_unit);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(options[i] + unit);
            rb.setTag(options[i]);
            rb.setTextColor(KcuUi.INK);
            rb.setTextSize(15f);
            group.addView(rb);
            if (i == 3) {
                group.check(rb.getId());
            }
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            android.view.View v = g.findViewById(checkedId);
            if (v != null && v.getTag() instanceof Integer) {
                selected[0] = (Integer) v.getTag();
            }
        });
        card.addView(group);

        android.widget.Button confirm = KcuUi.primaryButton(this, getString(R.string.kcu_age_confirm));
        confirm.setOnClickListener(v -> {
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
        card.addView(confirm);

        root.addView(card);
        scroll.addView(root);
        setContentView(scroll);
    }
}
