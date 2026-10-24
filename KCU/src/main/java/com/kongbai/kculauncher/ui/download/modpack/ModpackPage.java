package com.kongbai.kculauncher.ui.download.modpack;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ScrollView;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.ui.KCUPage;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUEditText;
import com.kongbai.kculauncher.library.component.view.KCULinearLayout;
import com.kongbai.kculauncher.library.component.view.KCUProgressBar;
import com.kongbai.kculauncher.library.component.view.KCUTextView;

public abstract class ModpackPage extends KCUPage implements View.OnClickListener {

    protected final Profile profile;

    protected KCUProgressBar progressBar;
    protected KCULinearLayout layout;
    protected ScrollView infoLayout;

    protected KCUEditText editText;
    protected KCUTextView name;
    protected KCUTextView version;
    protected KCUTextView author;

    protected KCUButton install;
    protected KCUButton describe;

    public ModpackPage(Context context, int id, int resId, Profile profile) {
        super(context, id, resId);
        this.profile = profile;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        progressBar = findViewById(R.id.progress);
        layout = findViewById(R.id.layout);
        infoLayout = findViewById(R.id.info_layout);
        editText = findViewById(R.id.name);
        name = findViewById(R.id.modpack_name);
        version = findViewById(R.id.version);
        author = findViewById(R.id.author);
        install = findViewById(R.id.install);
        describe = findViewById(R.id.describe);
        install.setOnClickListener(this);
        describe.setOnClickListener(this);
        ThemeEngine.getInstance().registerEvent(infoLayout, () -> infoLayout.setBackgroundTintList(new ColorStateList(new int[][]{{}}, new int[]{ThemeEngine.getInstance().getTheme().getLtColor()})));
    }



    protected abstract void onInstall();

    protected abstract void onDescribe();

    @Override
    public void onClick(View v) {
        if (v == install) {
            KCUAlertDialog dialog = new KCUAlertDialog(getContext());
            dialog.setTitle(R.string.modpack_download_warn_title);
            dialog.setMessage(getContext().getString(R.string.modpack_download_warn_msg));
            dialog.setCanceledOnTouchOutside(false);
            dialog.setPositiveButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_positive), this::onInstall);
            dialog.setNegativeButton(getContext().getString(com.kongbai.kculauncher.R.string.dialog_negative), null);
            dialog.show();
        }
        if (v == describe) {
            onDescribe();
        }
    }
}
