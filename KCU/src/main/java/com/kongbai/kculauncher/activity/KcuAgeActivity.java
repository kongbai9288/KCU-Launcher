package com.kongbai.kculauncher.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.util.KcuAgeGate;
import com.kongbai.kculauncher.util.KcuDevMode;
import com.kongbai.kculauncher.util.KcuUi;

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

        showDeclare(null);
    }

    /**
     * 构建声明界面。
     *
     * notice 非空时在顶部加一句说明，用于"刚才选的年龄不符合要求"这种情况。
     */
    private void showDeclare(String notice) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = KcuUi.root(this);

        LinearLayout card = KcuUi.card(this);
        if (notice != null) {
            card.addView(KcuUi.body(this, notice));
            card.addView(KcuUi.gap(this, 12));
        }
        // 标题与说明由 KcuAgeGate.buildInto 统一构建，此处不再重复添加
        final int[] selected = new int[]{KcuAgeGate.THRESHOLD};
        KcuAgeGate.buildInto(this, card, selected, () -> {
            KcuAgeGate.declare(selected[0]);
            if (KcuAgeGate.isBlocked()) {
                // 选的年龄不符合要求时，不再直接换成不可用的页面：
                // 那样一次误触就再也改不回来，只能清数据或重装。
                // 这里清掉刚才那次声明并回到选择界面，
                // 限制本身仍然有效 —— 只要选的年龄仍不符合要求，就一直进不了应用。
                KcuAgeGate.reset();
                showDeclare(getString(R.string.kcu_blocked_body));
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
