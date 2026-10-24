package com.kongbai.kculauncher.control;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.control.data.ControlViewGroup;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUEditText;
import com.kongbai.kculauncher.library.component.view.KCUSpinner;

import java.util.ArrayList;
import java.util.Objects;

public class EditViewGroupDialog extends KCUDialog implements View.OnClickListener {

    private final GameMenu menu;
    private final ControlViewGroup viewGroup;
    private final Callback callback;

    private KCUEditText editText;
    private KCUSpinner<String> visibilitySpinner;

    private KCUButton positive;
    private KCUButton negative;

    public interface Callback {
        void onPositive(String name, ControlViewGroup.Visibility visibility);
    }

    public EditViewGroupDialog(@NonNull Context context, GameMenu menu, ControlViewGroup viewGroup, Callback callback) {
        super(context);
        this.menu = menu;
        this.viewGroup = viewGroup;
        this.callback = callback;
        setCancelable(false);
        setContentView(R.layout.dialog_edit_view_group);

        editText = findViewById(R.id.name);
        visibilitySpinner = findViewById(R.id.visibility);
        ArrayList<String> visibilityString = new ArrayList<>();
        visibilityString.add(getContext().getString(R.string.menu_control_view_group_visible));
        visibilityString.add(getContext().getString(R.string.menu_control_view_group_invisible));
        visibilitySpinner.setItems(visibilityString);

        editText.setText(viewGroup.getName());
        visibilitySpinner.setSelection(viewGroup.getVisibility() == ControlViewGroup.Visibility.VISIBLE ? 0 : 1);

        positive = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);
        positive.setOnClickListener(this);
        negative.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == positive) {
            if (menu.getController().viewGroups().stream().anyMatch(it -> it.getName().equals(Objects.requireNonNull(editText.getText()).toString()) && !viewGroup.getName().equals(editText.getText().toString()))) {
                Toast.makeText(getContext(), getContext().getString(R.string.menu_control_view_group_exist), Toast.LENGTH_SHORT).show();
            } else if (StringUtils.isBlank(Objects.requireNonNull(editText.getText()).toString())) {
                Toast.makeText(getContext(), getContext().getString(R.string.menu_control_view_group_empty), Toast.LENGTH_SHORT).show();
            } else {
                dismiss();
                callback.onPositive(editText.getText().toString(), visibilitySpinner.getSelectedIndex() == 0 ? ControlViewGroup.Visibility.VISIBLE : ControlViewGroup.Visibility.INVISIBLE);
            }
        }
        if (v == negative) {
            dismiss();
        }
    }
}
