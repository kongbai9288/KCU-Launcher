package com.kongbai.kculauncher.ui.version;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.game.KCUGameRepository;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.core.util.FutureCallback;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUCheckBox;
import com.kongbai.kculauncher.library.component.view.KCUEditText;

import java.util.ArrayList;
import java.util.Objects;

public class DuplicateVersionDialog extends KCUDialog implements View.OnClickListener {

    private final Profile profile;
    private final String version;
    private final FutureCallback<ArrayList<Object>> callback;

    private KCUEditText editText;
    private KCUCheckBox checkBox;
    private KCUButton positive;
    private KCUButton negative;

    public DuplicateVersionDialog(@NonNull Context context, Profile profile, String version, FutureCallback<ArrayList<Object>> callback) {
        super(context);
        this.profile = profile;
        this.version = version;
        this.callback = callback;
        setContentView(R.layout.dialog_duplicate_version);
        setCancelable(false);

        editText = findViewById(R.id.new_name);
        checkBox = findViewById(R.id.check_save);
        positive = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);

        editText.setText(version);
        positive.setOnClickListener(this);
        negative.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view == positive) {
            String newVersionName = Objects.requireNonNull(editText.getText()).toString();
            if (StringUtils.isBlank(newVersionName)) {
                Toast.makeText(getContext(), getContext().getString(R.string.input_not_empty), Toast.LENGTH_SHORT).show();
            } else if (profile.getRepository().versionIdConflicts(newVersionName)) {
                Toast.makeText(getContext(), getContext().getString(R.string.install_new_game_already_exists), Toast.LENGTH_SHORT).show();
            } else if (!KCUGameRepository.isValidVersionId(newVersionName)) {
                Toast.makeText(getContext(), getContext().getString(R.string.install_new_game_malformed), Toast.LENGTH_SHORT).show();
            } else {
                positive.setEnabled(false);
                negative.setEnabled(false);
                ArrayList<Object> res = new ArrayList<>();
                res.add(newVersionName);
                res.add(checkBox.isChecked());
                callback.call(res, () -> {
                    positive.setEnabled(true);
                    negative.setEnabled(true);
                    dismiss();
                }, msg -> {
                    positive.setEnabled(true);
                    negative.setEnabled(true);
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                });
            }
        }
        if (view == negative) {
            dismiss();
        }
    }
}
