package com.kongbai.kculauncher.mio.ui.dialog

import android.content.Context
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.kongbai.kculauncher.mio.manager.RendererManager
import com.kongbai.kculauncher.mio.ui.adapter.RendererSelectItemAdapter
import com.kongbai.kculauncher.mio.ui.adapter.SpacingItemDecoration
import com.kongbai.kculauncher.mio.util.getScreenHeight
import com.kongbai.kculauncher.mio.util.getScreenWidth
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.databinding.DialogRendererSelectBinding
import com.kongbai.kculauncher.setting.Profiles
import com.kongbai.kculauncher.library.component.dialog.KCUDialog
import com.kongbai.kculauncher.library.util.ConvertUtils
import java.util.function.Consumer

class RendererSelectDialog(
    context: Context,
    val isGlobal: Boolean,
    val callback: Consumer<String>
) : KCUDialog(context) {

    init {
        val params = window?.attributes
        params?.width = ConvertUtils.dip2px(context, 500f)
        val ratio = getScreenWidth().toFloat() / getScreenHeight().toFloat()
        if (ratio >= 1.5f) {
            params?.height = WindowManager.LayoutParams.MATCH_PARENT
        } else {
            params?.height = getScreenHeight() * 1 / 2
        }
        window?.attributes = params
        val binding = DialogRendererSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.title.text = context.getString(R.string.settings_kcu_renderer)
        val currentId = currentRendererId()
        val adapter = RendererSelectItemAdapter(
            context,
            RendererManager.rendererList,
            currentId
        ) { renderer ->
            val versionSetting =
                if (isGlobal) Profiles.getSelectedProfile().globalVersionSetting else Profiles.getSelectedProfile().versionSetting
            versionSetting.renderer = renderer.id
            dismiss()
            callback.accept(renderer.des)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.addItemDecoration(
            SpacingItemDecoration(ConvertUtils.dip2px(context, 10f))
        )
        binding.recyclerView.adapter = adapter
        // 打开时定位到当前选中的渲染器（首次布局前调用，LayoutManager 会从该位置开始布局）
        val selectedIndex = RendererManager.rendererList.indexOfFirst { it.id == currentId }
        if (selectedIndex > 0) {
            binding.recyclerView.scrollToPosition(selectedIndex)
        }
        binding.refresh.setOnClickListener {
            RendererManager.refresh(context)
            // rendererList 是同一实例被 clear 后重填，直接全量刷新
            adapter.notifyDataSetChanged()
        }
        binding.cancel.setOnClickListener {
            dismiss()
        }
    }

    private fun currentRendererId(): String {
        return if (isGlobal) Profiles.getSelectedProfile().globalVersionSetting.renderer
        else Profiles.getSelectedProfile().versionSetting.renderer
    }
}
