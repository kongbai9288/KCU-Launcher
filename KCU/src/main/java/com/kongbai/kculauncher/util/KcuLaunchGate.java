package com.kongbai.kculauncher.util;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ScrollView;

import com.kongbai.kculauncher.mio.JavaManager;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.core.game.JavaVersion;
import com.kongbai.kculauncher.setting.Accounts;
import com.kongbai.kculauncher.core.auth.Account;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
            File versionDir = profile.getRepository().getVersionRoot(id);
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
            checkWorldUuid(context, profile, proceed);
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
        card.addView(KcuUi.header(context, "\u2261", context.getString(R.string.kcu_pick_java_title)));
        card.addView(KcuUi.gap(context, 12));
        card.addView(KcuUi.body(context, context.getString(R.string.kcu_pick_java_hint)));
        card.addView(KcuUi.gap(context, 16));

        // 不可取消：按返回键会让 proceed 永不执行，表现为"点了启动什么都没发生"
        AlertDialog dialog = new AlertDialog.Builder(context).setView(scroll).create();
        dialog.setCancelable(false);
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

    /**
     * 启动前的存档身份检查。
     *
     * 先查注册表：由本启动器创建的实例直接跳过扫描，不产生任何目录遍历开销。
     * 只有来源不明的实例才扫存档 —— 这类实例里的存档可能来自其他设备，
     * 记录的 UUID 与本账号对不上，进游戏会表现为背包与进度全空。
     *
     * 检测到不一致时只劝导，不自动改写：改写存档是破坏性操作，
     * 自动执行一旦出问题会被归咎于启动器。
     */
    private static void checkWorldUuid(Context context, Profile profile, Runnable proceed) {
        try {
            File gameDir = profile.getGameDir();
            if (gameDir == null) {
                proceed.run();
                return;
            }
            if (KcuInstanceRegistry.OWNER_KCU.equals(
                    KcuInstanceRegistry.query(gameDir.getAbsolutePath()))) {
                proceed.run();
                return;
            }
            Account account = Accounts.getSelectedAccount();
            UUID target = KcuUuid.resolve(context, account);
            if (target == null) {
                proceed.run();
                return;
            }
            List<KcuWorldUuid.Mismatch> mismatches = KcuWorldUuid.scan(gameDir, target);
            if (mismatches.isEmpty()) {
                proceed.run();
                return;
            }
            showUuidAdvice(context, mismatches, proceed);
        } catch (Throwable ignored) {
            proceed.run();
        }
    }

    /** 只提示，不改写：把处理权完全留给玩家与外部 NBT 工具 */
    private static void showUuidAdvice(Context context,
                                       List<KcuWorldUuid.Mismatch> mismatches, Runnable proceed) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < mismatches.size(); i++) {
            if (i > 0) {
                names.append("\n");
            }
            names.append("\u2022 ").append(mismatches.get(i).worldName);
        }
        ScrollView scroll = new ScrollView(context);
        android.widget.LinearLayout root = KcuUi.root(context);
        android.widget.LinearLayout card = KcuUi.card(context);
        card.addView(KcuUi.header(context, "!", context.getString(R.string.kcu_uuid_title)));
        card.addView(KcuUi.gap(context, 12));
        card.addView(KcuUi.body(context, context.getString(R.string.kcu_uuid_body)));
        card.addView(KcuUi.gap(context, 10));
        card.addView(KcuUi.hint(context, names.toString()));
        card.addView(KcuUi.gap(context, 10));
        card.addView(KcuUi.hint(context, context.getString(R.string.kcu_uuid_note)));

        AlertDialog dialog = new AlertDialog.Builder(context).setView(scroll).create();
        android.widget.Button ok =
                KcuUi.primaryButton(context, context.getString(R.string.kcu_uuid_continue));
        ok.setOnClickListener(v -> {
            dialog.dismiss();
            proceed.run();
        });
        card.addView(ok);
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
