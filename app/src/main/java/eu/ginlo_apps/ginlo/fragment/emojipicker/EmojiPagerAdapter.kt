// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.emojipicker

import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.model.Emoji

class EmojiPagerAdapter(
    private val contentList: List<String>,
    private val emojiList: List<Emoji>,
    private var recentlyUsed: LruCache<Int, String>,
    private val emojiColumns: Int,
    private val onClick: (String) -> Unit
): PagerAdapter() {

    override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`

    override fun getCount(): Int = contentList.size + 1

    override fun instantiateItem(container: ViewGroup, position: Int): Any =
        with(LayoutInflater.from(container.context).inflate(R.layout.emoji_picker_page, container, false)) {
            setupAdapter(this, position)
            container.addView(this)
            this
        }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    private fun setupAdapter(view: View, position: Int) {
        val content =  (view.findViewById(R.id.emoji_picker_content) as RecyclerView)
        content.layoutManager = GridLayoutManager(view.context, emojiColumns)

        if (position == 0) {
            content.adapter = EmojiPickerAdapter(
                recentlyUsed.snapshot().values.toMutableList()
                    .map { Emoji("", it, "", false) },
                true
            ) {
                onClick(it)
            }
        } else {
            content.adapter = EmojiPickerAdapter(
                emojiList.filter { it.category == contentList[position - 1] },
                true
            ) {
                onClick(it)
                recentlyUsed.put(recentlyUsed.putCount(), it)
            }
        }
    }
}
