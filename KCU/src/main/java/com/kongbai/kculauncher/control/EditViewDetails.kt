package com.kongbai.kculauncher.control

import android.content.Context
import android.view.View
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.control.data.BaseInfoData
import com.kongbai.kculauncher.control.data.BaseInfoData.PercentageSize
import com.kongbai.kculauncher.control.data.BaseInfoData.SizeType
import com.kongbai.kculauncher.control.data.BaseInfoData.VisibilityType
import com.kongbai.kculauncher.core.fakefx.beans.property.IntegerProperty
import com.kongbai.kculauncher.library.component.view.KCUNumberSeekBar
import com.kongbai.kculauncher.library.component.view.KCUSpinner

/** 控件编辑页基类，收拢按钮 / 方向键编辑共用的行装配逻辑 */
internal abstract class EditViewDetails(
    protected val context: Context,
    protected val menu: GameMenu?,
) : EditViewDialog.Details {

    protected abstract val baseInfo: BaseInfoData

    /** 可见范围行：总是 / 游戏内 / 仅菜单 */
    protected fun setupVisibilitySpinner(spinner: KCUSpinner<String>) {
        val types = listOf(VisibilityType.ALWAYS, VisibilityType.IN_GAME, VisibilityType.MENU)
        spinner.setItems(
            listOf(
                context.getString(R.string.view_info_visibility_always),
                context.getString(R.string.view_info_visibility_game),
                context.getString(R.string.view_info_visibility_menu)
            )
        )
        spinner.setSelection(types.indexOf(baseInfo.visibilityType))
        spinner.setOnItemSelectedListener { index, _ -> baseInfo.visibilityTypeProperty().set(types[index]) }
    }

    /** 尺寸类型行：百分比 / 绝对 */
    protected fun setupSizeTypeSpinner(spinner: KCUSpinner<String>) {
        val types = listOf(SizeType.PERCENTAGE, SizeType.ABSOLUTE)
        spinner.setItems(
            listOf(
                context.getString(R.string.view_info_size_type_percentage),
                context.getString(R.string.view_info_size_type_absolute)
            )
        )
        spinner.setSelection(types.indexOf(baseInfo.sizeType))
        spinner.setOnItemSelectedListener { index, _ -> baseInfo.sizeTypeProperty().set(types[index]) }
    }

    /** 参考轴行：按屏幕宽 / 高 */
    protected fun setupReferenceSpinner(spinner: KCUSpinner<String>, percentageSize: PercentageSize) {
        val references = listOf(PercentageSize.Reference.SCREEN_WIDTH, PercentageSize.Reference.SCREEN_HEIGHT)
        spinner.setItems(
            listOf(
                context.getString(R.string.view_info_reference_width),
                context.getString(R.string.view_info_reference_height)
            )
        )
        spinner.setSelection(references.indexOf(percentageSize.reference))
        spinner.setOnItemSelectedListener { index, _ -> percentageSize.referenceProperty().set(references[index]) }
    }

    /** 数值滑条与数据属性双向绑定（量程 / 后缀由布局声明；进度按实际值 10 倍存储时 scale 传 10） */
    protected fun bindNumberSeekBar(bar: KCUNumberSeekBar, property: IntegerProperty, scale: Int = 1) {
        bar.setValueScale(scale)
        bar.addProgressListener()
        bar.setProgress(property.get())
        bar.progressProperty().bindBidirectional(property)
    }

    /**
     * 尺寸滑条：按尺寸类型在百分比（0~1000，实际值 10 倍）与绝对 dp 间切换量程 / 后缀 / 缩放，
     * 并在两个数据属性间换绑，同时联动参考轴行的可见性
     */
    protected fun bindSizeSeekBar(
        bar: KCUNumberSeekBar,
        percentageSize: IntegerProperty,
        absoluteSize: IntegerProperty,
        absoluteMax: Int,
        vararg referenceLayouts: View,
    ) {
        fun apply() {
            val percentage = baseInfo.sizeType == SizeType.PERCENTAGE
            bar.progressProperty().unbindBidirectional(percentageSize)
            bar.progressProperty().unbindBidirectional(absoluteSize)
            if (percentage) {
                bar.max = 1000
                bar.setSuffix("%")
                bar.setValueScale(10)
                bar.setProgress(percentageSize.get())
                bar.progressProperty().bindBidirectional(percentageSize)
            } else {
                bar.max = absoluteMax
                bar.setSuffix("dp")
                bar.setValueScale(1)
                bar.setProgress(absoluteSize.get())
                bar.progressProperty().bindBidirectional(absoluteSize)
            }
            for (layout in referenceLayouts) {
                layout.visibility = if (percentage) View.VISIBLE else View.GONE
            }
        }

        bar.addProgressListener()
        apply()
        baseInfo.sizeTypeProperty().addListener { apply() }
    }
}
