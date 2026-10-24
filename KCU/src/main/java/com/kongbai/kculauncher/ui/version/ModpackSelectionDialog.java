package com.kongbai.kculauncher.ui.version;

import android.content.Context;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;

public class ModpackSelectionDialog extends KCUDialog {

    public ModpackSelectionDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_modpack_selection);
        setCancelable(false);
    }
}
