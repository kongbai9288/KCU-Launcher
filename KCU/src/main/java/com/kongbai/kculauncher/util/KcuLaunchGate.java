package com.kongbai.kculauncher.util;

import android.content.Context;

import com.kongbai.kculauncher.mio.JavaManager;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.core.game.JavaVersion;
import com.kongbai.kculauncher.setting.Accounts;
import com.kongbai.kculauncher.core.auth.Account;

import android.widget.Toast;

import com.kongbai.kculauncher.core.util.Logging;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

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

        // 交给统一弹窗：blocking 会同时关掉返回键与点击外部，
        // 并且无论走哪条路径都保证 proceed 被执行一次
        KcuDialog.Builder builder = KcuDialog.builder(context)
                .icon(KcuDialog.Icon.GEAR)
                .title(context.getString(R.string.kcu_pick_java_title))
                .body(context.getString(R.string.kcu_pick_java_hint))
                .blocking(true)
                .onFinish(proceed);
        for (JavaVersion java : candidates) {
            final String name = java.getName();
            builder.secondary(name, v -> {
                meta.java = name;
                KcuVersionMeta.write(versionDir, meta);
                try {
                    profile.getRepository().getVersionSetting(id).setJava(name);
                    profile.getRepository().saveVersionSetting(id);
                } catch (Throwable e) {
                    // 保存失败若只吞掉，玩家会以为自己选过了，下次启动又弹一次同样的框
                    Logging.LOG.log(Level.WARNING, "KCU: failed to save java selection", e);
                    Toast.makeText(context, R.string.kcu_pick_java_save_failed, Toast.LENGTH_LONG).show();
                }
                refreshDisplay(profile, id, versionDir, meta);
            });
        }
        // 没有候选时不会走到这里：上面已提前放行。
        // 若真出现零按钮的异常情况，框架会自动退回可取消，不会把玩家锁死
        builder.show();
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
            String dir = gameDir.getAbsolutePath();
            // 归属与存档指纹同时满足才跳过扫描：只看归属会漏掉
            // "拷进本启动器建的实例里的外来存档"
            if (KcuInstanceRegistry.isSavesKnown(dir)) {
                proceed.run();
                return;
            }
            Account account = Accounts.getSelectedAccount();
            UUID target = KcuUuid.resolve(context, account);
            if (target == null) {
                proceed.run();
                return;
            }
            KcuWorldUuid.ScanResult scan = KcuWorldUuid.scan(gameDir, target);
            if (scan.mismatches.isEmpty()) {
                // 确实读到标识且全部一致才算验证通过，此时才建立信任。
                // checked 为 0 表示一个世界都没读出来 —— 那不是"一致"，
                // 是"没验证"，不能据此跳过以后的检查
                if (scan.checked > 0) {
                    KcuInstanceRegistry.markSavesVerified(dir);
                }
                proceed.run();
                return;
            }
            // 有不一致时不标记：玩家还没处理，下次启动仍需提示
            showUuidAdvice(context, scan.mismatches, proceed);
        } catch (Throwable ignored) {
            proceed.run();
        }
    }

    /** 只提示，不改写：把处理权完全留给玩家与外部 NBT 工具 */
    private static void showUuidAdvice(Context context,
                                       List<KcuWorldUuid.Mismatch> mismatches, Runnable proceed) {
        // 每个世界都列出"现记录值 → 应改为的值"：
        // 玩家用 NBT 工具改时需要知道从什么改成什么，只报世界名没法动手
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < mismatches.size(); i++) {
            if (i > 0) {
                names.append("\n");
            }
            KcuWorldUuid.Mismatch one = mismatches.get(i);
            names.append("\u2022 ").append(one.worldName).append("\n    ")
                    .append(one.found).append("\n  \u2192 ")
                    .append(one.expected);
        }
        // 不可取消：按返回键或点外部会让对话框消失但 proceed 永不执行，
        // 表现为"点了启动什么都没发生"，玩家只会以为启动器坏了。
        // 由框架统一保证两道都关掉，且 proceed 一定被执行一次
        KcuDialog.builder(context)
                .icon(KcuDialog.Icon.WARN)
                .title(context.getString(R.string.kcu_uuid_title))
                .body(context.getString(R.string.kcu_uuid_body))
                .hint(names.toString())
                .hint(context.getString(R.string.kcu_uuid_note))
                .primary(context.getString(R.string.kcu_uuid_continue), null)
                .blocking(true)
                .onFinish(proceed)
                .show();
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
