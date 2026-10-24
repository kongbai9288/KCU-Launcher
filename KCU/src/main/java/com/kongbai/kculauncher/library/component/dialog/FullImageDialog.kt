package com.kongbai.kculauncher.library.component.dialog

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import com.kongbai.kculauncher.databinding.DialogFullImageBinding

class FullImageDialog(context: Context) : KCUDialog(context) {
    private var binding: DialogFullImageBinding

    init {
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setCancelable(true)
        binding = DialogFullImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.close.setOnClickListener {
            dismiss()
        }
    }

    fun getImageView(): ImageView {
        return binding.image
    }
}