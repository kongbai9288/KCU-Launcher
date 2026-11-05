package com.kongbai.kculauncher.ui.main;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.util.KcuVersionMeta;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.setting.Profiles;
import com.kongbai.kculauncher.ui.UIManager;

import com.kongbai.kculauncher.library.component.ui.KCUCommonUI;
import com.kongbai.kculauncher.library.component.view.KCUTextView;

/**
 * 首页舞台：按移动端揉纸试卷 UI 重建。
 * 0.99.1 起中央舞台有首页 / 衣柜两个视图，空状态、公告卡、8 格皮肤都是可点的，
 * 每个点击都有可见反馈，不再是「有入口、点不动」的静态摆设。
 */
public class MainUI extends KCUCommonUI {

    private KCUTextView watermark;
    private KCUTextView stageVersion;

    private View viewHome;
    private View viewCloset;
    private View emptyState;
    private View noticeCard;
    private View tabHome;
    private View tabCloset;
    private KCUTextView noticeBody;
    private KCUTextView noticeHint;
    private KCUTextView closetCurrent;

    private final View[] skinCells = new View[8];
    private final int[] skinNames = new int[] {
            R.string.kcu_skin_1, R.string.kcu_skin_2, R.string.kcu_skin_3, R.string.kcu_skin_4,
            R.string.kcu_skin_5, R.string.kcu_skin_6, R.string.kcu_skin_7, R.string.kcu_skin_8
    };
    private boolean noticeExpanded = true;
    /** 玩家点过「查看示例数据」后，即使仍没有版本也保持公告卡可见 */
    private boolean showSample = false;

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

        bindStage();

        // 首页回到前台：选中版本可能在别处被改过，按当前值重画
        getContentView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                refreshSelectedVersion();
                applyEmptyState();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
            }
        });
    }

    private void bindStage() {
        viewHome = findViewById(R.id.kcu_view_home);
        viewCloset = findViewById(R.id.kcu_view_closet);
        emptyState = findViewById(R.id.kcu_empty_state);
        noticeCard = findViewById(R.id.kcu_notice_card);
        tabHome = findViewById(R.id.kcu_tab_home);
        tabCloset = findViewById(R.id.kcu_tab_closet);
        noticeBody = findViewById(R.id.kcu_notice_body);
        noticeHint = findViewById(R.id.kcu_notice_hint);
        closetCurrent = findViewById(R.id.kcu_closet_current);

        tabHome.setOnClickListener(v -> showStage(true));
        tabCloset.setOnClickListener(v -> showStage(false));

        // 前往下载：走和左栏 download 菜单同一条页面切换路径
        findViewById(R.id.kcu_btn_go_download).setOnClickListener(v -> openDownload());
        // 查看示例数据：空状态切到公告卡（可见反馈）
        findViewById(R.id.kcu_btn_skip_empty).setOnClickListener(v -> {
            showSample = true;
            applyEmptyState();
        });
        // 公告卡：默认全文同屏显示，点击在全文 / 折叠之间切换
        noticeCard.setOnClickListener(v -> toggleNotice());

        skinCells[0] = findViewById(R.id.kcu_skin_cell_1);
        skinCells[1] = findViewById(R.id.kcu_skin_cell_2);
        skinCells[2] = findViewById(R.id.kcu_skin_cell_3);
        skinCells[3] = findViewById(R.id.kcu_skin_cell_4);
        skinCells[4] = findViewById(R.id.kcu_skin_cell_5);
        skinCells[5] = findViewById(R.id.kcu_skin_cell_6);
        skinCells[6] = findViewById(R.id.kcu_skin_cell_7);
        skinCells[7] = findViewById(R.id.kcu_skin_cell_8);
        for (int i = 0; i < skinCells.length; i++) {
            final int index = i;
            skinCells[i].setOnClickListener(v -> selectSkin(index));
        }

        showStage(true);
        selectSkin(0);
        applyNoticeState();
        applyEmptyState();
    }

    /** 没有选中版本时显示空状态，有版本时显示公告卡；重回前台会按最新值重判。 */
    private void applyEmptyState() {
        String version = Profiles.getSelectedVersion();
        boolean hasVersion = version != null && !version.isEmpty();
        boolean showNotice = hasVersion || showSample;
        emptyState.setVisibility(showNotice ? View.GONE : View.VISIBLE);
        noticeCard.setVisibility(showNotice ? View.VISIBLE : View.GONE);
    }

    private void showStage(boolean home) {
        viewHome.setVisibility(home ? View.VISIBLE : View.GONE);
        viewCloset.setVisibility(home ? View.GONE : View.VISIBLE);
        tabHome.setBackgroundResource(home ? R.drawable.bg_kcu_tab_active : R.drawable.bg_kcu_tab_idle);
        tabCloset.setBackgroundResource(home ? R.drawable.bg_kcu_tab_idle : R.drawable.bg_kcu_tab_active);
        ((KCUTextView) tabHome).setTextColor(getContext().getResources().getColor(
                home ? R.color.kcu_accent : R.color.kcu_ink_2));
        ((KCUTextView) tabCloset).setTextColor(getContext().getResources().getColor(
                home ? R.color.kcu_ink_2 : R.color.kcu_accent));
    }

    private void toggleNotice() {
        noticeExpanded = !noticeExpanded;
        applyNoticeState();
    }

    private void applyNoticeState() {
        noticeBody.setMaxLines(noticeExpanded ? Integer.MAX_VALUE : 2);
        noticeHint.setText(getContext().getString(noticeExpanded
                ? R.string.kcu_notice_collapse_hint : R.string.kcu_notice_expand_hint));
    }

    private void selectSkin(int index) {
        for (int i = 0; i < skinCells.length; i++) {
            skinCells[i].setBackgroundResource(i == index
                    ? R.drawable.bg_kcu_skin_cell_active : R.drawable.bg_kcu_skin_cell);
        }
        closetCurrent.setText(getContext().getString(R.string.kcu_closet_current,
                getContext().getString(skinNames[index])));
    }

    private void openDownload() {
        try {
            UIManager.getInstance().switchUI(UIManager.getInstance().getDownloadUI());
        } catch (Throwable ignored) {
            // 兜底：模拟点击左栏下载菜单，避免按钮变成「点了没反应」
            Context c = getContext();
            if (c instanceof Activity) {
                View download = ((Activity) c).findViewById(R.id.download);
                if (download != null) {
                    download.performClick();
                }
            }
        }
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
        // 导入版本：未探测时显示「未知」，探测到后显示真实版本
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
