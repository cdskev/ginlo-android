// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.Toast
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import eu.ginlo_apps.ginlo.activity.chat.BaseChatActivity
import eu.ginlo_apps.ginlo.activity.chat.GroupChatActivity
import eu.ginlo_apps.ginlo.activity.chat.SingleChatActivity
import eu.ginlo_apps.ginlo.activity.chatsOverview.ChatsAdapter
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemClick
import eu.ginlo_apps.ginlo.adapter.PageAdapterItemInfo
import eu.ginlo_apps.ginlo.adapter.SimsmeFragmentPagerAdapter
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.controller.ChannelController
import eu.ginlo_apps.ginlo.controller.ChatImageController
import eu.ginlo_apps.ginlo.controller.ChatOverviewController
import eu.ginlo_apps.ginlo.controller.ContactController
import eu.ginlo_apps.ginlo.controller.message.SingleChatController
import eu.ginlo_apps.ginlo.controller.message.contracts.OnChatDataChangedListener
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.fragment.BaseContactsFragment
import eu.ginlo_apps.ginlo.fragment.ContactsFragment
import eu.ginlo_apps.ginlo.fragment.ForwardChatListFragment
import eu.ginlo_apps.ginlo.greendao.Account
import eu.ginlo_apps.ginlo.greendao.Chat
import eu.ginlo_apps.ginlo.greendao.Contact
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO
import eu.ginlo_apps.ginlo.model.param.SendActionContainer
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.FileUtil
import eu.ginlo_apps.ginlo.util.ImageLoader
import eu.ginlo_apps.ginlo.util.StringUtil
import java.util.ArrayList

abstract class ForwardActivityBase : BaseActivity(), OnChatDataChangedListener,
    AdapterView.OnItemClickListener,
    OnChatItemClick {

    companion object {
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_STARTED_INTERNALLY = "ForwardActivity.startedInternally"
        const val EXTRA_FORWARD_CHANNELMESSAGE_IS_IMAGE = "forwardChannelMessageIsImageExtra"
        const val EXTRA_FORWARD_CHANNELMESSAGE_IS_TEXT = "forwardChannelMessageIsTextExtra"
    }

    protected val TAB_SINGLE = "tab_single"
    protected val TAB_GROUP = "tab_group"
    protected val TAB_CONTACT = "tab_contact"
    protected val TAB_DOMAIN = "tab_domain"
    protected val TAB_COMPANY = "tab_company"
    private val REQUEST_CODE = 111

    internal lateinit var mChatController: SingleChatController
    internal lateinit var mViewPager: ViewPager
    protected lateinit var mImageLoader: ImageLoader
    private var mMessageId: Long = -1
    private lateinit var mChatOverviewController: ChatOverviewController
    private lateinit var mContactController: ContactController
    private var mForwardChannelMessageIsImage: Boolean = false
    private var mForwardChannelMessageIsText: Boolean = false
    private var mIsSendAction: Boolean = false

    private var mStartedInternally: Boolean = false
    private lateinit var mForwardChatListSingleFragment: ForwardChatListFragment
    private lateinit var mForwardChatListGroupFragment: ForwardChatListFragment
    protected lateinit var mContactsFragment: ContactsFragment
    protected lateinit var mContactsPageInfo: PageAdapterItemInfo
    private lateinit var mTabLayout: TabLayout

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        title = getString(R.string.forward_title)

        mChatOverviewController = simsMeApplication.chatOverviewController
        mChatController = simsMeApplication.singleChatController
        mContactController = simsMeApplication.contactController

        val channelController = simsMeApplication.channelController
        val chatImageController = simsMeApplication.chatImageController

        mImageLoader = initImageLoader(chatImageController, channelController)

        val chatsAdapterSingle =
            ChatsAdapter(
                this,
                mImageLoader,
                mChatController,
                windowManager.defaultDisplay,
                ArrayList(),
                this,
                null
            )

        val chatsAdapterGroup =
            ChatsAdapter(
                this,
                mImageLoader,
                mChatController,
                windowManager.defaultDisplay,
                ArrayList(),
                this,
                null
            )

        mForwardChatListSingleFragment = ForwardChatListFragment()
        mForwardChatListSingleFragment.init(
            mChatOverviewController,
            chatsAdapterSingle,
            ChatOverviewController.MODE_FORWARD_SINGLE,
            getString(R.string.forward_filter_chats_noChatsFound)
        )

        mForwardChatListGroupFragment = ForwardChatListFragment()
        mForwardChatListGroupFragment.init(
            mChatOverviewController,
            chatsAdapterGroup,
            ChatOverviewController.MODE_FORWARD_GROUP,
            getString(R.string.forward_filter_chats_noGroupsFound)
        )

        mContactsFragment = ContactsFragment.newInstance(ContactsActivity.MODE_SIMSME_SINGLE)
        mContactsFragment.setOnItemClickListener(this)

        mTabLayout = findViewById(R.id.forward_activity_tab_layout)

        mViewPager = findViewById(R.id.forward_activity_viewpager)
        val pagerAdapter = SimsmeFragmentPagerAdapter(supportFragmentManager)

        pagerAdapter.addNewFragment(PageAdapterItemInfo("", mForwardChatListSingleFragment))
        pagerAdapter.addNewFragment(PageAdapterItemInfo("", mForwardChatListGroupFragment))
        mContactsPageInfo = PageAdapterItemInfo("", mContactsFragment)
        pagerAdapter.addNewFragment(mContactsPageInfo)

        pagerAdapter.notifyDataSetChanged()

        mViewPager.setAdapter(pagerAdapter)
        mTabLayout.setupWithViewPager(mViewPager)
        mTabLayout.addOnTabSelectedListener(getTabSelectedListener())

        handleIntent(intent)
    }

    open fun getTabSelectedListener(): TabLayout.OnTabSelectedListener {
        return object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab != null) {
                    val colorUtil = ColorUtil.getInstance()
                    ColorUtil.setColorFilter(tab.icon,
                            colorUtil.getAppAccentColor(SimsMeApplication.getInstance()))
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                if (tab != null) {
                    val colorUtil = ColorUtil.getInstance()
                    ColorUtil.setColorFilter(tab.icon,
                            colorUtil.getMainContrast80Color(SimsMeApplication.getInstance()))
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                if (tab != null) {
                    val colorUtil = ColorUtil.getInstance()
                    ColorUtil.setColorFilter(tab.icon,
                            colorUtil.getAppAccentColor(SimsMeApplication.getInstance()))
                }
            }
        }
    }

    private fun initImageLoader(
        chatImageController: ChatImageController,
        channelController: ChannelController
    ): ImageLoader {
        //Image Loader zum Laden der ChatoverviewItems Icons
        val imageLoader =
            object : ImageLoader(this, ChatImageController.SIZE_CHAT_OVERVIEW, false) {
                override fun processBitmap(data: Any): Bitmap? {
                    try {
                        if (data is ChannelController.ChannelIdentifier) {

                            if (data.guid != null && data.type != null) {
                                return channelController.loadImage(data.guid, data.type)
                            }
                        } else {
                            // This gets called in a background thread
                            return chatImageController.getImageByGuidWithoutCacheing(
                                data as String,
                                imageSize
                            )
                        }

                        return null
                    } catch (e: LocalizedException) {
                        LogUtil.w(
                            this@ForwardActivityBase.javaClass.name,
                            "Image can't be loaded.",
                            e
                        )
                        return null
                    }
                }

                override fun processBitmapFinished(data: Any, imageView: ImageView) {
                    //Nothing to do
                }
            }

        imageLoader.addImageCache(supportFragmentManager, 0.1f)
        imageLoader.setImageFadeIn(false)
        chatImageController.addListener(imageLoader)

        return imageLoader
    }

    private fun handleIntent(intent: Intent) {

        mForwardChannelMessageIsImage =
            getIntent().getBooleanExtra(EXTRA_FORWARD_CHANNELMESSAGE_IS_IMAGE, false)
        mForwardChannelMessageIsText =
            getIntent().getBooleanExtra(EXTRA_FORWARD_CHANNELMESSAGE_IS_TEXT, false)
        mStartedInternally = getIntent().getBooleanExtra(EXTRA_STARTED_INTERNALLY, false)

        try {
            checkForFinishOnUrlHandlerStart()

            //----------- Oeffnen In ----------->

            val action = intent.action

            if (!StringUtil.isNullOrEmpty(action) && (Intent.ACTION_SEND == action || Intent.ACTION_SEND_MULTIPLE == action)) {
                try {
                    val actionContainer = FileUtil(this).checkFileSendActionIntent(intent)

                    if (!StringUtil.isNullOrEmpty(actionContainer.displayMessage)) {
                        Toast.makeText(this, actionContainer.displayMessage, Toast.LENGTH_LONG)
                            .show()
                    }
                    if (!simsMeApplication.preferencesController.canSendMedia() && actionContainer.type != SendActionContainer.TYPE_TXT) {
                        val errorMsg = getString(R.string.chats_addAttachments_some_imports_fails)
                        throw LocalizedException(
                            LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED,
                            errorMsg
                        )
                    }

                    mIsSendAction = true
                } catch (e: LocalizedException) {
                    val identifier = e.identifier
                    if (!StringUtil.isNullOrEmpty(identifier)) {
                        if (LocalizedException.NO_ACTION_SEND == identifier || LocalizedException.NO_DATA_FOUND == identifier) {
                            Toast.makeText(
                                this,
                                R.string.chat_share_file_infos_are_missing,
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED == identifier) {
                            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    finish()
                    return
                } catch (e: UnsupportedOperationException) {
                    Toast.makeText(this, R.string.chat_forward_message_toast_msg, Toast.LENGTH_LONG)
                        .show()
                }
            } else {
                mIsSendAction = false
                mMessageId = getIntent().getLongExtra(EXTRA_MESSAGE_ID, -1)
            }//<----------- Oeffnen In -----------

            if (!mIsSendAction && mMessageId == -1L) {
                Toast.makeText(
                    this,
                    getString(R.string.chats_forward_message_error),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        } catch (e: LocalizedException) {
            LogUtil.w(this.javaClass.name, e.message, e)
            finish()
        }
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_forward
    }

    override fun onResumeActivity() {
        val colorUtil = ColorUtil.getInstance()
        val pagerAdapter = mViewPager.adapter as? SimsmeFragmentPagerAdapter ?: return

        initializeTabLayout(pagerAdapter, colorUtil)

        //FIXME aussortieren
        mChatOverviewController.setListRefreshedListener(null)
        mChatOverviewController.addListener(this)

        if (mStartedInternally) {
            mChatOverviewController.filterMessages()
        } else {
            if (mChatOverviewController.hasChatOverviewItems()) {
                mForwardChatListSingleFragment.refresh()
                mForwardChatListGroupFragment.refresh()
            } else {
                mChatOverviewController.loadChatOverviewItems()
                showIdleDialog(-1)
            }
        }

        // farben erst hier setzen, da jetzt erst die tabs geladne wurden
        colorUtil.colorizeTabLayoutHeader(simsMeApplication, mTabLayout)

        val selectedTabIndex = mTabLayout.selectedTabPosition
        if (selectedTabIndex > -1) {
            val tab = mTabLayout.getTabAt(selectedTabIndex)
            if(tab != null) {
                ColorUtil.setColorFilter(tab.icon,
                        colorUtil.getAppAccentColor(SimsMeApplication.getInstance()))
            }
        }
    }

    private fun initializeTabLayout(
        pagerAdapter: SimsmeFragmentPagerAdapter,
        colorUtil: ColorUtil
    ) {
        for (i in 0 until pagerAdapter.count) {
            val fragment = pagerAdapter.getItem(i)
            val tab = mTabLayout.getTabAt(i)
            if (tab == null) continue

            if (fragment is BaseContactsFragment) {
                initializeBaseContactsFragment(fragment, tab, colorUtil)
            } else if (fragment is ForwardChatListFragment) {
                initializeForwardChatListFragment(fragment, tab, colorUtil)
            }
        }
    }

    private fun initializeBaseContactsFragment(
        fragment: BaseContactsFragment,
        tab: TabLayout.Tab,
        colorUtil: ColorUtil
    ) {
        val type = fragment.contactsFragmentType
        if (type == BaseContactsFragment.ContactsFragmentType.TYPE_COMPANY) {
            setTabIconAndTag(tab, R.drawable.business, TAB_COMPANY, colorUtil)
        } else if (type == BaseContactsFragment.ContactsFragmentType.TYPE_DOMAIN) {
            setTabIconAndTag(tab, R.drawable.mail, TAB_DOMAIN, colorUtil)
        } else if (type == BaseContactsFragment.ContactsFragmentType.TYPE_PRIVATE) {
            setTabIconAndTag(tab, R.drawable.phone, TAB_CONTACT, colorUtil)
        }
    }

    private fun initializeForwardChatListFragment(
        fragment: ForwardChatListFragment,
        tab: TabLayout.Tab,
        colorUtil: ColorUtil
    ) {
        if (fragment.mode == ChatOverviewController.MODE_FORWARD_SINGLE) {
            setTabIconAndTag(tab, R.drawable.chat, TAB_SINGLE, colorUtil)
        } else if (fragment.mode == ChatOverviewController.MODE_FORWARD_GROUP) {
            setTabIconAndTag(tab, R.drawable.group, TAB_GROUP, colorUtil)
        }
    }

    private fun setTabIconAndTag(
        tab: TabLayout.Tab,
        resId: Int,
        tag: String,
        colorUtil: ColorUtil
    ) {
        tab.setIcon(resId)
        tab.tag = tag
        ColorUtil.setColorFilter(tab.icon,
                colorUtil.getMainContrast80Color(SimsMeApplication.getInstance()))
    }

    override fun onClick(item: BaseChatOverviewItemVO) {
        try {
            startChat(item.chat)
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        try {
            val lPagerAdapter = mViewPager.adapter as? SimsmeFragmentPagerAdapter
            val currentFragment = lPagerAdapter?.getItem(mViewPager.currentItem)

            if (currentFragment !is ContactsFragment)
                return

            val lAdapter = currentFragment.contactsAdapter
            val contact = lAdapter?.getItem(position - currentFragment.getListView().headerViewsCount)
            if (contact?.accountGuid != null) {
                startChat(contact)
            }
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    protected fun startChat(contact: Contact) {
        val chat = mChatController.getChatByGuid(contact.accountGuid) ?: Chat().apply {
            chatGuid = contact.accountGuid
            type = Chat.TYPE_SINGLE_CHAT
            title = contact.name
            mChatController.insertOrUpdateChat(this)
        }
        startChat(chat)
    }

    private fun startChat(chat: Chat?) {
        if (chat == null) {
            return
        }

        if (chat.type == Chat.TYPE_SINGLE_CHAT) {
            val contact = mContactController.getContactByGuid(chat.chatGuid) ?: return
            val activityClass = SingleChatActivity::class.java
            val targetGuid = contact.accountGuid

            if (contact.publicKey == null) {
                mContactController.loadPublicKey(contact,
                    object : ContactController.OnLoadPublicKeyListener {
                        override fun onLoadPublicKeyError(message: String?) {
                            Toast.makeText(
                                this@ForwardActivityBase,
                                message.orEmpty(),
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }

                        override fun onLoadPublicKeyComplete(contact: Contact) {
                            navigateTo(activityClass, targetGuid)
                        }
                    })
            } else {
                navigateTo(activityClass, targetGuid)
            }
        } else {
            navigateTo(GroupChatActivity::class.java, chat.chatGuid)
        }
    }

    private fun navigateTo(target: Class<*>, targetGuid: String) {
        val intent: Intent

        if (mIsSendAction) {
            intent = getIntentFromCallerIntent(target)
        } else if (mMessageId != -1L) {
            intent = Intent(this, target)
            intent.putExtra(BaseChatActivity.EXTRA_FORWARD_MESSAGE_ID, mMessageId)
            if (mForwardChannelMessageIsText) {
                intent.putExtra(BaseChatActivity.EXTRA_FORWARD_CHANNELMESSAGE_IS_TEXT, true)
            } else if (mForwardChannelMessageIsImage) {
                intent.putExtra(BaseChatActivity.EXTRA_FORWARD_CHANNELMESSAGE_IS_IMAGE, true)
            }
        } else {
            return
        }

        intent.putExtra(BaseChatActivity.EXTRA_TARGET_GUID, targetGuid)

        startActivityForResult(intent, REQUEST_CODE)
        mMessageId = -1

        if (mStartedInternally) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        callerIntent = intent
        setIntent(intent)
        if (mExceptionWasThrownInOnCreate) {
            mExceptionWasThrownInOnCreate = isLogout
            if (!mExceptionWasThrownInOnCreate) {
                onCreateActivity(mSaveInstanceState)
            }
        }

        handleIntent(intent)
    }

    override fun finish() {
        //Finish nur wenn die App eingeloggt ist oder die Activity nicht gerade kreiert wurde oder keine Account vorhanden ist
        //Die Activity darf nicht gefinshed werden, da bei einem "Öffnen in" die Permission auf die Datei entfallen
        //von FPL
        if (!mExceptionWasThrownInOnCreate || !mAfterCreate
            || simsMeApplication.accountController.accountState == Account.ACCOUNT_STATE_NO_ACCOUNT
        ) {
            LogUtil.d("FORWARD_ACT", "FINSISH!")
            super.finish()
        }
    }

    override fun onChatDataChanged(clearImageCache: Boolean) {
    }

    override fun onChatDataLoaded(lastMessageId: Long) {
        //remove listener sonst endlosschleife
        mChatOverviewController.removeListener(this@ForwardActivityBase)
        mForwardChatListSingleFragment.refresh()
        mForwardChatListGroupFragment.refresh()
        dismissIdleDialog()
    }

    override fun colorizeActivity() {
        super.colorizeActivity()
        val colorUtil = ColorUtil.getInstance()
        mTabLayout.setSelectedTabIndicatorColor(colorUtil.getAppAccentColor(simsMeApplication))
    }
}