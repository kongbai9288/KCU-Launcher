package com.kongbai.kculauncher.ui.setting;

import android.content.Context;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.setting.Profiles;
import com.kongbai.kculauncher.ui.manage.VersionSettingPage;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.library.component.ui.KCUMultiPageUI;
import com.kongbai.kculauncher.library.component.ui.KCUPage;
import com.kongbai.kculauncher.library.component.view.KCUTabLayout;
import com.kongbai.kculauncher.library.component.view.KCUUILayout;

public class SettingUI extends KCUMultiPageUI {

    public static final int PAGE_ID_SETTING_GAME = 15030;
    public static final int PAGE_ID_SETTING_LAUNCHER = 15031;
    public static final int PAGE_ID_SETTING_PLUGIN = 15032;
    public static final int PAGE_ID_SETTING_ABOUT = 15034;
    public static final int PAGE_ID_SETTING_KCU_DEV = 15035;

    private KCUUILayout container;
    public KCUTabLayout tabLayout;


    public SettingUI(Context context, int id) {
        super(context, id);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        tabLayout = findViewById(R.id.tab_layout);
        container = findViewById(R.id.container);
        setupPages(container, tabLayout);
    }

    @Override
    public int getPageCount() {
        return 5;
    }

    @Override
    public KCUPage createPage(int position) {
        switch (position) {
            case 1:
                return new LauncherSettingPage(getContext(), PAGE_ID_SETTING_LAUNCHER);
            case 2:
                return new PluginManagePage(getContext(), PAGE_ID_SETTING_PLUGIN);
            case 3:
                return new AboutPage(getContext(), PAGE_ID_SETTING_ABOUT);
            case 4:
                return new KcuDevPage(getContext(), PAGE_ID_SETTING_KCU_DEV, R.layout.page_kcu_dev);
            default:
                return new VersionSettingPage(getContext(), PAGE_ID_SETTING_GAME, true);
        }
    }

    @Override
    public String[] getTabTitles() {
        return new String[]{
                getContext().getString(R.string.settings_type_global_manage),
                getContext().getString(R.string.settings_launcher),
                getContext().getString(R.string.settings_plugin),
                getContext().getString(R.string.about),
                getContext().getString(R.string.kcu_dev_title)
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从系统卸载页返回等场景：同步插件管理页的插件列表（仅刷新已创建的页面）
        forEachCreatedPage(page -> {
            if (page instanceof PluginManagePage) {
                ((PluginManagePage) page).onHostResume();
            }
        });
    }

    @Override
    protected void onPageCreated(KCUPage page) {
        if (page instanceof VersionSettingPage) {
            ((VersionSettingPage) page).loadVersion(Profiles.getSelectedProfile(), null);
        }
    }

}
