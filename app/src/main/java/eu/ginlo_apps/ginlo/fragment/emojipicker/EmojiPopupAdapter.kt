// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.fragment.emojipicker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.emoji.widget.EmojiTextView
import androidx.recyclerview.widget.RecyclerView
import eu.ginlo_apps.ginlo.R

class EmojiPopupAdapter(
        private val content: List<String>,
        private val onClick: (String) -> Unit
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            EmojiPopupItem(LayoutInflater.from(parent.context).inflate(R.layout.emoji_picker_popup_item, parent, false))

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) =
            (holder as EmojiPopupItem).bind(content[position], onClick)

    override fun getItemCount(): Int = content.size

    inner class EmojiPopupItem(view: View) : RecyclerView.ViewHolder(view) {
        private val emojiTextView = view.findViewById<EmojiTextView>(R.id.emoji_picker_item_text)

        fun bind(item: String, onClick: (String) -> Unit) {
            emojiTextView.text = item
            emojiTextView.setOnClickListener { onClick(item) }
        }
    }
}
