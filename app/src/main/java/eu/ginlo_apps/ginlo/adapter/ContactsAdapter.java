// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.adapter;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SectionIndexer;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ImageController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.ContactMessageInfo;
import eu.ginlo_apps.ginlo.model.Mandant;
import eu.ginlo_apps.ginlo.util.ImageUtil;
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ContactsAdapter extends ArrayAdapter<Contact>
        implements SectionIndexer {

    private final static String TAG = "ContactsAdapter";
    private final Context mContext;
    private final List<Contact> mContacts;
    private final ContactController mContactController;
    private final PreferencesController mPreferencesController;
    private final ImageController mImageController;
    private final int mResource;

    private final boolean mSetCheckedAsDefault;
    // Liste der Anfangsbuchstaben
    private ArrayList<ContactSection> mSections;
    private Account mAccount;
    private String mGroupChatOwnerGuid;

    private final int mHighLevelColor;
    private final int mMediumLevelColor;
    private final int mLowLevelColor;
    private final int mMainColor;

    private LayoutInflater mInflater;

    private ISelectedContacts mSelectedContacts;

    private final Drawable mCheckDrawable;
    private final boolean mIsCheckAnAddAction;

    /**
     *
     * @param isCheckAnAddAction isCheckAnAddAction
     */
    public ContactsAdapter(Context context,
                           int resource,
                           List<Contact> contacts,
                           boolean setCheckedAsDefault,
                           boolean isCheckAnAddAction) {
        super(context, resource, contacts);

        this.mContacts = new ArrayList<>(contacts);
        this.mContext = context;
        this.mResource = resource;

        if (context instanceof ISelectedContacts) {
            this.mSelectedContacts = (ISelectedContacts) context;
        }

        this.mContactController = ((SimsMeApplication) ((Activity) context).getApplication()).getContactController();
        this.mPreferencesController = ((SimsMeApplication) ((Activity) context).getApplication()).getPreferencesController();
        this.mImageController = ((SimsMeApplication) ((Activity) context).getApplication()).getImageController();
        this.mSetCheckedAsDefault = setCheckedAsDefault;

        final ScreenDesignUtil screenDesignUtil = ScreenDesignUtil.getInstance();
        mHighLevelColor = screenDesignUtil.getHighColor((Application) getContext().getApplicationContext());
        mMediumLevelColor = screenDesignUtil.getMediumColor((Application) getContext().getApplicationContext());
        mLowLevelColor = screenDesignUtil.getLowColor((Application) getContext().getApplicationContext());
        mMainColor = screenDesignUtil.getMainColor((Application) getContext().getApplicationContext());

        mCheckDrawable = context.getDrawable(isCheckAnAddAction ? R.drawable.profile_check_done : R.drawable.profile_check_remove);
        mIsCheckAnAddAction = isCheckAnAddAction;
    }

    public void onContactItemClick(final int position) {
        if (mSelectedContacts == null) {
            return;
        }

        Contact selectedContact = getItem(position);

        if (selectedContact == null) {
            return;
        }

        if (mSelectedContacts.containsSelectedContactGuid(selectedContact.getAccountGuid())) {
            mSelectedContacts.removeSelectedContactGuid(selectedContact.getAccountGuid());
        } else {
            mSelectedContacts.addSelectedContactGuid(selectedContact.getAccountGuid());
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int position,
                        View view,
                        @NonNull ViewGroup viewgroup) {
        if (mInflater == null) {
            mInflater = ViewExtensionsKt.themedInflater(LayoutInflater.from(mContext), mContext);
        }

        View itemView = (view != null) ? view
                : mInflater.inflate(mResource, viewgroup, false);
        if (!itemView.isEnabled()) {
            itemView = mInflater.inflate(mResource, viewgroup, false);
        }

        if (position >= this.getCount()) {
            return itemView;
        }

        try {
            Contact contact = getItem(position);
            if (contact == null) {
                return new LinearLayout(getContext());
            }

            // contact aktualisieren, falls sich z.b. der status geaendert hat
            if (contact.getAccountGuid() != null) {
                final Contact tmpContact = mContactController.getContactByGuid(contact.getAccountGuid());
                if (tmpContact != null) {
                    contact = tmpContact;
                }
            }

            boolean isSelectedItem = mSelectedContacts != null && mSelectedContacts.containsSelectedContactGuid(contact.getAccountGuid());

            if (mResource == R.layout.contact_item_message_info_layout) {
                final ContactMessageInfo contactMessageInfo = (ContactMessageInfo) getItem(position);
                final TextView timeText = itemView.findViewById(R.id.contact_item_time_text);
                final TextView dateText = itemView.findViewById(R.id.contact_item_date_text);

                if (contactMessageInfo != null && timeText != null && dateText != null) {
                    long dateRead = contactMessageInfo.getDateRead();
                    long dateDownloaded = contactMessageInfo.getDateDownloaded();
                    long datesend = contactMessageInfo.getDateSent();

                    final String today = DateUtil.getDateStringFromMillis(new Date().getTime());

                    Boolean state;
                    final String date;
                    if (dateRead != 0) {
                        date = DateUtil.getDateStringFromMillis(dateRead);
                        timeText.setText(DateUtil.getTimeStringFromMillis(dateRead));
                        state = true;
                    } else if (dateDownloaded != 0) {
                        date = DateUtil.getDateStringFromMillis(dateDownloaded);
                        timeText.setText(DateUtil.getTimeStringFromMillis(dateDownloaded));
                        state = false;
                    } else {
                        date = DateUtil.getDateStringFromMillis(datesend);
                        timeText.setText(DateUtil.getTimeStringFromMillis(datesend));
                        state = null;
                    }

                    if (StringUtil.isEqual(today, date)) {
                        dateText.setText(getContext().getResources().getString(R.string.chat_overview_date_today));
                    } else {
                        dateText.setText(date);
                    }

                    RelativeLayout cestionTitleContainer = itemView.findViewById(R.id.contact_item_section_title_container);
                    TextView sectionTitleText = itemView.findViewById(R.id.contact_item_section_title_text);
                    ImageView sectionTitleImage = itemView.findViewById(R.id.contact_item_section_title_image);

                    if (contactMessageInfo.getIstFirstElementOfType()) {
                        cestionTitleContainer.setVisibility(View.VISIBLE);
                        if (state == null) {
                            sectionTitleText.setText(R.string.message_info_sent);
                            sectionTitleImage.setImageDrawable(ResourcesCompat.getDrawable(getContext().getResources(), R.drawable.send_1, null));
                        } else if (!state) {
                            sectionTitleText.setText(R.string.message_info_delivered);
                            sectionTitleImage.setImageDrawable(ResourcesCompat.getDrawable(getContext().getResources(), R.drawable.send_2, null));
                        } else // true
                        {
                            sectionTitleText.setText(R.string.message_info_read);
                            sectionTitleImage.setImageDrawable(ResourcesCompat.getDrawable(getContext().getResources(), R.drawable.send_3, null));
                        }
                    } else {
                        cestionTitleContainer.setVisibility(View.GONE);
                    }
                }
            }

            boolean isGroupContactNotClickable = contact.getAccountGuid() != null && !StringUtil.isNullOrEmpty(mGroupChatOwnerGuid)
                    && StringUtil.isEqual(mGroupChatOwnerGuid, contact.getAccountGuid());

            //View clickContainer = itemView.findViewById(R.id.click_container);

            if (itemView != null) {
                if (isSelectedItem) {
                    //40%
                    itemView.setBackgroundColor(ColorUtils.setAlphaComponent(mIsCheckAnAddAction ? mHighLevelColor : mLowLevelColor, 102));
                } else if (isGroupContactNotClickable) {
                    //20%
                    itemView.setBackgroundColor(ColorUtils.setAlphaComponent(mHighLevelColor, 51));
                } else {
                    //itemView.setBackgroundColor(mMainColor);
                }
            }

            ImageView selectIconImageView = itemView.findViewById(R.id.contact_item_image_view_select_icon);

            if ((selectIconImageView != null) && (viewgroup instanceof ListView)) {
                ListView listView = (ListView) viewgroup;

                /* fuer Verteiler - es wird ein Haken als Default angezeigt */
                int resource = mSetCheckedAsDefault ? R.drawable.ico_check : R.drawable.ico_add_empty;

                if (mSelectedContacts != null && mSelectedContacts.containsSelectedContactGuid(contact.getAccountGuid())) {
                    resource = R.drawable.ico_add_green;
                }

                selectIconImageView.setImageDrawable(listView.getResources().getDrawable(resource));
            }

            final View trustedStateDivider = itemView.findViewById(R.id.trust_state_divider);

            final ImageView profileImageView = itemView.findViewById(R.id.contact_item_mask_image_view_image);

            if (profileImageView != null) {
                if (isSelectedItem) {
                    profileImageView.setImageDrawable(mCheckDrawable);
                } else {
                    mImageController.fillViewWithProfileImageByGuid(contact.getAccountGuid(), profileImageView, -1, true);
                }
            }

            if (trustedStateDivider != null) {
                switch (contact.getState()) {
                    case Contact.STATE_HIGH_TRUST:
                        trustedStateDivider.setBackgroundColor(mHighLevelColor);
                        trustedStateDivider.setVisibility(View.VISIBLE);
                        break;
                    case Contact.STATE_MIDDLE_TRUST:
                        trustedStateDivider.setBackgroundColor(mMediumLevelColor);
                        trustedStateDivider.setVisibility(View.VISIBLE);
                        break;
                    case Contact.STATE_LOW_TRUST:
                        trustedStateDivider.setBackgroundColor(mLowLevelColor);
                        trustedStateDivider.setVisibility(View.VISIBLE);
                        break;
                    case Contact.STATE_UNSIMSABLE: {
                        trustedStateDivider.setVisibility(View.INVISIBLE);
                        break;
                    }
                    default:
                        break;
                }
            }

            final String mandantIdent = contact.getMandant();
            final TextView mandantTextView = itemView.findViewById(R.id.contact_item_mandant_label);
            if (mandantTextView != null) {
                final Mandant mandant = mPreferencesController.getMandantFromIdent(mandantIdent);

                if (mandant != null) {
                    mandantTextView.setVisibility(View.VISIBLE);
                    boolean isPrivate = StringUtil.isNullOrEmpty(contact.getClassEntryName()) || StringUtil.isEqual(contact.getClassEntryName(), Contact.CLASS_PRIVATE_ENTRY);
                    ScreenDesignUtil.getInstance().colorizeMandantTextView((Application) getContext().getApplicationContext(), mandant, mandantTextView, isPrivate);
                } else {
                    mandantTextView.setVisibility(View.GONE);
                }
            }

            final TextView nameTextView = itemView.findViewById(R.id.contact_item_text_view_name);
            final TextView statusTextView = itemView.findViewById(R.id.contact_item_text_view_status);
            final TextView secondDetailTextView = itemView.findViewById(R.id.contact_item_text_view_detail_second);

            setTextView(nameTextView, statusTextView, secondDetailTextView, contact);

            itemView.setTag(contact);

            if (isGroupContactNotClickable) {
                itemView.setEnabled(false);
                itemView.setOnClickListener(null);
                itemView.setAlpha(0.6f);
            }
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }

        if (itemView != null) {
            return itemView;
        }
        return new LinearLayout(getContext());
    }

    @Override
    public Object[] getSections() {
        initSections();

        return mSections.toArray();
    }

    @Override
    public int getPositionForSection(int section) {
        initSections();

        if (section < 0) {
            return 0;
        }
        if (section >= mSections.size()) {
            return 0;
        }
        return mSections.get(section).getContactIndex();
    }

    @Override
    public int getSectionForPosition(int position) {
        initSections();
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.get(i).getContactIndex() > position) {
                return i - 1;
            }
        }
        return mSections.size() - 1;
    }

    private synchronized void initSections() {
        if (mSections != null) {
            return;
        }
        mSections = new ArrayList<>();

        Contact contact = null;
        String name = "";
        String section = "";

        try {
            String lastContactSection = "";

            for (int i = 0; i < mContacts.size(); i++) {
                contact = mContacts.get(i);
                name = contact.getIdentName();

                if ((name == null) || (name.length() == 0)) {
                    continue;
                }

                section = name.substring(0, 1).toUpperCase();

                if (section.equals(lastContactSection)) {
                    continue;
                } else {
                    lastContactSection = section;
                }

                mSections.add(new ContactSection(section, i));
            }
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }
    }

    private int getSizeForLayout() {
        if ((mResource == R.layout.contact_item_multi_select_layout)
                || (mResource == R.layout.contact_item_multi_select_distributor_layout)
                || (mResource == R.layout.contact_item_single_select_layout)
                || (mResource == R.layout.contact_item_overview_layout)) {
            return ImageUtil.SIZE_CONTACT;
        } else if (mResource == R.layout.contact_item_group_info_layout) {
            return ImageUtil.SIZE_CONTACT_GROUP_INFO;
        }
        return 1;
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    private void setTextView(TextView nameTextView,
                             TextView statusTextView,
                             TextView secondDetailTextView,
                             Contact contact)
            throws LocalizedException {
        if (nameTextView != null) {
            String displayName = contact.getNameFromNameAttributes();
            if (StringUtil.isNullOrEmpty(displayName)) {
                nameTextView.setVisibility(View.GONE);
            } else {
                nameTextView.setVisibility(View.VISIBLE);
                nameTextView.setText(displayName);
            }
        }

        if (statusTextView != null) {
            String firstTextOption = RuntimeConfig.isBAMandant() ? contact.getDepartment() : getContactStatusText(contact);
            if (StringUtil.isNullOrEmpty(firstTextOption)) {
                String simsmeID = contact.getSimsmeId();
                if (StringUtil.isNullOrEmpty(simsmeID)) {
                    statusTextView.setVisibility(View.GONE);
                } else {
                    statusTextView.setVisibility(View.VISIBLE);
                    String label = mContext.getString(R.string.label_simsme_id_with_ph, simsmeID);
                    statusTextView.setText(label);
                }
            } else {
                statusTextView.setVisibility(View.VISIBLE);
                statusTextView.setText(firstTextOption);
            }
        }

        if (secondDetailTextView != null) {
            String email = contact.getEmail();
            if (StringUtil.isNullOrEmpty(email)) {
                String phoneNumber = contact.getPhoneNumber();
                if (StringUtil.isNullOrEmpty(phoneNumber)) {
                    secondDetailTextView.setVisibility(View.GONE);
                } else {
                    secondDetailTextView.setVisibility(View.VISIBLE);
                    secondDetailTextView.setText(phoneNumber);
                }
            } else {
                secondDetailTextView.setVisibility(View.VISIBLE);
                secondDetailTextView.setText(email);
            }
        }
    }

    private String getContactStatusText(Contact contact)
            throws LocalizedException {
        String statusText = contact.getStatusText();

        if ((mAccount != null) && StringUtil.isEqual(contact.getAccountGuid(), mAccount.getAccountGuid())) {
            statusText = mAccount.getStatusText();
        }

        return statusText;
    }

    public void setAccount(Account account) {
        mAccount = account;
    }

    public void setGroupChatOwner(final String guid) {
        mGroupChatOwnerGuid = guid;
    }

    public interface ISelectedContacts {
        void addSelectedContactGuid(final String contactGuid);

        void removeSelectedContactGuid(final String contactGuid);

        boolean containsSelectedContactGuid(final String contactGuid);
    }

    /**
     * @author Florian
     * @version $Revision$, $Date$, $Author$
     */
    static class ContactSection {

        final String mFirstChar;

        final int mContactIndex;

        ContactSection(String firstChar,
                       int contactIndex) {
            mFirstChar = firstChar;
            mContactIndex = contactIndex;
        }

        int getContactIndex() {
            return mContactIndex;
        }

        @Override
        public String toString() {
            return mFirstChar;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ContactSection)) {
                return false;
            }
            return other.toString().equals(this.toString());
        }

        /**
         * Hashcode
         *
         * @return hashCode
         */
        @Override
        public int hashCode() {
            return toString().hashCode();
        }
    }
}
