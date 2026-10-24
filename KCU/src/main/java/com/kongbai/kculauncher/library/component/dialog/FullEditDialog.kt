package com.kongbai.kculauncher.library.component.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.kongbai.kculauncher.library.component.view.KCUEditText
import com.kongbai.kculauncher.databinding.DialogFullEditBinding
import com.kongbai.kculauncher.library.util.ConvertUtils
import java.util.function.Consumer

class FullEditDialog(
    context: Context,
    private val canBeEmpty: Boolean = false,
    private val callback: Consumer<String>
) : KCUDialog(context),
    View.OnClickListener {
    var binding: DialogFullEditBinding

    init {
        setCancelable(false)
        window?.setLayout(ConvertUtils.dip2px(context, 400f), ViewGroup.LayoutParams.MATCH_PARENT)
        binding = DialogFullEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            positive.setOnClickListener(this@FullEditDialog)
            negative.setOnClickListener(this@FullEditDialog)
        }
    }

    override fun onClick(v: View?) {
        binding.apply {
            if (v === positive) {
                val s = editText.getText().toString()
                if (canBeEmpty || !s.trim { it <= ' ' }.isEmpty()) {
                    callback.accept(s)
                    dismiss()
                }
            }
            if (v === negative) {
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
