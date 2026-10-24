package com.kongbai.kculauncher.ui.download.modpack;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUEditText;

public class ModpackUrlDialog extends KCUDialog implements View.OnClickListener {

    private final Callback callback;

    private KCUEditText editText;
    private KCUButton positive;
    private KCUButton negative;

    public ModpackUrlDialog(@NonNull Context context, Callback callback) {
        super(context);
        this.callback = callback;
        setCancelable(false);
        setContentView(R.layout.dialog_modpack_url);

        editText = findViewById(R.id.url);

        positive = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);
        positive.setOnClickListener(this);
        negative.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == positive) {
            if (StringUtils.isNotBlank(editText.getText().toString())) {
                callback.onPositive(editText.getText().toString());
                dismiss();
            }
        }
        if (v == negative) {
            dismiss();
        }
    }

    public interface Callback {
        void onPositive(String urlString);
    }
}
