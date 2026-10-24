package com.kongbai.kculauncher.library.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.kongbai.kculauncher.library.component.dialog.KCUDialog
import com.kongbai.kculauncher.library.component.view.KCUProgressBar
import androidx.core.graphics.drawable.toDrawable

class ProgressDialog(context: Context) : KCUDialog(context) {

    init {
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        // 覆盖 KCUDialog 的圆角卡片背景：进度框直接浮于全屏模糊背景上
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        setContentView(KCUProgressBar(context))
        show()
    }
}