package com.kongbai.kculauncher.mio.util

import android.content.Context
import com.kongbai.kculauncher.mio.dialog.ItemSelectionDialog
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog
import com.kongbai.kculauncher.library.component.dialog.KCUDialog

fun showErrorDialog(context: Context, message: Int, vararg args: String?) {
    showErrorDialog(context, context.getString(message, *args))
}

fun showErrorDialog(context: Context, message: String) {
    KCUAlertDialog.Builder(context)
        .setAlertLevel(KCUAlertDialog.AlertLevel.ALERT)
        .setMessage(message)
        .setNegativeButton(context.getString(R.string.dialog_positive)) { }
        .create()
        .show()
}

@JvmOverloads
fun showItemSelectionDialog(
    context: Context,
    title: String = "",
    items: List<String>,
    small: Boolean = true,
    selectedIndex: Int = -1,
    callback: (Int, String) -> Unit
) {
    ItemSelectionDialog(context, title, items, small, selectedIndex) { position, item ->
        callback(position, item)
    }.show()
}

fun showWarningDialog(context: Context, message: String, onConfirm: () -> Unit) {
    KCUAlertDialog.Builder(context)
        .setAlertLevel(KCUAlertDialog.AlertLevel.INFO)
        .setMessage(message)
        .setPositiveButton {
            onConfirm()
        }
        .setNegativeButton {

        }
        .create()
        .show()
}