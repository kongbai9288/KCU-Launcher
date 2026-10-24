package com.kongbai.kculauncher.ui.controller;

import android.content.Context;
import android.view.View;
import android.widget.ListView;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.control.download.ControllerVersion;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;

import java.util.ArrayList;

public class OldVersionDialog extends KCUDialog implements View.OnClickListener {

    private ListView listView;
    private KCUButton cancel;

    public OldVersionDialog(@NonNull Context context, ArrayList<ControllerVersion.VersionInfo> versionInfos, Callback callback) {
        super(context);
        setCancelable(false);
        setContentView(R.layout.dialog_download_controllor);

        listView = findViewById(R.id.list);
        HistoricalListAdapter adapter = new HistoricalListAdapter(getContext(), versionInfos, versionInfo -> {
            callback.download(versionInfo.getVersionCode());
            dismiss();
        });
        listView.setAdapter(adapter);

        cancel = findViewById(R.id.negative);
        cancel.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view == cancel) {
            dismiss();
        }
    }

    public interface Callback {
        void download(int versionCode);
    }
}
