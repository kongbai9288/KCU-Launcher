package com.kongbai.kculauncher.control

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.tabs.TabLayout
import com.kongbai.kculauncher.mio.util.getScreenWidth
import com.kongbai.kculauncher.mio.util.getScreenHeight
import com.kongbai.kculauncher.control.data.BaseInfoData
import com.kongbai.kculauncher.control.data.ButtonEventData
import com.kongbai.kculauncher.control.data.ButtonStyles
import com.kongbai.kculauncher.control.data.ControlButtonData
import com.kongbai.kculauncher.control.data.ControlViewGroup
import com.kongbai.kculauncher.databinding.ViewEditButtonEventBinding
import com.kongbai.kculauncher.databinding.ViewEditButtonEventChildBinding
import com.kongbai.kculauncher.databinding.ViewEditButtonInfoBinding
import com.kongbai.kculauncher.util.FXUtils
import com.kongbai.kculauncher.core.fakefx.collections.FXCollections
import com.kongbai.kculauncher.library.component.view.KCUSpinner
import com.kongbai.kculauncher.library.util.ConvertUtils
import java.util.stream.Collectors

/** 按钮控件编辑页：文本 / 位置尺寸 / 样式 + 触发事件配置 */
internal class EditButtonDetails(
    context: Context,
    menu: GameMenu?,
    private val data: ControlButtonData,
) : EditViewDetails(context, menu) {

    override val baseInfo: BaseInfoData get() = data.baseInfo

    private val infoBinding = ViewEditButtonInfoBinding.inflate(LayoutInflater.from(context))
    private val eventBinding = ViewEditButtonEventBinding.inflate(LayoutInflater.from(context))

    override val infoLayout: View get() = infoBinding.root
    override val eventLayout: View get() = eventBinding.root

    init {
        setupInfo()
        setupEvent()
    }

    override fun getView(): ControlButtonData {
        val view = data.clone()
        // 多字符模式：输入框里用 | 分隔的多套文字在这里拆成候选列表，
        // 按钮实际显示第一套，其余由运行时切换事件轮换
        val raw = view.text ?: ""
        val parts = raw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size > 1) {
            view.setTexts(parts)
            view.setText(parts[0])
        } else {
            view.setTexts(emptyList())
        }
        return view
    }

    @Suppress("UNCHECKED_CAST")
    private fun setupInfo() {
        // ViewBinding 对布局中的泛型控件生成 raw 类型，条目实际为 String
        val visibility = infoBinding.visibility as KCUSpinner<String>
        val sizeType = infoBinding.sizeType as KCUSpinner<String>

        // 多字符模式：候选文字用 | 分隔显示在同一输入框，保存时再拆开
        infoBinding.text.setText(if (data.texts.isEmpty()) data.text else data.texts.joinToString("|"))
        data.textProperty().bind(infoBinding.text.stringProperty())

        setupVisibilitySpinner(visibility)
        setupSizeTypeSpinner(sizeType)
        setupReferenceSpinner(infoBinding.widthReference as KCUSpinner<String>, baseInfo.percentageWidth)
        setupReferenceSpinner(infoBinding.heightReference as KCUSpinner<String>, baseInfo.percentageHeight)
        bindNumberSeekBar(infoBinding.xPosition, baseInfo.xPositionProperty(), scale = 10)
        bindNumberSeekBar(infoBinding.yPosition, baseInfo.yPositionProperty(), scale = 10)
        bindSizeSeekBar(
            infoBinding.width, baseInfo.percentageWidth.sizeProperty(), baseInfo.absoluteWidthProperty(),
            ConvertUtils.px2dip(context, getScreenWidth().toFloat()), infoBinding.widthReferenceLayout
        )
        bindSizeSeekBar(
            infoBinding.height, baseInfo.percentageHeight.sizeProperty(), baseInfo.absoluteHeightProperty(),
            ConvertUtils.px2dip(context, getScreenHeight().toFloat()), infoBinding.heightReferenceLayout
        )

        infoBinding.styleText.text = data.style.name
        infoBinding.style.setOnClickListener {
            val target = ButtonStyles.findStyleByName(data.style.name)
            ButtonStyleDialog(context, true, target) { style ->
                data.style = style
                infoBinding.styleText.text = style.name
            }.apply { setGameMenu(menu) }.show()
        }
    }

    private fun setupEvent() {
        with(eventBinding) {
            pointerFollow.isChecked = data.event.isPointerFollow
            movable.isChecked = data.event.isMovable
            swipable.isChecked = data.event.isSwipable
            FXUtils.bindBoolean(pointerFollow, data.event.pointerFollowProperty())
            FXUtils.bindBoolean(movable, data.event.movableProperty())
            FXUtils.bindBoolean(swipable, data.event.swipableProperty())
        }

        val children = listOf(
            ViewEditButtonEventChildBinding.inflate(LayoutInflater.from(context)),
            ViewEditButtonEventChildBinding.inflate(LayoutInflater.from(context)),
            ViewEditButtonEventChildBinding.inflate(LayoutInflater.from(context)),
            ViewEditButtonEventChildBinding.inflate(LayoutInflater.from(context))
        )
        val events = listOf(
            data.event.pressEvent,
            data.event.longPressEvent,
            data.event.clickEvent,
            data.event.doubleClickEvent
        )
        children.forEachIndexed { index, child -> setupEventChild(child, events[index]) }

        eventBinding.container.addView(children[0].root)
        // 与游戏菜单条目一致的卡片背景
        eventBinding.tabLayout.background = menuCardBackground(context.resources.displayMetrics.density)
        eventBinding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                eventBinding.container.removeAllViews()
                eventBinding.container.addView(children[tab.position].root)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupEventChild(binding: ViewEditButtonEventChildBinding, event: ButtonEventData.Event) {
        with(binding) {
            autoKeep.isChecked = event.isAutoKeep
            autoClick.isChecked = event.isAutoClick
            openMenu.isChecked = event.isOpenMenu
            touchMode.isChecked = event.isSwitchTouchMode
            mouseMode.isChecked = event.isSwitchMouseMode
            input.isChecked = event.isInput
            quickInput.isChecked = event.isQuickInput
            pasteClipboard.isChecked = event.isPasteClipboard
            switchText.isChecked = event.isSwitchText
            pasteDismissIme.isChecked = event.isPasteDismissIme
            FXUtils.bindBoolean(autoKeep, event.autoKeepProperty())
            FXUtils.bindBoolean(autoClick, event.autoClickProperty())
            FXUtils.bindBoolean(openMenu, event.openMenuProperty())
            FXUtils.bindBoolean(touchMode, event.switchTouchModeProperty())
            FXUtils.bindBoolean(mouseMode, event.switchMouseModeProperty())
            FXUtils.bindBoolean(input, event.inputProperty())
            FXUtils.bindBoolean(quickInput, event.quickInputProperty())
            FXUtils.bindBoolean(pasteClipboard, event.pasteClipboardProperty())
            FXUtils.bindBoolean(switchText, event.switchTextProperty())
            FXUtils.bindBoolean(pasteDismissIme, event.pasteDismissImeProperty())

            outputText.setText(event.outputText)
            event.outputTextProperty().bind(outputText.stringProperty())

            keycode.setOnClickListener {
                SelectKeycodeDialog(context, event.outputKeycodesList(),
                    singleSelection = false,
                    mouse = true
                ).show()
            }
            bindGroup.setOnClickListener {
                val selected = FXCollections.observableArrayList<ControlViewGroup>().apply {
                    menu?.controller?.viewGroups()
                        ?.filter { event.bindViewGroupList().contains(it.id) }
                        ?.let { addAll(it) }
                }
                ViewGroupDialog(context, menu, true, selected) { viewGroups ->
                    event.setBindViewGroup(FXCollections.observableList(viewGroups.stream().map { it.id }.collect(Collectors.toList())))
                }.show()
            }
        }
    }
}
