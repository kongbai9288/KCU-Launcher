package com.tungsten.fcl.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 界面构建的唯一入口。
 *
 * 所有新增界面都必须经由本类构建，不得各自直接 new 控件或另建布局文件，
 * 以保证配色、间距、圆角、字号全局一致。
 */
public final class KcuUi {

    public static final int PAPER = 0xFFF1EFE8;
    public static final int PAPER_HI = 0xFFFAF8F2;
    public static final int BACKDROP = 0xFFC9C6BB;
    public static final int INK = 0xFF2C2C2A;
    public static final int INK_2 = 0xFF55534C;
    public static final int INK_3 = 0xFF8E8B80;
    public static final int LINE = 0x1A282826;
    public static final int ACCENT = 0xFF3D6B66;
    public static final int ACCENT_HI = 0xFF4F8580;
    public static final int CARD = 0xB8FFFFFF;

    public static final int RADIUS_DP = 4;
    public static final int PAD_DP = 22;

    private KcuUi() {
    }

    public static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    /** 卡片底：圆角 + 半透明纸面 + 细描边 */
    private static GradientDrawable cardBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD);
        d.setCornerRadius(RADIUS_DP * 3);
        d.setStroke(1, LINE);
        return d;
    }

    /** 竖排容器卡片，带内边距 */
    public static LinearLayout card(Context context) {
        LinearLayout v = new LinearLayout(context);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(cardBackground());
        int pad = dp(context, 16);
        v.setPadding(pad, pad, pad, pad);
        return v;
    }

    /** 页面根容器：纸面背景 */
    public static LinearLayout root(Context context) {
        LinearLayout v = new LinearLayout(context);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackgroundColor(PAPER);
        int pad = dp(context, PAD_DP);
        v.setPadding(pad, pad, pad, pad);
        return v;
    }

    public static TextView title(Context context, CharSequence text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextColor(INK);
        v.setTextSize(19f);
        v.setGravity(Gravity.START);
        return v;
    }

    public static TextView body(Context context, CharSequence text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextColor(INK_2);
        v.setTextSize(13f);
        v.setGravity(Gravity.START);
        return v;
    }

    public static TextView hint(Context context, CharSequence text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextColor(INK_3);
        v.setTextSize(12f);
        v.setGravity(Gravity.START);
        return v;
    }

    public static TextView status(Context context, CharSequence text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextColor(INK_3);
        v.setTextSize(12f);
        return v;
    }

    /** 主操作按钮：实心强调色 */
    public static Button primaryButton(Context context, CharSequence text) {
        Button b = new Button(context);
        b.setText(text);
        b.setTextColor(PAPER_HI);
        b.setTextSize(14f);
        b.setAllCaps(false);
        GradientDrawable d = new GradientDrawable();
        d.setColor(ACCENT);
        d.setCornerRadius(RADIUS_DP * 3);
        b.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(context, 12);
        b.setLayoutParams(lp);
        return b;
    }

    /** 次操作按钮：描边纸面 */
    public static Button secondaryButton(Context context, CharSequence text) {
        Button b = new Button(context);
        b.setText(text);
        b.setTextColor(INK_2);
        b.setTextSize(14f);
        b.setAllCaps(false);
        GradientDrawable d = new GradientDrawable();
        d.setColor(PAPER_HI);
        d.setCornerRadius(RADIUS_DP * 3);
        d.setStroke(1, LINE);
        b.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(context, 12);
        b.setLayoutParams(lp);
        return b;
    }

    /** 输入区：纸面高亮 + 描边 */
    public static android.widget.EditText field(Context context, CharSequence hint) {
        android.widget.EditText e = new android.widget.EditText(context);
        e.setHint(hint);
        e.setHintTextColor(INK_3);
        e.setTextColor(INK);
        e.setTextSize(14f);
        e.setSingleLine(true);
        int pad = dp(context, 12);
        e.setPadding(pad, pad, pad, pad);
        GradientDrawable d = new GradientDrawable();
        d.setColor(PAPER_HI);
        d.setCornerRadius(RADIUS_DP * 3);
        d.setStroke(1, LINE);
        e.setBackground(d);
        return e;
    }

    /** 占位：用于卡片之间留白 */
    public static View gap(Context context, int dpValue) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, dpValue)));
        return v;
    }

    public static int parseColorOrDefault(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (Throwable e) {
            return fallback;
        }
    }
}
