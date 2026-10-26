package com.kongbai.kculauncher.util;

import android.content.Context;
import android.os.Handler;
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
 * 视图部分交给 {@link KcuDialog}：令牌框曾经因为取消后不放行，
 * 导致玩家被永久挡在启动页，这类错误由框架统一兜住。
 */
public final class KcuDevUnlockDialog {

    private KcuDevUnlockDialog() {
    }

    /**
     * @param onFinished 无论校验成功、失败还是玩家取消，都会调用一次且仅一次
     */
    public static void show(Context context, Runnable onFinished) {
        KcuDialog dialog = KcuDialog.builder(context)
                .icon(KcuDialog.Icon.LOCK)
                .title(context.getString(R.string.kcu_dev_unlock_title))
                .body(context.getString(R.string.kcu_dev_unlock_body))
                .field(context.getString(R.string.kcu_dev_unlock_hint), "\u2022")
                .hint(context.getString(R.string.kcu_dev_unlock_note))
                // 不自动关闭：要等网络校验结果回来再决定去留
                .primaryStay(context.getString(R.string.kcu_dev_unlock_confirm), null)
                .onFinish(onFinished)
                .show();

        // 主按钮的行为要引用对话框自身，只能在此处挂
        android.widget.Button primary = dialog.primaryButton();
        if (primary == null) {
            return;
        }
        primary.setOnClickListener(v -> {
            String token = dialog.fieldText(0);
            if (token.isEmpty()) {
                Toast.makeText(context, R.string.kcu_dev_unlock_need_token, Toast.LENGTH_SHORT).show();
                return;
            }
            primary.setEnabled(false);
            Toast.makeText(context, R.string.kcu_dev_unlock_checking, Toast.LENGTH_SHORT).show();
            ExecutorService worker = Executors.newSingleThreadExecutor();
            worker.execute(() -> {
                boolean owner = KcuDevMode.verifyOwner(token);
                new Handler(context.getMainLooper()).post(() -> {
                    primary.setEnabled(true);
                    if (owner && KcuDevMode.grant()) {
                        KcuDevMode.refresh();
                        Toast.makeText(context, R.string.kcu_dev_unlock_ok, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        // 不区分"令牌无效"与"写入失败"以外的细节，
                        // 但必须留下可查记录，否则排查时无从下手
                        android.util.Log.w("KCU", "Dev unlock failed, owner=" + owner);
                        Toast.makeText(context, R.string.kcu_dev_unlock_failed, Toast.LENGTH_SHORT).show();
                        // 失败不关闭：不删请求文件，玩家改对令牌后仍可再试
                    }
                    worker.shutdownNow();
                });
            });
        });
    }
}
