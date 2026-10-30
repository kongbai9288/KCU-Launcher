package com.kongbai.kculauncher.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.kongbai.kculauncher.KCUApp;
import com.kongbai.kculauncher.R;

/**
 * 年龄声明与访问判定。
 *
 * 声明值存于独立 SharedPreferences，不写进 Config：
 * 入口在 SplashActivity.onCreate 最前执行，此时 ConfigHolder 尚未 init，
 * config() 会抛 IllegalStateException，导致声明读不出也写不进。
 */
public final class KcuAgeGate {

    /** 判定阈值：低于该岁数不允许使用 */
    public static final int THRESHOLD = 18;

    private static final String PREF = "kcu_age";
    private static final String KEY_AGE = "age";
    private static final String KEY_PROMPTED = "prompted";

    private KcuAgeGate() {
    }

    private static SharedPreferences prefs() {
        Context context = KCUApp.getAppContext();
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 是否已完成声明 */
    public static boolean isDeclared() {
        try {
            return prefs().getBoolean(KEY_PROMPTED, false)
                    && prefs().getInt(KEY_AGE, 0) > 0;
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
            return prefs().getInt(KEY_AGE, 0) < THRESHOLD;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 记录声明。
     *
     * 【为什么用同步写入而不是异步】
     * 这个值决定应用能不能继续用，而声明完成后紧接着就会跳回启动页：
     * 若只用异步写入，那一瞬间进程被终止的话声明就没落盘，
     * 下次启动又会被要求重新声明一次 —— 玩家只会觉得"我明明选过了"。
     * 异步写入只保证内存里立刻可见，不保证已经写到文件，
     * 这里要的正是后者。文件很小，同步写入的开销可以接受。
     */
    public static void declare(int age) {
        try {
            prefs().edit().putInt(KEY_AGE, age).putBoolean(KEY_PROMPTED, true).commit();
        } catch (Throwable ignored) {
        }
    }

    /** 清除声明 */
    public static void reset() {
        try {
            prefs().edit().remove(KEY_AGE).remove(KEY_PROMPTED).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 构建选择界面；selected[0] 为当前选中值 */
    public static void buildInto(Context context, LinearLayout card, final int[] selected, Runnable onConfirm) {
        card.addView(KcuUi.header(context, "!", context.getString(R.string.kcu_age_title)));
        card.addView(KcuUi.gap(context, 12));
        card.addView(KcuUi.body(context, context.getString(R.string.kcu_age_prompt)));
        card.addView(KcuUi.gap(context, 18));

        RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.VERTICAL);
        int[] options = {6, 12, 16, 18, 20};
        String unit = context.getString(R.string.kcu_age_unit);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(context);
            rb.setId(i + 1);
            rb.setText(options[i] + unit);
            rb.setTag(options[i]);
            rb.setTextColor(KcuUi.INK);
            rb.setTextSize(15f);
            group.addView(rb);
            if (options[i] == THRESHOLD) {
                group.check(rb.getId());
            }
        }
        selected[0] = THRESHOLD;
        group.setOnCheckedChangeListener((g, checkedId) -> {
            android.view.View v = g.findViewById(checkedId);
            if (v != null && v.getTag() instanceof Integer) {
                selected[0] = (Integer) v.getTag();
            }
        });
        card.addView(group);

        android.widget.Button confirm = KcuUi.primaryButton(context, context.getString(R.string.kcu_age_confirm));
        confirm.setOnClickListener(v -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
        });
        card.addView(confirm);
    }
}
