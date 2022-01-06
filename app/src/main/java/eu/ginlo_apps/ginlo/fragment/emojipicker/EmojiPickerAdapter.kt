// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.emojipicker

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.emoji.text.EmojiCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.emoji.widget.EmojiTextView
import androidx.recyclerview.widget.RecyclerView
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.model.Emoji
import eu.ginlo_apps.ginlo.model.EmojiType

class EmojiPickerAdapter(
        private val content: List<Emoji>,
        private val popupEnabled: Boolean,
        private val onClick: (String) -> Unit
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val ZERO_WIDTH_JOINER ="\u200D"
        const val COLOR_JOINER = "\uD83C"
        const val SKIN_TONE_TYPE_0 = "\uDFFB"
        const val SKIN_TONE_TYPE_1 = "\uDFFC"
        const val SKIN_TONE_TYPE_2 = "\uDFFD"
        const val SKIN_TONE_TYPE_3 = "\uDFFE"
        const val SKIN_TONE_TYPE_4 = "\uDFFF"
        const val GENDER_MALE = "\u2642"
        const val GENDER_FEMALE = "\u2640"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            EmojiPickerItem(LayoutInflater.from(parent.context).inflate(R.layout.emoji_picker_item, parent, false))

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) =
            (holder as EmojiPickerItem).bind(content[position], onClick)

    override fun getItemCount(): Int = content.size

    inner class EmojiPickerItem(
            private val view: View
    ): RecyclerView.ViewHolder(view) {
        private val emojiTextView = view.findViewById<EmojiTextView>(R.id.emoji_picker_item_text)
        private val emojiVariationHint = view.findViewById<ImageView>(R.id.emoji_picker_item_hint)

        fun bind(emoji: Emoji, onClick: (String) -> Unit) {
            val str = createEmoji(emoji)
            emojiTextView.text = str
            emojiTextView.setOnClickListener { onClick(str) }
            emojiVariationHint.visibility = if (emoji.hasColorVariations) View.VISIBLE else View.INVISIBLE

            emojiTextView.setOnLongClickListener {
                if (popupEnabled && emoji.hasColorVariations || emoji.type == EmojiType.MALE || emoji.type == EmojiType.FEMALE) {
                    showVariationPopup(emoji, onClick)
                }
                true
            }
        }

        private fun createEmoji(emoji: Emoji): String =
            if (emoji.type == EmojiType.NONE) EmojiCompat.get().process(emoji.symbol).toString()
            else appendGender(emoji, SKIN_TONE_TYPE_0)

        private fun showVariationPopup(emoji: Emoji, onClick: (String) -> Unit) {
            val location = IntArray(2)
            emojiTextView.getLocationInWindow(location)

            val layout = LayoutInflater.from(view.context)
                    .inflate(R.layout.emoji_picker_popup, null, false)

            val popupWindow = PopupWindow(view.context).apply {
                contentView = layout
                width = FrameLayout.LayoutParams.WRAP_CONTENT
                height = FrameLayout.LayoutParams.WRAP_CONTENT
                isOutsideTouchable = true
                elevation = 0F
                setBackgroundDrawable(null)
                showAtLocation(emojiTextView, Gravity.NO_GRAVITY, view.x.toInt(), location[1])
            }

            layout.findViewById<RecyclerView>(R.id.emoji_picker_popup_list).apply {
                layoutManager = GridLayoutManager(view.context, 5)
                adapter = EmojiPopupAdapter(generateList(emoji)) {
                    popupWindow.dismiss()
                    onClick(it)
                }
            }
        }

        private fun generateList(emoji: Emoji) =
            if (emoji.type == EmojiType.NONE) {
                listOf(
                        appendColor(emoji.symbol, SKIN_TONE_TYPE_0),
                        appendColor(emoji.symbol, SKIN_TONE_TYPE_1),
                        appendColor(emoji.symbol, SKIN_TONE_TYPE_2),
                        appendColor(emoji.symbol, SKIN_TONE_TYPE_3),
                        appendColor(emoji.symbol, SKIN_TONE_TYPE_4)
                )
            } else {
                if (emoji.attr.isNotEmpty())
                    listOf(
                            appendGender(emoji, SKIN_TONE_TYPE_0, GENDER_MALE),
                            appendGender(emoji, SKIN_TONE_TYPE_1, GENDER_MALE),
                            appendGender(emoji, SKIN_TONE_TYPE_2, GENDER_MALE),
                            appendGender(emoji, SKIN_TONE_TYPE_3, GENDER_MALE),
                            appendGender(emoji, SKIN_TONE_TYPE_4, GENDER_MALE)
                    )
                else
                    listOf(
                            appendGender(emoji, SKIN_TONE_TYPE_0, GENDER_MALE),
                            appendGender(emoji, SKIN_TONE_TYPE_1, GENDER_MALE),
                            appendGender(emoji, SKIN_TONE_TYPE_2, GENDER_MALE),
                            appendGender(emoji, SKIN_TONE_TYPE_3, GENDER_MALE),
                            appendGender(emoji, SKIN_TONE_TYPE_4, GENDER_MALE),
                            appendGender(emoji, SKIN_TONE_TYPE_0, GENDER_FEMALE),
                            appendGender(emoji, SKIN_TONE_TYPE_1, GENDER_FEMALE),
                            appendGender(emoji, SKIN_TONE_TYPE_2, GENDER_FEMALE),
                            appendGender(emoji, SKIN_TONE_TYPE_3, GENDER_FEMALE),
                            appendGender(emoji, SKIN_TONE_TYPE_4, GENDER_FEMALE)
                    )
            }

        private fun appendColor(str: String, color: String) = "$str$COLOR_JOINER$color"

        private fun appendGender(emoji: Emoji, color: String): String {
            return if (emoji.symbol.isEmpty())
                "${appendColor(emoji.symbol, color)}$ZERO_WIDTH_JOINER${if (emoji.type == EmojiType.MALE) GENDER_MALE else GENDER_FEMALE}"
            else "${appendColor(emoji.symbol, color)}$ZERO_WIDTH_JOINER${emoji.attr}"
        }

        private fun appendGender(emoji: Emoji, skinTone: String, gender: String): String {
            return if (emoji.attr.isEmpty())
                "${appendColor(emoji.symbol, skinTone)}$ZERO_WIDTH_JOINER$gender"
            else "${appendColor(emoji.symbol, skinTone)}$ZERO_WIDTH_JOINER${emoji.attr}"
        }
    }
}
