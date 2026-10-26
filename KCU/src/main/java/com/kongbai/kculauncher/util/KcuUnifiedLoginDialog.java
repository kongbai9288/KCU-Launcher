package com.kongbai.kculauncher.util;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.auth.Account;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.setting.Accounts;

/**
 * 统一登录入口。
 *
 * 玩家只需填写名称：先探测该名称是否对应正版账号，可用则引导正版登录，
 * 不可用则按名称直接生成离线账号进入。
 *
 * 皮肤为可选项，当前不提供选择界面 —— 皮肤选择与角色切换将合并处理，
 * 此处始终不设置皮肤，待该界面完成后再接入。
 *
 * 本入口只在未开启开发者模式时使用；开发者模式下由调用方改用原有的
 * 离线 / 微软 / 第三方三条独立入口。
 */
public final class KcuUnifiedLoginDialog {

    private KcuUnifiedLoginDialog() {
    }

    /**
     * @param onFinished 账号创建完成后回调（用于刷新账号列表）；未创建也会在关闭时回调一次
     */
    public static void show(Context context, Runnable onFinished) {
        try {
            new Session(context, onFinished).start();
        } catch (Throwable ignored) {
            // 界面构建失败不应影响账号页
        }
    }

    private static final class Session {

        private final Context context;
        private final Runnable onFinished;
        private final AlertDialog dialog;
        private final LinearLayout card;
        private String name = "";
        /** 状态行视图；复用以免多次提示堆积 */
        private android.widget.TextView statusView = null;
        /** 玩家自定义标识；留空表示使用按名称推导的默认值 */
        private java.util.UUID customUuid = null;

        Session(Context context, Runnable onFinished) {
            this.context = context;
            this.onFinished = onFinished;
            ScrollView scroll = new ScrollView(context);
            LinearLayout root = KcuUi.root(context);
            this.card = KcuUi.card(context);
            root.addView(card);
            scroll.addView(root);
            this.dialog = new AlertDialog.Builder(context).setView(scroll).create();
            // 取消同样要放行：框架约定是 onFinish 一定被调用一次，
            // 这个对话框尚未迁到 KcuDialog，但约定必须一致。
            // 否则将来一旦把它接进启动流程，取消就会变成"点了没反应"
            this.dialog.setOnCancelListener(d -> dismiss());
        }

        void start() {
            buildInput();
            dialog.show();
        }

        /** 阶段一：填写名称 */
        private void buildInput() {
            card.removeAllViews();
            statusView = null;
            card.addView(KcuUi.header(context, "\u2261", context.getString(R.string.kcu_login_title)));
            card.addView(KcuUi.gap(context, 12));
            card.addView(KcuUi.body(context, context.getString(R.string.kcu_login_hint)));
            card.addView(KcuUi.gap(context, 16));

            final android.widget.EditText edit =
                    KcuUi.field(context, context.getString(R.string.kcu_login_name_hint));
            edit.setSingleLine(true);
            card.addView(edit);
            card.addView(KcuUi.gap(context, 14));

            // 自定义标识：多数玩家不需要，但跨设备迁移时要把标识对齐到原设备
            card.addView(KcuUi.hint(context, context.getString(R.string.kcu_login_uuid_hint)));
            card.addView(KcuUi.gap(context, 6));
            final android.widget.EditText uuidEdit =
                    KcuUi.field(context, context.getString(R.string.kcu_login_uuid_field));
            uuidEdit.setSingleLine(true);
            card.addView(uuidEdit);
            card.addView(KcuUi.gap(context, 18));

            android.widget.Button confirm =
                    KcuUi.primaryButton(context, context.getString(R.string.kcu_login_confirm));
            confirm.setOnClickListener(v -> {
                String value = edit.getText() == null ? "" : edit.getText().toString();
                if (TextUtils.isEmpty(value.trim())) {
                    appendStatus(context.getString(R.string.kcu_login_name_empty));
                    return;
                }
                if (!KcuAccountFlow.isValidName(value)) {
                    appendStatus(context.getString(R.string.kcu_login_name_invalid));
                    return;
                }
                name = value.trim();
                String rawUuid = uuidEdit.getText() == null
                        ? "" : uuidEdit.getText().toString();
                customUuid = null;
                if (!TextUtils.isEmpty(rawUuid.trim())) {
                    customUuid = KcuUuid.parse(rawUuid);
                    if (customUuid == null) {
                        appendStatus(context.getString(R.string.kcu_login_uuid_invalid));
                        buildInput();
                        return;
                    }
                }
                buildChecking();
                runProbe();
            });
            card.addView(confirm);
        }

        private void buildChecking() {
            card.removeAllViews();
            statusView = null;
            card.addView(KcuUi.header(context, "\u2261", context.getString(R.string.kcu_login_title)));
            card.addView(KcuUi.gap(context, 18));
            card.addView(KcuUi.status(context, context.getString(R.string.kcu_login_checking)));
        }

        private void runProbe() {
            final String checking = name;
            Schedulers.defaultScheduler().execute(() -> {
                int result = KcuLoginProbe.probe(checking);
                Schedulers.androidUIThread().execute(() -> onProbed(result));
            });
        }

        private void onProbed(int result) {
            // 名称对应正版账号：把选择权交回玩家，不擅自替其登录
            if (result == KcuLoginProbe.RESULT_PREMIUM) {
                buildPremiumChoice();
                return;
            }
            // 探测未成功时不能静默走离线 —— 玩家会以为自己登录成功了。
            // 明确说明服务不可用，并告知将以填写的名称进入游戏
            if (result == KcuLoginProbe.RESULT_UNAVAILABLE) {
                buildServiceUnavailable();
                return;
            }
            finishWithOffline();
        }

        /** 探测失败：说明服务不可用，并说明将以该名称离线进入 */
        private void buildServiceUnavailable() {
            card.removeAllViews();
            statusView = null;
            card.addView(KcuUi.header(context, "!", context.getString(R.string.kcu_login_unavailable_title)));
            card.addView(KcuUi.gap(context, 12));
            card.addView(KcuUi.body(context, context.getString(R.string.kcu_login_unavailable_body)));
            card.addView(KcuUi.gap(context, 10));
            card.addView(KcuUi.hint(context,
                    context.getString(R.string.kcu_login_uuid_note)));
            card.addView(KcuUi.gap(context, 18));

            android.widget.Button offline =
                    KcuUi.primaryButton(context, context.getString(R.string.kcu_login_use_offline));
            offline.setOnClickListener(v -> finishWithOffline());
            card.addView(offline);
        }

        /** 阶段二：名称对应正版账号，让玩家决定走哪条路 */
        private void buildPremiumChoice() {
            card.removeAllViews();
            statusView = null;
            card.addView(KcuUi.header(context, "!", context.getString(R.string.kcu_login_premium_title)));
            card.addView(KcuUi.gap(context, 12));
            card.addView(KcuUi.body(context, context.getString(R.string.kcu_login_premium_body)));
            card.addView(KcuUi.gap(context, 18));

            android.widget.Button premium =
                    KcuUi.primaryButton(context, context.getString(R.string.kcu_login_use_microsoft));
            premium.setOnClickListener(v -> {
                dismiss();
                try {
                    new com.kongbai.kculauncher.ui.account.CreateAccountDialog(
                            context, Accounts.FACTORY_MICROSOFT).show();
                } catch (Throwable ignored) {
                }
            });
            card.addView(premium);
            card.addView(KcuUi.gap(context, 10));

            android.widget.Button offline =
                    KcuUi.secondaryButton(context, context.getString(R.string.kcu_login_use_offline));
            offline.setOnClickListener(v -> finishWithOffline());
            card.addView(offline);
        }

        /**
         * 确定账号并结束。
         *
         * 先自定义标识落库，再创建账号 —— 顺序不能反：
         * 创建过程会读自定义值，反了本次填写的自定义不生效。
         *
         * 填了自定义标识时必须显式传入：走 resolve 的话内部按名称推导，
         * 自定义值会被忽略。那样账号实际用的是推导值，而存档检测拿自定义值比对，
         * 两处不一致 —— 玩家照提示改完存档仍然对不上。
         */
        private void finishWithOffline() {
            try {
                KcuUuid.setCustom(context, name, customUuid);
            } catch (Throwable ignored) {
            }
            Account account = customUuid != null
                    ? KcuAccountFlow.createOffline(name, customUuid)
                    : KcuAccountFlow.resolve(name, null);
            if (account != null) {
                try {
                    Accounts.addAccount(account);
                    Accounts.setSelectedAccount(account);
                } catch (Throwable e) {
                    // 不能只吞掉：对话框照常消失，玩家以为账号建好了，
                    // 回到列表却是空的，且不会有任何提示说明为什么
                    android.util.Log.w("KCU", "Failed to add offline account", e);
                    Toast.makeText(context, R.string.kcu_login_add_failed, Toast.LENGTH_LONG).show();
                }
            }
            dismiss();
        }

        /**
         * 显示状态行。复用同一个视图而不是每次追加 ——
         * 玩家连点几次确认就会堆出好几行同样的提示，既难看又把按钮挤下去。
         */
        private void appendStatus(String text) {
            if (statusView == null) {
                statusView = KcuUi.status(context, text);
                card.addView(KcuUi.gap(context, 10));
                card.addView(statusView);
                return;
            }
            statusView.setText(text);
        }

        private void dismiss() {
            try {
                dialog.dismiss();
            } catch (Throwable ignored) {
            }
            if (onFinished != null) {
                try {
                    onFinished.run();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
