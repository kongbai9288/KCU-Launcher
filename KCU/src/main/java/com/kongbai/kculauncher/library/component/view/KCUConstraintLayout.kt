package com.kongbai.kculauncher.library.component.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.withStyledAttributes
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.library.component.theme.ThemeEngine

class KCUConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    var autoTint = false

    init {
        context.withStyledAttributes(attrs, R.styleable.KCUConstraintLayout) {
            autoTint = getBoolean(R.styleable.FCLConstraintLayout_auto_tint, false)
        }
        ThemeEngine.getInstance().registerEvent(this) {
            if (autoTint) {
                setBackgroundTintList(
                    ColorStateList(
                        arrayOf<IntArray?>(intArrayOf()),
                        intArrayOf(ThemeEngine.getInstance().getTheme().ltColor)
                    )
                )
            }
        }
    }
}
