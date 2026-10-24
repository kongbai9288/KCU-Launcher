package com.kongbai.kculauncher.ui.manage;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.activity.MainActivity;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.setting.Profiles;
import com.kongbai.kculauncher.util.WeakListenerHolder;
import com.kongbai.kculauncher.core.event.EventBus;
import com.kongbai.kculauncher.core.event.EventPriority;
import com.kongbai.kculauncher.core.event.RefreshedVersionsEvent;
import com.kongbai.kculauncher.core.fakefx.beans.property.ObjectProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleObjectProperty;
import com.kongbai.kculauncher.core.game.GameRepository;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.library.component.ui.KCUMultiPageUI;
import com.kongbai.kculauncher.library.component.ui.KCUPage;
import com.kongbai.kculauncher.library.component.view.KCUTabLayout;
import com.kongbai.kculauncher.library.component.view.KCUUILayout;

import java.util.Optional;

public class ManageUI extends KCUMultiPageUI {

    public static final int PAGE_ID_MANAGE_MANAGE = 15000;
    public static final int PAGE_ID_MANAGE_SETTING = 15001;
    public static final int PAGE_ID_MANAGE_INSTALL = 15002;
    public static final int PAGE_ID_MANAGE_MOD = 15003;
    public static final int PAGE_ID_MANAGE_WORLD = 15004;
    public static final int PAGE_ID_MANAGE_RESOURCE_PACK = 15005;
    public static final int PAGE_ID_MANAGE_SHADER_PACK = 15006;

    private final ObjectProperty<Profile.ProfileVersion> version = new SimpleObjectProperty<>();
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();
    public String preferredVersionName = null;
    public KCUTabLayout tabLayout;

    /**
     * 切换 Profile 时重新加载版本设置（页面保留时不经过 onSelect/setVersion 的兜底）
     */
    private final Runnable profileListener = () -> {
        Profile profile = Profiles.getSelectedProfile();
        setVersion(profile.getSelectedVersion(), profile);
    };

    public ManageUI(Context context, int id) {
        super(context, id);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        tabLayout = findViewById(R.id.tab_layout);
        KCUUILayout container = findViewById(R.id.container);
        setupPages(container, tabLayout);

        listenerHolder.add(EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).registerWeak(event -> checkSelectedVersion(), EventPriority.HIGHEST));

        // 切换 Profile 时刷新版本设置页（基于 StateFlow），UI 被 ViewPager 回收时注销监听
        Profiles.addSelectedProfileListener(profileListener);
        getContentView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                // 页面切走再切回时恢复监听并立即刷新（后台可能已切换 Profile）
                Profiles.removeSelectedProfileListener(profileListener);
                Profiles.addSelectedProfileListener(profileListener);
                profileListener.run();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                Profiles.removeSelectedProfileListener(profileListener);
            }
        });
    }

    @Override
    public int getPageCount() {
        return 7;
    }

    @Override
    public KCUPage createPage(int position) {
        return switch (position) {
            case 1 ->
                    new ManagePage(getContext(), PAGE_ID_MANAGE_MANAGE);
            case 2 ->
                    new InstallerListPage(getContext(), PAGE_ID_MANAGE_INSTALL);
            case 3 -> new ModListPage(getContext(), PAGE_ID_MANAGE_MOD);
            case 4 ->
                    new WorldListPage(getContext(), PAGE_ID_MANAGE_WORLD);
            case 5 ->
                    new ResourcePackListPage(getContext(), PAGE_ID_MANAGE_RESOURCE_PACK);
            case 6 ->
                    new ShaderPackListPage(getContext(), PAGE_ID_MANAGE_SHADER_PACK);
            default ->
                    new VersionSettingPage(getContext(), PAGE_ID_MANAGE_SETTING, false);
        };
    }

    @Override
    public String[] getTabTitles() {
        return new String[]{
                getContext().getString(R.string.settings_game),
                getContext().getString(R.string.manage),
                getContext().getString(R.string.settings_tabs_installers),
                getContext().getString(R.string.mods_manage),
                getContext().getString(R.string.world_manage),
                getContext().getString(R.string.resourcepack_manage),
                getContext().getString(R.string.shaderpack_manage)
        };
    }

    @Override
    protected void onPageCreated(KCUPage page) {
        // 未 setVersion 时跳过（版本校验由 RefreshedVersionsEvent 事件兜底）
        if (page instanceof VersionLoadable && getProfile() != null) {
            ((VersionLoadable) page).loadVersion(getProfile(), getVersion());
        }
    }


    private void checkSelectedVersion() {
        Schedulers.androidUIThread().execute(() -> {
            if (this.version.get() == null) return;
            GameRepository repository = this.version.get().getProfile().getRepository();
            if (!repository.hasVersion(this.version.get().getVersion())) {
                if (preferredVersionName != null) {
                    loadVersion(preferredVersionName, this.version.get().getProfile());
                } else if (isShowing()) {
                    MainActivity.getInstance().refreshMenuView(null);
                    MainActivity.getInstance().binding.home.setSelected(true);
                }
            }
        });
    }

    public void setVersion(String version, Profile profile) {
        this.version.set(new Profile.ProfileVersion(profile, version));
        // 分发版本到已创建页面
        forEachCreatedPage(page -> {
            if (page instanceof VersionLoadable) {
                ((VersionLoadable) page).loadVersion(profile, version);
            }
        });
    }

    public void loadVersion(String version, Profile profile) {
        setVersion(version, profile);
        preferredVersionName = version;

        dismissAllTempPages();
        forEachCreatedPage(page -> {
            if (page instanceof VersionLoadable) {
                ((VersionLoadable) page).loadVersion(profile, version);
            }
        });
    }

    /**
     * 游戏目录变更时刷新模组/世界列表（原 ManagePageManager.onRunDirectoryChange）
     */
    public void onRunDirectoryChange(Profile profile, String version) {
        KCUPage modPage = getPage(3);
        if (modPage instanceof VersionLoadable) {
            ((VersionLoadable) modPage).loadVersion(profile, version);
        }
        KCUPage worldPage = getPage(4);
        if (worldPage instanceof VersionLoadable) {
            ((VersionLoadable) worldPage).loadVersion(profile, version);
        }
        KCUPage resourcePackPage = getPage(5);
        if (resourcePackPage instanceof VersionLoadable) {
            ((VersionLoadable) resourcePackPage).loadVersion(profile, version);
        }
        KCUPage shaderPackPage = getPage(6);
        if (shaderPackPage instanceof VersionLoadable) {
            ((VersionLoadable) shaderPackPage).loadVersion(profile, version);
        }
    }

    public Profile getProfile() {
        return Optional.ofNullable(version.get()).map(Profile.ProfileVersion::getProfile).orElse(null);
    }

    public String getVersion() {
        return Optional.ofNullable(version.get()).map(Profile.ProfileVersion::getVersion).orElse(null);
    }

    public interface VersionLoadable {
        void loadVersion(Profile profile, String version);
    }
}
