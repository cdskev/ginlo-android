// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.theme

import android.app.Application
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.util.*

object Style {
    fun setStyledTextColor(textView: TextView?, styledAttributes: TypedArray) {
        if (textView == null) return

        styledAttributes.getString(R.styleable.StyledView_styledTextColor)?.let { styledAttribute ->
            ScreenDesignUtil.getInstance().getNamedColor(
                styledAttribute,
                textView.getApplication()
            )?.let { color ->
                textView.setTextColor(
                    ColorStateList(
                        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                        intArrayOf(Color.argb(128, Color.red(color), Color.green(color), Color.blue(color)), color)
                    )
                )
            }
        }
    }

    fun setStyledCompoundDrawables(textView: TextView?, styledAttributes: TypedArray) {
        if (textView == null) return

        styledAttributes.getString(R.styleable.StyledView_styledIconColor)?.let { styledAttribute ->
            ScreenDesignUtil.getInstance().getNamedColor(
                styledAttribute,
                textView.getApplication()
            ).let { colorFilter ->
                textView.compoundDrawables.forEach { drawable ->
                    //drawable?.setColorFilter(colorFilter, PorterDuff.Mode.SRC_ATOP)
                    ScreenDesignUtil.setColorFilter(drawable, colorFilter)
                }
            }
        }
    }

    fun setStyledBackground(view: View?, styledAttributes: TypedArray) {
        if (view == null) return

        styledAttributes.getString(R.styleable.StyledView_styledBackground)?.let { styledBackground ->
            ScreenDesignUtil.getInstance().getNamedColor(
                styledBackground,
                view.getApplication()
            )?.let { color ->
                view.setBackgroundColor(color)
            }
        }
    }

    fun setStyledBackgroundColorFilter(view: View?, styledAttributes: TypedArray) {
        if (view == null) return

        styledAttributes.getString(R.styleable.StyledView_styledBackgroundColorFilter)?.let { styledBackground ->
            ScreenDesignUtil.getInstance().getNamedColor(
                styledBackground,
                view.getApplication()
            )?.let { color ->
                val background = view.background
                //background?.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
                ScreenDesignUtil.setColorFilter(background, color)
            }
        }
    }

    fun setStyledBackgroundColorFilter(button: Button?, styledAttributes: TypedArray) {
        if (button == null) return

        val styledBackground = styledAttributes.getString(R.styleable.StyledView_styledBackgroundColorFilter).orEmpty()
        val styledButtonType = styledAttributes.getString(R.styleable.StyledView_styledButtonType).orEmpty()

        if (styledButtonType.isNotBlank()) {
            val rippleColor: Int
            val colorList: ColorStateList
            var colorDrawable: ColorDrawable? = null

            var color: Int? = null
            if (styledBackground.isNotBlank()) {
                color = ScreenDesignUtil.getInstance().getNamedColor(
                    styledBackground,
                    button.getApplication()
                )
            }

            when (styledButtonType) {
                "borderless" -> {
                    if (color == null) {
                        color = ScreenDesignUtil.getInstance()
                            .getMainContrastColor(button.context.applicationContext as Application)
                    }

                    rippleColor = Color.argb(27, Color.red(color), Color.green(color), Color.blue(color))
                }
                "normal" -> {
                    rippleColor = ContextCompat.getColor(button.context, R.color.rippleColorButtonNormal)

                    if (color == null) {
                        color = ScreenDesignUtil.getInstance().getAppAccentColor(button.getApplication())
                    }

                    colorDrawable = ColorDrawable(color)
                }
                "invite" -> {
                    if (styledBackground.isNotBlank()) {
                        if (color != null) {
                            val background = button.background
                            //background?.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
                            ScreenDesignUtil.setColorFilter(background, color)
                        }
                    }
                    return
                }
                else -> return
            }

            colorList = ColorStateList(
                arrayOf(intArrayOf()),
                intArrayOf(rippleColor)
            )

            val drawable = ContextCompat.getDrawable(
                button.context,
                R.drawable.button_ripple_mask
            )

            val rd = RippleDrawable(colorList, colorDrawable, drawable)

            if (colorDrawable != null) {
                val states = StateListDrawable()
                states.addState(
                    intArrayOf(-android.R.attr.state_enabled), ColorDrawable(
                        Color.argb(
                            128, Color.red(color), Color.green(color), Color.blue(
                                color
                            )
                        )
                    )
                )
                states.addState(intArrayOf(), rd)

                button.background = states
            } else {
                button.background = rd
            }
        } else {
            if (styledBackground.isNotBlank()) {
                val c = ScreenDesignUtil.getInstance().getNamedColor(styledBackground, button.getApplication())
                if (c != null) {
                    val background = button.background
                    //background?.setColorFilter(c, PorterDuff.Mode.SRC_ATOP)
                    ScreenDesignUtil.setColorFilter(background, c)
                }
            }
        }
    }

    fun setStyledBackgroundColorFilter(editText: EditText?, styledAttributes: TypedArray) {
        if (editText == null) return

        val styleEditTextNoLineBg = styledAttributes.getBoolean(R.styleable.StyledView_styledEditTextNoLineBG, false)

        if (styleEditTextNoLineBg) return

        val styledBackground = styledAttributes.getString(R.styleable.StyledView_styledBackgroundColorFilter).orEmpty()
        val activeColor: Int =
            if (styledBackground.isNotBlank()) {
                ScreenDesignUtil.getInstance().getNamedColor(styledBackground, editText.getApplication())
            } else {
                ScreenDesignUtil.getInstance().getAppAccentColor(editText.getApplication())
            }

        val mainContrastColor =
            ScreenDesignUtil.getInstance().getMainContrastColor(editText.getApplication())

        val normalColor =
            Color.argb(27, Color.red(mainContrastColor), Color.green(mainContrastColor), Color.blue(mainContrastColor))
        val disabledColor =
            Color.argb(77, Color.red(mainContrastColor), Color.green(mainContrastColor), Color.blue(mainContrastColor))

        ScreenDesignUtil.changeEditTextUnderlineColor(editText, activeColor, normalColor, disabledColor)
    }

    fun setStyledTextColorHint(textView: TextView?, styledAttributes: TypedArray) {
        if (textView == null) return

        styledAttributes.getString(R.styleable.StyledView_styledTextColorHint)?.let { styledTextColorHint ->
            ScreenDesignUtil.getInstance().getNamedColor(styledTextColorHint, textView.getApplication())?.let {
                textView.setHintTextColor(it)
            }
        }
    }

    fun setStyledTextSize(textView: TextView?, styledAttributes: TypedArray) {
        if (textView == null) return

        styledAttributes.getString(R.styleable.StyledView_styledTextSize)?.let { styledTextSize ->

            ScreenDesignUtil.getInstance().getNamedTextSize(styledTextSize, textView.getApplication())?.let {
                textView.textSize = it
            }
        }
    }

    fun setStyledIconColor(imageView: ImageView?, styledAttributes: TypedArray) {
        if (imageView == null) return

        styledAttributes.getString(R.styleable.StyledView_styledIconColor)?.let { styledIconColor ->
            ScreenDesignUtil.getInstance().getNamedColor(styledIconColor, imageView.getApplication())?.let { color ->
                imageView.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)

            }
        }
    }

    private fun View.getApplication(): Application =
        this.context.applicationContext as Application
}