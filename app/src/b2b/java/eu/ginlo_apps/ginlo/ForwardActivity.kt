// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.LinearLayout
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import eu.ginlo_apps.ginlo.adapter.PageAdapterItemInfo
import eu.ginlo_apps.ginlo.adapter.SearchContactsCursorAdapter
import eu.ginlo_apps.ginlo.adapter.SimsmeFragmentPagerAdapter
import eu.ginlo_apps.ginlo.context.SimsMeApplicationBusiness
import eu.ginlo_apps.ginlo.controller.ChatOverviewController
import eu.ginlo_apps.ginlo.controller.ContactController
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.fragment.CompanyAddressBookFragment
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener
import eu.ginlo_apps.ginlo.view.ThemedSearchView
import kotlinx.android.synthetic.main.activity_forward.contacts_activity_content
import kotlinx.android.synthetic.main.activity_forward.contacts_activity_search_recycler_view
import net.sqlcipher.Cursor

class ForwardActivity : ForwardActivityBase(), SearchView.OnQueryTextListener {

    private lateinit var searchView: SearchView

    private lateinit var searchItem: MenuItem

    private var searchQueryOldText = ""

    private lateinit var contactContentContainer: LinearLayout

    private lateinit var contactSearchAdapter: SearchContactsCursorAdapter

    private val contactControllerBusiness: ContactControllerBusiness by lazy { simsMeApplication.contactController as ContactControllerBusiness }

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        super.onCreateActivity(savedInstanceState)

        try {
            val adapter = mViewPager.adapter as? SimsmeFragmentPagerAdapter ?: return

            val accountController = simsMeApplication.accountController
            if (accountController.managementCompanyIsUserRestricted) {
                adapter.removeFragment(mContactsPageInfo)
            }

            if (accountController.isDeviceManaged) {
                val companyAddressBookFragment = CompanyAddressBookFragment.newInstance(
                    application as SimsMeApplicationBusiness,
                    ChatOverviewController.MODE_FORWARD_SINGLE,
                    ContactController.IndexType.INDEX_TYPE_COMPANY
                )

                companyAddressBookFragment.setOnItemClickListener(this)
                adapter.addNewFragment(PageAdapterItemInfo("", companyAddressBookFragment))
            }

            val ownContact = simsMeApplication.contactController.ownContact

            if (ownContact != null) {
                val domain = ownContact.domain

                if (!domain.isNullOrBlank() && !accountController.managementCompanyIsUserRestricted) {
                    val companyAddressBookFragment = CompanyAddressBookFragment.newInstance(
                        application as SimsMeApplicationBusiness,
                        ChatOverviewController.MODE_FORWARD_SINGLE,
                        ContactController.IndexType.INDEX_TYPE_DOMAIN
                    )
                    companyAddressBookFragment.setOnItemClickListener(this)
                    adapter.addNewFragment(PageAdapterItemInfo("", companyAddressBookFragment))
                }
            }

            adapter.notifyDataSetChanged()

            contactContentContainer = contacts_activity_content

            // improve performance if you know that changes in content
            // do not change the size of the RecyclerView
            contacts_activity_search_recycler_view.setHasFixedSize(true)

            // use a linear layout manager
            contacts_activity_search_recycler_view.layoutManager = LinearLayoutManager(this)

            contactSearchAdapter = SearchContactsCursorAdapter(this, null, mImageLoader)
            contactSearchAdapter.setItemClickListener { v ->
                onSearchItemClick(v)
            }
            contacts_activity_search_recycler_view.adapter = contactSearchAdapter
            contacts_activity_search_recycler_view.visibility = View.GONE
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    override fun onBackPressed() {
        if (!searchView.isIconified) {
            searchView.setQuery("", true)
            searchView.isIconified = true
            return
        }
        super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        searchView = ThemedSearchView(
            supportActionBar!!.themedContext,
            ColorUtil.getInstance().getMainContrast80Color(simsMeApplication)
        ).apply {
            queryHint = getString(R.string.android_search_placeholder_contacts)
            setOnCloseListener(getSearchCloseListener())
            setOnSearchClickListener(getSearchClickListener())
        }
        searchView.setOnQueryTextListener(this)
        searchItem = menu.add("Search")

        searchItem.actionView = searchView
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        searchItem.isVisible = false
        setIsMenuVisible(true)

        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        if (newText?.length!! > 1) {
            if (contacts_activity_search_recycler_view.visibility != View.VISIBLE) {
                contacts_activity_search_recycler_view.visibility = View.VISIBLE
                contactContentContainer.visibility = View.GONE
            }
            if (searchQueryOldText != newText) {
                searchQueryOldText = newText
                showIdleDialog()
                contactControllerBusiness.searchContactsInFtsDb(
                    newText,
                    object : GenericActionListener<Cursor> {
                        override fun onSuccess(cursor: Cursor?) {
                            dismissIdleDialog()
                            if (cursor != null) {
                                contactSearchAdapter.changeCursor(cursor)
                            }
                        }

                        override fun onFail(message: String?, errorIdent: String?) {
                            dismissIdleDialog()
                        }
                    })
            }
        } else {
            searchQueryOldText = ""
            resetSearch()
        }
        return true
    }

    private fun getSearchCloseListener(): SearchView.OnCloseListener {
        return SearchView.OnCloseListener {
            resetSearch()
            contactContentContainer.setBackgroundColor(Color.TRANSPARENT)
            contactContentContainer.alpha = 1f
            false
        }
    }

    private fun resetSearch() {
        contacts_activity_search_recycler_view.visibility = View.GONE

        contactSearchAdapter.changeCursor(null)
        contactContentContainer.visibility = View.VISIBLE
    }

    private fun getSearchClickListener(): View.OnClickListener {
        return View.OnClickListener { grayingContactContent() }
    }

    private fun grayingContactContent() {
        contactContentContainer.setBackgroundColor(Color.parseColor("#3e494e"))
        contactContentContainer.alpha = 0.24f
    }

    fun handleHeaderSearchButtonClick(@Suppress("UNUSED_PARAMETER") v: View?) {
        searchView.isIconified = false
        grayingContactContent()
    }

    private fun onSearchItemClick(v: View?) {
        val tag = v?.tag as? SearchContactsCursorAdapter.ViewHolder ?: return
        val pos = tag.adapterPosition

        val item = contactSearchAdapter.getItemAt(pos) ?: return

        if (!item.accountGuid.isNullOrBlank()) {
            try {
                val contactControllerBusiness =
                    (application as SimsMeApplicationBusiness).contactController as ContactControllerBusiness

                val companyContact =
                    contactControllerBusiness.getCompanyContactWithAccountGuid(item.accountGuid)
                var contact = contactControllerBusiness.getContactByGuid(item.accountGuid)

                if (companyContact == null && contact == null) {
                    return
                } else if (companyContact != null && contact == null) {
                    contact = contactControllerBusiness.createHiddenContactForCompanyContact(
                        companyContact
                    )
                }

                if (contact == null) {
                    return
                }

                startChat(contact)
            } catch (e: LocalizedException) {
                LogUtil.w("ForwadActivityBusiness", e.message, e)
            }
        }
    }

    override fun onItemClick(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) {
        try {
            val lPagerAdapter = mViewPager.adapter as? SimsmeFragmentPagerAdapter ?: return
            val currentFragment = lPagerAdapter.getItem(mViewPager.currentItem)

            if (currentFragment is CompanyAddressBookFragment) {
                val lAdapter = currentFragment.adapter

                val companyContact =
                    lAdapter.getItem(position - currentFragment.listview.headerViewsCount)
                if (companyContact != null && companyContact.accountGuid != null) {
                    val contactControllerBusiness =
                        (application as SimsMeApplicationBusiness).contactController as ContactControllerBusiness
                    var contact =
                        contactControllerBusiness.getContactByGuid(companyContact.accountGuid)

                    if (contact == null) {
                        contact = contactControllerBusiness.createHiddenContactForCompanyContact(
                            companyContact
                        )
                    }
                    startChat(contact)
                }
            } else {
                super.onItemClick(parent, view, position, id)
            }
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    override fun getTabSelectedListener(): TabLayout.OnTabSelectedListener {
        return object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                adjustTabColor(tab)

                val visibility: Any? = tab?.tag
                if (visibility is String) {
                    setSearchIconVisibility(visibility)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {

                adjustTabColor(tab)
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                adjustTabColor(tab)
            }

            private fun adjustTabColor(tab: TabLayout.Tab?) =
                tab?.icon?.apply {
                    setColorFilter(
                        ColorUtil.getInstance().getAppAccentColor(this@ForwardActivity.simsMeApplication),
                        PorterDuff.Mode.SRC_ATOP
                    )

                }
        }
    }

    private fun setSearchIconVisibility(tabTag: String) {
        when (tabTag) {
            TAB_COMPANY, TAB_DOMAIN, TAB_CONTACT -> {
                searchItem.isVisible = true
            }
            else -> {
                searchItem.isVisible = false
            }
        }
    }
}