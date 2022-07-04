// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.util.ImageUtil
import eu.ginlo_apps.ginlo.util.MetricsUtil
import kotlin.math.roundToInt

class BackgroundGalleryAdapter(private val context: Context) : BaseAdapter() {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        if (position < 0 || position >= RESOURCE_IDS.size) {
            return getImageView(convertView)
        }
        val resourceId = RESOURCE_IDS[position]
        initImageViewHeight(resourceId)
        return getImageView(convertView).apply {
            contentDescription =
                context.resources.getString(R.string.content_description_chatbackgrounds_image) + position
            setImageBitmap(
                ImageUtil.decodeSampledBitmapFromResource(
                    context.resources,
                    RESOURCE_IDS[position],
                    imageViewWidth,
                    imageViewHeight
                )
            )
        }
    }

    private var imageViewHeight: Int = 0
    private fun initImageViewHeight(resourceId: Int): Int {
        if (imageViewHeight > 0) return imageViewHeight
        val options = ImageUtil.getDimensions(context.resources, resourceId)

        imageViewHeight =
            (options.outHeight.toFloat() / options.outWidth.toFloat() * imageViewWidth.toFloat()).roundToInt()
        return imageViewHeight
    }

    private fun getImageView(convertView: View?): ImageView {
        return if (convertView == null) {
            ImageView(context).apply {
                layoutParams = AbsListView.LayoutParams(imageViewWidth, imageViewHeight)
            }
        } else {
            convertView as ImageView
        }
    }

    override fun getCount(): Int {
        return RESOURCE_IDS.size
    }

    override fun getItem(position: Int): Any {
        return RESOURCE_IDS[position]
    }

    override fun getItemId(arg0: Int): Long {
        return arg0.toLong()
    }

    private val imageViewWidth: Int by lazy {
        MetricsUtil.getDisplayMetrics(context).let {
            (it.density * 130).roundToInt()
        }
    }

    companion object {
        private val RESOURCE_IDS = intArrayOf(
            R.drawable.backgroundart_01,
            R.drawable.backgroundart_02,
            R.drawable.backgroundart_03,
            R.drawable.backgroundart_04,
            R.drawable.backgroundart_05,
            R.drawable.backgroundart_06,
            R.drawable.backgroundart_07,
            R.drawable.backgroundart_08,
            R.drawable.backgroundart_09,
            R.drawable.backgroundart_10,
            R.drawable.backgroundart_11
        )
    }
}
