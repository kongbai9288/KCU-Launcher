package com.kongbai.kculauncher.ui.account

import android.content.Context
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.databinding.DialogReloginPasswordBinding
import com.kongbai.kculauncher.setting.Accounts
import com.kongbai.kculauncher.core.auth.AuthInfo
import com.kongbai.kculauncher.core.auth.ClassicAccount
import com.kongbai.kculauncher.core.task.Schedulers
import com.kongbai.kculauncher.core.task.Task
import com.kongbai.kculauncher.core.util.Logging.LOG
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog
import com.kongbai.kculauncher.library.component.dialog.KCUDialog
import com.kongbai.kculauncher.library.util.ConvertUtils
import java.util.function.Consumer
import java.util.logging.Level

/** Classic 账户（Yggdrasil / authlib-injector）凭据过期后的密码重登弹窗 */
class ClassicAccountLoginDialog(
    context: Context,
    private val account: ClassicAccount,
    private val success: Consumer<AuthInfo>,
    private val failed: Runnable,
) : KCUDialog(context) {

    private val binding = DialogReloginPasswordBinding.inflate(layoutInflater)

    init {
        setCancelable(false)
        // 布局根节点是 400dp×match_parent，不显式设定窗口尺寸时对话框按内容收缩成一团
        window?.setLayout(ConvertUtils.dip2px(context, 400f), ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(binding.root)
        binding.username.text = account.username
        binding.login.setOnClickListener { logIn() }
        binding.cancel.setOnClickListener {
            failed.run()
            dismiss()
        }
        binding.password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                logIn()
                true
            } else {
                false
            }
        }
    }

    private fun logIn() {
        val password = binding.password.text?.toString().orEmpty()
        if (password.isEmpty()) {
            binding.password.error = context.getString(R.string.input_hint_not_empty)
            return
        }
        binding.login.isEnabled = false
        binding.cancel.isEnabled = false
        Task.supplyAsync { account.logInWithPassword(password) }
            .whenComplete(Schedulers.androidUIThread()) { authInfo, exception ->
                if (exception == null) {
                    success.accept(authInfo)
                    dismiss()
                } else {
                    LOG.log(Level.INFO, "Failed to login with password: $account", exception)
                    KCUAlertDialog.Builder(context).apply {
                        setAlertLevel(KCUAlertDialog.AlertLevel.ALERT)
                        setMessage(Accounts.localizeErrorMessage(context, exception))
                        setCancelable(false)
                        setNegativeButton(context.getString(R.string.dialog_positive), null)
                    }.create().show()
                    binding.login.isEnabled = true
                    binding.cancel.isEnabled = true
                }
            }.start()
    }
}
