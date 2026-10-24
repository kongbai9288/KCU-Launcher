package com.kongbai.kculauncher.ui.manage;

import android.content.Context;
import android.view.View;
import android.widget.ListView;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.mod.LocalModFile;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;

import java.util.List;

public class ModRollbackDialog extends KCUDialog implements View.OnClickListener {

    private ListView listView;

    private KCUButton negative;

    public ModRollbackDialog(@NonNull Context context, List<LocalModFile> list, Callback callback) {
        super(context);
        setCancelable(false);
        setContentView(R.layout.dialog_rollback_mod);

        listView = findViewById(R.id.list);
        negative = findViewById(R.id.negative);
        negative.setOnClickListener(this);

        ModOldVersionListAdapter adapter = new ModOldVersionListAdapter(getContext(), this, list, callback);
        listView.setAdapter(adapter);
    }

    @Override
    public void onClick(View v) {
        if (v == negative) {
            dismiss();
        }
    }

    public interface Callback {
        void onOldVersionSelect(LocalModFile localModFile);
    }
}
