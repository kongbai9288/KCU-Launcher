package com.kongbai.kculauncher.library.component.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.library.component.theme.ThemeEngine

class KCUView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    var useThemeColor = false

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.KCUView)
        useThemeColor = typedArray.getBoolean(R.styleable.KCUView_use_theme_color, false)
        typedArray.recycle()
        ThemeEngine.getInstance().registerEvent(this) {
            if (useThemeColor) {
                setBackgroundColor(ThemeEngine.getInstance().getTheme().getColor2())
            }
        }
    }
}
