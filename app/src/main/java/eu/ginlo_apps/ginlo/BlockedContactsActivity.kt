// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo

import android.os.Bundle
import android.view.View
import eu.ginlo_apps.ginlo.adapter.BlockedContactsAdapter
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.controller.ContactController
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Contact
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener
import kotlinx.android.synthetic.main.activity_blocked_contacts.blocked_contacts_list_view
import kotlinx.android.synthetic.main.activity_blocked_contacts.blocked_contacts_text_view

class BlockedContactsActivity : BaseActivity() {

    private val contactController: ContactController by lazy { (application as SimsMeApplication).contactController }

    private lateinit var adapter: BlockedContactsAdapter

    override fun onResumeActivity() {}

    override fun getActivityLayout(): Int {
        return R.layout.activity_blocked_contacts
    }

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        setTextViewVisibility(contactController.blockedContacts.size)

        LogUtil.i(
            this.javaClass.simpleName,
            " Number of blocked contacts ${contactController.blockedContacts.count()}"
        )

        adapter = BlockedContactsAdapter(this, 0, contactController.blockedContacts)

        blocked_contacts_list_view.adapter = adapter
    }

    private fun setTextViewVisibility(itemsSize: Int) {
        if (itemsSize == 0) {
            blocked_contacts_text_view.visibility = View.VISIBLE
        } else {
            blocked_contacts_text_view.visibility = View.GONE
        }
    }

    fun handleUnblockClick(contact: Contact) {
        LogUtil.i(BlockedContactsActivity::class.java.simpleName, "Unblock contact with id ${contact.accountGuid}")

        try {
            showIdleDialog(-1)
            contactController.blockContact(contact.accountGuid, false, true, object : GenericActionListener<Void> {
                override fun onSuccess(nothing: Void?) {
                    dismissIdleDialog()
                    adapter.remove(contact)
                    setTextViewVisibility(adapter.count)
                }

                override fun onFail(message: String?, errorIdent: String?) {
                    dismissIdleDialog()
                }
            })
        } catch (e: LocalizedException) {
            dismissIdleDialog()
            LogUtil.e(this.javaClass.simpleName, e.message, e)
        }
    }
}
