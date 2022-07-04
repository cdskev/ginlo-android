// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.CompanyContactDetailActivity;
import eu.ginlo_apps.ginlo.ContactDetailActivity;
import eu.ginlo_apps.ginlo.ContactsActivity;
import eu.ginlo_apps.ginlo.ContactsActivityBusiness;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.adapter.CompanyContactsAdapter;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ContactController.IndexType;
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.CompanyContact;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import static android.app.Activity.RESULT_OK;
import static eu.ginlo_apps.ginlo.ContactsActivity.EXTRA_GROUP_CHAT_OWNER_GUID;
import static eu.ginlo_apps.ginlo.ContactsActivity.EXTRA_GROUP_CONTACTS;
import static eu.ginlo_apps.ginlo.ContactsActivity.MODE_ALL;
import static eu.ginlo_apps.ginlo.ContactsActivity.MODE_SEND_CONTACT;
import static eu.ginlo_apps.ginlo.ContactsActivity.MODE_SIMSME_DISTRIBUTOR;
import static eu.ginlo_apps.ginlo.ContactsActivity.MODE_SIMSME_GROUP;
import static eu.ginlo_apps.ginlo.ContactsActivity.MODE_SIMSME_SINGLE;

public class CompanyAddressBookFragment
        extends BaseContactsFragment implements SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemClickListener, AbsListView.OnScrollListener {

    private static final int MAX_CONTACTS_COUNT = 100;
    private CompanyContactsAdapter mAdapter;
    private LinearLayout mRootLayout;
    private ViewGroup mListContainer;
    private String mQuery;
    private ContactControllerBusiness mContactControllerBusiness;
    private String mDomain;
    private ArrayList<CompanyContact> mGroupCompanyContacts;
    private String mGroupChatOwnerGuid;
    private ListView mListView;
    private AdapterView.OnItemClickListener mOnItemClickListener;
    private View mNoContactsLayout;
    private Button mNextButton;
    private IndexType mIndexType;
    private View mHeaderListView;
    private int mContactCount;

    public static CompanyAddressBookFragment newInstance(@NonNull final SimsMeApplication application, final int mode, final IndexType indexType) {
        CompanyAddressBookFragment fragment = new CompanyAddressBookFragment();

        fragment.setApplication(application);
        fragment.setMode(mode);
        fragment.setIndexType(indexType);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null && mIndexType == null) {
            String indexTypeName = savedInstanceState.getString("INDEX_TYPE");

            if (!StringUtil.isNullOrEmpty(indexTypeName)) {
                try {
                    mIndexType = IndexType.valueOf(indexTypeName);
                } catch (IllegalArgumentException e) {
                    LogUtil.w("CompanyAddressBookFragment", "onCreate()", e);
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mIndexType != null) {
            outState.putString("INDEX_TYPE", mIndexType.name());
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        mContactControllerBusiness = (ContactControllerBusiness) getApplication().getContactController();

        try {
            mDomain = getApplication().getContactController().getOwnContact().getDomain();
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getIdentifier(), e);
        }

        final FragmentActivity activity = getActivity();
        if (activity != null) {
            Intent intent = activity.getIntent();
            if (intent != null && intent.getExtras() != null) {
                if (intent.getExtras().get(EXTRA_GROUP_CONTACTS) != null) {
                    ArrayList<String> contactsGuids = intent.getExtras().getStringArrayList(EXTRA_GROUP_CONTACTS);

                    if (contactsGuids != null) {
                        mGroupCompanyContacts = mContactControllerBusiness.getCompanyContactsByGuid(contactsGuids.toArray(new String[0]), null);
                    }
                }

                if (intent.getExtras().get(EXTRA_GROUP_CHAT_OWNER_GUID) != null) {
                    mGroupChatOwnerGuid = intent.getStringExtra(EXTRA_GROUP_CHAT_OWNER_GUID);
                }
            }
        }

        LayoutInflater inflater2 = ViewExtensionsKt.themedInflater(inflater.cloneInContext(this.getContext()), this.getContext());

        mRootLayout = (LinearLayout) inflater2.inflate(R.layout.fragment_company_addressbook, container,
                false);

        mNextButton = mRootLayout.findViewById(R.id.next_button);
        mNoContactsLayout = mRootLayout.findViewById(R.id.company_addressbook_no_contacts_layout);
        mListContainer = mRootLayout.findViewById(R.id.fragment_contacts_business_list_container);
        mListView = mRootLayout.findViewById(R.id.company_addressbook_list_view);

        mHeaderListView = inflater2.inflate(R.layout.contact_list_header_view, mListView,
                false);

        mContactCount = mContactControllerBusiness.countCompanyContacts(mIndexType);

        refreshViews();

        return mRootLayout;
    }

    @Override
    public void onPause() {
        super.onPause();

        try {
            String newDomain = getApplication().getContactController().getOwnContact().getDomain();
            if (!StringUtil.isEqual(mDomain, newDomain)) {
                mDomain = newDomain;
                refreshViews();
            }
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getIdentifier(), e);
        }
    }

    @Override
    public void onResumeFragment() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    public void refreshData() {
        if (mContactCount > MAX_CONTACTS_COUNT) {
            configureViewsForToManyContacts();
            return;
        }

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void refreshViews() {
        if (mRootLayout == null) {
            return;
        }

        if (mContactCount > MAX_CONTACTS_COUNT) {
            configureViewsForToManyContacts();
            return;
        }

        try {
            if (getApplication().getAccountController().isDeviceManaged() || !StringUtil.isNullOrEmpty(mDomain)) {
                AccountController accountControllerBusiness = getApplication().getAccountController();
                boolean isWaitingForConformation = accountControllerBusiness.getWaitingForEmailConfirmation();

                if (isWaitingForConformation) {
                    if (mListContainer != null) {
                        mListContainer.setVisibility(View.GONE);
                    }
                    mNoContactsLayout.setVisibility(View.VISIBLE);

                    mNextButton.setText(getResources().getString(R.string.profile_info_email_button_enter_code));
                } else {
                    if (mAdapter != null && mAdapter.getCount() != 0) {
                        mListContainer.setVisibility(View.VISIBLE);
                        mNoContactsLayout.setVisibility(View.GONE);
                    } else {
                        mListContainer.setVisibility(View.GONE);
                        mNoContactsLayout.setVisibility(View.VISIBLE);
                        final TextView textView = mRootLayout.findViewById(R.id.company_addressbook_text);

                        textView.setText(String.format(getString(R.string.contacts_activity_company_addressbook_text2), mDomain));
                        mNextButton.setVisibility(View.GONE);
                    }
                    onRefresh();
                }
            } else {
                View view = mRootLayout.findViewById(R.id.fragment_contacts_business_list_container);
                view.setVisibility(View.GONE);

                mNoContactsLayout.setVisibility(View.VISIBLE);
                mNextButton.setText(getResources().getString(R.string.profile_info_email_button_register_mail));
            }
        } catch (LocalizedException e) {
            //keine Anzeige
            LogUtil.w(getClass().getSimpleName(), "onCreateView failed", e);
        }
    }

    private void configureViewsForToManyContacts() {
        mListContainer.setVisibility(View.VISIBLE);
        mNoContactsLayout.setVisibility(View.GONE);
        mListView.setOnItemClickListener(mOnItemClickListener != null ? mOnItemClickListener : this);

        mAdapter = new CompanyContactsAdapter(getActivity(), R.layout.contact_item_overview_layout,
                mContactControllerBusiness.getLastUsedCompanyContacts(mIndexType), false);

        mListView.setOnScrollListener(this);
        mListView.setAdapter(mAdapter);

        if (mListView.getHeaderViewsCount() < 1) {
            View headerView = getHeaderView();
            if (headerView != null) {
                mListView.addHeaderView(headerView, null, false);
            }
        }

        mListView.invalidate();
    }

    private void getKeyAndInitCompAnyContacts() {
        try {
            SecretKey key = getContactKey();
            if (key != null) {
                fillList(mGroupCompanyContacts);

                if (!StringUtil.isNullOrEmpty(mGroupChatOwnerGuid)) {
                    if (mAdapter != null) {
                        mAdapter.setGroupChatOwner(mGroupChatOwnerGuid);
                    }
                }

                refreshTitle();

            }
        } catch (LocalizedException e) {
            LogUtil.e(e);
        }
    }


    private void fillList(List<CompanyContact> companyContacts) {
        final Context context = getContext();
        if (context == null) {
            return;
        }

        ContactControllerBusiness contactControllerBusiness = (ContactControllerBusiness) getApplication().getContactController();
        List<CompanyContact> contacts = companyContacts;

        if (contacts == null) {
            contacts = contactControllerBusiness.getAllCompanyContactsSort(mIndexType);
        }

        if (contacts != null && contacts.size() != 0) {
            mListContainer.setVisibility(View.VISIBLE);
            mNoContactsLayout.setVisibility(View.GONE);
            mListView.setOnItemClickListener(mOnItemClickListener != null ? mOnItemClickListener : this);

            mAdapter = new CompanyContactsAdapter(getActivity(), R.layout.contact_item_overview_layout, contacts, false);

            mListView.setOnScrollListener(this);
            mListView.setAdapter(mAdapter);

            if (mListView.getHeaderViewsCount() < 1) {
                View headerView = getHeaderView();
                if (headerView != null) {
                    mListView.addHeaderView(headerView, null, false);
                }
            }

            mListView.invalidate();
        } else {
            mListContainer.setVisibility(View.GONE);
            mNoContactsLayout.setVisibility(View.VISIBLE);
            final TextView textView = mRootLayout.findViewById(R.id.company_addressbook_text);

            textView.setText(String.format(getContext().getResources().getString(R.string.contacts_activity_company_addressbook_text2),
                    mDomain));
            mNextButton.setVisibility(View.GONE);
        }
    }

    private View getHeaderView() {
        if (mHeaderListView == null) {
            return null;
        }

        TextView titleTv = mHeaderListView.findViewById(R.id.header_title);
        TextView subTitleTv = mHeaderListView.findViewById(R.id.header_sub_title);
        TextView descTv = mHeaderListView.findViewById(R.id.header_desc);
        Button button = mHeaderListView.findViewById(R.id.header_button);
        TextView lastContactsTv = mHeaderListView.findViewById(R.id.header_last_contacts);

        if (titleTv != null) {
            if (mContactCount == 1) {
                titleTv.setText(getString(R.string.contacts_fragment_header_title_singular, mContactCount));
            } else {
                titleTv.setText(getString(R.string.contacts_fragment_header_title, mContactCount));
            }
        }

        if (subTitleTv != null) {
            try {
                String indexName = null;
                if (IndexType.INDEX_TYPE_COMPANY.equals(mIndexType)) {
                    final AccountController accountController = mApplication.getAccountController();
                    indexName = accountController.getManagementCompanyName();
                } else if (!StringUtil.isNullOrEmpty(mDomain)) {
                    indexName = mDomain;
                }

                if (StringUtil.isNullOrEmpty(indexName)) {
                    subTitleTv.setVisibility(View.GONE);
                } else {
                    subTitleTv.setText(getString(R.string.contacts_fragment_header_subtitle, indexName));
                }
            } catch (LocalizedException e) {
                subTitleTv.setVisibility(View.GONE);
            }
        }

        if (mContactCount > MAX_CONTACTS_COUNT) {
            if (descTv != null) {
                descTv.setVisibility(View.VISIBLE);
            }
            if (button != null) {
                button.setVisibility(View.VISIBLE);
            }
            if (lastContactsTv != null) {
                lastContactsTv.setVisibility(View.VISIBLE);
            }
        } else {
            if (descTv != null) {
                descTv.setVisibility(View.GONE);
            }
            if (button != null) {
                button.setVisibility(View.GONE);
            }
            if (lastContactsTv != null) {
                lastContactsTv.setVisibility(View.GONE);
            }
        }

        return mHeaderListView;
    }

    public CompanyContactsAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public boolean searchQueryTextChanged(@Nullable String query) {
        try {
            if (StringUtil.isEqual(mQuery, query)) {
                return false;
            }

            mQuery = query;

            SecretKey key = getContactKey();

            if (key == null) {
                return false;
            }

            if ((mQuery == null) || (mQuery.length() == 0)) {
                fillList(mGroupCompanyContacts);
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
                return true;
            }

            List<CompanyContact> companyContacts = mGroupCompanyContacts != null ? mGroupCompanyContacts : mContactControllerBusiness.getAllCompanyContactsSort(mIndexType);

            ArrayList<CompanyContact> rc = new ArrayList<>();

            for (CompanyContact companyContact : companyContacts) {
                if (companyContact.findString(mQuery)) {
                    rc.add(companyContact);
                }
            }

            fillList(rc);
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }

            return true;
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }
        return false;
    }

    @Override
    public String getTitle() {
        return getString(R.string.contacts_overViewViewControllerTitle);
    }

    @Override
    public ContactsFragmentType getContactsFragmentType() {
        return mIndexType.equals(IndexType.INDEX_TYPE_COMPANY) ? ContactsFragmentType.TYPE_COMPANY : ContactsFragmentType.TYPE_DOMAIN;
    }

    @Override
    public void onRefresh() {
        try {
            if (mContactCount > MAX_CONTACTS_COUNT) {
                configureViewsForToManyContacts();
                return;
            }

            if (getApplication().getAccountController().isDeviceManaged()) {
                if (getApplication().getAccountController().hasCompanyUserAesKey()) {
                    getKeyAndInitCompAnyContacts();
                }
            }

            if (!StringUtil.isNullOrEmpty(mDomain)) {
                final ContactControllerBusiness.LoadCompanyContactsListener getAddressInformationsListener = new ContactControllerBusiness.LoadCompanyContactsListener() {

                    @Override
                    public void onLoadSuccess() {
                        getKeyAndInitCompAnyContacts();
                    }

                    @Override
                    public void onLoadFail(String message, String errorIdent) {
                        // TODO wenn es noch keine kontakte gibt
                        getKeyAndInitCompAnyContacts();
                    }

                    @Override
                    public void onLoadCompanyContactsSize(int size) {

                    }

                    @Override
                    public void onLoadCompanyContactsUpdate(int count) {

                    }
                };

                if (!StringUtil.isNullOrEmpty(mDomain)) {
                    mContactControllerBusiness.getAddressInformation(getAddressInformationsListener, null);
                }
            }
        } catch (LocalizedException e) {
            LogUtil.e(e);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final BaseActivity activity = (BaseActivity) getActivity();

        if (activity != null) {
            if (mMode == MODE_ALL) {
                final String guid = (String) view.getTag();

                startContactDetailsActivity(guid);
            } else if (mMode == MODE_SIMSME_SINGLE) {
                final String guid = (String) view.getTag();

                if (!StringUtil.isNullOrEmpty(guid)) {
                    if (activity instanceof ContactsActivityBusiness) {
                        ((ContactsActivityBusiness) activity).startActivityForModeSingle(guid);
                    }

                }
            } else if (mMode == MODE_SIMSME_GROUP || mMode == MODE_SIMSME_DISTRIBUTOR) {
                if (mAdapter == null) {
                    return;
                }

                mAdapter.onContactItemClick(position - mListView.getHeaderViewsCount());
            } else if (mMode == MODE_SEND_CONTACT) {
                try {
                    final String guid = (String) view.getTag();

                    if (StringUtil.isNullOrEmpty(guid)) {
                        return;
                    }

                    final Contact contact = mContactControllerBusiness.getContactByGuid(guid);

                    if (contact != null) {
                        Intent returnIntent = new Intent();

                        returnIntent.putExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS, contact.getAccountGuid());

                        activity.setResult(RESULT_OK, returnIntent);
                        activity.finish();
                    } else {
                        CompanyContact companyContactByGuid = mContactControllerBusiness.getCompanyContactWithAccountGuid(guid);

                        if (companyContactByGuid != null) {
                            Contact hiddenContact = mContactControllerBusiness.createHiddenContactForCompanyContact(companyContactByGuid);

                            Intent returnIntent = new Intent();
                            returnIntent.putExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS, hiddenContact.getAccountGuid());
                            activity.setResult(RESULT_OK, returnIntent);
                            activity.finish();
                        }
                    }

                } catch (LocalizedException e) {
                    LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
        }
    }

    private void startContactDetailsActivity(final String guid) {
        final BaseActivity activity = (BaseActivity) getActivity();

        if (activity != null) {
            if (!StringUtil.isNullOrEmpty(guid)) {
                try {
                    Class nextActivity;
                    final Contact contact = mContactControllerBusiness.getContactByGuid(guid);
                    if (contact != null) {
                        nextActivity = ContactDetailActivity.class;
                    } else {
                        nextActivity = CompanyContactDetailActivity.class;
                    }

                    final Intent intent = new Intent(activity, nextActivity);
                    intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_GUID, guid);
                    activity.startActivity(intent);
                } catch (LocalizedException e) {
                    LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
        }
    }

    private void refreshTitle() {
        if (getActivity() instanceof ContactsActivity) {
            ((ContactsActivity) getActivity()).refreshTitle();
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                         int totalItemCount) {

    }

    public ListView getListview() {
        return mListView;
    }

    public void setOnItemClickListener(AdapterView.OnItemClickListener listener) {
        mOnItemClickListener = listener;
    }

    private SecretKey getContactKey()
            throws LocalizedException {
        if (getApplication().getAccountController().isDeviceManaged()) {
            return getApplication().getAccountController().getCompanyAesKey();
        } else {
            return getApplication().getAccountController().getDomainAesKey();
        }
    }

    private SimsMeApplication getApplication() {
        if (mApplication != null) {
            return mApplication;
        }

        if (getActivity() instanceof BaseActivity) {
            mApplication = ((BaseActivity) getActivity()).getSimsMeApplication();
        }

        return mApplication;
    }

    private void setIndexType(final IndexType indexType) {
        mIndexType = indexType;
    }

    public void startRefresh() {
        if (mContactControllerBusiness == null) {
            //quickfix fue Ticket 6119
            mContactControllerBusiness = (ContactControllerBusiness) getApplication().getContactController();
        }

        if (IndexType.INDEX_TYPE_COMPANY.equals(mIndexType)) {
            mContactControllerBusiness.loadCompanyIndexAsync(new ContactController.LoadCompanyContactsListener() {
                @Override
                public void onLoadSuccess() {
                    final CompanyContactsAdapter adapter = getAdapter();

                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    refreshViews();
                }

                @Override
                public void onLoadFail(String message, String errorIdent) {

                }

                @Override
                public void onLoadCompanyContactsSize(int size) {

                }

                @Override
                public void onLoadCompanyContactsUpdate(int count) {

                }
            }, null);
        } else {
            mContactControllerBusiness.getAddressInformation(new ContactControllerBusiness.LoadCompanyContactsListener() {
                @Override
                public void onLoadSuccess() {
                    refreshViews();
                }

                @Override
                public void onLoadFail(String message, String errorIdent) {

                }

                @Override
                public void onLoadCompanyContactsSize(int size) {

                }

                @Override
                public void onLoadCompanyContactsUpdate(int count) {

                }

            }, null);
        }
    }
}
