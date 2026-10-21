package com.tungsten.fcllibrary.component.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.ColorUtils
import com.tungsten.fcl.FCLApp
import com.tungsten.fcl.R
import com.tungsten.fcllibrary.util.ConvertUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 主题数据（不可变 data class，替代原 fakefx 属性版 Theme）。
 *
 * 亮暗切换不改变数据本身，动态取色见 [getColor] 与 [getColor2] 与 [getBackground]；
 * ltColor/dkColor/autoTint 等由主色派生，随 [getColor] 变化自动重算。
 */
data class ThemeData(
    /** 亮色模式主要主题色（原始值，Java 侧经 [getColor] 动态取色或 [_getColor] 取原始值） */
    @get:JvmName("_getColor")
    val color: Int,
    /** 暗色模式主要主题色（原始值） */
    val colorDark: Int,
    /** 亮色模式次要色（原始值，Java 侧经 [getColor2] 动态取色或 [_getColor2] 取原始值） */
    @get:JvmName("_getColor2")
    val color2: Int,
    val color2Dark: Int,
    /** Java getter 命名为 isFullscreen（Kotlin Boolean 属性默认 getFullscreen） */
    @get:JvmName("isFullscreen")
    val fullscreen: Boolean,
    /** Java getter 命名为 isCloseSkinModel */
    @get:JvmName("isCloseSkinModel")
    val closeSkinModel: Boolean,
    val animationSpeed: Int,
    /** 派生色（ltColor/dkColor）透明度，独立于主色 alpha */
    val colorAlpha: Int,
    val backgroundLt: BitmapDrawable,
    val backgroundDk: BitmapDrawable
) {
    /** 主色的亮色变体（HSV 提亮，浅色背景/按压态用，随当前模式主色动态变化） */
    val ltColor: Int
        get() = deriveColor(getColor(), brighten = true)

    /** 主色的暗色变体（HSV 压暗，选中态/进度条等用，随当前模式主色动态变化） */
    val dkColor: Int
        get() = deriveColor(getColor(), brighten = false)

    /** 自动文字色。
     *  原实现按主色亮度推导（主色偏暗时取白），在浅色纸面底色上会得到白色而不可读；
     *  纸面观感下底色由亮暗模式决定，故改为按亮暗模式取墨色/浅纸色。 */
    val autoTint: Int
        get() {
            val activity = FCLApp.getActivity()
            val dark = activity != null && ThemeEngine.isNightMode(activity)
            return if (dark) 0xFFE4E1D8.toInt() else 0xFF2C2C2A.toInt()
        }

    /** 半透明自动文字色（提示文字用） */
    val autoHintTint: Int
        get() {
            val activity = FCLApp.getActivity()
            val dark = activity != null && ThemeEngine.isNightMode(activity)
            return if (dark) 0x99E4E1D8.toInt() else 0x992C2C2A.toInt()
        }

    /** 当前模式主色的不透明形态（文字/图标等可读性关键场景用，不受透明度设置影响） */
    val opaqueColor: Int
        get() = getColor() or 0xFF000000.toInt()

    /** 亮变体的不透明形态 */
    val opaqueLtColor: Int
        get() = ltColor or 0xFF000000.toInt()

    /** 暗变体的不透明形态 */
    val opaqueDkColor: Int
        get() = dkColor or 0xFF000000.toInt()

    /** 按当前亮暗模式取主要主题色（亮色用 [color]，暗色用 [colorDark]） */
    fun getColor(): Int {
        val activity = FCLApp.getActivity()
        return if (activity != null && ThemeEngine.isNightMode(activity)) colorDark else color
    }

    /** 按当前亮暗模式取次要色（亮色用 [color2]，暗色用 [color2Dark]） */
    fun getColor2(): Int {
        val activity = FCLApp.getActivity()
        return if (activity != null && ThemeEngine.isNightMode(activity)) color2Dark else color2
    }

    /** 按当前亮暗模式取背景 */
    fun getBackground(context: Context): BitmapDrawable {
        val dark = ThemeEngine.isNightMode(context)
        return if (dark) backgroundDk else backgroundLt
    }

    private fun deriveColor(base: Int, brighten: Boolean): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        if (brighten) {
            hsv[1] -= (1 - hsv[1]) * 0.3f
            hsv[2] += (1 - hsv[2]) * 0.3f
        } else {
            hsv[1] += (1 - hsv[1]) * 0.3f
            hsv[2] -= (1 - hsv[2]) * 0.3f
        }
        return Color.HSVToColor(colorAlpha, hsv)
    }

    companion object {

        // 纸面观感：配色固定，不提供用户自定义入口
        private val COLOR = 0xFF3D6B66.toInt()
        private val COLOR_DARK = 0xFF4F8580.toInt()
        private val COLOR2 = 0xFF2C2C2A.toInt()
        private val COLOR2_DARK = 0xFFE4E1D8.toInt()
        private const val ALPHA = 255
        private const val ANIMATION_SPEED = 8

        /**
         * 构建主题。颜色与背景为常量，仅全屏/皮肤模型/动画速度沿用用户偏好。
         * 不再读取自定义背景文件，也不再迁移旧 SharedPreferences。
         */
        @JvmStatic
        fun getTheme(context: Context): ThemeData {
            val lt = BitmapDrawable(
                context.resources,
                ConvertUtils.getBitmapFromRes(context, R.drawable.background_light)
            )
            val dk = BitmapDrawable(
                context.resources,
                ConvertUtils.getBitmapFromRes(context, R.drawable.background_dark)
            )
            val pref = runCatching {
                runBlocking { context.themeDataStore.data.first() }
            }.getOrNull()
            return ThemeData(
                COLOR, COLOR_DARK, COLOR2, COLOR2_DARK,
                pref?.fullscreen ?: false,
                pref?.closeSkinModel ?: false,
                pref?.animationSpeed ?: ANIMATION_SPEED,
                ALPHA,
                lt, dk
            )
        }

        /** 颜色不可自定义，仅持久化功能偏好；失败静默忽略 */
        @JvmStatic
        fun saveTheme(context: Context, theme: ThemeData) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    context.themeDataStore.updateData { current ->
                        current.copy(
                            fullscreen = theme.fullscreen,
                            closeSkinModel = theme.closeSkinModel,
                            animationSpeed = theme.animationSpeed
                        )
                    }
                }
            }
        }
    }
}