package com.kongbai.kculauncher.control

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.kongbai.kculauncher.control.data.QuickInputTexts
import com.kongbai.kculauncher.databinding.DialogQuickInputBinding
import com.kongbai.kculauncher.library.component.dialog.KCUDialog

class QuickInputDialog(activity: AppCompatActivity, private val menu: GameMenu) :
    KCUDialog(activity),
    View.OnClickListener {
    private val binding: DialogQuickInputBinding

    init {
        setCancelable(false)
        window!!.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        binding = DialogQuickInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addText.setOnClickListener(this)
        binding.positive.setOnClickListener(this)

        refreshList()
    }

    private fun refreshList() {
        val adapter = InputTextAdapter(
            context,
            QuickInputTexts.getInputTexts()
        ) {
            if (it.isNotEmpty()) {
                GameTextSender.send(menu, it)
            }
            dismiss()
        }
        binding.list.setAdapter(adapter)
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.addText -> AddInputTextDialog(
                context
            ) { refreshList() }.show()

            binding.positive -> dismiss()
        }
    }
}
