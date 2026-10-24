package com.kongbai.kculauncher.ui.version;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.util.FutureCallback;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUEditText;

import java.util.concurrent.CompletableFuture;

public class RenameVersionDialog extends KCUDialog implements View.OnClickListener {

    private final FutureCallback<String> callback;
    private final CompletableFuture<String> future = new CompletableFuture<>();

    private KCUEditText editText;
    private KCUButton positive;
    private KCUButton negative;

    public RenameVersionDialog(@NonNull Context context, String oldName, FutureCallback<String> callback) {
        super(context);
        setContentView(R.layout.dialog_rename_version);
        setCancelable(false);
        this.callback = callback;
        editText = findViewById(R.id.new_name);
        positive = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);
        positive.setOnClickListener(this);
        negative.setOnClickListener(this);
        editText.setText(oldName);
    }

    @Override
    public void onClick(View view) {
        if (view == positive) {
            positive.setEnabled(false);
            negative.setEnabled(false);
            callback.call(editText.getText().toString(), () -> {
                positive.setEnabled(true);
                negative.setEnabled(true);
                future.complete(editText.getText().toString());
                dismiss();
            }, msg -> {
                positive.setEnabled(true);
                negative.setEnabled(true);
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            });
        }
        if (view == negative) {
            dismiss();
        }
    }

    public CompletableFuture<String> getFuture() {
        return future;
    }
}
