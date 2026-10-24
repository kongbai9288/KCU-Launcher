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
     * 只对来源不明的实例做：本启动器创建的实例，账号与存档 UUID 天然一致。
     * 玩家用文件管理器直接拷入的存档记的是原设备 UUID，与本账号对不上，
     * 进游戏会表现为背包与进度全空。
     */
    private static void checkWorldUuid(Context context, Profile profile, Runnable proceed) {
        try {
            File gameDir = profile.getGameDir();
            if (gameDir == null) {
                proceed.run();
                return;
            }
            String dir = gameDir.getAbsolutePath();
            if (KcuInstanceRegistry.OWNER_KCU.equals(KcuInstanceRegistry.query(dir))) {
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
            showUuidFix(context, gameDir, mismatches, proceed);
        } catch (Throwable ignored) {
            proceed.run();
        }
    }

    private static void showUuidFix(Context context, File gameDir,
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
        android.widget.Button fix =
                KcuUi.primaryButton(context, context.getString(R.string.kcu_uuid_confirm));
        fix.setOnClickListener(v -> {
            dialog.dismiss();
            boolean allOk = true;
            for (KcuWorldUuid.Mismatch mismatch : mismatches) {
                if (!KcuWorldUuid.rewrite(mismatch, gameDir)) {
                    allOk = false;
                }
            }
            if (!allOk) {
                // 半改写状态必须让玩家知道，不能当作无事发生
                android.widget.Toast.makeText(context,
                        context.getString(R.string.kcu_uuid_partial),
                        android.widget.Toast.LENGTH_LONG).show();
            }
            proceed.run();
        });
        card.addView(fix);
        card.addView(KcuUi.gap(context, 8));
        android.widget.Button skip =
                KcuUi.secondaryButton(context, context.getString(R.string.kcu_uuid_skip));
        skip.setOnClickListener(v -> {
            dialog.dismiss();
            proceed.run();
        });
        card.addView(skip);
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
