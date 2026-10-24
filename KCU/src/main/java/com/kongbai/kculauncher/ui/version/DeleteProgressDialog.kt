package com.kongbai.kculauncher.ui.version

import android.content.Context
import android.view.View
import android.widget.ScrollView
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.core.task.Schedulers
import com.kongbai.kculauncher.library.component.dialog.KCUDialog
import com.kongbai.kculauncher.library.component.view.KCUTextView

/**
 * 版本删除进度对话框：实时滚动展示删除过程中的每一步动作，删除完成前不可关闭。
 */
class DeleteProgressDialog(context: Context, title: String) : KCUDialog(context) {

    private val logScroll: ScrollView
    private val logView: KCUTextView

    init {
        setContentView(R.layout.dialog_delete_version)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        findViewById<KCUTextView>(R.id.title)!!.text = title
        logScroll = findViewById(R.id.logScroll)!!
        logView = findViewById(R.id.logView)!!
        show()
    }

    /** 追加一行进度信息并滚动到底部，可在任意线程调用 */
    fun appendLog(message: String) {
        Schedulers.androidUIThread().execute {
            logView.append(message)
            logView.append("\n")
            logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }
}
