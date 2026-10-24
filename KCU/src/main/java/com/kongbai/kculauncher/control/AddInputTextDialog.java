package com.kongbai.kculauncher.control;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.control.data.QuickInputTexts;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUEditText;

import java.util.Objects;

public class AddInputTextDialog extends KCUDialog implements View.OnClickListener {

    private final Callback callback;

    private KCUButton positive;
    private KCUButton negative;
    private KCUEditText editText;
    private KCUEditText remarks;

    public interface Callback {
        void onTextAdd();
    }

    public AddInputTextDialog(@NonNull Context context, Callback callback) {
        super(context);
        this.callback = callback;
        setCancelable(false);
        setContentView(R.layout.dialog_add_input_text);

        positive = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);
        positive.setOnClickListener(this);
        negative.setOnClickListener(this);

        editText = findViewById(R.id.text);
        remarks = findViewById(R.id.remarks);
    }

    @Override
    public void onClick(View v) {
        if (v == positive) {
            if (StringUtils.isBlank(Objects.requireNonNull(editText.getText()).toString())) {
                Toast.makeText(getContext(), getContext().getString(R.string.quick_input_empty), Toast.LENGTH_SHORT).show();
            } else if (QuickInputTexts.getInputTexts().contains(editText.getText().toString())) {
                Toast.makeText(getContext(), getContext().getString(R.string.quick_input_exist), Toast.LENGTH_SHORT).show();
            } else {
                String remarksText = Objects.requireNonNull(remarks.getText()).toString();
                QuickInputTexts.addInputText(StringUtils.isNotBlank(remarksText) ? remarksText + "&*&" + editText.getText().toString() : editText.getText().toString());
                callback.onTextAdd();
                dismiss();
            }
        }
        if (v == negative) {
            dismiss();
        }
    }
}
