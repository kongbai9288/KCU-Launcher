package com.kongbai.kculauncher.util;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.kongbai.kculauncher.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 开发者模式解锁：请求文件被置为 true 后弹出，要求填入个人访问令牌。
 *
 * 【为什么必须联网校验归属】
 * 请求文件在公共目录，任何应用都能写成 true。只认它就等于谁都能开。
 * 令牌要打到账号接口，确认登录名是本人才允许开启。
 *
 * 【为什么校验放在后台线程】
 * 网络请求不能在主线程执行，且失败要能给出提示而不是直接崩。
 */
public final class KcuDevUnlockDialog {

    private KcuDevUnlockDialog() {
    }

    /**
     * @param onFinished 无论校验成功、失败还是玩家取消，都会被调用一次且仅一次。
     *                   调用方必须靠它继续启动流程 —— 若只在成功时回调，
     *                   玩家取消后就会卡在启动页进不去应用。
     */
    public static void show(Context context, Runnable onFinished) {
        reset();
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = KcuUi.root(context);
        LinearLayout card = KcuUi.card(context);

        card.addView(KcuUi.header(context, "\u2699", context.getString(R.string.kcu_dev_unlock_title)));
        card.addView(KcuUi.gap(context, 12));
        card.addView(KcuUi.body(context, context.getString(R.string.kcu_dev_unlock_body)));
        card.addView(KcuUi.gap(context, 16));

        LinearLayout row = KcuUi.fieldRow(context, context.getString(R.string.kcu_dev_unlock_hint), "\u2022");
        card.addView(row);
        EditText edit = KcuUi.editOf(row);

        card.addView(KcuUi.gap(context, 8));
        card.addView(KcuUi.hint(context, context.getString(R.string.kcu_dev_unlock_note)));
        card.addView(KcuUi.gap(context, 18));

        android.widget.TextView status = KcuUi.status(context, "");

        AlertDialog dialog = new AlertDialog.Builder(context).setView(scroll).create();
        // 可取消，且取消也必须放行：玩家放弃解锁不该被挡在应用外面
        dialog.setCancelable(true);

        android.widget.Button ok = KcuUi.primaryButton(context, context.getString(R.string.kcu_dev_unlock_confirm));
        ok.setOnClickListener(v -> {
            String token = edit.getText() == null ? "" : edit.getText().toString().trim();
            if (token.isEmpty()) {
                Toast.makeText(context, R.string.kcu_dev_unlock_need_token, Toast.LENGTH_SHORT).show();
                return;
            }
            status.setText(context.getString(R.string.kcu_dev_unlock_checking));
            ok.setEnabled(false);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            worker.execute(() -> {
                boolean owner = KcuDevMode.verifyOwner(token);
                android.os.Handler handler = new android.os.Handler(context.getMainLooper());
                handler.post(() -> {
                    ok.setEnabled(true);
                    if (owner && KcuDevMode.grant()) {
                        KcuDevMode.refresh();
                        status.setText(context.getString(R.string.kcu_dev_unlock_ok));
                        Toast.makeText(context, R.string.kcu_dev_unlock_ok, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        // 校验失败不删请求文件，玩家改对令牌后仍可再试
                        finish(onFinished);
                    } else {
                        // 不区分"令牌无效"与"写入失败"以外的细节，
                        // 但必须留下可查记录，否则排查时无从下手
                        android.util.Log.w("KCU", "Dev unlock failed, owner=" + owner);
                        status.setText(context.getString(R.string.kcu_dev_unlock_failed));
                        finish(onFinished);
                    }
                    worker.shutdownNow();
                });
            });
        });
        card.addView(ok);
        card.addView(KcuUi.gap(context, 10));
        card.addView(status);

        root.addView(card);
        scroll.addView(root);
        dialog.setOnCancelListener(d -> finish(onFinished));
        dialog.show();
    }

    /** 放行。用标记保证只调用一次，避免取消与校验完成两条路径重复触发 */
    private static void finish(Runnable onFinished) {
        if (fired) {
            return;
        }
        fired = true;
        if (onFinished != null) {
            onFinished.run();
        }
    }

    /** 单次弹框的状态标记，show 是低频操作，show 开头会重置 */
    private static volatile boolean fired;

    private static void reset() {
        fired = false;
    }
}
