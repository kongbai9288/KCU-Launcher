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
    private val callback: Consumer<String>
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
    }

    override fun onClick(v: View?) {
        binding.apply {
            if (v === positive) {
                val s = editText.getText().toString()
                if (!s.trim { it <= ' ' }.isEmpty()) {
                    callback.accept(s)
                    dismiss()
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
