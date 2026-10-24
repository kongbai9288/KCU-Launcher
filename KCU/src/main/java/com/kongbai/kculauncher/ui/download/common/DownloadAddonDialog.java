package com.kongbai.kculauncher.ui.download.common;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.util.platform.OperatingSystem;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUEditText;

public class DownloadAddonDialog extends KCUDialog implements View.OnClickListener {

    private final Callback callback;

    private KCUEditText editText;
    private KCUButton positive;
    private KCUButton negative;

    public DownloadAddonDialog(@NonNull Context context, String name, Callback callback) {
        super(context);
        this.callback = callback;
        setCancelable(false);
        setContentView(R.layout.dialog_download_addon);

        editText = findViewById(R.id.name);
        editText.setText(name);

        positive = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);
        positive.setOnClickListener(this);
        negative.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == positive) {
            if (!OperatingSystem.isNameValid(editText.getText().toString())) {
                Toast.makeText(getContext(), getContext().getString(R.string.install_new_game_malformed), Toast.LENGTH_SHORT).show();
            } else {
                callback.onPositive(editText.getText().toString());
                dismiss();
            }
        }
        if (v == negative) {
            dismiss();
        }
    }

    public interface Callback {
        void onPositive(String name);
    }
}
