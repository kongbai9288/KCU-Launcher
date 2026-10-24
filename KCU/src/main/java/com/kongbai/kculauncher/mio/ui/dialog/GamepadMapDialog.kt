package com.kongbai.kculauncher.mio.ui.dialog

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.kongbai.kculauncher.mio.ui.adapter.GamepadMapItemAdapter
import com.kongbai.kculauncher.control.KCUInput
import com.kongbai.kculauncher.databinding.DialogGamepadMapBinding
import com.kongbai.kculauncher.library.component.dialog.KCUDialog
import com.kongbai.kculauncher.library.util.ConvertUtils

class GamepadMapDialog(context: Context, fclInput: KCUInput) : KCUDialog(context) {
    init {
        window?.setLayout(ConvertUtils.dip2px(context, 400F), ViewGroup.LayoutParams.MATCH_PARENT)
        val binding = DialogGamepadMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.recyclerView.adapter = GamepadMapItemAdapter(context, fclInput.gamepad.currentMap)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.confirm.setOnClickListener {
            fclInput.gamepad.saveMapper()
            dismiss()
        }
        binding.cancel.setOnClickListener {
            dismiss()
        }
    }
}