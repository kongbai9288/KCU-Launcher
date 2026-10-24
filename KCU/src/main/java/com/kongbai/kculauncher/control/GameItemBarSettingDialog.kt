package com.kongbai.kculauncher.control

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.kongbai.kculauncher.mio.datastore.GameItemBarSetting
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.databinding.DialogItembarSettingBinding
import com.kongbai.kculauncher.library.component.dialog.KCUDialog
import com.kongbai.kculauncher.library.util.ConvertUtils

class GameItemBarSettingDialog(
    context: Context,
    val setting: GameItemBarSetting,
    val callback: (GameItemBarSetting) -> Unit
) : KCUDialog(context) {
    init {
        window?.setLayout(ConvertUtils.dip2px(context, 400f), ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setBackgroundDrawableResource(R.drawable.bg_game_menu)
        val binding = DialogItembarSettingBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)
        binding.slideSelection.isChecked = setting.slideSelection
        binding.swapHands.isChecked = setting.doubleTapSwapHands

        binding.slideSelection.setOnCheckedChangeListener { _, isChecked ->
            callback(setting.copy(slideSelection = isChecked))
        }
        binding.swapHands.setOnCheckedChangeListener { _, isChecked ->
            callback(setting.copy(doubleTapSwapHands = isChecked))
        }
        binding.close.setOnClickListener { dismiss() }
    }
}