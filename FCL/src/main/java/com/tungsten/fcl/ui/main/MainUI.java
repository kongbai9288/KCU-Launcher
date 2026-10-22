package com.tungsten.fcl.ui.main;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.mio.skin.AnimationDialog;
import com.mio.skin.SkinAnimations;
import com.mio.skin.SkinRenderer;
import com.mio.skin.SkinTextureLoader;
import com.mio.skin.SkinViewer;

import static com.mio.skin.SkinAnimationsKt.restoreSkinSettings;
import static com.mio.skin.SkinAnimationsKt.saveSkinSettings;
import com.tungsten.fcl.R;
import com.tungsten.fcl.setting.Accounts;
import com.tungsten.fclcore.auth.Account;
import com.tungsten.fclcore.fakefx.beans.InvalidationListener;
import com.tungsten.fclcore.task.Schedulers;

import com.tungsten.fcllibrary.component.theme.ThemeEngine;
import com.tungsten.fcllibrary.component.ui.FCLCommonUI;

import com.tungsten.fcllibrary.component.view.FCLTextView;

public class MainUI extends FCLCommonUI {

    private FCLTextView watermark;

    private SkinViewer skinViewer;
    private SkinRenderer renderer;
    private SkinTextureLoader skinLoader;

    /** 选中账户变化时重载皮肤（attach 时注册、detach 时注销） */
    private final InvalidationListener accountListener = o ->
            skinLoader.load(Accounts.getSelectedAccount(), false);

    public MainUI(Context context, int id) {
        super(context, id);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        watermark = findViewById(R.id.watermark);
        watermark.setText(getContext().getString(R.string.app_name) + " " + getContext().getString(R.string.app_version));

        skinViewer = findViewById(R.id.skin_viewer);
        renderer = new SkinRenderer(getContext());
        skinViewer.setRenderer(renderer, 5f);
        skinLoader = new SkinTextureLoader(renderer);
        skinLoader.load(Accounts.getSelectedAccount(), false);
        // 恢复上次选择的动画与 3D 皮肤层开关
        restoreSkinSettings(getContext(), renderer);
        // 双击模型弹出皮肤模型设置窗口
        skinViewer.setOnDoubleClick(() -> {
            Context context = getContext();
            if (context instanceof Activity && !((Activity) context).isDestroyed() && !((Activity) context).isFinishing()) {
                new AnimationDialog(context, renderer.getAnimationId(), renderer.getSolidLayerEnabled(), renderer.getUpperBodySeparated(), clipId -> {
                    renderer.playAnimation(clipId);
                    saveSkinSettings(context, renderer);
                }, enabled -> {
                    renderer.setSolidLayerEnabled(enabled);
                    saveSkinSettings(context, renderer);
                }, separated -> {
                    renderer.setUpperBodySeparated(separated);
                    saveSkinSettings(context, renderer);
                }).show();
            }
        });

        // 皮肤渲染随页面挂载/回收恢复与暂停（替代原 onStart/onStop 生命周期）
        getContentView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                if (skinViewer != null) {
                    if (!ThemeEngine.getInstance().getTheme().isCloseSkinModel()) {
                        skinViewer.setVisibility(View.VISIBLE);
                        skinViewer.onResume();
                        // 纹理由渲染线程 onSurfaceCreated 从 renderer.texture 自行重建，
                        // 此处不重喂纹理；同账户已在 onCreate 加载过，load 会自动跳过
                    } else {
                        skinViewer.onPause();
                        skinViewer.setVisibility(View.GONE);
                    }
                }
                if (skinLoader != null) {
                    // detach 期间选中的账户可能已切换（监听已注销），重新对齐一次
                    Accounts.selectedAccountProperty().addListener(accountListener);
                    skinLoader.load(Accounts.getSelectedAccount(), false);
                }
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                Accounts.selectedAccountProperty().removeListener(accountListener);
                if (skinLoader != null) {
                    skinLoader.release();
                }
                if (skinViewer != null) {
                    skinViewer.onPause();
                    skinViewer.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (skinViewer != null) {
            skinViewer.onPause();
            skinViewer.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (skinViewer != null && isShowing() && !ThemeEngine.getInstance().getTheme().isCloseSkinModel()) {
            skinViewer.setVisibility(View.VISIBLE);
            skinViewer.onResume();
        }
    }

    public void refreshSkin(Account account) {
        Schedulers.androidUIThread().execute(() -> {
            if (skinLoader != null) {
                skinLoader.load(account, true);
            }
        });
    }

}
