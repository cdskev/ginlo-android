// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.adapter;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.CompanyContact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.Mandant;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.CompanyContactUtil;
import eu.ginlo_apps.ginlo.util.ImageLoader;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.List;

public class CompanyContactsAdapter extends ArrayAdapter<CompanyContact> {

    private final int mResource;

    private final SimsMeApplication mApplication;

    private final boolean mSetCheckedAsDefault;

    private final ChatImageController mChatImageController;

    private String mGroupChatOwnerGuid;

    private ImageLoader mImageLoader;

    private Mandant tenant;

    private final Context mContext;

    private LayoutInflater mInflater;

    private ContactsAdapter.ISelectedContacts mSelectedContacts;

    private int mHighLevelColor;

    private int mMainColor;

    private Drawable mCheckDrawable;

    public CompanyContactsAdapter(final Context context,
                                  final int resource,
                                  final List<CompanyContact> contacts,
                                  final boolean setCheckedAsDefault) {
        super(context, resource, contacts);

        mApplication = (SimsMeApplication) context.getApplicationContext();
        mContext = context;
        mResource = resource;

        if (context instanceof ContactsAdapter.ISelectedContacts) {
            this.mSelectedContacts = (ContactsAdapter.ISelectedContacts) context;
        }

        mChatImageController = mApplication.getChatImageController();
        mSetCheckedAsDefault = setCheckedAsDefault;
        try {
            tenant = mApplication.getPreferencesController().getMandantFromIdent(BuildConfig.SIMSME_MANDANT_BA);
        } catch (final LocalizedException e) {
            LogUtil.w(this.getClass().getName(), e.getMessage(), e);
        }

        final ColorUtil colorUtil = ColorUtil.getInstance();
        mHighLevelColor = colorUtil.getHighColor((Application) getContext().getApplicationContext());
        mMainColor = colorUtil.getMainColor((Application) getContext().getApplicationContext());

        mCheckDrawable = context.getDrawable(R.drawable.profile_check_done);
    }

    @NonNull
    @Override
    public View getView(int position,
                        View view,
                        @NonNull ViewGroup viewgroup) {
        if (mInflater == null) {
            mInflater = ViewExtensionsKt.themedInflater(LayoutInflater.from(mContext), mContext);
        }
        View itemView = (view != null) ? view : mInflater.inflate(mResource, viewgroup, false);

        if (position >= this.getCount()) {
            return itemView;
        }

        try {
            CompanyContact companyContact = getItem(position);
            if (companyContact == null) {
                return new LinearLayout(getContext());
            }

            boolean isSelectedItem = false;
            boolean isGroupContactNotClickable = false;

            if (companyContact.getAccountGuid() != null) {
                isSelectedItem = mSelectedContacts != null && mSelectedContacts.containsSelectedContactGuid(companyContact.getAccountGuid());
                isGroupContactNotClickable = !StringUtil.isNullOrEmpty(mGroupChatOwnerGuid)
                        && StringUtil.isEqual(mGroupChatOwnerGuid, companyContact.getAccountGuid());
            }

            ImageView selectIconImageView = itemView.findViewById(R.id.contact_item_image_view_select_icon);

            if ((selectIconImageView != null) && (viewgroup instanceof ListView)) {
                ListView listView = (ListView) viewgroup;

                /* fuer Verteiler - es wird ein Haken als Default angezeigt */
                int resource = (mSetCheckedAsDefault) ? R.drawable.ico_check : R.drawable.ico_add_empty;

                if (mSelectedContacts != null && mSelectedContacts.containsSelectedContactGuid(companyContact.getAccountGuid())) {
                    resource = R.drawable.ico_add_green;
                }

                selectIconImageView.setImageDrawable(listView.getResources().getDrawable(resource));
            }

            if (isSelectedItem) {
                //40%
                itemView.setBackgroundColor(ColorUtils.setAlphaComponent(mHighLevelColor, 102));
            } else if (isGroupContactNotClickable) {
                //20%
                itemView.setBackgroundColor(ColorUtils.setAlphaComponent(mHighLevelColor, 51));
            } else {
                itemView.setBackgroundColor(mMainColor);
            }

            final TextView nameTextView = itemView.findViewById(R.id.contact_item_text_view_name);
            final TextView statusTextView = itemView.findViewById(R.id.contact_item_text_view_status);
            final TextView secondDetailTextView = itemView.findViewById(R.id.contact_item_text_view_detail_second);
            statusTextView.setVisibility(View.GONE);

            String firstName = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_FIRSTNAME);
            String lastName = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_LASTNAME);

            if (StringUtil.isNullOrEmpty(firstName) && StringUtil.isNullOrEmpty(lastName)) {
                nameTextView.setVisibility(View.GONE);
            } else {
                nameTextView.setVisibility(View.VISIBLE);

                String name = StringUtil.isNullOrEmpty(firstName) ? "" : (firstName + " ");

                name = name + (StringUtil.isNullOrEmpty(lastName) ? "" : lastName);

                nameTextView.setText(name);
            }

            String simsmeId = companyContact.getAccountId();
            String department = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_DEPARTMENT);
            if (StringUtil.isNullOrEmpty(simsmeId) && StringUtil.isNullOrEmpty(department)) {
                statusTextView.setVisibility(View.GONE);
            } else {
                if (!StringUtil.isNullOrEmpty(department)) {
                    statusTextView.setText(department);
                } else {
                    String label = mContext.getString(R.string.label_simsme_id_with_ph, simsmeId);
                    statusTextView.setText(label);
                }
                statusTextView.setVisibility(View.VISIBLE);
            }

            if (secondDetailTextView != null) {
                String mail = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_EMAIL);
                String phone = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_PHONE);

                if (StringUtil.isNullOrEmpty(mail) && StringUtil.isNullOrEmpty(phone)) {
                    secondDetailTextView.setVisibility(View.GONE);
                } else {
                    if (!StringUtil.isNullOrEmpty(mail)) {
                        secondDetailTextView.setText(mail);
                    } else {
                        secondDetailTextView.setText(phone);
                    }
                    secondDetailTextView.setVisibility(View.VISIBLE);
                }
            }

            final View trustedStateDivider = itemView.findViewById(R.id.trust_state_divider);

            final ImageView profileImageView = itemView.findViewById(R.id.contact_item_mask_image_view_image);

            if (profileImageView != null) {
                if (isSelectedItem) {
                    profileImageView.setImageDrawable(mCheckDrawable);
                } else if (mImageLoader != null) {
                    mImageLoader.loadImage(companyContact, profileImageView);
                } else {
                    int size = (int) getContext().getResources().getDimension(R.dimen.contact_item_single_select_icon_diameter);

                    Bitmap image = mChatImageController.getImageByGuid(companyContact.getAccountGuid(),
                            ChatImageController.SIZE_ORIGINAL);

                    int px = size * mApplication.getResources().getDisplayMetrics().densityDpi / 160;
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(image, px, px, false);

                    RoundedBitmapDrawable dr = RoundedBitmapDrawableFactory.create(mApplication.getResources(),
                            scaledBitmap);

                    dr.setCornerRadius(px / 2.0f);

                    profileImageView.setImageDrawable(dr);
                }
            }

            if (trustedStateDivider != null) {
                trustedStateDivider.setVisibility(View.VISIBLE);
                trustedStateDivider.setBackgroundColor(mHighLevelColor);
            }

            itemView.setTag(companyContact.getAccountGuid());

            if (isGroupContactNotClickable) {
                itemView.setEnabled(false);
                itemView.setOnClickListener(null);
                itemView.setAlpha(0.6f);
            }

            // bisher hat jeder companycontact den business-mandanten, daher aus performancegruenden keine Datenbankabfrage
            final TextView tenantTextView = itemView.findViewById(R.id.contact_item_mandant_label);
            if (tenantTextView != null) {
                if (tenant != null) {
                    ColorUtil.getInstance().colorizeMandantTextView((Application) getContext().getApplicationContext(), tenant, tenantTextView, false);
                }
            }
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }

        return itemView;
    }

    public void onContactItemClick(final int position) {
        if (mSelectedContacts == null) {
            return;
        }

        CompanyContact selectedContact = getItem(position);

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

    public void setGroupChatOwner(final String guid) {
        mGroupChatOwnerGuid = guid;
    }

    public void setImageLoader(final ImageLoader imageLoader) {
        mImageLoader = imageLoader;
    }
}
