// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import eu.ginlo_apps.ginlo.adapter.PageAdapterItemInfo;
import eu.ginlo_apps.ginlo.adapter.SearchContactsCursorAdapter;
import eu.ginlo_apps.ginlo.adapter.SimsmeFragmentPagerAdapter;
import eu.ginlo_apps.ginlo.context.SimsMeApplicationBusiness;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ContactController.IndexType;
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BaseContactsFragment;
import eu.ginlo_apps.ginlo.fragment.CompanyAddressBookFragment;
import eu.ginlo_apps.ginlo.fragment.ContactsFragment;
import eu.ginlo_apps.ginlo.greendao.CompanyContact;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.StringUtil;
import net.sqlcipher.Cursor;
import java.util.ArrayList;

public class ContactsActivityBusiness
        extends ContactsActivity {
    private static final int RESULT_CODE_ENTER_ACTIVIATION_CODE = 1;

    private ContactControllerBusiness mContactControllerBusiness;

    private CompanyAddressBookFragment mCompanyAddressBookFragment;

    private CompanyAddressBookFragment mDomainAddressBookFragment;

    private int mLastPagerPosition;

    private View.OnClickListener mBaseRightClickListener;

    private LinearLayout mContactContentContainer;

    private RecyclerView mContactSearchRecyclerView;

    private SearchContactsCursorAdapter mContactSearchAdapter;

    @Override
    protected void initFragments(SimsmeFragmentPagerAdapter pagerAdapter) throws LocalizedException {


        final AccountController accountController = getSimsMeApplication().getAccountController();

        if (!accountController.getManagementCompanyIsUserRestricted()) {
            mContactsFragment = ContactsFragment.Companion.newInstance(mMode);

            pagerAdapter.addNewFragment(new PageAdapterItemInfo("", mContactsFragment));
        }

        if (mMode == MODE_NON_SIMSME) {
            // kein E-Mailverzeichnis / Company Verzeichnis bei Kontakte einladen
            return;
        }

        final boolean deviceManaged = accountController.isDeviceManaged();

        if (!accountController.getManagementCompanyIsUserRestricted()) {
            final Contact ownContact = getSimsMeApplication().getContactController().getOwnContact();

            if (ownContact != null) {
                final String domain = ownContact.getDomain();
                if (!StringUtil.isNullOrEmpty(domain)) {
                    mDomainAddressBookFragment = CompanyAddressBookFragment.newInstance((SimsMeApplicationBusiness) getApplication(), mMode, IndexType.INDEX_TYPE_DOMAIN);
                    pagerAdapter.addNewFragment(new PageAdapterItemInfo("", mDomainAddressBookFragment));

                } else if (!deviceManaged) {
                    mDomainAddressBookFragment = CompanyAddressBookFragment.newInstance((SimsMeApplicationBusiness) getApplication(), mMode, IndexType.INDEX_TYPE_DOMAIN);
                    pagerAdapter.addNewFragment(new PageAdapterItemInfo("", mDomainAddressBookFragment));
                }
            }
        }

        if (deviceManaged) {
            mCompanyAddressBookFragment = CompanyAddressBookFragment.newInstance((SimsMeApplicationBusiness) getApplication(), mMode, ContactController.IndexType.INDEX_TYPE_COMPANY);
            pagerAdapter.addNewFragment(new PageAdapterItemInfo("", mCompanyAddressBookFragment));
        }

        if (accountController.getManagementCompanyIsUserRestricted()) {
            mSecondaryTitle = findViewById(R.id.toolbar_secondary_title);
            mSecondaryTitle.setVisibility(View.VISIBLE);
            mSecondaryTitle.setText(accountController.getManagementCompanyName());
        }
    }

    @Override
    public void onCreateActivity(Bundle savedInstanceStae) {
        super.onCreateActivity(savedInstanceStae);

        mContactControllerBusiness = (ContactControllerBusiness) ((SimsMeApplicationBusiness) getApplication()).getContactController();

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    mFabButton.setVisibility(View.VISIBLE);
                } else {
                    mFabButton.setVisibility(View.GONE);
                }

                SimsmeFragmentPagerAdapter adapter = (SimsmeFragmentPagerAdapter) mViewPager.getAdapter();

                if (adapter != null) {
                    Fragment fragment = adapter.getItem(position);

                    if (fragment instanceof BaseContactsFragment) {
                        if (fragment.getActivity() != null) {
                            ((BaseContactsFragment) fragment).onResumeFragment();
                        } else {
                            //Ausnahme wenn die Activity im Hintergrund bereinigt wurde.
                            //Fragmente sind Initialisiert aber nicht an einer Aktivity gebunden
                            finish();
                        }
                    }

                    for (int i = 0; i < adapter.getCount(); i++) {
                        Fragment f = adapter.getItem(i);

                        if (f instanceof BaseContactsFragment && f != fragment) {
                            ((BaseContactsFragment) f).searchQueryTextChanged(null);
                        }
                    }

                    if (mSearchView != null && !mSearchView.isIconified()) {
                        mSearchView.setIconified(true);
                        getToolbar().collapseActionView();
                    }

                    if (fragment instanceof BaseContactsFragment) {
                        setTitle(((BaseContactsFragment) fragment).getTitle());
                    }
                }

                mLastPagerPosition = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        mContactContentContainer = findViewById(R.id.contacts_activity_content);
        mContactSearchRecyclerView = findViewById(R.id.contacts_activity_search_recycler_view);

        // improve performance if you know that changes in content
        // do not change the size of the RecyclerView
        mContactSearchRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mContactSearchRecyclerView.setLayoutManager(layoutManager);

        mContactSearchAdapter = new SearchContactsCursorAdapter(this, null, initImageLoader());
        mContactSearchAdapter.setItemClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSearchItemClick(v);
            }
        });
        mContactSearchRecyclerView.setAdapter(mContactSearchAdapter);
        mContactSearchRecyclerView.setVisibility(View.GONE);
    }

    private void onSearchItemClick(View v) {
        Object tag = v.getTag();
        if (!(tag instanceof SearchContactsCursorAdapter.ViewHolder)) {
            return;
        }
        int pos = ((SearchContactsCursorAdapter.ViewHolder) tag).getAdapterPosition();

        SearchContactsCursorAdapter.ContactListItem item = mContactSearchAdapter.getItemAt(pos);

        if (item == null) {
            return;
        }

        if (mMode == MODE_ALL) {
            if (!StringUtil.isNullOrEmpty(item.getAccountGuid())) {
                startContactDetailsActivity(item.getAccountGuid());
            }
        } else if (mMode == MODE_SIMSME_SINGLE) {
            final String guid = item.getAccountGuid();

            if (!StringUtil.isNullOrEmpty(guid)) {
                startActivityForModeSingle(guid);
            }
        } else if (mMode == MODE_SIMSME_GROUP || mMode == MODE_SIMSME_DISTRIBUTOR) {
            mContactSearchAdapter.onContactItemClick(pos);

            if (!StringUtil.isNullOrEmpty(item.getAccountGuid()) && !StringUtil.isNullOrEmpty(item.getClassType())) {
                String classEntry = item.getClassType();
                if (!StringUtil.isNullOrEmpty(classEntry) && !StringUtil.isEqual(Contact.CLASS_PRIVATE_ENTRY, classEntry)
                        && !StringUtil.isEqual(Contact.CLASS_OWN_ACCOUNT_ENTRY, classEntry)) {
                    getSimsMeApplication().getPreferencesController().addLastUsedContactGuid(item.getAccountGuid(),
                            StringUtil.isEqual(Contact.CLASS_COMPANY_ENTRY, classEntry)
                                    ? IndexType.INDEX_TYPE_COMPANY : IndexType.INDEX_TYPE_DOMAIN);

                    if (mCompanyAddressBookFragment != null) {
                        mCompanyAddressBookFragment.refreshData();
                    }

                    if (mDomainAddressBookFragment != null) {
                        mDomainAddressBookFragment.refreshData();
                    }
                }

                final Handler handler = new Handler();
                final Runnable runnable = new Runnable() {
                    public void run() {
                        onBackPressed();
                    }
                };
                handler.postDelayed(runnable, 300);
            }
        } else if (mMode == MODE_SEND_CONTACT) {
            try {
                final String guid = item.getAccountGuid();

                if (StringUtil.isNullOrEmpty(guid)) {
                    return;
                }

                final Contact contact = mContactControllerBusiness.getContactByGuid(guid);

                if (contact != null) {
                    Intent returnIntent = new Intent();

                    returnIntent.putExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS, contact.getAccountGuid());

                    setResult(RESULT_OK, returnIntent);
                    finish();
                } else {
                    CompanyContact companyContactByGuid = mContactControllerBusiness.getCompanyContactWithAccountGuid(guid);

                    if (companyContactByGuid != null) {
                        Contact hiddenContact = mContactControllerBusiness.createHiddenContactForCompanyContact(companyContactByGuid);

                        Intent returnIntent = new Intent();
                        returnIntent.putExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS, hiddenContact.getAccountGuid());
                        setResult(RESULT_OK, returnIntent);
                        finish();
                    }
                }

            } catch (LocalizedException e) {
                LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onResumeActivity() {
        super.onResumeActivity();

        if (!mAfterCreate) {
            if (mViewPager != null) {
                mViewPager.setCurrentItem(mLastPagerPosition);
            }
        }

        // farben erst hier setzen, da jetzt erst die tabs geladne wurden
        ColorUtil.getInstance().colorizeTabLayoutHeader(getSimsMeApplication(), mTabLayout);
    }

    @Override
    protected void onActivityPostLoginResult(int requestCode,
                                             int resultCode,
                                             Intent returnIntent) {
        super.onActivityPostLoginResult(requestCode, resultCode, returnIntent);

        if (resultCode == RESULT_OK) {
            if (requestCode == RESULT_CODE_ENTER_ACTIVIATION_CODE && mCompanyAddressBookFragment != null) {
                mCompanyAddressBookFragment.onRefresh();
            }
        }
    }

    public void onRegisterEmailClick(View v) {
        AccountController accountControllerBusiness = ((SimsMeApplicationBusiness) getApplication()).getAccountController();
        boolean isWaitingForConformation = accountControllerBusiness.getWaitingForEmailConfirmation();
        if (isWaitingForConformation) {
            Intent intent = new Intent(this, EnterEmailActivationCodeActivity.class);
            startActivityForResult(intent, RESULT_CODE_ENTER_ACTIVIATION_CODE);
        } else {
            Intent intent = new Intent(ContactsActivityBusiness.this, RegisterEmailActivity.class);
            startActivity(intent);
            try {
                Contact ownContact = getSimsMeApplication().getContactController().getOwnContact();
                intent.putExtra(RegisterEmailActivity.EXTRA_PREFILLED_FIRST_NAME, ownContact.getFirstName());
                intent.putExtra(RegisterEmailActivity.EXTRA_PREFILLED_LAST_NAME, ownContact.getLastName());

            } catch (LocalizedException le) {
                LogUtil.e(this.getClass().getName(), le.getMessage(), le);
            }
        }
    }

    protected View.OnClickListener createRightClicklistener() {
        mBaseRightClickListener = super.createRightClicklistener();

        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ArrayList<String> selectedContacsGuids = getSelectedContactsGuids();
                    if (selectedContacsGuids != null && selectedContacsGuids.size() > 0) {
                        for (String contactGuid : selectedContacsGuids) {
                            Contact contact = mContactControllerBusiness.getContactByGuid(contactGuid);
                            if (contact == null) {
                                CompanyContact companyContact = mContactControllerBusiness.getCompanyContactWithAccountGuid(contactGuid);

                                if (companyContact != null) {
                                    mContactControllerBusiness.createHiddenContactForCompanyContact(companyContact);
                                }
                            }
                        }
                    }
                } catch (LocalizedException e) {
                    LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                }

                if (mBaseRightClickListener != null) {
                    mBaseRightClickListener.onClick(v);
                }
            }
        };
    }

    @Override
    protected void colorizeActivity() {
        super.colorizeActivity();
        // fab
        final ColorUtil colorUtil = ColorUtil.getInstance();
        final int accentColor = colorUtil.getAppAccentColor(getSimsMeApplication());

        if (mTabLayout != null) {
            mTabLayout.setSelectedTabIndicatorColor(accentColor);
        }
    }

    @Override
    public void startActivityForModeSingle(@NonNull final String contactGuid) {
        try {
            final Contact contact = mContactControllerBusiness.getContactByGuid(contactGuid);

            ContactController.OnLoadPublicKeyListener onLoadPublicKeyListener = new ContactController.OnLoadPublicKeyListener() {
                @Override
                public void onLoadPublicKeyComplete(Contact contact) {
                    dismissIdleDialog();
                    ContactsActivityBusiness.super.startActivityForModeSingle(contactGuid);
                    finish();
                }

                @Override
                public void onLoadPublicKeyError(String message) {
                    dismissIdleDialog();
                    DialogBuilderUtil.buildErrorDialog(ContactsActivityBusiness.this, message).show();
                }
            };

            if (contact == null || (StringUtil.isNullOrEmpty(contact.getPublicKey()))) {
                CompanyContact companyContact = mContactControllerBusiness.getCompanyContactWithAccountGuid(contactGuid);

                if (companyContact != null) {
                    Contact hiddenContact = mContactControllerBusiness.createHiddenContactForCompanyContact(companyContact);

                    if (StringUtil.isNullOrEmpty(hiddenContact.getPublicKey()) || StringUtil.isNullOrEmpty(hiddenContact.getSimsmeId())) {
                        showIdleDialog(R.string.start_chat_with_company_contact_idle_text);
                        mContactControllerBusiness.loadPublicKey(hiddenContact, onLoadPublicKeyListener);
                    } else {
                        super.startActivityForModeSingle(contactGuid);
                        finish();
                    }
                } else {
                    //der Fall kann eigentlich nicht eintreten, da man ja uebe reinen Company-Contact hierher gekommen ist
                    finish();
                }

            } else {
                super.startActivityForModeSingle(contactGuid);
                finish();
            }
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }
    }

    private void startContactDetailsActivity(final String guid) {
        if (!StringUtil.isNullOrEmpty(guid)) {
            try {
                Class nextActivity;
                final Contact contact = mContactControllerBusiness.getContactByGuid(guid);
                if (contact != null) {
                    nextActivity = ContactDetailActivity.class;
                } else {
                    nextActivity = CompanyContactDetailActivity.class;
                }

                final Intent intent = new Intent(this, nextActivity);
                intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_GUID, guid);
                startActivity(intent);
            } catch (LocalizedException e) {
                LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

    }

    /**
     * refreshContacts
     */
    @Override
    public void refreshContacts(final View view) {
        super.refreshContacts(view);

        if (mCompanyAddressBookFragment != null) {
            mCompanyAddressBookFragment.startRefresh();
        }

        if (mDomainAddressBookFragment != null) {
            mDomainAddressBookFragment.startRefresh();
        }
    }

    protected SearchView.OnCloseListener getSearchCloseListener() {
        return new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                if (mContactContentContainer != null) {
                    resetSearch();
                    mContactContentContainer.setBackgroundColor(Color.TRANSPARENT);
                    mContactContentContainer.setAlpha(1f);
                    showFabButon(true);
                }
                return false;
            }
        };
    }

    private void resetSearch() {
        mContactSearchRecyclerView.setVisibility(View.GONE);

        mContactSearchAdapter.changeCursor(null);
        mContactContentContainer.setVisibility(View.VISIBLE);

    }

    protected View.OnClickListener getSearchClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                grayingContactContent();
                showFabButon(false);
            }
        };
    }

    private void grayingContactContent() {
        if (mContactContentContainer != null) {
            mContactContentContainer.setBackgroundColor(Color.parseColor("#3e494e"));
            mContactContentContainer.setAlpha(0.24f);
        }
    }

    @Override
    public void handleHeaderSearchButtonClick(View v) {
        if (mSearchView != null) {
            mSearchView.setIconified(false);
            grayingContactContent();
            showFabButon(false);
        }
    }

    @Override
    protected SearchView.OnQueryTextListener getSearchOnQueryTextListener() {
        return new SearchView.OnQueryTextListener() {
            String oldText = "";

            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.length() > 1) {
                    if (mContactSearchRecyclerView.getVisibility() != View.VISIBLE) {
                        mContactSearchRecyclerView.setVisibility(View.VISIBLE);
                        mContactContentContainer.setVisibility(View.GONE);
                    }
                    if (!StringUtil.isEqual(oldText, newText)) {
                        oldText = newText;
                        if (mContactControllerBusiness == null) {
                            return true;
                        }
                        mContactControllerBusiness.searchContactsInFtsDb(newText, new GenericActionListener<Cursor>() {
                            @Override
                            public void onSuccess(Cursor cursor) {
                                if (cursor != null) {
                                    mContactSearchAdapter.changeCursor(cursor);
                                }
                            }

                            @Override
                            public void onFail(String message, String errorIdent) {
                            }
                        });
                    }
                } else {
                    oldText = "";
                    resetSearch();
                }
                return true;
            }
        };
    }
}