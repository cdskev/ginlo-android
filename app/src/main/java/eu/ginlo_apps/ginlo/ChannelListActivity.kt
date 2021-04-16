// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo

import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager.BadTokenException
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.android.AndroidInjection
import eu.ginlo_apps.ginlo.activity.chat.BaseChatActivity
import eu.ginlo_apps.ginlo.activity.chat.ChannelChatActivity
import eu.ginlo_apps.ginlo.adapter.ChannelRecycleViewAdapter
import eu.ginlo_apps.ginlo.adapter.ChannelRecycleViewAdapter.OnChannelItemClickListener
import eu.ginlo_apps.ginlo.adapter.FilterChannelsAdapter
import eu.ginlo_apps.ginlo.context.SimsMeApplication.getInstance
import eu.ginlo_apps.ginlo.controller.ChannelController
import eu.ginlo_apps.ginlo.controller.ChannelController.ChannelAsyncLoaderCallback
import eu.ginlo_apps.ginlo.controller.ChannelController.ChannelIdentifier
import eu.ginlo_apps.ginlo.controller.ChatImageController
import eu.ginlo_apps.ginlo.data.network.AppConnectivity
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.backend.ChannelCategoryModel
import eu.ginlo_apps.ginlo.model.backend.ChannelListModel
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil.OnCloseListener
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.ImageLoader
import eu.ginlo_apps.ginlo.util.UrlHandlerUtil
import eu.ginlo_apps.ginlo.view.ThemedSearchView
import kotlinx.android.synthetic.main.activity_channel_list.activity_channel_progress
import kotlinx.android.synthetic.main.activity_channel_list.channel_empty_list_message
import kotlinx.android.synthetic.main.activity_channel_list.channel_list_filter_listview
import kotlinx.android.synthetic.main.activity_channel_list.channel_list_filter_menu
import kotlinx.android.synthetic.main.activity_channel_list.channel_recycler_view
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.Arrays
import java.util.Comparator
import javax.inject.Inject

class ChannelListActivity : BaseActivity(), OnChannelItemClickListener,
    ChannelController.SubscribeChannelNotificationListener {

    companion object {
        private const val SORT_STATE_UP = 1

        private const val SORT_STATE_DOWN = 2
    }

    private var adapter: ChannelRecycleViewAdapter? = null

    private val channelController: ChannelController by lazy { simsMeApplication.channelController }

    private var shouldLoadChannels: Boolean = false

    private var categoryModel: ChannelCategoryModel? = null

    private var imageLoader: ImageLoader? = null

    private var categoryItems = mutableListOf<ChannelListModel>()

    private var requestedChannel: String? = null

    private var sortState = SORT_STATE_UP

    private lateinit var sortButton: ImageView

    private var channelList: Array<ChannelListModel>? = null

    private var loadChannelDataListener: ChannelAsyncLoaderCallback<Array<ChannelListModel>>? = null

    private var searchItem: MenuItem? = null

    private var lastClickedItemIndex: Int = 0

    private var searchView: SearchView? = null

    @Inject
    lateinit var appConnectivity: AppConnectivity

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        try {
            checkForFinishOnUrlHandlerStart()

            channel_recycler_view.setHasFixedSize(true)
            channel_recycler_view.layoutManager = LinearLayoutManager(this)

            imageLoader = initImageLoader()

            adapter = createChannelListAdapter()

            sortButton = findViewById(R.id.action_bar_image_view_profile_picture)
            sortButton.apply {
                setImageResource(R.drawable.ico_sort_up)
                setColorFilter(ColorUtil.getInstance().getMainContrastColor(simsMeApplication))
                visibility = View.VISIBLE
                contentDescription = resources.getString(R.string.content_description_channellist_sort_down)
                setOnClickListener { sortList() }
                isEnabled = false
            }

            channel_recycler_view.adapter = adapter

            shouldLoadChannels = true

            channelController.registerSubscribeChannelNotificationListener(this)

            lastClickedItemIndex = -1
        } catch (e: LocalizedException) {
            finish()
        }
    }

    override fun onDestroy() {
        channelController.unregisterSubscribeChannelNotificationListener(this)
        super.onDestroy()
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_channel_list
    }

    override fun onResumeActivity() {
        try {
            //TODO add-parameter wird im util weggeschnitten - vorerst ok, muss spaeter evtl erweitert werden
            requestedChannel = UrlHandlerUtil.getStringFromIntent(intent, "channel=")
            if (requestedChannel != null) {
                //wenn man sich waehrend de soeffnens des links in dieser activity befindet sollen die channels neu geladen werden, wnen man ueber den url-handler rienkommt
                shouldLoadChannels = true
            }
        } catch (e: IOException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }

        if (shouldLoadChannels) {
            loadChannels()
        }

        searchItem?.collapseActionView()
    }

    private fun loadChannels() {
        loadCategories()

        shouldLoadChannels = false
        showProgressView(true)

        loadChannelDataListener = object : ChannelAsyncLoaderCallback<Array<ChannelListModel>> {
            override fun asyncLoaderFinishedWithSuccess(list: Array<ChannelListModel>?) {
                if (list == null || list.isEmpty()) {
                    showProgressView(false)
                    channel_empty_list_message.setText(R.string.channel_no_channels_available)
                    channel_empty_list_message.visibility = View.VISIBLE
                    channel_recycler_view.visibility = View.GONE
                    return
                }

                channelList = list
                sortButton.isEnabled = true

                if (requestedChannel?.isNotBlank() == true) {
                    openRequestedChannel()
                    requestedChannel = null
                }

                refreshChannelList(list)
            }

            override fun asyncLoaderFinishedWithError(errorMessage: String?) {
                try {
                    showProgressView(false)

                    if (!appConnectivity.isConnected()) {
                        Toast.makeText(
                            this@ChannelListActivity,
                            R.string.backendservice_internet_connectionFailed,
                            Toast.LENGTH_LONG
                        ).show()
                        channel_empty_list_message.setText(R.string.channel_no_internet_connection)
                        channel_empty_list_message.visibility = View.VISIBLE
                        channel_recycler_view.visibility = View.GONE
                    } else {
                        showErrorDialog(getString(R.string.channel_list_loading_error))
                    }
                } catch (ignored: NullPointerException) {
                }
            }
        }

        try {
            channelController.loadChannelList(loadChannelDataListener!!)
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }

        channel_empty_list_message.visibility = View.GONE
        channel_recycler_view.visibility = View.VISIBLE
    }

    private fun openRequestedChannel() {
        channelList?.firstOrNull { it.shortDesc.toUpperCase() == requestedChannel?.toUpperCase() }?.let { model ->
            val intent = if (model.isSubscribed) {
                Intent(this@ChannelListActivity, ChannelChatActivity::class.java).apply {
                    putExtra(BaseChatActivity.EXTRA_TARGET_GUID, model.guid)
                }
            } else {
                Intent(this@ChannelListActivity, ChannelDetailActivity::class.java).apply {
                    putExtra(ChannelDetailActivity.CHANNEL_GUID, model.guid)
                    putExtra(ChannelDetailActivity.EXTRA_FINISH_TO_OVERVIEW, true)
                }
            }

            startActivity(intent)
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val supportActionBar = supportActionBar ?: return true

        searchView =
            ThemedSearchView(supportActionBar.themedContext, ColorUtil.getInstance().getMainContrastColor(simsMeApplication))
        searchView!!.queryHint = getString(R.string.android_search_placeholder_channels)
        searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(query: String?): Boolean {
                if (adapter == null || categoryItems.isEmpty()) {
                    return true
                }

                if (query == null || query.isEmpty()) {
                    adapter!!.addItems(categoryItems)
                    return true
                }
                val resultItems = mutableListOf<ChannelListModel>()

                categoryItems.forEach { model ->
                    val channel = channelController.getChannelFromDB(model.guid)

                    if (channel?.findString(query) == true) {
                        resultItems.add(model)
                    }
                }

                adapter!!.addItems(resultItems)

                return false
            }
        })

        searchItem = menu.add("Search")
        searchItem!!.actionView = searchView
        searchItem!!.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        setIsMenuVisible(true)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when {
            item == searchItem -> true
            channel_list_filter_menu.visibility == View.VISIBLE -> {
                closeFilterMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun createChannelListAdapter(): ChannelRecycleViewAdapter? {
        if (imageLoader == null) {
            LogUtil.e(this.javaClass.name, "createChannelListAdapter(): ImageLoader is null.")
            finish()
            return null
        }

        return ChannelRecycleViewAdapter(imageLoader).apply {
            setOnChannelItemClickListener(this@ChannelListActivity)
        }
    }

    fun closeFilterMenu() {
        channel_list_filter_menu.setBackgroundColor(ContextCompat.getColor(this, R.color.transparent))
        channel_list_filter_menu.visibility = View.GONE
    }

    private fun loadCategories() {
        channelController.getCategoriesFromBackend(object :
            ChannelAsyncLoaderCallback<ArrayList<ChannelCategoryModel>> {
            override fun asyncLoaderFinishedWithSuccess(list: ArrayList<ChannelCategoryModel>) {
                val filterChannelsAdapter = FilterChannelsAdapter(
                    this@ChannelListActivity,
                    R.layout.channel_filter_list_item,
                    list
                )

                val itemOnClickListener = object : AdapterView.OnItemClickListener {
                    override fun onItemClick(
                        parent: AdapterView<*>,
                        view: View,
                        position: Int,
                        id: Long
                    ) {
                        filterChannelsAdapter.setcheckedPosition(position)
                        filterChannelsAdapter.notifyDataSetChanged()
                        showProgressView(true)

                        val categoryItem = filterChannelsAdapter.getItem(position)

                        if (categoryItem == null || categoryItem.ident == "categoriesTitleTextKey" || categoryItem.ident == null) {
                            categoryModel = null
                        } else {
                            categoryModel = categoryItem
                        }

                        if (loadChannelDataListener != null) {
                            sortButton.isEnabled = false

                            try {
                                channelController.loadChannelList(loadChannelDataListener!!)
                            } catch (e: LocalizedException) {
                                LogUtil.e(this.javaClass.name, e.message, e)
                            }
                        }

                        adapter!!.notifyDataSetChanged()

                        sortState = SORT_STATE_UP
                        closeFilterMenu()

                        val resourceId = resources.getIdentifier(
                            categoryItem!!.titleKey, "string",
                            packageName
                        )
                        val title = try {
                            getString(resourceId)
                        } catch (e: Resources.NotFoundException) {
                            getString(R.string.channellist_title)
                        }

                        setTitle(title)
                    }
                }

                channel_list_filter_listview.adapter = filterChannelsAdapter
                channel_list_filter_listview.onItemClickListener = itemOnClickListener


                setRightActionBarImage(
                    R.drawable.ic_more_white_24dp,
                    { onCategoryFilterClick() },
                    resources.getString(R.string.content_description_channellist_categories),
                    -1
                )
            }

            override fun asyncLoaderFinishedWithError(errorMessage: String?) {
                try {
                    removeRightActionBarImage()
                } catch (ignored: NullPointerException) {
                }
            }
        })
    }

    private fun onCategoryFilterClick() {
        if (channel_list_filter_menu.visibility == View.VISIBLE) {
            closeFilterMenu()
            return
        }
        channel_list_filter_menu.visibility = View.VISIBLE
    }

    private fun refreshChannelList(list: Array<ChannelListModel>?) {
        if (list == null || list.isEmpty()) {
            showProgressView(false)
            return
        }

        if (adapter == null) {
            adapter = createChannelListAdapter()
            channel_recycler_view.adapter = adapter
        }

        if (categoryModel != null && categoryModel!!.items != null) {
            val comparator = Comparator<ChannelListModel> { lhs, rhs ->
                val ldx1 = categoryModel!!.items.indexOf(lhs.guid)
                val ldx2 = categoryModel!!.items.indexOf(rhs.guid)

                Integer.compare(ldx1, ldx2)
            }

            Arrays.sort(list, comparator)
        }

        if (sortState == SORT_STATE_DOWN) {
            val comparatorAlphabeticalDown = Comparator<ChannelListModel> { lhs, rhs ->
                val ldx1 = lhs.shortDesc
                val ldx2 = rhs.shortDesc

                ldx2.compareTo(ldx1, ignoreCase = true)
            }

            Arrays.sort(list, comparatorAlphabeticalDown)
        } else if (sortState == SORT_STATE_UP) {
            val comparatorAlphabeticalUp = Comparator<ChannelListModel> { lhs, rhs ->
                val ldx1 = lhs.shortDesc
                val ldx2 = rhs.shortDesc

                ldx1.compareTo(ldx2, ignoreCase = true)
            }

            Arrays.sort(list, comparatorAlphabeticalUp)
        }

        categoryItems.clear()

        list.forEach { model ->
            val channel = channelController.getChannelFromDB(model.guid)

            if (categoryModel == null || channel!!.isCategory(categoryModel!!.ident)) {
                if (channel!!.externalUrl != null) {
                    LogUtil.d(this.javaClass.simpleName, "Skipping Channel:" + channel.shortDesc)
                } else {
                    categoryItems.add(model)
                }
            }
        }
        adapter!!.addItems(categoryItems)

        showProgressView(false)
    }

    private fun initImageLoader(): ImageLoader {
        val imageLoader = object : ImageLoader(this, ChatImageController.SIZE_CHAT_OVERVIEW, true) {
            override fun processBitmap(data: Any): Bitmap? {
                return try {
                    val ci = data as ChannelIdentifier

                    channelController.loadImage(ci.clModel, ci.type)
                } catch (e: LocalizedException) {
                    LogUtil.w(this@ChannelListActivity.javaClass.name, "Image can't be loaded.", e)
                    null
                }
            }

            override fun processBitmapFinished(data: Any, imageView: ImageView?) {
                if (imageView != null) {
                    imageView.visibility = View.VISIBLE
                    if (imageView.id == R.id.channel_item_label && imageView.parent is RelativeLayout) {
                        val checkView = (imageView.parent as RelativeLayout).findViewById<View>(R.id.channel_text_label)
                        if (checkView != null) {
                            checkView.visibility = View.GONE
                        }
                    }
                }
            }
        }

        imageLoader.addImageCache(supportFragmentManager, 0.1f)

        imageLoader.setImageFadeIn(false)

        return imageLoader
    }

    override fun onChannelItemClick(position: Int) {
        if (position < 0) {
            return
        }

        lastClickedItemIndex = position

        val channelModel = adapter!!.getItemAt(position)

        val intent = Intent(this@ChannelListActivity, ChannelDetailActivity::class.java)

        intent.putExtra(ChannelDetailActivity.CHANNEL_GUID, channelModel!!.guid)

        if (channelModel.localChecksum != null && channelModel.checksum != channelModel.localChecksum) {
            intent.putExtra(ChannelDetailActivity.CHANNEL_NEW_CHECKSUM, channelModel.checksum)
        }
        if (categoryModel != null) {
            intent.putExtra(ChannelDetailActivity.LIST_CATEGORY, categoryModel!!.ident)
        }
        startActivity(intent)
    }

    private fun showProgressView(show: Boolean) {
        if (show) {
            channel_recycler_view.visibility = View.GONE
            activity_channel_progress.visibility = View.VISIBLE
        } else {
            activity_channel_progress.visibility = View.GONE
            channel_recycler_view.visibility = View.VISIBLE
        }
    }

    private fun showErrorDialog(errorText: String) {
        val activityWeakRef = WeakReference(this)
        val closeListener = OnCloseListener {
            if (activityWeakRef.get() != null && activityWeakRef.get()?.isFinishing == false) {
                activityWeakRef.get()?.finish()
            }
        }
        try {
            val dialog = DialogBuilderUtil.buildErrorDialog(this, errorText, 0, closeListener)

            dialog.show()
        } catch (ignored: BadTokenException) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onBackPressed() {
        if (searchView?.isIconified == false) {
            searchView?.apply {
                setQuery("", true)
                isIconified = true
            }
            return
        }
        super.onBackPressed()
    }

    private fun sortList() {
        if (channelList == null) {
            return
        }

        when (sortState) {
            SORT_STATE_DOWN -> {
                sortButton.setImageResource(R.drawable.ico_sort_up)
                sortState = SORT_STATE_UP
                sortButton.contentDescription =
                    resources.getString(R.string.content_description_channellist_sort_down)
            }
            SORT_STATE_UP -> {
                sortButton.setImageResource(R.drawable.ico_sort_down)
                sortState = SORT_STATE_DOWN
                sortButton.contentDescription = resources.getString(R.string.content_description_channellist_sort_up)
            }
        }

        refreshChannelList(channelList)
    }

    override fun newChannelSubscribe(guid: String) {
        if (adapter == null || guid.isBlank()) {
            return
        }

        var channelListModel: ChannelListModel? = null
        if (lastClickedItemIndex > -1) {
            channelListModel = adapter!!.getItemAt(lastClickedItemIndex)

            if (channelListModel != null && channelListModel.guid != guid) {
                channelListModel = null
            }
        }

        if (channelListModel == null) {
            channelListModel = adapter!!.getItemForGuid(guid)
        }

        if (channelListModel != null) {
            channelListModel.isSubscribed = true
        }

        adapter!!.notifyDataSetChanged()
    }

    override fun newChannelUnsubscribe(guid: String) {
        //TODO wird momentan nie aufgerufen, da nach einer Stornierung diese Activity gekillt wird
    }
}
