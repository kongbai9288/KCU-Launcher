package com.kongbai.kculauncher.library.component.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.text.Spanned;
import android.text.util.Linkify;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.utils.widget.ImageFilterView;
import androidx.core.text.method.LinkMovementMethodCompat;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUTextView;
import com.kongbai.kculauncher.library.util.ConvertUtils;

public class KCUAlertDialog extends KCUDialog implements View.OnClickListener {

    private String titleString;

    private ButtonListener positiveListener;
    private ButtonListener negativeListener;
    private ButtonListener neutralListener;
    private ButtonListener extraListener;

    private View parent;
    private ImageFilterView icon;
    private KCUTextView title;
    private ScrollView scrollView;
    private KCUTextView message;
    private KCUButton positive;
    private KCUButton negative;
    private KCUButton neutral;
    private KCUButton extra;

    @SuppressLint("UseCompatLoadingForDrawables")
    public KCUAlertDialog(@NonNull Context context) {
        super(context);

        setContentView(R.layout.dialog_alert);

        parent = findViewById(R.id.parent);

        icon = findViewById(R.id.image);
        title = findViewById(R.id.title);
        scrollView = findViewById(R.id.text_scroll);
        message = findViewById(R.id.text);
        positive = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);
        neutral = findViewById(R.id.neutral);
        extra = findViewById(R.id.extra);

        checkHeight();

        positive.setVisibility(View.GONE);
        negative.setVisibility(View.GONE);
        neutral.setVisibility(View.GONE);
        extra.setVisibility(View.GONE);

        positive.setOnClickListener(this);
        negative.setOnClickListener(this);
        neutral.setOnClickListener(this);
        extra.setOnClickListener(this);

        icon.setImageDrawable(getContext().getDrawable(R.drawable.ic_baseline_info_24));
        title.setText(getContext().getString(R.string.dialog_info));

        positive.setSelected(true);
        negative.setSelected(true);
        neutral.setSelected(true);
        extra.setSelected(true);
    }

    private void checkHeight() {
        parent.post(() -> message.post(() -> {
            // 按钮行在 dialog_alert.xml 中位于 ScrollView 之外，永远不参与滚动。
            // 旧写法把整个窗口钳到「屏高 - 30dp」，正常长度的弹窗因此被塞进滚动区，
            // 表现为「标题看完要下滑才能看到确认按钮」。现在窗口一律自适应，
            // 只有正文确实超过屏高 60% 时才给正文设上限，按钮始终同屏可见。
            WindowManager wm = getWindow().getWindowManager();
            Point point = new Point();
            wm.getDefaultDisplay().getSize(point);
            int maxContent = (int) (point.y * 0.6f);
            int content = message.getMeasuredHeight();
            ViewGroup.LayoutParams lp = scrollView.getLayoutParams();
            lp.height = (content > 0 && content > maxContent)
                    ? maxContent : ViewGroup.LayoutParams.WRAP_CONTENT;
            scrollView.setLayoutParams(lp);
            getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }));
    }

    @Override
    public void onClick(View view) {
        ButtonListener listener = null;
        if (view == positive) listener = positiveListener;
        else if (view == negative) listener = negativeListener;
        else if (view == neutral) listener = neutralListener;
        else if (view == extra) listener = extraListener;

        if (listener != null) {
            listener.onClick();
        }
        dismiss();
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public void setAlertLevel(AlertLevel alertLevel) {
        switch (alertLevel) {
            case ALERT:
                icon.setImageDrawable(getContext().getDrawable(R.drawable.ic_baseline_warning_24));
                if (titleString == null) {
                    title.setText(getContext().getString(R.string.dialog_alert));
                }
                break;
            default:
                icon.setImageDrawable(getContext().getDrawable(R.drawable.ic_baseline_info_24));
                if (titleString == null) {
                    title.setText(getContext().getString(R.string.dialog_info));
                }
                break;
        }
    }

    public void setTitle(String title) {
        titleString = title;
        this.title.setText(title);
    }

    public void setMessage(String message) {
        this.message.setText(message);
        checkHeight();
    }

    public void setMessage(CharSequence message) {
        this.message.setText(message);
        checkHeight();
    }

    public void setMessage(Spanned message) {
        this.message.setText(message);
        checkHeight();
    }

    public void setPositiveButton(String text, ButtonListener listener) {
        positive.setVisibility(View.VISIBLE);
        positive.setText(text);
        positiveListener = listener;
    }

    public void setNegativeButton(String text, ButtonListener listener) {
        negative.setVisibility(View.VISIBLE);
        negative.setText(text);
        negativeListener = listener;
    }

    public void setNeutralButton(String text, ButtonListener listener) {
        neutral.setVisibility(View.VISIBLE);
        neutral.setText(text);
        neutralListener = listener;
    }

    public void setExtraButton(String text, ButtonListener listener) {
        extra.setVisibility(View.VISIBLE);
        extra.setText(text);
        extraListener = listener;
    }

    public void useAutoLink() {
        Linkify.addLinks(this.message, Linkify.WEB_URLS);
        this.message.setMovementMethod(LinkMovementMethodCompat.getInstance());
        this.message.setLinkTextColor(ThemeEngine.getInstance().getTheme().getColor());
    }

    public static class Builder {

        private Context context;
        private KCUAlertDialog dialog;

        public Builder(Context context) {
            this.context = context;
            dialog = new KCUAlertDialog(context);
        }

        public KCUAlertDialog create() {
            return dialog;
        }

        public Builder setAlertLevel(AlertLevel alertLevel) {
            dialog.setAlertLevel(alertLevel);
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            dialog.setCancelable(cancelable);
            return this;
        }

        public Builder setTitle(String title) {
            dialog.setTitle(title);
            return this;
        }

        public Builder setMessage(String message) {
            dialog.setMessage(message);
            return this;
        }

        public Builder setMessage(CharSequence message) {
            dialog.setMessage(message);
            return this;
        }

        public Builder setPositiveButton(ButtonListener listener) {
            dialog.setPositiveButton(context.getString(R.string.dialog_positive), listener);
            return this;
        }

        public Builder setPositiveButton(String text, ButtonListener listener) {
            dialog.setPositiveButton(text, listener);
            return this;
        }

        public Builder setNegativeButton(ButtonListener listener) {
            dialog.setNegativeButton(context.getString(R.string.dialog_negative), listener);
            return this;
        }

        public Builder setNegativeButton(String text, ButtonListener listener) {
            dialog.setNegativeButton(text, listener);
            return this;
        }

        public Builder setNeutralButton(String text, ButtonListener listener) {
            dialog.setNeutralButton(text, listener);
            return this;
        }

        public Builder setExtraButton(String text, ButtonListener listener) {
            dialog.setExtraButton(text, listener);
            return this;
        }

        public Builder useAutoLink() {
            dialog.useAutoLink();
            return this;
        }
    }

    public enum AlertLevel {
        ALERT,
        INFO
    }

    public interface ButtonListener {
        void onClick();
    }

}
