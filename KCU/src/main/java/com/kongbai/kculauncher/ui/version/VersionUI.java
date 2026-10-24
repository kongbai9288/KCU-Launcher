package com.kongbai.kculauncher.ui.version;

import android.content.Context;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.library.component.ui.KCUMultiPageUI;
import com.kongbai.kculauncher.library.component.ui.KCUPage;
import com.kongbai.kculauncher.library.component.view.KCUUILayout;

public class VersionUI extends KCUMultiPageUI {

    public static final int PAGE_ID_VERSION_LIST = 15020;

    private KCUUILayout container;

    public VersionUI(Context context, int id) {
        super(context, id);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        container = findViewById(R.id.container);
        setupPages(container, null);
    }

    @Override
    public int getPageCount() {
        return 1;
    }

    @Override
    public KCUPage createPage(int position) {
        return new VersionListPage(getContext(), PAGE_ID_VERSION_LIST);
    }

}
