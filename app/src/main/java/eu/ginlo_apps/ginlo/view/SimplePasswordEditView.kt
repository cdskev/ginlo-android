// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Style
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import eu.ginlo_apps.ginlo.R

class SimplePasswordEditView : View {
    private var mTextLength = -1

    private val defaultPaint: Paint by lazy {
        Paint().apply {
            style = Style.FILL
            color = ContextCompat.getColor(context, R.color.pwd_strength_grey)
        }
    }

    private val activatedPaint: Paint by lazy {
        Paint().apply {
            style = Style.STROKE
            color = ContextCompat.getColor(context, R.color.actionSecondary)
        }
    }

    private val filledPaint: Paint by lazy {
        Paint().apply {
            style = Style.FILL
            color = ContextCompat.getColor(context, R.color.mainContrast)
        }
    }

    private var dimPx: Int = 0

    private var fillDimPx: Int = 0

    private var activatedDimPx: Int = 0

    private val rect: RectF = RectF()

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        dimPx = 6 * resources.displayMetrics.densityDpi / 160
        fillDimPx = 6 * resources.displayMetrics.densityDpi / 160
        activatedDimPx = 7 * resources.displayMetrics.densityDpi / 160

        activatedPaint.strokeWidth = (2 * resources.displayMetrics.densityDpi / 160).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = measuredWidth
        val height = measuredHeight
        val quarterWidth = width / 4
        val halfHeight = height / 2

        for (i in 0..3) {
            when {
                i < mTextLength -> {
                    rect.left = (i * quarterWidth + quarterWidth / 2 - fillDimPx).toFloat()
                    rect.right = (i * quarterWidth + quarterWidth - quarterWidth / 2 + fillDimPx).toFloat()
                    rect.top = (halfHeight - fillDimPx).toFloat()
                    rect.bottom = (halfHeight + fillDimPx).toFloat()

                    canvas.drawOval(rect, filledPaint)
                }
                i == mTextLength -> {
                    rect.left = (i * quarterWidth + quarterWidth / 2 - activatedDimPx).toFloat()
                    rect.right = (i * quarterWidth + quarterWidth - quarterWidth / 2 + activatedDimPx).toFloat()
                    rect.top = (halfHeight - activatedDimPx).toFloat()
                    rect.bottom = (halfHeight + activatedDimPx).toFloat()

                    canvas.drawOval(rect, activatedPaint)
                }
                else -> {
                    rect.left = (i * quarterWidth + quarterWidth / 2 - dimPx).toFloat()
                    rect.right = (i * quarterWidth + quarterWidth - quarterWidth / 2 + dimPx).toFloat()
                    rect.top = (halfHeight - dimPx).toFloat()
                    rect.bottom = (halfHeight + dimPx).toFloat()

                    canvas.drawOval(rect, defaultPaint)
                }
            }
        }
    }

    fun setTextLength(textLength: Int) {
        mTextLength = textLength
        invalidate()
    }
}
