// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.tabs.TabLayout;

import eu.ginlo_apps.ginlo.UseCases.InviteFriendUseCase;
import eu.ginlo_apps.ginlo.activity.chat.SingleChatActivity;
import eu.ginlo_apps.ginlo.adapter.ContactsAdapter;
import eu.ginlo_apps.ginlo.adapter.PageAdapterItemInfo;
import eu.ginlo_apps.ginlo.adapter.SimsmeFragmentPagerAdapter;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BaseContactsFragment;
import eu.ginlo_apps.ginlo.fragment.ContactsFragment;
import eu.ginlo_apps.ginlo.greendao.CompanyContact;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.model.param.SendActionContainer;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.ImageLoader;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import eu.ginlo_apps.ginlo.view.FloatingActionButton;
import eu.ginlo_apps.ginlo.view.ThemedSearchView;
import javax.inject.Inject;
import java.util.ArrayList;

public class ContactsActivity
        extends BaseActivity
        implements ContactsAdapter.ISelectedContacts,
        ContactController.OnContactsChangedListener {

    private static final String TAG = ContactsActivity.class.getSimpleName();
    private static final String EXTRA_FORWARD_USE = "ContactsActivity.forward.use";
    private static final int REQUEST_ACTION_SEND_CODE = 111;
    private final int REQUEST_ADD_CONTACT = 267;

    public static final String EXTRA_MODE = "ContactsActivity.modeExtra";
    public static final String EXTRA_SELECTED_CONTACTS = "ContactsActivity.selectedContacts";
    public static final String EXTRA_SELECTED_CONTACTS_FROM_GROUP = "ContactsActivity.selectedContacts.from.group";
    public static final String EXTRA_GROUP_CONTACTS = "ContactsActivity.group.contacts";
    public static final String EXTRA_GROUP_CHAT_OWNER_GUID = "ContactsActivity.group.chat.guid";
    public static final String EXTRA_MAX_SELECTED_CONTACTS_SIZE = "ChatRoomInfoActivity.maxSelectedContactSize";
    public static final int MODE_ALL = 0;
    public static final int MODE_SIMSME_SINGLE = 1;
    public static final int MODE_SIMSME_GROUP = 2;
    public static final int MODE_SIMSME_DISTRIBUTOR = 3;
    public static final int MODE_NON_SIMSME = 4;
    public static final int MODE_ADD_CONTACT = 5;
    public static final int MODE_SEND_CONTACT = 6;
    public static final String EXTRA_ADD_CONTACTS_LIST = "ContactsActivity.add.contacts.list";

    @Inject
    public Router router;
    protected MenuItem mSearchItem;
    int mMode;
    ViewPager mViewPager;
    ContactsFragment mContactsFragment;
    View mFabButton;
    SearchView mSearchView;
    TabLayout mTabLayout;
    private boolean mIsSendAction;
    private Dialog mOverflowMenuDialog;
    /**
     * selektierte Gruppenkontakte
     */
    private ArrayList<String> mSelectedContactsGuids;
    private int mMaxSelectableContactSize;
    private AccountController mAccountController;
    private ContactController mContactController;

    public void onOptionsMenuClick(final View v) {
        try {
            View menuRoot = ViewExtensionsKt.themedInflate(LayoutInflater.from(this), this, R.layout.menu_overflow_contacts, null);

            if (mAccountController.getManagementCompanyIsUserRestricted()) {
                final TextView firstItem = menuRoot.findViewById(R.id.menu_contacs_first_item);
                final TextView secondItem = menuRoot.findViewById(R.id.menu_contacs_second_item);
                firstItem.setVisibility(View.GONE);
                secondItem.setVisibility(View.GONE);
            }

            mOverflowMenuDialog = new AppCompatDialog(this);
            mOverflowMenuDialog.setContentView(menuRoot);
            Window dialogWindow = mOverflowMenuDialog.getWindow();
            if (dialogWindow != null) {
                dialogWindow.setGravity(Gravity.END | Gravity.TOP);
                dialogWindow.setBackgroundDrawableResource(R.color.transparent);
            }
            mOverflowMenuDialog.show();
        } catch (final LocalizedException le) {
            LogUtil.w(ContactsActivity.this.getClass().getSimpleName(), le.getMessage(), le);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mMode != MODE_NON_SIMSME) {
            SearchView.OnQueryTextListener onQueryTextListener = getSearchOnQueryTextListener();

            mSearchView = new ThemedSearchView(getSupportActionBar().getThemedContext(), ScreenDesignUtil.getInstance().getMainContrast80Color(getSimsMeApplication()));
            mSearchView.setQueryHint(getString(R.string.android_search_placeholder_contacts));
            mSearchView.setOnQueryTextListener(onQueryTextListener);
            mSearchView.setOnCloseListener(getSearchCloseListener());
            mSearchView.setOnSearchClickListener(getSearchClickListener());

            mSearchItem = menu.add("Search");

            mSearchItem.setActionView(mSearchView);
            mSearchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

            setIsMenuVisible(true);

            getMenuInflater().inflate(R.menu.menu_chats_overflow_dummy, menu);
        }
        return true;
    }

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        mAccountController = getSimsMeApplication().getAccountController();
        if(mAccountController == null) {
            LogUtil.e(TAG, "onCreateActivity: mAccountController = null");
            return;
        }

        mContactController = getSimsMeApplication().getContactController();
        if(mContactController == null) {
            LogUtil.e(TAG, "onCreateActivity: mContactController = null");
            return;
        }

        mContactController.setOnContactsChangedListener(this);

        try {
            setTitle(R.string.contacts_overViewViewControllerTitle);

            mMode = (getIntent().getExtras() != null) ? getIntent().getExtras().getInt(EXTRA_MODE) : 0;

            //----------- Oeffnen In ----------->
            Intent intent = getIntent();
            String action = intent.getAction();

            if (!StringUtil.isNullOrEmpty(action) && (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action))) {
                SendActionContainer actionContainer = new FileUtil(this).checkFileSendActionIntent(intent);

                if (!StringUtil.isNullOrEmpty(actionContainer.displayMessage)) {
                    Toast.makeText(this, actionContainer.displayMessage, Toast.LENGTH_LONG).show();
                }

                mIsSendAction = true;
                mMode = MODE_SIMSME_SINGLE;
            }
            //<----------- Oeffnen In -----------

            mTabLayout = findViewById(R.id.contacts_activity_tab_layout);
            mViewPager = findViewById(R.id.contatcs_activity_viewpager);

            SimsmeFragmentPagerAdapter pagerAdapter = new SimsmeFragmentPagerAdapter(getSupportFragmentManager());
            mViewPager.setAdapter(pagerAdapter);

            initFragments(pagerAdapter);

            mTabLayout.setupWithViewPager(mViewPager);
            mTabLayout.addOnTabSelectedListener(getTabSelectedListener());

            mFabButton = findViewById(R.id.fab_image_button_contacts);

            if (mIsSendAction || mAccountController.getManagementCompanyIsUserRestricted() || mMode == MODE_ADD_CONTACT || mMode == MODE_NON_SIMSME) {
                mFabButton.setVisibility(View.GONE);
            }
            if (mMode == MODE_NON_SIMSME) {
                setTitle(R.string.settings_informFriends);
            }

            if (mMode == MODE_SIMSME_GROUP || mMode == MODE_SIMSME_DISTRIBUTOR) {
                if (intent.getExtras() != null) {
                    mSelectedContactsGuids = intent.getExtras().getStringArrayList(EXTRA_SELECTED_CONTACTS_FROM_GROUP);
                    mMaxSelectableContactSize = intent.getExtras().getInt(EXTRA_MAX_SELECTED_CONTACTS_SIZE, -1);
                }

                setRightActionBarImage(R.drawable.ic_done_white_24dp, createRightClicklistener(), getResources().getString(R.string.content_description_contact_list_apply), -1);
            }

            if (mSelectedContactsGuids == null) {
                mSelectedContactsGuids = new ArrayList<>();
            }
            handleTabLayout(pagerAdapter);

        } catch (LocalizedException e) {
            String identifier = e.getIdentifier();
            if (!StringUtil.isNullOrEmpty(identifier)) {
                if (LocalizedException.NO_ACTION_SEND.equals(identifier) || LocalizedException.NO_DATA_FOUND.equals(identifier)) {
                    Toast.makeText(this, R.string.chat_share_file_infos_are_missing, Toast.LENGTH_LONG).show();
                } else if (LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED.equals(identifier)) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            finish();
        }
    }

    protected void initFragments(SimsmeFragmentPagerAdapter pagerAdapter) throws LocalizedException {
        mContactsFragment = ContactsFragment.Companion.newInstance(mMode);
        pagerAdapter.addNewFragment(new PageAdapterItemInfo("", mContactsFragment));
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_contacts;
    }

    @Override
    protected void onResumeActivity() {
        LogUtil.d(TAG, "onResumeActivity called.");
    }

    @Override
    public void onContactsChanged() {
        LogUtil.d(TAG, "onContactsChanged called.");
    }

    private void handleTabLayout(SimsmeFragmentPagerAdapter pagerAdapter) {

        if (pagerAdapter.getCount() < 2) {
            mTabLayout.setVisibility(View.GONE);
        } else {
            final ScreenDesignUtil screenDesignUtil = ScreenDesignUtil.getInstance();

            for (int i = 0; i < pagerAdapter.getCount(); i++) {
                Fragment f = pagerAdapter.getItem(i);
                if (f instanceof BaseContactsFragment) {
                    TabLayout.Tab tab = mTabLayout.getTabAt(i);
                    if (tab != null) {
                        BaseContactsFragment.ContactsFragmentType type = ((BaseContactsFragment) f).getContactsFragmentType();
                        if (type.equals(BaseContactsFragment.ContactsFragmentType.TYPE_COMPANY)) {
                            tab.setIcon(R.drawable.business);
                        } else if (type.equals(BaseContactsFragment.ContactsFragmentType.TYPE_DOMAIN)) {
                            tab.setIcon(R.drawable.mail);
                        } else if (type.equals(BaseContactsFragment.ContactsFragmentType.TYPE_PRIVATE)) {
                            tab.setIcon(R.drawable.phone);
                            //tab.getIcon().setColorFilter(c , PorterDuff.Mode.SRC_ATOP);
                        }
                    }

                    if (tab != null && tab.getIcon() != null) {
                        tab.getIcon().setColorFilter(screenDesignUtil.getMainContrast80Color(SimsMeApplication.getInstance()), PorterDuff.Mode.SRC_ATOP);
                    }
                }
            }

            int selectedTabIndex = mTabLayout.getSelectedTabPosition();
            if (selectedTabIndex > -1) {
                TabLayout.Tab tab = mTabLayout.getTabAt(selectedTabIndex);

                if (tab != null && tab.getIcon() != null) {
                    tab.getIcon().setColorFilter(screenDesignUtil.getAppAccentColor(SimsMeApplication.getInstance()), PorterDuff.Mode.SRC_ATOP);
                }
            }
        }
    }

    @Override
    protected void onPauseActivity() {
        super.onPauseActivity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private TabLayout.OnTabSelectedListener getTabSelectedListener() {
        return new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab != null && tab.getIcon() != null) {
                    tab.getIcon().setColorFilter(ScreenDesignUtil.getInstance().getAppAccentColor(SimsMeApplication.getInstance()), PorterDuff.Mode.SRC_ATOP);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                if (tab != null && tab.getIcon() != null) {
                    tab.getIcon().setColorFilter(ScreenDesignUtil.getInstance().getMainContrast80Color(SimsMeApplication.getInstance()), PorterDuff.Mode.SRC_ATOP);
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                if (tab != null && tab.getIcon() != null) {
                    tab.getIcon().setColorFilter(ScreenDesignUtil.getInstance().getAppAccentColor(SimsMeApplication.getInstance()), PorterDuff.Mode.SRC_ATOP);
                }
            }
        };
    }

    public void handleHeaderSearchButtonClick(View v) {
        // In PK keine Funktionalität
    }

    protected SearchView.OnCloseListener getSearchCloseListener() {
        return null;
    }

    protected OnClickListener getSearchClickListener() {
        return null;
    }

    protected SearchView.OnQueryTextListener getSearchOnQueryTextListener() {
        return new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                SimsmeFragmentPagerAdapter adapter = (SimsmeFragmentPagerAdapter) mViewPager.getAdapter();

                if (adapter != null) {
                    Fragment fragment = adapter.getItem(mViewPager.getCurrentItem());

                    if (fragment instanceof BaseContactsFragment) {
                        return ((BaseContactsFragment) fragment).searchQueryTextChanged(query);
                    }
                }
                return false;
            }
        };
    }

    OnClickListener createRightClicklistener() {
        return new OnClickListener() {
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

    public void startActivityForModeAll(final ArrayList<Contact> contacts) {
        Intent intent = new Intent(ContactsActivity.this, eu.ginlo_apps.ginlo.ContactDetailActivity.class);

        intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_LIST, contacts);

        startActivity(intent);
    }

    public void startActivityForModeSingle(@NonNull final String contactGuid) {
        Intent intent = getIntentFromCallerIntent(SingleChatActivity.class);

        if (mIsSendAction) {
            intent.putExtra(SingleChatActivity.EXTRA_TARGET_GUID, contactGuid);
            startActivityForResult(intent, REQUEST_ACTION_SEND_CODE);
        } else {
            if (intent.getBooleanExtra(EXTRA_FORWARD_USE, false)) {
                intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            } else {
                intent = new Intent(ContactsActivity.this, SingleChatActivity.class);
            }
            intent.putExtra(SingleChatActivity.EXTRA_TARGET_GUID, contactGuid);
            startActivity(intent);

            // selbstzerstören, damit die activity wieder aus den activity
            // stack raus fliegt
            finish();
        }
    }

    public void startActivityForModeNonSimsme(@NonNull final Contact contact) {
        try {
            if (!StringUtil.isNullOrEmpty(contact.getPhoneNumber())) {

                DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        try {
                            final Intent intent = new Intent(Intent.ACTION_SENDTO);
                            intent.setData(Uri.parse("smsto:" + contact.getPhoneNumber()));
                            intent.putExtra("sms_body", getString(R.string.contacts_smsMessageBody));
                            router.startExternalActivity(intent);
                        } catch (LocalizedException e) {
                            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                        }
                    }
                };

                DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int which) {
                    }
                };

                String title = getString(R.string.notification_title);
                String positiveButton = getString(R.string.next);
                String message = getString(R.string.settings_informFriends_button_textMessageButton_hint).replace("*", "");

                String negativeButton = getString(R.string.std_cancel);

                final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, message,
                        title,
                        positiveButton,
                        negativeButton,
                        positiveOnClickListener,
                        negativeOnClickListener);

                dialog.show();
            } else if (!StringUtil.isNullOrEmpty(contact.getEmail())) {
                final Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setDataAndType(Uri.parse("mailto:" + contact.getEmail()), MimeType.TEXT_PLAIN);
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{contact.getEmail()});
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.contacts_eMailMessageSubject));
                intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.contacts_eMailMessageBody));
                router.startExternalActivity(intent);
            }
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }
    }

    @Override
    public void onBackPressed() {
        if (mSearchView != null && !mSearchView.isIconified()) {
            mSearchView.setQuery("", true);
            mSearchView.setIconified(true);
            return;
        }
        super.onBackPressed();
        setResult(RESULT_CANCELED);
    }

    @Override
    protected void onActivityPostLoginResult(int requestCode,
                                             int resultCode,
                                             Intent data) {
        super.onActivityPostLoginResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ADD_CONTACT) {
            if (mContactsFragment != null) {
                mContactsFragment.startRefresh();
            }
        } else if ((requestCode == REQUEST_ACTION_SEND_CODE) && (resultCode == RESULT_OK)) {
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (mExceptionWasThrownInOnCreate) {
            mExceptionWasThrownInOnCreate = isLogout();
            if (!mExceptionWasThrownInOnCreate) {
                onCreateActivity(mSaveInstanceState);
            }
        }
    }

    @Override
    public void finish() {
        //Finish nur wenn die App eingeloggt ist oder die Activity nicht gerade kreiert wurde
        //Die Activity darf nicht gefinshed werden, da bei einem "Öffnen in" die Permission auf die Datei entfallen
        //von FPL
        if (!mExceptionWasThrownInOnCreate || !mAfterCreate) {
            LogUtil.d("FORWARD_ACT", "FINSISH!");
            super.finish();
        }
    }

    public void onFabPressed(View v) {
        createContact(null);
    }

    public void refreshTitle() {
        if (mViewPager == null) {
            return;
        }

        SimsmeFragmentPagerAdapter adapter = (SimsmeFragmentPagerAdapter) mViewPager.getAdapter();

        if (adapter != null) {
            Fragment fragment = adapter.getItem(mViewPager.getCurrentItem());

            if (fragment instanceof BaseContactsFragment) {
                String title = ((BaseContactsFragment) fragment).getTitle();

                if (!StringUtil.isNullOrEmpty(title)) {
                    setTitle(title);
                }
            }
        }
    }

    public void createContact(final View view) {
        if (mOverflowMenuDialog != null) {
            mOverflowMenuDialog.hide();
        }
        final Intent intent = new Intent(this, SearchContactActivity.class);
        startActivity(intent);
    }

    public void refreshContacts(final View view) {
        mOverflowMenuDialog.hide();
        if (mContactsFragment != null) {
            mContactsFragment.startRefresh();
        }
    }

    public void handleInviteClick(final View view) {
       if (mOverflowMenuDialog != null) {
            mOverflowMenuDialog.hide();
        }

        InviteFriendUseCase inviteFriendUseCase = new InviteFriendUseCase();
        inviteFriendUseCase.execute(this);
    }

    public void handleSearchClick(final View view) {
        final Intent intent = new Intent(ContactsActivity.this, SearchContactActivity.class);
        startActivity(intent);
    }

    @Override
    protected void colorizeActivity() {
        super.colorizeActivity();
        final ScreenDesignUtil screenDesignUtil = ScreenDesignUtil.getInstance();

        final int fabOverviewColor = screenDesignUtil.getFabOverviewColor(getSimsMeApplication());
        final int fabOverviewIconColor = screenDesignUtil.getFabIconOverviewColor(getSimsMeApplication());

        if (mFabButton != null) {
            final FloatingActionButton castedFab = (FloatingActionButton) mFabButton;
            castedFab.setColorNormal(fabOverviewColor);
            castedFab.setColorPressed(fabOverviewColor);
            final Drawable drawable = castedFab.getIconDrawable();
            drawable.setColorFilter(fabOverviewIconColor, PorterDuff.Mode.SRC_ATOP);
        }
    }

    @Override
    public void addSelectedContactGuid(String contactGuid) {
        if (mSelectedContactsGuids != null && !mSelectedContactsGuids.contains(contactGuid)) {
            final boolean mMaxSelectContactUnlimited = mMaxSelectableContactSize < 0;
            if (mMaxSelectContactUnlimited) {
                mSelectedContactsGuids.add(contactGuid);
            } else if ((mSelectedContactsGuids.size() + 1) <= mMaxSelectableContactSize) {
                mSelectedContactsGuids.add(contactGuid);
            } else {
                Toast.makeText(this, R.string.service_ERR_0100, Toast.LENGTH_LONG).show();
            }
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

    @Nullable
    ArrayList<String> getSelectedContactsGuids() {
        return mSelectedContactsGuids;
    }

    protected void showFabButon(final boolean show) {
        try {
            if (mIsSendAction || mAccountController.getManagementCompanyIsUserRestricted() || mMode == MODE_ADD_CONTACT || mMode == MODE_NON_SIMSME) {
                mFabButton.setVisibility(View.GONE);
            } else {
                mFabButton.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        } catch (LocalizedException e) {
            LogUtil.w("ContactsActivity", "ShowFabButton", e);
        }
    }

    protected ImageLoader initImageLoader() {
        ImageLoader imageLoader = new ImageLoader(this, (int) getResources().getDimension(R.dimen.contact_item_multi_select_icon_diameter), false) {
            @Override
            protected Bitmap processBitmap(Object data) {
                if (data == null) {
                    return null;
                }

                try {
                    String accountGuid = null;
                    if (data instanceof String) {
                        accountGuid = (String) data;
                    } else if (data instanceof CompanyContact) {
                        accountGuid = ((CompanyContact) data).getAccountGuid();
                    } else if (data instanceof Contact) {
                        accountGuid = ((Contact) data).getAccountGuid();
                    }

                    if (!StringUtil.isNullOrEmpty(accountGuid)) {
                        return getSimsMeApplication().getChatImageController().getImageByGuidWithoutCacheing(accountGuid,
                                getImageSize(), getImageSize());
                    }

                    Bitmap returnImage = null;

                    if (data instanceof Contact) {
                        Contact contact = (Contact) data;

                        if (((contact.getIsSimsMeContact() == null) || !contact.getIsSimsMeContact())
                                && (contact.getPhotoUri() != null)) {
                            returnImage = ContactUtil.loadContactPhotoThumbnail(contact.getPhotoUri(), getImageSize(), ContactsActivity.this);
                        }

                        if (returnImage == null) {
                            returnImage = mContactController.getFallbackImageByContact(getSimsMeApplication(), contact);
                        }

                        if (returnImage == null) {
                            returnImage = getSimsMeApplication().getChatImageController().getImageByGuidWithoutCacheing(AppConstants.GUID_PROFILE_USER,
                                    getImageSize(), getImageSize());
                        }
                    }

                    return returnImage;
                } catch (LocalizedException e) {
                    LogUtil.w("Image Loader Contacts Activity", "Image can't be loaded.", e);
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

        getSimsMeApplication().getChatImageController().addListener(imageLoader);

        return imageLoader;
    }

}
