// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.fragment

import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.core.content.ContextCompat
import eu.ginlo_apps.ginlo.BaseActivity
import eu.ginlo_apps.ginlo.LoginActivity
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.themedInflater
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.KeyboardUtil
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import kotlinx.android.synthetic.main.simple_password_layout.view.simple_password_edit_text_field
import kotlinx.android.synthetic.main.simple_password_layout.view.simple_password_hidden_text_field

class SimplePasswordFragment : BasePasswordFragment() {

    private lateinit var rootView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layoutInflater = if (activity is BaseActivity) {
            LayoutInflater.from(activity).themedInflater(activity)
        } else inflater

        rootView = layoutInflater.inflate(R.layout.simple_password_layout, container, false)

        rootView.simple_password_edit_text_field.setOnClickListener {
            rootView.simple_password_hidden_text_field.requestFocus()
            rootView.simple_password_hidden_text_field.callOnClick()
            KeyboardUtil.toggleSoftInputKeyboard(requireActivity(), rootView.simple_password_hidden_text_field, true)
        }

        rootView.simple_password_hidden_text_field.requestFocus()

        // there are issues with detecting the backspace key with the google
        // keyboard:
        // http://stackoverflow.com/questions/18581636/android-cannot-capture-backspace-delete-press-in-soft-keyboard

        rootView.simple_password_hidden_text_field.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                onCompleteAction()
            } else false
        }

        rootView.simple_password_hidden_text_field.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                rootView.simple_password_edit_text_field.setTextLength(s.length)
                if (s.length == 4) {
                    onCompleteAction()
                }
            }
        })

        rootView.simple_password_edit_text_field.setTextLength(rootView.simple_password_hidden_text_field.text.length)
        openKeyboard()
        val nextButton = requireActivity().findViewById<Button>(R.id.next_button)
        if (RuntimeConfig.isBAMandant() && nextButton != null && activity != null) {
            ColorUtil.setColorFilter(nextButton.background, ColorUtil.getInstance().getAppAccentColor(activity?.application))
        }

        return rootView
    }

    private fun onCompleteAction(): Boolean {
        (activity as? LoginActivity)?.let {
            it.handleLogin()
            return true
        }

        requireActivity().findViewById<Button>(R.id.next_button).performClick()
        return false
    }

    override fun getPassword(): String {
        return rootView.simple_password_hidden_text_field.text.toString()
    }

    override fun clearInput() {
        rootView.simple_password_hidden_text_field.setText("")
        rootView.simple_password_edit_text_field.setTextLength(-1)
    }

    override fun getEditText(): EditText? {
        return rootView.simple_password_hidden_text_field
    }

    override fun openKeyboard() {
        if (activity != null) {
            rootView.simple_password_hidden_text_field.requestFocus()
            KeyboardUtil.toggleSoftInputKeyboard(activity, rootView.simple_password_hidden_text_field, true)
        }
    }
}
