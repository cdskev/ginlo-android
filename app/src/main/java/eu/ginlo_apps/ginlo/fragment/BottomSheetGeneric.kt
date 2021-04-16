// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class BottomSheetGeneric : Fragment() {
    private var resourceId = -1

    private var hiddenViews: List<Int> = listOf()

    private var titleToSet : String = ""

    private var titleViewId : Int = -1

    fun setResourceID(resourceId: Int) {
        this.resourceId = resourceId
    }

    fun setHiddenViews(hiddenViews: List<Int>) {
        this.hiddenViews = hiddenViews
    }

    fun setBottomSheetTitle(viewId: Int, title: String)
    {
        titleViewId = viewId
        titleToSet = title
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return if (resourceId != -1) {
            val layout = inflater.inflate(resourceId, container, false)

            hiddenViews.forEach { viewId ->
                layout.findViewById<View>(viewId)?.apply {
                    visibility = View.GONE
                }
            }

            if(titleViewId != -1 && titleToSet.isNotEmpty())
            {
                val titleView = layout.findViewById<TextView>(titleViewId)
                titleView?.text = titleToSet
                titleView?.visibility = View.VISIBLE
            }

            layout
        } else {
            LinearLayout(inflater.context)
        }
    }
}
