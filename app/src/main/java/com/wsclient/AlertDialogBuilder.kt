package com.wsclient

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText

object AlertDialogBuilder {
    fun show(
        context: Context,
        title: String,
        input: EditText,
        onOk: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Добавить") { _, _ -> onOk() }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
