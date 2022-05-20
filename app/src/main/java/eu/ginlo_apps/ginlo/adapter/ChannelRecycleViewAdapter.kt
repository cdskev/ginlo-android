// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.controller.ChannelController
import eu.ginlo_apps.ginlo.model.backend.ChannelListModel
import eu.ginlo_apps.ginlo.util.ImageLoader
import kotlinx.android.synthetic.main.channel_list_overview_item.view.channel_avatar
import kotlinx.android.synthetic.main.channel_list_overview_item.view.channel_description
import kotlinx.android.synthetic.main.channel_list_overview_item.view.channel_divider
import kotlinx.android.synthetic.main.channel_list_overview_item.view.channel_initial_text
import kotlinx.android.synthetic.main.channel_list_overview_item.view.channel_item_subscribed
import kotlinx.android.synthetic.main.channel_list_overview_item.view.channel_title

class ChannelRecycleViewAdapter(private val imageLoader: ImageLoader?) : Adapter<RecyclerView.ViewHolder>() {
    private val source = mutableListOf<ChannelListModel>()
    private val separators = HashSet<String>()
    private var clickListener: OnChannelItemClickListener? = null

    fun setOnChannelItemClickListener(listener: OnChannelItemClickListener) {
        clickListener = listener
    }

    override fun getItemCount(): Int = source.size

    fun addItems(models: List<ChannelListModel>?) {
        source.clear()
        separators.clear()

        if (models != null)
            source.addAll(models)

        notifyDataSetChanged()
    }

    fun getItemAt(position: Int): ChannelListModel? {
        return if (position < source.size) {
            source[position]
        } else null
    }

    fun getItemForGuid(guid: String): ChannelListModel? {
        return if (guid.isBlank()) null
        else source.firstOrNull { channelListModel -> channelListModel.guid == guid }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolderNormal).bind(source[position])
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ViewHolderNormal(
            LayoutInflater.from(parent.context).inflate(R.layout.channel_list_overview_item, parent, false),
            clickListener
        )
    }

    interface OnChannelItemClickListener {
        fun onChannelItemClick(position: Int)
    }

    private inner class ViewHolderNormal(
        v: View,
        private val clickListener: OnChannelItemClickListener?
    ) : RecyclerView.ViewHolder(v), OnClickListener {

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(model: ChannelListModel) {
            val separator = model.shortDesc.first().uppercase().toString()
            if (separators.contains(separator) && !model.isFirstItemOfGroup) {
                itemView.channel_initial_text.visibility = View.INVISIBLE
                itemView.channel_divider.visibility = View.GONE
            } else {
                itemView.channel_initial_text.visibility = View.VISIBLE
                itemView.channel_initial_text.text = separator
                itemView.channel_divider.visibility = if (adapterPosition > 0) View.VISIBLE else View.GONE
                if(!separators.contains(separator)) {
                    separators.add(separator)
                    model.isFirstItemOfGroup = true
                }
            }
            itemView.channel_title.text = model.shortDesc
            itemView.channel_avatar.contentDescription = model.shortDesc
            itemView.channel_description.text = model.description

            imageLoader?.loadImage(
                ChannelController.ChannelIdentifier(
                    model,
                    ChannelController.IMAGE_TYPE_PROVIDER_ICON
                ), itemView.channel_avatar
            )

            if (model.isSubscribed) {
                itemView.channel_item_subscribed.visibility = View.VISIBLE
            } else {
                itemView.channel_item_subscribed.visibility = View.INVISIBLE
            }
        }

        override fun onClick(v: View) {
            clickListener?.onChannelItemClick(adapterPosition)
        }
    }
}
