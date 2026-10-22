package com.tungsten.fcl.ui.main;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.tungsten.fcl.R;
import com.tungsten.fcl.setting.Profiles;

import com.tungsten.fcllibrary.component.ui.FCLCommonUI;

import com.tungsten.fcllibrary.component.view.FCLTextView;

public class MainUI extends FCLCommonUI {

    private FCLTextView watermark;
    private FCLTextView stageVersion;

    public MainUI(Context context, int id) {
        super(context, id);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        watermark = findViewById(R.id.watermark);
        watermark.setText(getContext().getString(R.string.app_name) + " " + getContext().getString(R.string.app_version));

        stageVersion = findViewById(R.id.stage_version);
        refreshSelectedVersion();

        // 首页回到前台：选中版本可能在别处被改过，按当前值重画
        getContentView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                refreshSelectedVersion();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
            }
        });
    }

    /**
     * 首页展示当前选中版本。
     * 选中的版本可能在别处变更（版本页切换、启动参数改动），首页回到前台时按当前值重画。
     */
    public void refreshSelectedVersion() {
        if (stageVersion == null) {
            return;
        }
        String version = Profiles.getSelectedVersion();
        stageVersion.setText(version == null || version.isEmpty()
                ? getContext().getString(R.string.kcu_stage_version_none)
                : version);
    }

}
