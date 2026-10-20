package com.tungsten.fcl.util;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.tungsten.fcl.R;
import com.tungsten.fcl.setting.ConfigHolder;

/**
 * 年龄声明与访问判定。
 * 声明值保存在配置中，仅取整数岁数。
 */
public final class KcuAgeGate {

    /** 判定阈值：低于该岁数不允许使用 */
    public static final int THRESHOLD = 18;

    private KcuAgeGate() {
    }

    /** 是否已完成声明 */
    public static boolean isDeclared() {
        try {
            return ConfigHolder.config().isKcuAgePrompted()
                    && ConfigHolder.config().getKcuAgeDeclared() > 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 已声明且低于阈值 */
    public static boolean isBlocked() {
        try {
            if (!isDeclared()) {
                return false;
            }
            return ConfigHolder.config().getKcuAgeDeclared() < THRESHOLD;
        } catch (Throwable e) {
            return false;
        }
    }

    public static void declare(int age) {
        try {
            ConfigHolder.config().setKcuAgeDeclared(age);
            ConfigHolder.config().setKcuAgePrompted(true);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 构建选择界面。返回值的第一个元素为根布局，
     * 调用方需在 onConfirm 中自行读取 getSelectedAge()。
     */
    public static View buildView(Context context, final int[] selected, Runnable onConfirm) {
        float density = context.getResources().getDisplayMetrics().density;
        int pad = (int) (24 * density);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(context);
        title.setText(R.string.kcu_age_title);
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        TextView sub = new TextView(context);
        sub.setText(R.string.kcu_age_prompt);
        sub.setTextSize(13f);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        sub.setPadding(0, (int) (12 * density), 0, (int) (20 * density));
        root.addView(sub);

        final RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.VERTICAL);
        int[] options = {6, 12, 16, 18, 20};
        String unit = context.getString(R.string.kcu_age_unit);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(context);
            rb.setText(options[i] + unit);
            rb.setTag(options[i]);
            rb.setTextSize(15f);
            group.addView(rb);
            if (i == 3) {
                group.check(rb.getId());
            }
        }
        selected[0] = 18;
        group.setOnCheckedChangeListener((g, checkedId) -> {
            View v = g.findViewById(checkedId);
            if (v != null && v.getTag() instanceof Integer) {
                selected[0] = (Integer) v.getTag();
            }
        });
        root.addView(group);

        Button confirm = new Button(context);
        confirm.setText(R.string.kcu_age_confirm);
        confirm.setPadding(0, (int) (10 * density), 0, (int) (10 * density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (24 * density);
        confirm.setLayoutParams(lp);
        confirm.setOnClickListener(v -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
        });
        root.addView(confirm);

        return root;
    }
}
