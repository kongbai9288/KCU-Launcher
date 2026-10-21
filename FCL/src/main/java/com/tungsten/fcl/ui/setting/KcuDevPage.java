package com.tungsten.fcl.ui.setting;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tungsten.fcl.R;
import com.tungsten.fcl.util.KcuDevMode;
import com.tungsten.fcl.util.KcuUi;
import com.tungsten.fcllibrary.component.ui.FCLPage;
import com.tungsten.fclcore.task.Schedulers;

/**
 * 开发者模式页。
 *
 * 显示开关文件位置与当前状态；令牌校验通过后才允许清除标记。
 */
public class KcuDevPage extends FCLPage implements View.OnClickListener {

    private TextView state;
    private TextView path;
    private EditText token;
    private Button verify;
    private Button clear;
    private TextView result;

    private boolean verified;

    public KcuDevPage(Context context, int id, int resId) {
        super(context, id, resId);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        ScrollView scroll = (ScrollView) findViewById(R.id.kcu_dev_scroll);
        LinearLayout root = KcuUi.root(getContext());
        scroll.removeAllViews();
        scroll.addView(root);

        LinearLayout card = KcuUi.card(getContext());
        card.addView(KcuUi.title(getContext(), getString(R.string.kcu_dev_title)));
        card.addView(KcuUi.gap(getContext(), 12));

        state = KcuUi.body(getContext(), "");
        card.addView(state);

        path = KcuUi.hint(getContext(), "");
        path.setPadding(0, KcuUi.dp(getContext(), 6), 0, 0);
        card.addView(path);

        refreshState();

        Button refresh = KcuUi.secondaryButton(getContext(), getString(R.string.kcu_dev_refresh));
        refresh.setOnClickListener(this);
        card.addView(refresh);

        card.addView(KcuUi.gap(getContext(), 18));
        card.addView(KcuUi.hint(getContext(), getString(R.string.kcu_dev_token_hint)));

        token = KcuUi.field(getContext(), getString(R.string.kcu_dev_token_placeholder));
        token.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = KcuUi.dp(getContext(), 10);
        token.setLayoutParams(lp);
        card.addView(token);

        verify = KcuUi.secondaryButton(getContext(), getString(R.string.kcu_dev_verify));
        verify.setOnClickListener(this);
        card.addView(verify);

        clear = KcuUi.primaryButton(getContext(), getString(R.string.kcu_dev_clear));
        clear.setEnabled(false);
        clear.setOnClickListener(this);
        card.addView(clear);

        result = KcuUi.status(getContext(), "");
        result.setPadding(0, KcuUi.dp(getContext(), 14), 0, 0);
        card.addView(result);

        root.addView(card);
    }

    private void refreshState() {
        boolean enabled = KcuDevMode.isEnabled();
        state.setText(getString(enabled
                ? R.string.kcu_dev_state_on
                : R.string.kcu_dev_state_off));
        try {
            path.setText(KcuDevMode.getFile().getAbsolutePath());
        } catch (Throwable e) {
            path.setText("");
        }
    }

    @Override
    public void onClick(View v) {
        if (v == verify) {
            doVerify();
        } else if (v == clear) {
            doClear();
        } else {
            refreshState();
            setResult(getString(R.string.kcu_dev_refreshed));
        }
    }

    private void doVerify() {
        String value = token.getText() == null ? "" : token.getText().toString();
        if (TextUtils.isEmpty(value.trim())) {
            setResult(getString(R.string.kcu_dev_token_empty));
            return;
        }
        setResult(getString(R.string.kcu_dev_verifying));
        clear.setEnabled(false);
        final String finalValue = value;
        Schedulers.defaultScheduler().execute(() -> {
            boolean ok = KcuDevMode.verifyOwner(finalValue);
            Schedulers.androidUIThread().execute(() -> {
                verified = ok;
                clear.setEnabled(ok);
                setResult(getString(ok
                        ? R.string.kcu_dev_verify_ok
                        : R.string.kcu_dev_verify_fail));
            });
        });
    }

    private void doClear() {
        if (!verified) {
            setResult(getString(R.string.kcu_dev_need_verify));
            return;
        }
        setResult(getString(R.string.kcu_dev_clearing));
        Schedulers.defaultScheduler().execute(() -> {
            int count = KcuDevMode.clearMarks();
            Schedulers.androidUIThread().execute(() -> {
                verified = false;
                clear.setEnabled(false);
                refreshState();
                setResult(getString(R.string.kcu_dev_cleared, count));
            });
        });
    }

    private void setResult(String text) {
        if (result != null) {
            result.setText(text);
        }
    }

    private String getString(int resId) {
        return getContext().getString(resId);
    }

    private String getString(int resId, Object... args) {
        return getContext().getString(resId, args);
    }
}
