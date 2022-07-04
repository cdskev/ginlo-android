// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import eu.ginlo_apps.ginlo.adapter.ContactsAdapter;
import eu.ginlo_apps.ginlo.greendao.Contact;

import java.util.ArrayList;
import java.util.List;

import static eu.ginlo_apps.ginlo.ContactsActivity.*;

public class ChatRoomMemberActivity
        extends BaseActivity implements ContactsAdapter.ISelectedContacts, AdapterView.OnItemClickListener {
    public static final String EXTRA_MODE = "ChatRoomMemberActivity.mode";
    public static final int EXTRA_MODE_REMOVE = 1;
    public static final int EXTRA_MODE_ADMIN = 2;

    /**
     * selektierte Gruppenkontakte
     */
    private ArrayList<String> mSelectedContactsGuids;

    private ContactsAdapter mMemberAdapter;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        if (getIntent() == null || getIntent().getExtras() == null) {
            return;
        }

        int mode = getIntent().getExtras().getInt(EXTRA_MODE, -1);

        if (mode == -1 || mode > EXTRA_MODE_ADMIN) {
            return;
        }

        List<Contact> members = null;
        if (getIntent().getExtras().get(EXTRA_GROUP_CONTACTS) == null) {
            return;
        }
        ArrayList<String> contactsGuids = getIntent().getExtras().getStringArrayList(EXTRA_GROUP_CONTACTS);

        if (contactsGuids == null) {
            return;
        }

        members = getSimsMeApplication().getContactController().getContactsByGuid(contactsGuids.toArray(new String[0]));

        if (members == null) {
            return;
        }

        boolean isCheckAnAddAction;
        if (mode == EXTRA_MODE_REMOVE) {
            setTitle(R.string.chat_group_remove_member);
            mSelectedContactsGuids = new ArrayList<>();
            isCheckAnAddAction = false;
        } else {
            setTitle(R.string.chat_group_label_addAdmin);
            if (getIntent().getExtras().get(EXTRA_SELECTED_CONTACTS_FROM_GROUP) != null) {
                ArrayList<String> adminGuids = getIntent().getExtras().getStringArrayList(EXTRA_SELECTED_CONTACTS_FROM_GROUP);
                mSelectedContactsGuids = adminGuids;
            } else {
                mSelectedContactsGuids = new ArrayList<>();
            }

            isCheckAnAddAction = true;
        }

        ListView listView = findViewById(R.id.member_list_view);
        listView.setOnItemClickListener(this);

        mMemberAdapter = new ContactsAdapter(this, R.layout.contact_item_overview_layout, members, false, isCheckAnAddAction);

        int diameter = (int) getResources().getDimension(R.dimen.contact_item_single_select_icon_diameter);

        listView.setAdapter(mMemberAdapter);

        setRightActionBarImage(R.drawable.ic_done_white_24dp, createRightClicklistener(), getResources().getString(R.string.content_description_contact_list_apply), -1);
    }

    View.OnClickListener createRightClicklistener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();

                if (mSelectedContactsGuids != null) {
                    String[] selectedContactGuids = mSelectedContactsGuids.toArray(new String[]{});

                    intent.putExtra(EXTRA_SELECTED_CONTACTS, selectedContactGuids);
                }

                setResult(RESULT_OK, intent);
                finish();
            }
        };
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_chat_room_member;
    }

    @Override
    protected void onResumeActivity() {

    }

    @Override
    public void addSelectedContactGuid(String contactGuid) {
        if (mSelectedContactsGuids != null && !mSelectedContactsGuids.contains(contactGuid)) {
            mSelectedContactsGuids.add(contactGuid);
        }
    }

    @Override
    public void removeSelectedContactGuid(String contactGuid) {
        if (mSelectedContactsGuids != null) {
            mSelectedContactsGuids.remove(contactGuid);
        }
    }

    @Override
    public boolean containsSelectedContactGuid(String contactGuid) {
        return mSelectedContactsGuids != null && mSelectedContactsGuids.contains(contactGuid);
    }

    /**
     * Callback method to be invoked when an item in this AdapterView has
     * been clicked.
     * <p>
     * Implementers can call getItemAtPosition(position) if they need
     * to access the data associated with the selected item.
     *
     * @param parent   The AdapterView where the click happened.
     * @param view     The view within the AdapterView that was clicked (this
     *                 will be a view provided by the adapter)
     * @param position The position of the view in the adapter.
     * @param id       The row id of the item that was clicked.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mMemberAdapter != null) {
            mMemberAdapter.onContactItemClick(position);
        }
    }
}
