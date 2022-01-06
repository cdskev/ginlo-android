// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.theme

import android.app.Application
import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.emoji.widget.EmojiAppCompatTextView
import androidx.appcompat.widget.AppCompatImageView
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.view.ClickableEmojiconEditTextview
import eu.ginlo_apps.ginlo.view.EmojiconMediumTextView
import eu.ginlo_apps.ginlo.view.MaskImageView

class ThemedLayoutInflater(
        private val context: Context?,
        private val parentFactory: LayoutInflater.Factory,
        private val parentFactory2: LayoutInflater.Factory2
) : LayoutInflater.Factory, LayoutInflater.Factory2 {

    override fun onCreateView(s: String, context: Context, attributeSet: AttributeSet): View? {
        return parentFactory.onCreateView(s, context, attributeSet)
    }

    companion object {
        val genericViewTypes = listOf(
                "LinearLayout",
                "androidx.appcompat.widget.LinearLayoutCompat",
                "android.support.design.widget.CoordinatorLayout",
                "androidx.constraintlayout.widget.ConstraintLayout",
                "RelativeLayout",
                "View",
                "Spinner"
        )

        val textViewTypes = listOf(
                "TextView",
                "eu.ginlo_apps.ginlo.view.EmojiconMediumTextView",
                "androidx.emoji.widget.EmojiAppCompatTextView",
                "androidx.appcompat.widget.AppCompatTextView"
        )

        val editTextViewTypes = listOf(
                "EditText",
                "androidx.emoji.widget.EmojiAppCompatEditText",
                "eu.ginlo_apps.ginlo.view.ClickableEmojiconEditTextview"
        )

        val switchViewTypes = listOf(
                "androidx.appcompat.widget.SwitchCompat"
        )

        val imageViewTypes = listOf(
                "ImageView",
                "androidx.appcompat.widget.AppCompatImageView",
                "eu.ginlo_apps.ginlo.view.MaskImageView"
        )

        val imageButtonViewTypes = listOf(
                "androidx.appcompat.widget.AppCompatImageButton"
        )

        val buttonTypes = listOf(
                "androidx.appcompat.widget.AppCompatButton",
                "CheckBox"
        )
    }

    override fun onCreateView(view: View?, s: String, context: Context, attributeSet: AttributeSet): View? {
        val styledAttributes = context.obtainStyledAttributes(attributeSet, R.styleable.StyledView)
        try {
            return when {
                textViewTypes.contains(s) -> getTextView(view, s, context, attributeSet, styledAttributes)
                buttonTypes.contains(s) -> getButton(view, s, context, attributeSet, styledAttributes)
                genericViewTypes.contains(s) -> getView(view, s, context, attributeSet, styledAttributes)
                switchViewTypes.contains(s) -> getSwitch(view, s, context, attributeSet, styledAttributes)
                imageViewTypes.contains(s) -> getImageView(view, s, context, attributeSet, styledAttributes)
                editTextViewTypes.contains(s) -> getEditText(view, s, context, attributeSet, styledAttributes)
                imageButtonViewTypes.contains(s) -> getImageButton(view, s, context, attributeSet, styledAttributes)
                else -> parentFactory2.onCreateView(view, s, context, attributeSet)
            }
        } finally {
            // styledAttributes.recycle()
        }
    }

    private fun getImageButton(
            view: View?,
            s: String,
            context: Context,
            attributeSet: AttributeSet,
            styledAttributes: TypedArray
    ): View {
        var rc: androidx.appcompat.widget.AppCompatImageButton? = parentFactory2.onCreateView(
                view,
                s,
                context,
                attributeSet
        ) as? androidx.appcompat.widget.AppCompatImageButton
        if (rc == null) {
            rc = androidx.appcompat.widget.AppCompatImageButton(context, attributeSet)
        }
        Style.setStyledBackground(rc, styledAttributes)
        Style.setStyledBackgroundColorFilter(rc, styledAttributes)
        Style.setStyledIconColor(rc, styledAttributes)
        return rc
    }

    private fun getEditText(
            view: View?,
            s: String,
            context: Context,
            attributeSet: AttributeSet,
            styledAttributes: TypedArray
    ): View? {
        var rc: EditText? = parentFactory2.onCreateView(view, s, context, attributeSet) as? EditText
        if (rc == null) {
            when (s) {
                "androidx.emoji.widget.EmojiAppCompatEditText" -> rc =
                        androidx.emoji.widget.EmojiAppCompatEditText(context, attributeSet)
                "EditText" -> rc = EditText(context, attributeSet)
                "eu.ginlo_apps.ginlo.view.ClickableEmojiconEditTextview" -> rc =
                        ClickableEmojiconEditTextview(context, attributeSet)
            }
        }
        Style.setStyledTextSize(rc, styledAttributes)
        Style.setStyledBackground(rc, styledAttributes)
        Style.setStyledBackgroundColorFilter(rc, styledAttributes)
        Style.setStyledTextColor(rc, styledAttributes)
        Style.setStyledCompoundDrawables(rc, styledAttributes)
        Style.setStyledTextColorHint(rc, styledAttributes)
        return rc
    }

    private fun getImageView(
            view: View?,
            s: String,
            context: Context,
            attributeSet: AttributeSet,
            styledAttributes: TypedArray
    ): View? {
        var rc: ImageView? = parentFactory2.onCreateView(view, s, context, attributeSet) as? ImageView
        if (rc == null) {
            if (s == "ImageView") {
                rc = ImageView(context, attributeSet)
            } else if (s == "androidx.appcompat.widget.AppCompatImageView") {
                rc = AppCompatImageView(context, attributeSet)
            } else if (s == "eu.ginlo_apps.ginlo.view.MaskImageView") {
                rc = MaskImageView(context, attributeSet)
            }
        }
        if (rc != null) {
            Style.setStyledIconColor(rc, styledAttributes)
        }
        return rc
    }

    private fun getSwitch(
            view: View?,
            s: String,
            context: Context,
            attributeSet: AttributeSet,
            styledAttributes: TypedArray
    ): View {
        var rc: androidx.appcompat.widget.SwitchCompat? =
                parentFactory2.onCreateView(view, s, context, attributeSet) as? androidx.appcompat.widget.SwitchCompat
        if (rc == null) {
            rc = androidx.appcompat.widget.SwitchCompat(context, attributeSet)
        }
        ColorUtil.getInstance().colorizeSwitch(rc, this.context?.applicationContext as Application)
        Style.setStyledTextColor(rc, styledAttributes)
        return rc
    }

    private fun getView(
            view: View?,
            s: String,
            context: Context,
            attributeSet: AttributeSet,
            styledAttributes: TypedArray
    ): View? {
        var rc: View? = parentFactory2.onCreateView(view, s, context, attributeSet)
        if (rc == null) {
            when (s) {
                "LinearLayout" -> rc = LinearLayout(context, attributeSet)
                "androidx.appcompat.widget.LinearLayoutCompat" -> rc =
                        androidx.appcompat.widget.LinearLayoutCompat(context, attributeSet)
                "androidx.appcompat.widget.CoordinatorLayout" -> rc = CoordinatorLayout(context, attributeSet)
                "androidx.constraintlayout.widget.ConstraintLayout" -> CoordinatorLayout(context, attributeSet)
                "RelativeLayout" -> rc = RelativeLayout(context, attributeSet)
                "View" -> rc = View(context, attributeSet)
                "Spinner" -> rc = Spinner(context, attributeSet)
            }
        }
        Style.setStyledBackground(rc, styledAttributes)
        Style.setStyledBackgroundColorFilter(rc, styledAttributes)
        return rc
    }

    private fun getButton(
            view: View?,
            s: String,
            context: Context,
            attributeSet: AttributeSet,
            styledAttributes: TypedArray
    ): View? {
        var rc: Button? = parentFactory2.onCreateView(view, s, context, attributeSet) as? Button
        if (rc == null) {
            when (s) {
                "androidx.appcompat.widget.AppCompatButton" -> rc = AppCompatButton(context, attributeSet)
                "CheckBox" -> rc = CheckBox(context, attributeSet)
            }
        }
        Style.setStyledTextSize(rc, styledAttributes)
        Style.setStyledTextColor(rc, styledAttributes)
        Style.setStyledBackground(rc, styledAttributes)
        Style.setStyledBackgroundColorFilter(rc, styledAttributes)
        return rc
    }

    private fun getTextView(
            view: View?,
            s: String,
            context: Context,
            attributeSet: AttributeSet,
            styledAttributes: TypedArray
    ): View? {
        var textView = parentFactory2.onCreateView(view, s, context, attributeSet) as? TextView
        if (textView == null) {
            textView = when (s) {
                "TextView" -> TextView(context, attributeSet)
                "eu.ginlo_apps.ginlo.view.EmojiconMediumTextView" -> EmojiconMediumTextView(context, attributeSet)
                "androidx.emoji.widget.EmojiAppCompatTextView" -> EmojiAppCompatTextView(
                        context,
                        attributeSet
                )
                "androidx.appcompat.widget.AppCompatTextView" -> androidx.appcompat.widget.AppCompatTextView(
                        context,
                        attributeSet
                )
                else -> null
            }
        }
        Style.setStyledTextSize(textView, styledAttributes)
        Style.setStyledTextColor(textView, styledAttributes)
        Style.setStyledBackground(textView, styledAttributes)
        Style.setStyledBackgroundColorFilter(textView, styledAttributes)
        Style.setStyledCompoundDrawables(textView, styledAttributes)
        return textView
    }
}
