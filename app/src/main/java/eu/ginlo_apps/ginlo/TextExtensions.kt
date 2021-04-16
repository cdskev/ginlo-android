// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo

import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.widget.EditText
import android.widget.TextView

fun TextView.setAsUnderlinedText(text: String) {
    this.text = SpannableString(text).apply {
        setSpan(UnderlineSpan(), 0, text.length, 0)
    }
}

fun EditText.appendText(str: String) {
    if (text.isNullOrBlank()) {
        setText(str)
        setSelection(1)
    } else {
        val newText = text.append(str)
        text = newText
        setSelection(newText.length)
    }
}

fun EditText.insertText(str: String, pos: Int) {
    if (text.isNullOrBlank() || pos >= text.length) {
        appendText(str)
    } else {
        val a = text.substring(0, pos)
        val b = text.substring(pos)
        setText("$a$str$b")
        setSelection(pos + str.length)
    }
}

fun EditText.backspace() {
    if (!text.isNullOrBlank())
        dispatchKeyEvent(KeyEvent(0, 0, 0, KeyEvent.KEYCODE_DEL, 0, 0, 0, 0, KeyEvent.KEYCODE_ENDCALL))
}
