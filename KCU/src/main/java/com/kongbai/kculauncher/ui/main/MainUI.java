package com.kongbai.kculauncher.ui.main;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.util.KcuVersionMeta;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.setting.Profiles;

import com.kongbai.kculauncher.library.component.ui.KCUCommonUI;

import com.kongbai.kculauncher.library.component.view.KCUTextView;

public class MainUI extends KCUCommonUI {

    private KCUTextView watermark;
    private KCUTextView stageVersion;

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
        if (version == null || version.isEmpty()) {
            stageVersion.setText(getContext().getString(R.string.kcu_stage_version_none));
            return;
        }
        // 导入版本：未探测时显示“未知”，探测到后显示真实版本
        String text = version;
        try {
            java.io.File dir = Profiles.getSelectedProfile().getRepository().getVersionRoot(version);
            if (KcuVersionMeta.file(dir).isFile()) {
                text = KcuVersionMeta.read(dir).display;
            }
        } catch (Throwable ignored) {
        }
        stageVersion.setText(text);
    }

}
