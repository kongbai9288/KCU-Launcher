package com.kongbai.kculauncher.ui.controller;

import android.content.Context;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.library.component.ui.KCUMultiPageUI;
import com.kongbai.kculauncher.library.component.ui.KCUPage;
import com.kongbai.kculauncher.library.component.view.KCUUILayout;

public class ControllerUI extends KCUMultiPageUI {

    public static final int PAGE_ID_CONTROLLER_MANAGER = 15040;
    public static final int PAGE_ID_CONTROLLER_REPO = 15041;

    public ControllerUI(Context context, int id) {
        super(context, id);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        KCUUILayout container = findViewById(R.id.container);
        setupPages(container, null);
    }

    @Override
    public int getPageCount() {
        return 2;
    }

    @Override
    public KCUPage createPage(int position) {
        if (position == 1) {
            return new ControllerRepoPage(getContext(), PAGE_ID_CONTROLLER_REPO);
        }
        return new ControllerManagePage(getContext(), PAGE_ID_CONTROLLER_MANAGER);
    }

    @Override
    public void onBackPressed() {
        if (canReturn()) {
            dismissCurrentTempPage();
        } else if (getCurrentPagePosition() == 1) {
            // 仓库页返回管理页
            showPage(0);
        } else {
            super.onBackPressed();
        }
    }

}
