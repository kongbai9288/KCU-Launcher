package com.kongbai.kculauncher.ui.version;

import android.content.Context;
import com.kongbai.kculauncher.ui.UIManager;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.activity.MainActivity;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.setting.Profiles;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUEditText;
import com.kongbai.kculauncher.library.component.view.KCUImageButton;
import com.kongbai.kculauncher.library.component.view.KCUTextView;

import java.io.File;

public class AddProfileDialog extends KCUDialog implements View.OnClickListener {

    private final KCUEditText editText;
    private final KCUTextView pathText;
    private final KCUImageButton editPath;
    private final KCUButton positive;
    private final KCUButton negative;

    public AddProfileDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_add_profile);
        setCancelable(false);
        editText = findViewById(R.id.name);
        pathText = findViewById(R.id.path);
        editPath = findViewById(R.id.edit);
        positive = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);
        editPath.setOnClickListener(this);
        positive.setOnClickListener(this);
        negative.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view == editPath) {
            MainActivity.getInstance().fileLauncher.launchSingleSelection(null, null, true, files -> {
                if (files == null) return;
                pathText.setText(files.get(0).getPath());
            });
        }
        if (view == positive) {
            if (StringUtils.isBlank(editText.getText().toString()) || StringUtils.isBlank(pathText.getText().toString())) {
                Toast.makeText(getContext(), getContext().getString(R.string.input_not_empty), Toast.LENGTH_SHORT).show();
            } else if (Profiles.getProfiles().stream().anyMatch(profile -> profile.getName().equals(editText.getText().toString()))) {
                Toast.makeText(getContext(), getContext().getString(R.string.profile_already_exist), Toast.LENGTH_SHORT).show();
            } else {
                Profiles.addProfile(new Profile(editText.getText().toString(), new File(pathText.getText().toString())));
                ((VersionListPage) UIManager.getInstance().getVersionUI().getPage(0)).refreshProfile();
                dismiss();
            }
        }
        if (view == negative) {
            dismiss();
        }
    }
}
