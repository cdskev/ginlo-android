// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.adapter.ContactsAdapter;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.util.ImageLoader;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.util.ArrayList;
import java.util.List;

import static eu.ginlo_apps.ginlo.ContactsActivity.*;

public class ChatRoomMemberActivity
        extends BaseActivity implements ContactsAdapter.ISelectedContacts, AdapterView.OnItemClickListener {
    public static final String EXTRA_MODE = "ChatRoomMemberActivity.mode";
    public static final int EXTRA_MODE_REMOVE = 1;

    public static final int EXTRA_MODE_ADMIN = 2;

    private ChatImageController mChatImageController;
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

        ImageLoader imageLoader = initImageLoader(diameter);

        mMemberAdapter.setImageLoader(imageLoader);

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

    private ImageLoader initImageLoader(final int imageDiameter) {
        //Image Loader zum Laden der ChatoverviewItems Icons
        ImageLoader imageLoader = new ImageLoader(this, imageDiameter, false) {
            @Override
            protected Bitmap processBitmap(Object data) {
                if (data == null) {
                    return null;
                }
                try {
                    Bitmap returnImage = null;

                    if (data instanceof Contact) {
                        Contact contact = (Contact) data;

                        if (((contact.getIsSimsMeContact() == null) || !contact.getIsSimsMeContact())
                                && (contact.getPhotoUri() != null)) {
                            returnImage = ContactUtil.loadContactPhotoThumbnail(contact.getPhotoUri(), getImageSize(),
                                    ChatRoomMemberActivity.this);
                        }

                        if (returnImage == null) {
                            if (contact.getAccountGuid() != null) {
                                returnImage = mChatImageController.getImageByGuidWithoutCacheing(contact.getAccountGuid(),
                                        getImageSize(), getImageSize());
                            } else {
                                returnImage = getSimsMeApplication().getContactController().getFallbackImageByContact(ChatRoomMemberActivity.this.getApplicationContext(), contact
                                );
                            }
                        }

                        if (returnImage == null) {
                            returnImage = mChatImageController.getImageByGuidWithoutCacheing(AppConstants.GUID_PROFILE_USER,
                                    getImageSize(), getImageSize());
                        }
                    }

                    return returnImage;
                } catch (LocalizedException e) {
                    LogUtil.w("ChatRoomMemberActivity", "Image can't be loaded.", e);
                    return null;
                }
            }

            @Override
            protected void processBitmapFinished(Object data, ImageView imageView) {
                //Nothing to do
            }
        };

        // Add a cache to the image loader
        imageLoader.addImageCache(getSupportFragmentManager(), 0.1f);
        imageLoader.setImageFadeIn(false);
        imageLoader.setLoadingImage(R.drawable.gfx_profil_placeholder);

        if (mChatImageController == null) {
            mChatImageController = getSimsMeApplication().getChatImageController();
        }

        mChatImageController.addListener(imageLoader);

        return imageLoader;
    }
}
