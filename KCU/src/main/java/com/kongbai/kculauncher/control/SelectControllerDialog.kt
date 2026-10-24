package com.kongbai.kculauncher.control

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kongbai.kculauncher.mio.ui.adapter.SpacingItemDecoration
import com.kongbai.kculauncher.mio.ui.applySelectableItemStyle
import com.kongbai.kculauncher.mio.util.getScreenHeight
import com.kongbai.kculauncher.mio.util.getScreenWidth
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.databinding.DialogSelectControllerBinding
import com.kongbai.kculauncher.databinding.ItemControllerSelectableBinding
import com.kongbai.kculauncher.setting.Controller
import com.kongbai.kculauncher.setting.Controllers
import com.kongbai.kculauncher.library.component.dialog.KCUDialog
import com.kongbai.kculauncher.library.util.ConvertUtils

/**
 * 控制器选择对话框，外观与渲染器选择弹窗一致（卡片 + 当前项主题色高亮），点击条目即选中回调。
 */
class SelectControllerDialog(
    context: Context,
    currentId: String,
    private val callback: (Controller) -> Unit
) : KCUDialog(context) {

    private class ControllerAdapter(
        private val context: Context,
        private val controllers: List<Controller>,
        private val currentId: String,
        private val callback: (Controller) -> Unit
    ) : RecyclerView.Adapter<ControllerAdapter.Holder>() {

        private val density = context.resources.displayMetrics.density

        class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
            ItemControllerSelectableBinding.inflate(LayoutInflater.from(context), parent, false).root
        )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val binding = ItemControllerSelectableBinding.bind(holder.itemView)
            val controller = controllers[position]
            // 选中态统一样式：当前控制器主题色底 + 勾选，其余普通卡片
            applySelectableItemStyle(
                context,
                binding.root,
                binding.check,
                controller.id == currentId,
                density
            )
            binding.name.text = controller.name
            binding.version.text = controller.version
            binding.description.text = controller.description
            binding.root.setOnClickListener { callback(controller) }
        }

        override fun getItemCount(): Int = controllers.size
    }

    init {
        val params = window?.attributes
        params?.width = ConvertUtils.dip2px(context, 500f)
        params?.height = if (getScreenWidth().toFloat() / getScreenHeight().toFloat() >= 1.5f) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            getScreenHeight() / 2
        }
        window?.attributes = params
        val binding = DialogSelectControllerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val adapter = ControllerAdapter(context, Controllers.getControllers(), currentId) { controller ->
            callback(controller)
            dismiss()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.addItemDecoration(SpacingItemDecoration(ConvertUtils.dip2px(context, 10f)))
        binding.recyclerView.adapter = adapter
        binding.cancel.setOnClickListener { dismiss() }
    }
}