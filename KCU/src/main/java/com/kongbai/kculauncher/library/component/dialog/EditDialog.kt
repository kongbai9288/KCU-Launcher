package com.kongbai.kculauncher.library.component.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.kongbai.kculauncher.library.component.view.KCUEditText
import com.kongbai.kculauncher.databinding.DialogEditBinding
import com.kongbai.kculauncher.library.util.ConvertUtils
import java.util.function.Consumer

class EditDialog @JvmOverloads constructor(
    context: Context,
    private val defaultText: String = "",
    private val callback: Consumer<String>,
    private val selectMainName: Boolean = false
) : KCUDialog(context),
    View.OnClickListener {
    var binding: DialogEditBinding
    var onCancelListener: (() -> Unit)? = null

    init {
        setCancelable(false)
        window?.setLayout(ConvertUtils.dip2px(context, 400f), ViewGroup.LayoutParams.WRAP_CONTENT)
        binding = DialogEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            positive.setOnClickListener(this@EditDialog)
            negative.setOnClickListener(this@EditDialog)
            editText.setText(defaultText)
        }
        if (defaultText.isNotEmpty()) {
            if (selectMainName) {
                // 重命名时只选中主文件名、把扩展名留在外面：
                // 整个名字全选的话玩家一打字 ".jar" 就没了，
                // 而正常的文件管理器都是只选中名字部分
                // 点开头或没有扩展名（".minecraft" 这类）时仍全选
                val dot = defaultText.lastIndexOf('.')
                if (dot > 0) {
                    binding.editText.setSelection(0, dot)
                } else {
                    binding.editText.selectAll()
                }
            } else {
                // 传了默认文本（重命名、改名）时全选：光标默认在最前面，
                // 玩家想改后缀得先把光标挪过去；标准做法是打开即全选，直接输入就覆盖
                binding.editText.selectAll()
            }
        }
    }

    override fun onClick(v: View?) {
        binding.apply {
            if (v === positive) {
                val s = editText.getText().toString()
                if (!s.trim { it <= ' ' }.isEmpty()) {
                    callback.accept(s)
                    dismiss()
                } else {
                    // 空着点确定时原来什么都不做：对话框不关、也没提示，
                    // 玩家只会以为这个按钮坏了
                    // 用通用文案：这个对话框是全项目共用的，
                    // 拿文件管理专用的那句给别的页面用会文不对题
                    android.widget.Toast.makeText(
                        binding.root.context,
                        com.kongbai.kculauncher.R.string.input_not_empty,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            if (v === negative) {
                onCancelListener?.invoke()
                dismiss()
            }
        }
    }

    override fun setTitle(titleId: Int) {
        binding.title.setText(titleId)
    }

    override fun setTitle(title: CharSequence?) {
        binding.title.text = title
    }

    @SuppressLint("SetTextI18n")
    fun appendTitle(title: String) {
        binding.title.text = "${binding.title.text} $title"
    }

    fun getEditText(): KCUEditText {
        return binding.editText
    }
}
