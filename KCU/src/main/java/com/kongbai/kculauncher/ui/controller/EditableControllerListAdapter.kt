package com.kongbai.kculauncher.ui.controller

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.setting.Controller
import com.kongbai.kculauncher.library.component.KCUAdapter
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog
import com.kongbai.kculauncher.library.component.view.KCUImageButton
import com.kongbai.kculauncher.library.component.view.KCUTextView

/**
 * 控制布局列表适配器：列表项选择/删除通过回调交给页面处理，
 * 选中高亮由 [getSelected] 提供，不使用 fakefx property。
 */
class EditableControllerListAdapter(
    context: Context,
    private val list: List<Controller>,
    private val getSelected: () -> Controller?,
    private val onSelect: (Controller) -> Unit,
    private val onDelete: (Controller) -> Unit
) : KCUAdapter(context) {

    override fun getCount(): Int = list.size

    override fun getItem(i: Int): Any = list[i]

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun getView(i: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder
        if (convertView == null) {
            holder = ViewHolder()
            view = LayoutInflater.from(context)
                .inflate(R.layout.item_controller_editable, parent, false)
            holder.parent = view.findViewById(R.id.parent)
            holder.name = view.findViewById(R.id.name)
            holder.version = view.findViewById(R.id.version)
            holder.delete = view.findViewById(R.id.delete)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }
        val controller = list[i]
        holder.parent.background = if (controller == getSelected()) {
            context.getDrawable(R.drawable.bg_container_transparent_selected)
        } else {
            context.getDrawable(R.drawable.bg_container_transparent_clickable)
        }
        holder.name.text = controller.name
        holder.version.text = controller.version
        holder.parent.setOnClickListener { onSelect(controller) }
        holder.delete.setOnClickListener {
            KCUAlertDialog.Builder(context)
                .setAlertLevel(KCUAlertDialog.AlertLevel.INFO)
                .setCancelable(false)
                .setMessage(context.getString(R.string.control_delete))
                .setPositiveButton { onDelete(controller) }
                .setNegativeButton(null)
                .create()
                .show()
        }
        return view
    }

    private class ViewHolder {
        lateinit var parent: ConstraintLayout
        lateinit var name: KCUTextView
        lateinit var version: KCUTextView
        lateinit var delete: KCUImageButton
    }
}
