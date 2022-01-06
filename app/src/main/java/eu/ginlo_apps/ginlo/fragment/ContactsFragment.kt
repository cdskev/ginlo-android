// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import eu.ginlo_apps.ginlo.BaseActivity
import eu.ginlo_apps.ginlo.ContactDetailActivity
import eu.ginlo_apps.ginlo.ContactsActivity
import eu.ginlo_apps.ginlo.ContactsActivity.EXTRA_GROUP_CHAT_OWNER_GUID
import eu.ginlo_apps.ginlo.ContactsActivity.EXTRA_GROUP_CONTACTS
import eu.ginlo_apps.ginlo.ContactsActivity.MODE_ADD_CONTACT
import eu.ginlo_apps.ginlo.ContactsActivity.MODE_ALL
import eu.ginlo_apps.ginlo.ContactsActivity.MODE_NON_SIMSME
import eu.ginlo_apps.ginlo.ContactsActivity.MODE_SEND_CONTACT
import eu.ginlo_apps.ginlo.ContactsActivity.MODE_SIMSME_DISTRIBUTOR
import eu.ginlo_apps.ginlo.ContactsActivity.MODE_SIMSME_GROUP
import eu.ginlo_apps.ginlo.ContactsActivity.MODE_SIMSME_SINGLE
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.adapter.ContactsAdapter
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.controller.ChatImageController
import eu.ginlo_apps.ginlo.controller.ContactController
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Contact
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.constant.AppConstants
import eu.ginlo_apps.ginlo.model.constant.JsonConstants
import eu.ginlo_apps.ginlo.themedInflater
import eu.ginlo_apps.ginlo.util.ContactUtil
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil
import eu.ginlo_apps.ginlo.util.ImageLoader
import eu.ginlo_apps.ginlo.util.PermissionUtil
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import eu.ginlo_apps.ginlo.util.StringUtil
import eu.ginlo_apps.ginlo.util.SystemUtil
import kotlinx.android.synthetic.main.fragment_contacts.view.contacts_list_view
import kotlinx.android.synthetic.main.fragment_contacts.view.forward_pager_contacts_no_contacts_textview
import kotlinx.android.synthetic.main.fragment_contacts.view.fragment_no_local_contact_found
import kotlinx.android.synthetic.main.fragment_contacts.view.swipe_refresh_contacts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.util.ArrayList
import kotlin.coroutines.CoroutineContext

class ContactsFragment : BaseContactsFragment(), ContactController.OnLoadContactsListener,
        AdapterView.OnItemClickListener, ContactController.OnContactProfileInfoChangeNotification,
        AbsListView.OnScrollListener, SwipeRefreshLayout.OnRefreshListener, CoroutineScope {

    companion object {
        fun newInstance(mode: Int): ContactsFragment {
            val fragment = ContactsFragment()
            fragment.setMode(mode)

            return fragment
        }
    }

    private val TAG = ContactsFragment::class.java.simpleName
    private val contactController: ContactController by lazy { (context as BaseActivity).simsMeApplication.contactController }
    private val chatImageController: ChatImageController by lazy { (context as BaseActivity).simsMeApplication.chatImageController }
    private var contacts: List<Contact>? = null
    private var imageLoader: ImageLoader? = null
    private var onItemClickListener: AdapterView.OnItemClickListener? = null
    private var layout: Int = 0
    private var suppressNextError: Boolean = false
    private var firstLoad: Boolean = false
    private var lastSelectedItem: Int = 0
    private var setCheckedAsDefault: Boolean = false
    var contactsAdapter: ContactsAdapter? = null
        private set

    private var groupContacts: MutableList<Contact>? = null
    private val themedInflater: LayoutInflater by lazy { LayoutInflater.from(context).themedInflater(context) }
    private var headerView: View? = null
    private lateinit var rootView: View
    override val coroutineContext: CoroutineContext = Dispatchers.Default
    private lateinit var supervisorJob : Job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (mApplication == null) {
            mApplication = requireActivity().application as SimsMeApplication
        }

        firstLoad = true
        lastSelectedItem = 0
        suppressNextError = false
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        supervisorJob = SupervisorJob()
        rootView = themedInflater.inflate(R.layout.fragment_contacts, container, false)

        try {
            rootView.contacts_list_view.onItemClickListener =
                    if (onItemClickListener != null) onItemClickListener else this

            contacts = ArrayList()

            initForMode(mMode)

            rootView.swipe_refresh_contacts.isEnabled = true
            rootView.swipe_refresh_contacts.setOnRefreshListener(this)
            rootView.contacts_list_view.adapter = contactsAdapter
            rootView.contacts_list_view.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
            activity?.finish()
        }

        return rootView
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        refreshTitle()
    }

    private fun loadContacts() {
        rootView.swipe_refresh_contacts.isRefreshing = true

        launch(supervisorJob) {
            val dbContacts = getContactsFromDb()

            withContext(Dispatchers.Main) {
                if (dbContacts != null) {
                    contactsAdapter?.clear()
                    contactsAdapter?.addAll(dbContacts)
                    contacts = dbContacts

                    val headerView = getHeaderView(themedInflater, rootView.contacts_list_view, dbContacts.size)
                    if (headerView != null) {
                        if (this@ContactsFragment.headerView != null) {
                            rootView.contacts_list_view.removeHeaderView(this@ContactsFragment.headerView)
                        }
                        this@ContactsFragment.headerView = headerView
                        rootView.contacts_list_view.addHeaderView(headerView, null, false)
                    }
                }
                rootView.swipe_refresh_contacts.isRefreshing = false

                checkContactCount()
            }
        }
    }

    override fun onResume() {
        LogUtil.d(TAG, "onResume called.")
        super.onResume()

        if (contacts == null) {
            activity?.finish()
            return
        }

        if (contactController.isSyncingContacts) {
            contactController.addOnLoadContactsListener(this)
        }

        contactController.registerOnContactProfileInfoChangeNotification(this)

        if (groupContacts == null) {
            loadContacts()
        }
    }

    override fun onPause() {
        super.onPause()

        contactController.unregisterOnContactProfileInfoChangeNotification(this)
        contactController.removeOnLoadContactsListener(this)
    }

    override fun onResumeFragment() {
        contactsAdapter?.notifyDataSetChanged()
    }

    override fun onDestroy() {
        supervisorJob.cancel()

        super.onDestroy()
    }

    fun getListView(): ListView = rootView.contacts_list_view

    private fun getHeaderView(inflater: LayoutInflater, container: ViewGroup?, count: Int): View? {
        if (mMode == MODE_NON_SIMSME || mMode == MODE_ADD_CONTACT) {
            return null
        }

        val header = inflater.inflate(
                R.layout.contact_list_header_view, container,
                false
        ) ?: return null

        val titleTv = header.findViewById<TextView>(R.id.header_title)
        val subTitleTv = header.findViewById<TextView>(R.id.header_sub_title)
        val descTv = header.findViewById<TextView>(R.id.header_desc)
        val button = header.findViewById<Button>(R.id.header_button)
        val lastContactsTv = header.findViewById<TextView>(R.id.header_last_contacts)

        if (titleTv != null) {
            titleTv.text = if (count == 1) {
                getString(R.string.contacts_fragment_header_title_singular, count)
            } else {
                getString(R.string.contacts_fragment_header_title, count)
            }
        }

        if (subTitleTv != null) {
            subTitleTv.text =
                    getString(R.string.contacts_fragment_header_subtitle, getString(R.string.contacts_fragment_phone))
        }

        if (descTv != null) {
            descTv.visibility = View.GONE
        }

        if (button != null) {
            button.visibility = View.GONE
        }

        if (lastContactsTv != null) {
            lastContactsTv.visibility = View.GONE
        }

        return header
    }

    private fun checkContactCount() {
        if (contacts?.isNotEmpty() == true) {
            rootView.forward_pager_contacts_no_contacts_textview.visibility = View.GONE
            rootView.contacts_list_view.visibility = View.VISIBLE
        } else {
            rootView.contacts_list_view.visibility = View.GONE
            rootView.forward_pager_contacts_no_contacts_textview.visibility = View.VISIBLE
            if (mMode == MODE_ALL || mMode == MODE_NON_SIMSME) {
                rootView.forward_pager_contacts_no_contacts_textview.text =
                        getString(R.string.contact_list_no_contacts_at_all)
            } else {
                rootView.forward_pager_contacts_no_contacts_textview.text = getString(R.string.contact_list_no_contacts)
            }
        }
    }

    override fun getContactsFragmentType(): ContactsFragmentType {
        return ContactsFragmentType.TYPE_PRIVATE
    }

    //FIXME This code is copied from the ContactsActivity
    private fun initImageLoader(imageDiameter: Int): ImageLoader? {
        if (activity == null) {
            return null
        }

        val imageLoader = object : ImageLoader(requireActivity(), imageDiameter, false) {
            override fun processBitmap(contact: Any?): Bitmap? {
                if (contact == null || activity == null) {
                    return null
                }
                try {
                    var returnImage: Bitmap? = null

                    if (contact is Contact) {
                        if ((contact.isSimsMeContact == null || !contact.isSimsMeContact) && contact.photoUri != null) {
                            returnImage = ContactUtil.loadContactPhotoThumbnail(
                                    contact.photoUri, imageSize,
                                    activity
                            )
                        }

                        if (returnImage == null) {
                            returnImage = if (contact.accountGuid != null) {
                                chatImageController.getImageByGuidWithoutCacheing(
                                        contact.accountGuid,
                                        imageSize, imageSize
                                )
                            } else {
                                contactController.getFallbackImageByContact(
                                        activity?.applicationContext, contact
                                )
                            }
                        }

                        if (returnImage == null) {
                            returnImage = chatImageController.getImageByGuidWithoutCacheing(
                                    AppConstants.GUID_PROFILE_USER,
                                    imageSize, imageSize
                            )
                        }
                    }

                    return returnImage
                } catch (e: LocalizedException) {
                    LogUtil.w(TAG, "initImageLoader: Image can't be loaded.", e)
                    return null
                }
            }

            override fun processBitmapFinished(data: Any, imageView: ImageView) {
                //Nothing to do
            }
        }

        imageLoader.addImageCache(requireActivity().supportFragmentManager, 0.1f)
        imageLoader.setImageFadeIn(false)
        imageLoader.setLoadingImage(R.drawable.gfx_profil_placeholder)

        chatImageController.addListener(imageLoader)
        return imageLoader
    }

    fun startRefresh() {
        val activity = activity
        if (activity is BaseActivity) {
            activity.requestPermission(
                    PermissionUtil.PERMISSION_FOR_READ_CONTACTS,
                    R.string.permission_rationale_contacts
            ) { permission, permissionGranted ->
                val hasPerm = permission == PermissionUtil.PERMISSION_FOR_READ_CONTACTS && permissionGranted
                if (hasPerm) {
                    Toast.makeText(activity, R.string.chat_overview_wait_hint_loading2, Toast.LENGTH_SHORT).show()
                    mApplication.contactController.syncContacts(this@ContactsFragment, false, true)
                } else {
                    onLoadContactsCanceled()
                }
            }
        }
    }

    override fun onLoadContactsComplete() {
        try {
            if (groupContacts == null) {
                contacts = getContactsFromDb()
                checkContactCount()
                handleContactsChanged(this.contacts)
            }
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        } finally {
            rootView.swipe_refresh_contacts.isRefreshing = false
        }
    }

    override fun onLoadContactsCanceled() {
        rootView.swipe_refresh_contacts.isRefreshing = false
    }

    override fun onLoadContactsError(message: String?) {
        if (!suppressNextError && !message.isNullOrBlank()) {
            DialogBuilderUtil.buildErrorDialog(activity as BaseActivity?, message).show()
            suppressNextError = true
        }
        rootView.swipe_refresh_contacts.isRefreshing = false
    }

    private fun handleContactsChanged(allContacts: List<Contact>?) {
        if (allContacts != null) {
            updateShownContacts(allContacts)
            refreshSearchList(null)
        }
    }

    private var previousQuery: String? = null

    override fun searchQueryTextChanged(query: String?): Boolean {
        if (StringUtil.isEqual(previousQuery, query)) {
            return false
        }

        previousQuery = query
        return refreshSearchList(query)
    }

    private fun refreshSearchList(query: String?): Boolean {
        try {
            val contacts = if (groupContacts != null) groupContacts else contacts
            if (query == null || query.isEmpty()) {
                updateShownContacts(contacts)
                return true
            }

            if (contacts != null) {
                updateShownContacts(contacts.filter { contact -> contact.findString(query) })

                return true
            }

            return false
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }

        return false
    }

    override fun getTitle(): String {
        return if (mMode == MODE_NON_SIMSME) {
            getString(R.string.settings_informFriends)
        } else {
            getString(R.string.contacts_overViewViewControllerTitle)
        }
    }

    private fun initForMode(mode: Int) {
        when (mode) {
            MODE_ALL, MODE_SEND_CONTACT -> initForModeAll()
            MODE_SIMSME_SINGLE -> initForModeSimsmeSingle()
            MODE_SIMSME_GROUP -> initForModeSimsmeGroup()
            MODE_SIMSME_DISTRIBUTOR -> initForModeSimsmeGroup()
            MODE_NON_SIMSME -> initForModeNonSimsme()
            MODE_ADD_CONTACT -> initForModeAdd()
            else -> throw LocalizedException(LocalizedException.UNDEFINED_ARGUMENT)
        }
    }

    private fun initForModeAll() {
        layout = R.layout.contact_item_overview_layout
        setCheckedAsDefault = false
        contactsAdapter = ContactsAdapter(activity, layout, contacts, setCheckedAsDefault, true)

        val diameter = resources.getDimension(R.dimen.contact_item_single_select_icon_diameter).toInt()

        imageLoader = initImageLoader(diameter)

        contactsAdapter?.setImageLoader(imageLoader)
    }

    private fun initForModeSimsmeSingle() {
        layout = R.layout.contact_item_overview_layout
        setCheckedAsDefault = false
        contactsAdapter = ContactsAdapter(activity, layout, contacts, setCheckedAsDefault, true)

        val diameter = resources.getDimension(R.dimen.contact_item_single_select_icon_diameter).toInt()

        imageLoader = initImageLoader(diameter)

        contactsAdapter?.setImageLoader(imageLoader)
    }

    private fun initForModeNonSimsme() {
        layout = R.layout.contact_item_overview_layout
        setCheckedAsDefault = false
        contactsAdapter = ContactsAdapter(activity, layout, contacts, setCheckedAsDefault, true)

        val diameter = resources.getDimension(R.dimen.contact_item_single_select_icon_diameter).toInt()

        imageLoader = initImageLoader(diameter)

        contactsAdapter?.setImageLoader(imageLoader)
    }

    private fun initForModeSimsmeGroup() {
        if (activity != null) {
            val intent = activity?.intent
            if(intent?.extras?.get(EXTRA_GROUP_CONTACTS) != null) {
                groupContacts = ArrayList()
                val contactsGuids = intent.extras?.getStringArrayList(EXTRA_GROUP_CONTACTS)
                if (contactsGuids != null) {
                    for (guid in contactsGuids) {
                        val contact = contactController.getContactByGuid(guid)
                        if (contact?.classEntryName == Contact.CLASS_PRIVATE_ENTRY) {
                            groupContacts?.add(contact)
                        }
                    }
                }
            }
        }

        layout = R.layout.contact_item_overview_layout
        setCheckedAsDefault = false

        val contacts = if (groupContacts != null) groupContacts else contacts

        contactsAdapter = ContactsAdapter(activity, layout, contacts, setCheckedAsDefault, true)

        val diameter = resources.getDimension(R.dimen.contact_item_multi_select_icon_diameter).toInt()
        imageLoader = initImageLoader(diameter)
        contactsAdapter?.setImageLoader(imageLoader)

        val intent = activity?.intent
        if (intent?.extras?.get(EXTRA_GROUP_CHAT_OWNER_GUID) != null) {
            val ownerGuid = intent.getStringExtra(EXTRA_GROUP_CHAT_OWNER_GUID)
            contactsAdapter?.setGroupChatOwner(ownerGuid)
        }
    }

    private fun initForModeAdd() {
        val intent = activity?.intent
        val contacts = SystemUtil.dynamicCastToList(
                intent?.getSerializableExtra(ContactsActivity.EXTRA_ADD_CONTACTS_LIST),
                Contact::class.java)

        if (contacts != null) {
            this.contacts = contacts
            layout = R.layout.contact_item_overview_layout
            setCheckedAsDefault = false
            contactsAdapter = ContactsAdapter(activity, layout, contacts, setCheckedAsDefault, true)
            val diameter = resources.getDimension(R.dimen.contact_item_single_select_icon_diameter).toInt()
            imageLoader = initImageLoader(diameter)
            contactsAdapter?.setImageLoader(imageLoader)
        } else {
            DialogBuilderUtil.buildErrorDialog(
                    activity as BaseActivity?,
                    resources.getString(R.string.update_private_index_failed)
            ).show()
        }
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        when (mMode) {
            MODE_ALL -> onItemClickModeAll(parent, position)
            MODE_SIMSME_SINGLE -> onItemClickModeSingle(parent, view, position)
            MODE_SIMSME_GROUP -> onItemClickModeGroup(position)
            MODE_SIMSME_DISTRIBUTOR -> onItemClickModeGroup(position)
            MODE_NON_SIMSME -> onItemClickModeNonSimsme(parent, position)
            MODE_ADD_CONTACT -> onItemClickModeAdd(parent, position)
            MODE_SEND_CONTACT -> onItemClickModeSend(parent, position)
            else -> LogUtil.w(this.javaClass.name, LocalizedException.UNDEFINED_ARGUMENT)
        }
    }

    private fun onItemClickModeAll(parent: AdapterView<*>, position: Int) {
        val contactList = ArrayList<Contact>()
        val clickedContact = parent.adapter.getItem(position) as Contact

        contactList.add(clickedContact)

        (activity as? ContactsActivity)?.startActivityForModeAll(contactList)
    }

    private fun onItemClickModeSingle(parent: AdapterView<*>, view: View, position: Int) {
        val contact: Contact = if (view.tag is Contact) {
            view.tag as Contact
        } else {
            parent.adapter.getItem(position - rootView.contacts_list_view.headerViewsCount) as Contact
        }

        if (activity is ContactsActivity && !StringUtil.isNullOrEmpty(contact.accountGuid)) {
            (activity as ContactsActivity).startActivityForModeSingle(contact.accountGuid)
        }
    }

    private fun onItemClickModeNonSimsme(parent: AdapterView<*>, position: Int) {
        val contact = parent.adapter.getItem(position) as Contact

        (activity as? ContactsActivity)?.startActivityForModeNonSimsme(contact)
    }

    private fun onItemClickModeGroup(position: Int) {
        val clickedPosition = position - rootView.contacts_list_view.headerViewsCount

        contactsAdapter?.onContactItemClick(clickedPosition)
    }

    private fun onItemClickModeAdd(parent: AdapterView<*>, position: Int) {
        try {
            val activity = requireActivity()
            val callerIntent = activity.intent

            if (!callerIntent.hasExtra(ContactDetailActivity.EXTRA_CONTACT_MAP)) {
                LogUtil.w(this.javaClass.name, "MODE_CREATE but no contact given")
                activity.finish()
            }

            val contact = parent.adapter.getItem(position) as Contact

            val companyContact = mApplication.contactController.getCompanyContactWithAccountGuid(contact.accountGuid)
            val existingContact = mApplication.contactController.getContactByGuid(contact.accountGuid)
            if (companyContact != null || existingContact != null) {
                val nextActivity: Class<*> = if (existingContact != null) {
                    ContactDetailActivity::class.java
                } else {
                    RuntimeConfig.getClassUtil().companyContactDetailActivity
                }

                val intent = Intent(activity, nextActivity)
                intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_GUID, contact.accountGuid)
                startActivity(intent)
                activity.finish()
            } else {
                @Suppress("UNCHECKED_CAST")
                val contactDetails =
                        callerIntent.getSerializableExtra(ContactDetailActivity.EXTRA_CONTACT_MAP) as? MutableMap<String, String>

                if (contactDetails != null) {
                    val intent = Intent(activity, ContactDetailActivity::class.java)
                    intent.putExtra(ContactDetailActivity.EXTRA_MODE, ContactDetailActivity.MODE_CREATE)

                    contactDetails[JsonConstants.GUID] = contact.accountGuid
                    contactDetails[JsonConstants.ACCOUNT_ID] = contact.simsmeId.orEmpty()
                    contactDetails[JsonConstants.PUBLIC_KEY] = contact.publicKey
                    contactDetails[JsonConstants.MANDANT] = contact.mandant

                    intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_MAP, contactDetails as Serializable?)
                    startActivity(intent)
                    activity.finish()
                }
            }
        } catch (e: LocalizedException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
    }

    private fun onItemClickModeSend(parent: AdapterView<*>, position: Int) {
        val contact = parent.adapter.getItem(position) as Contact
        val returnIntent = Intent()

        returnIntent.putExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS, contact.accountGuid)
        activity?.let {
            it.setResult(Activity.RESULT_OK, returnIntent)
            it.finish()
        }
    }

    override fun onContactProfilInfoHasChanged(contactGuid: String) {
        contactsAdapter?.notifyDataSetChanged()
    }

    override fun onContactProfilImageHasChanged(contactGuid: String) {
        contactsAdapter?.notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: AdapterView.OnItemClickListener) {
        onItemClickListener = listener
    }

    private fun updateShownContacts(visibleContacts: List<Contact>?) {
        if (activity == null) {
            return
        }

        if (visibleContacts == null || visibleContacts.isEmpty()) {
            rootView.fragment_no_local_contact_found.visibility = View.VISIBLE
            rootView.contacts_list_view.visibility = View.GONE
        } else {
            rootView.fragment_no_local_contact_found.visibility = View.GONE
            rootView.contacts_list_view.visibility = View.VISIBLE

            setCheckedAsDefault = false
            contactsAdapter = ContactsAdapter(activity, layout, visibleContacts, setCheckedAsDefault, true).apply {
                setImageLoader(imageLoader)
            }

            rootView.contacts_list_view.apply {
                adapter = contactsAdapter
                setOnScrollListener(this@ContactsFragment)
                setSelection(lastSelectedItem)
            }

            when (mMode) {
                MODE_SIMSME_SINGLE, MODE_SEND_CONTACT, MODE_SIMSME_GROUP, MODE_SIMSME_DISTRIBUTOR -> {
                    val headerView = getHeaderView(themedInflater, rootView.contacts_list_view, visibleContacts.size)
                    if (headerView != null) {
                        if (this.headerView != null) {
                            rootView.contacts_list_view.removeHeaderView(this.headerView)
                        }
                        this.headerView = headerView
                        rootView.contacts_list_view.addHeaderView(headerView, null, false)
                    }
                }
            }

            contactsAdapter?.notifyDataSetChanged()
        }
    }

    override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
            imageLoader?.setPauseWork(true)
        } else {
            imageLoader?.setPauseWork(false)
        }
    }

    override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
        if (!firstLoad) {
            lastSelectedItem = firstVisibleItem
        } else {
            firstLoad = false
        }
    }

    private fun refreshTitle() {
        (activity as? ContactsActivity)?.refreshTitle()
    }

    override fun onRefresh() {
        startRefresh()
    }

    private fun getContactsFromDb(): ArrayList<Contact>? {
        when (mMode) {
            MODE_ALL, MODE_SIMSME_SINGLE, MODE_SEND_CONTACT, MODE_SIMSME_GROUP -> return contactController.loadSimsMeContacts()
            MODE_SIMSME_DISTRIBUTOR -> return contactController.loadNonBlockedSimsMeContacts()
            MODE_NON_SIMSME -> {
                if (activity != null && activity is BaseActivity) {
                    (activity as BaseActivity).requestPermission(
                            PermissionUtil.PERMISSION_FOR_READ_CONTACTS,
                            R.string.permission_rationale_contacts
                    ) { permission, permissionGranted ->
                        try {
                            val hasPerm =
                                    permission == PermissionUtil.PERMISSION_FOR_READ_CONTACTS && permissionGranted

                            if (hasPerm) {
                                contacts = contactController.loadNonSimsMeContacts()
                                Handler(Looper.getMainLooper()).post {
                                    checkContactCount()
                                    handleContactsChanged(contacts)
                                }
                            }
                        } catch (e: LocalizedException) {
                            LogUtil.w(ContactsFragment::class.java.simpleName, e.message, e)
                        }
                    }
                }
                return null
            }
            else -> return null
        }
    }
}
