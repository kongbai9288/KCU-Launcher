package com.kongbai.kculauncher.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一弹窗构建器。
 *
 * 【为什么要有它】
 * 此前每处弹窗各自 new AlertDialog + 手工堆卡片，同样的三件事被写了四遍：
 * 标题图标、按钮、以及"取消之后要不要继续流程"的判断。
 * 其中"取消之后"这一件已经出过两次事故 ——
 * 选运行时的框和存档劝导的框都因为取消后 proceed 不执行，
 * 表现为"点了启动什么都没发生"；令牌框反过来，取消后卡在启动页进不去应用。
 * 判断分散在各处，改一处漏一处。这里把它收成框架的一条规则，一次做对。
 *
 * 【核心约定：onFinish 一定被调用一次】
 * 无论玩家点了哪个按钮、按了返回键、还是点了对话框外部，
 * onFinish 都会被调用，且只调用一次。
 * 调用方把"继续往下走"写在 onFinish 里，就不必再逐处考虑取消路径。
 *
 * 【核心约定：blocking 必须有出口】
 * blocking 表示"玩家必须做出选择"（启动流程里的弹窗）。
 * 它会同时关掉返回键与点击外部两道 —— 只关一道等于没关。
 * 但如果没有任何按钮，玩家就没有出口，会被永久锁死，
 * 因此此时框架自动退回可取消。宁可放走，也不能锁死。
 */
public final class KcuDialog {

    /** 语义图标：符号一律基础字符，不新增 drawable，也不会变成彩色 emoji */
    public enum Icon {
        INFO("i", KcuUi.ACCENT),
        ASK("?", KcuUi.ACCENT),
        OK("\u221A", 0xFF4F7A54),
        WARN("!", 0xFFB26A2C),
        ERROR("\u00D7", 0xFFB5493F),
        DOWN("\u2193", KcuUi.ACCENT),
        GEAR("\u2261", 0xFF6A6760),
        LOCK("#", 0xFF6A6760);

        final String glyph;
        final int color;

        Icon(String glyph, int color) {
            this.glyph = glyph;
            this.color = color;
        }
    }

    private final Context context;
    private final LinearLayout card;
    private final List<EditText> fields = new ArrayList<>();
    private final List<android.widget.Button> buttons = new ArrayList<>();

    private Icon icon = Icon.INFO;
    private boolean blocking;
    private Runnable onFinish;
    private AlertDialog dialog;
    private boolean finished;
    private int buttonCount;

    private KcuDialog(Context context) {
        this.context = context;
        this.card = KcuUi.card(context);
    }

    public static Builder builder(Context context) {
        return new Builder(context);
    }

    /** 可带语义图标的徽章。与 KcuUi.iconBadge 的区别是底色随语义变化 */
    private static TextView badge(Context context, Icon icon) {
        TextView v = new TextView(context);
        v.setText(icon.glyph);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(KcuUi.PAPER_HI);
        v.setTextSize(23f);
        int size = KcuUi.dp(context, 46);
        v.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        GradientDrawable d = new GradientDrawable();
        d.setColor(icon.color);
        d.setCornerRadius(size / 2f);
        v.setBackground(d);
        return v;
    }

    private static LinearLayout header(Context context, Icon icon, CharSequence title) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(badge(context, icon));
        TextView t = KcuUi.title(context, title);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = KcuUi.dp(context, 14);
        t.setLayoutParams(lp);
        row.addView(t);
        return row;
    }

    /** 放行：只执行一次，取消与按钮两条路径不会重复触发 */
    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (onFinish != null) {
            onFinish.run();
        }
    }

    public void dismiss() {
        if (dialog != null) {
            try {
                dialog.dismiss();
            } catch (Throwable ignored) {
            }
        }
        finish();
    }

    /** 取主按钮。需要在构建完成后补挂监听器时使用 */
    public android.widget.Button primaryButton() {
        if (buttons.isEmpty()) {
            return null;
        }
        return buttons.get(0);
    }

    /** 取第 index 个输入框的内容 */
    public String fieldText(int index) {
        if (index < 0 || index >= fields.size()) {
            return "";
        }
        EditText e = fields.get(index);
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    public static final class Builder {
        private final KcuDialog target;

        private Builder(Context context) {
            target = new KcuDialog(context);
        }

        public Builder icon(Icon icon) {
            if (icon != null) {
                target.icon = icon;
            }
            return this;
        }

        public Builder title(CharSequence text) {
            target.pendingTitle = text;
            return this;
        }

        public Builder body(CharSequence text) {
            target.pendingBody = text;
            return this;
        }

        public Builder hint(CharSequence text) {
            target.pendingHints.add(text);
            return this;
        }

        /** 增加一个输入行，索引按添加顺序 */
        public Builder field(CharSequence hint, CharSequence glyph) {
            target.pendingFields.add(new Object[]{hint, glyph});
            return this;
        }

        /**
         * 主按钮。点击后默认关闭并放行。
         * 需要异步校验后再决定去留时用 {@link #primaryStay}。
         */
        public Builder primary(CharSequence text, android.view.View.OnClickListener onClick) {
            return addButton(0, text, onClick, true);
        }

        /** 主按钮但不自动关闭：由监听器自行调用 dismiss 或 finish */
        public Builder primaryStay(CharSequence text, android.view.View.OnClickListener onClick) {
            return addButton(0, text, onClick, false);
        }

        public Builder secondary(CharSequence text, android.view.View.OnClickListener onClick) {
            return addButton(1, text, onClick, true);
        }

        private Builder addButton(int kind, CharSequence text,
                                  android.view.View.OnClickListener onClick, boolean autoDismiss) {
            target.pendingButtons.add(new Object[]{kind, text, onClick, autoDismiss});
            target.buttonCount++;
            return this;
        }

        /** 必须做出选择：同时关掉返回键与点击外部 */
        public Builder blocking(boolean value) {
            target.blocking = value;
            return this;
        }

        public Builder onFinish(Runnable runnable) {
            target.onFinish = runnable;
            return this;
        }

        public KcuDialog show() {
            return target.build();
        }
    }

    private CharSequence pendingTitle = "";
    private CharSequence pendingBody = "";
    private final List<CharSequence> pendingHints = new ArrayList<>();
    private final List<Object[]> pendingFields = new ArrayList<>();
    private final List<Object[]> pendingButtons = new ArrayList<>();

    private KcuDialog build() {
        // 没有任何按钮却声明 blocking，玩家将无处可去；
        // 此处退回可取消，宁可让流程放空，也不能把人锁死在对话框上
        boolean effectiveBlocking = blocking && buttonCount > 0;

        card.removeAllViews();
        card.addView(header(context, icon, pendingTitle));
        if (pendingBody.length() > 0) {
            card.addView(KcuUi.gap(context, 12));
            card.addView(KcuUi.body(context, pendingBody));
        }
        for (CharSequence one : pendingHints) {
            card.addView(KcuUi.gap(context, 10));
            card.addView(KcuUi.hint(context, one));
        }
        if (!pendingFields.isEmpty()) {
            card.addView(KcuUi.gap(context, 16));
            for (Object[] one : pendingFields) {
                LinearLayout row = KcuUi.fieldRow(context, (CharSequence) one[0], (CharSequence) one[1]);
                card.addView(row);
                fields.add(KcuUi.editOf(row));
                card.addView(KcuUi.gap(context, 10));
            }
        }
        if (!pendingButtons.isEmpty()) {
            card.addView(KcuUi.gap(context, 18));
            for (Object[] one : pendingButtons) {
                int kind = (Integer) one[0];
                CharSequence text = (CharSequence) one[1];
                android.view.View.OnClickListener onClick = (android.view.View.OnClickListener) one[2];
                boolean autoDismiss = (Boolean) one[3];
                android.widget.Button b = kind == 0
                        ? KcuUi.primaryButton(context, text)
                        : KcuUi.secondaryButton(context, text);
                b.setOnClickListener(v -> {
                    if (onClick != null) {
                        onClick.onClick(v);
                    }
                    if (autoDismiss) {
                        dismiss();
                    }
                });
                card.addView(b);
                buttons.add(b);
            }
        }

        ScrollView scroll = new ScrollView(context);
        LinearLayout root = KcuUi.root(context);
        root.addView(card);
        scroll.addView(root);

        dialog = new AlertDialog.Builder(context).setView(scroll).create();
        dialog.setCancelable(!effectiveBlocking);
        dialog.setCanceledOnTouchOutside(!effectiveBlocking);
        // 取消同样要放行：这是本框架存在的根本原因
        dialog.setOnCancelListener((DialogInterface d) -> finish());
        dialog.show();
        return this;
    }
}
