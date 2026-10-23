package com.tungsten.fcl.util;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ScrollView;

import com.mio.JavaManager;
import com.tungsten.fcl.R;
import com.tungsten.fcl.setting.Profile;
import com.tungsten.fclcore.game.JavaVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入版本的启动前处理。
 *
 * 只对带 kcu_meta.json 的版本生效，正常下载的版本不进入本流程，
 * 因此不会改变原有启动路径。
 */
public final class KcuLaunchGate {

    private KcuLaunchGate() {
    }

    /**
     * 需要时先让用户选定运行时，再继续启动。
     * 已选过或不是导入版本时直接放行。
     */
    public static void prepare(Context context, Profile profile, String id, Runnable proceed) {
        try {
            File versionDir = new File(profile.getRepository().getVersionRoot(id));
            KcuVersionMeta.Meta meta = KcuVersionMeta.read(versionDir);
            if (!KcuVersionMeta.file(versionDir).isFile()) {
                proceed.run();
                return;
            }
            if (meta.java == null || meta.java.isEmpty()) {
                showJavaPicker(context, profile, id, versionDir, meta, proceed);
                return;
            }
            refreshDisplay(profile, id, versionDir, meta);
            proceed.run();
        } catch (Throwable ignored) {
            // 任何异常都放行，不阻断启动
            proceed.run();
        }
    }

    private static void showJavaPicker(Context context, Profile profile, String id,
                                       File versionDir, KcuVersionMeta.Meta meta, Runnable proceed) {
        List<JavaVersion> candidates = new ArrayList<>();
        try {
            for (JavaVersion java : JavaManager.getJavaList()) {
                if (java != null && !"Auto".equals(java.getName())) {
                    candidates.add(java);
                }
            }
        } catch (Throwable ignored) {
        }
        if (candidates.isEmpty()) {
            refreshDisplay(profile, id, versionDir, meta);
            proceed.run();
            return;
        }

        ScrollView scroll = new ScrollView(context);
        android.widget.LinearLayout root = KcuUi.root(context);
        android.widget.LinearLayout card = KcuUi.card(context);
        card.addView(KcuUi.title(context, context.getString(R.string.kcu_pick_java_title)));
        card.addView(KcuUi.gap(context, 8));
        card.addView(KcuUi.body(context, context.getString(R.string.kcu_pick_java_hint)));
        card.addView(KcuUi.gap(context, 16));

        AlertDialog dialog = new AlertDialog.Builder(context).setView(scroll).create();
        for (JavaVersion java : candidates) {
            android.widget.Button button = KcuUi.secondaryButton(context, java.getName());
            button.setOnClickListener(v -> {
                meta.java = java.getName();
                KcuVersionMeta.write(versionDir, meta);
                try {
                    profile.getRepository().getVersionSetting(id).setJava(java.getName());
                    profile.getRepository().saveVersionSetting(id);
                } catch (Throwable ignored) {
                }
                dialog.dismiss();
                refreshDisplay(profile, id, versionDir, meta);
                proceed.run();
            });
            card.addView(button);
            card.addView(KcuUi.gap(context, 8));
        }
        root.addView(card);
        scroll.addView(root);
        dialog.show();
    }

    /** 已探测过就跳过；未探测过则试一次，成功则写回 meta 与版本 JSON */
    private static void refreshDisplay(Profile profile, String id, File versionDir, KcuVersionMeta.Meta meta) {
        if (meta.known) {
            return;
        }
        String found = KcuVersionMeta.probeFromJar(versionDir, id);
        if (found == null) {
            return;
        }
        meta.known = true;
        meta.display = found;
        KcuVersionMeta.write(versionDir, meta);
    }
}
