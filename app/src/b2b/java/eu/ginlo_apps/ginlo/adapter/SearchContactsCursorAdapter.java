// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.adapter;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonObject;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.Mandant;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.ImageLoader;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.fts.FtsDatabaseHelper;
import net.sqlcipher.Cursor;

public class SearchContactsCursorAdapter extends CursorRecycleViewAdapter<SearchContactsCursorAdapter.ViewHolder> {
    private final static String TAG = "SearchContactsCursorAdapter";
    private ImageLoader mImageLoader;
    private LayoutInflater mInflater;

    private final int mHighLevelColor;
    private final int mMediumLevelColor;
    private final int mLowLevelColor;
    private final int mMainColor;

    private Drawable mCheckDrawable;
    private View.OnClickListener mOnItemClickListener;
    private ContactsAdapter.ISelectedContacts mSelectedContacts;

    public SearchContactsCursorAdapter(@NonNull final Context context, final Cursor cursor, final ImageLoader imageLoader) {
        super(context, cursor);

        mImageLoader = imageLoader;

        final ColorUtil colorUtil = ColorUtil.getInstance();
        mHighLevelColor = colorUtil.getHighColor((Application) getContext().getApplicationContext());
        mMediumLevelColor = colorUtil.getMediumColor((Application) getContext().getApplicationContext());
        mLowLevelColor = colorUtil.getLowColor((Application) getContext().getApplicationContext());
        mMainColor = colorUtil.getMainColor((Application) getContext().getApplicationContext());

        mCheckDrawable = context.getDrawable(R.drawable.profile_check_done);

        if (context instanceof ContactsAdapter.ISelectedContacts) {
            this.mSelectedContacts = (ContactsAdapter.ISelectedContacts) context;
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Cursor cursor) {
        try {
            ContactListItem item = ContactListItem.fromCursor(cursor);

            boolean isSelectedItem = false;

            if (item.getAccountGuid() != null) {
                isSelectedItem = mSelectedContacts != null && mSelectedContacts.containsSelectedContactGuid(item.getAccountGuid());
            }

            if (isSelectedItem) {
                //40%
                viewHolder.itemView.setBackgroundColor(ColorUtils.setAlphaComponent(mHighLevelColor, 102));
            } else {
                viewHolder.itemView.setBackgroundColor(mMainColor);
            }

            if (viewHolder.profileImageView != null && !StringUtil.isNullOrEmpty(item.accountGuid)) {
                if (isSelectedItem) {
                    viewHolder.profileImageView.setImageDrawable(mCheckDrawable);
                } else if (mImageLoader != null) {
                    mImageLoader.loadImage(item.accountGuid, viewHolder.profileImageView);
                } else {
                    Bitmap image = SimsMeApplication.getInstance().getChatImageController().getImageByGuid(item.accountGuid, ChatImageController.SIZE_ORIGINAL);

                    int px = R.dimen.contact_item_multi_select_icon_diameter * (SimsMeApplication.getInstance().getResources().getDisplayMetrics().densityDpi / 160);
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(image, px, px, false);

                    RoundedBitmapDrawable dr = RoundedBitmapDrawableFactory.create(SimsMeApplication.getInstance().getResources(), scaledBitmap);

                    dr.setCornerRadius(px / 2.0f);

                    viewHolder.profileImageView.setImageDrawable(dr);
                }

                viewHolder.profileImageView.setVisibility(View.VISIBLE);
            } else if (viewHolder.profileImageView != null) {
                viewHolder.profileImageView.setVisibility(View.INVISIBLE);
            }

            if (viewHolder.trustedStateDivider != null && !StringUtil.isNullOrEmpty(item.trustState)) {
                try {
                    switch (Integer.parseInt(item.trustState)) {
                        case Contact.STATE_HIGH_TRUST:
                            viewHolder.trustedStateDivider.setBackgroundColor(mHighLevelColor);
                            viewHolder.trustedStateDivider.setVisibility(View.VISIBLE);
                            break;
                        case Contact.STATE_MIDDLE_TRUST:
                            viewHolder.trustedStateDivider.setBackgroundColor(mMediumLevelColor);
                            viewHolder.trustedStateDivider.setVisibility(View.VISIBLE);
                            break;
                        case Contact.STATE_LOW_TRUST:
                            viewHolder.trustedStateDivider.setBackgroundColor(mLowLevelColor);
                            viewHolder.trustedStateDivider.setVisibility(View.VISIBLE);
                            break;
                        case Contact.STATE_UNSIMSABLE: {
                            viewHolder.trustedStateDivider.setVisibility(View.INVISIBLE);
                            break;
                        }
                        default:
                            break;
                    }
                } catch (NumberFormatException e) {
                    LogUtil.w(TAG, "truststate", e);
                }
            } else if (viewHolder.trustedStateDivider != null) {
                viewHolder.trustedStateDivider.setVisibility(View.INVISIBLE);
            }

            if (viewHolder.mandantTextView != null && !StringUtil.isNullOrEmpty(item.mandant)) {
                final Mandant mandant = SimsMeApplication.getInstance().getPreferencesController().getMandantFromIdent(item.mandant);

                if (mandant != null) {
                    viewHolder.mandantTextView.setVisibility(View.VISIBLE);
                    boolean isPrivate = item.classType == null || StringUtil.isEqual(Contact.CLASS_PRIVATE_ENTRY, item.classType);
                    ColorUtil.getInstance().colorizeMandantTextView((Application) getContext().getApplicationContext(), mandant, viewHolder.mandantTextView, isPrivate);
                } else {
                    viewHolder.mandantTextView.setVisibility(View.GONE);
                }
            } else if (viewHolder.mandantTextView != null) {
                viewHolder.mandantTextView.setVisibility(View.GONE);
            }

            if (viewHolder.nameTextView != null) {
                if (StringUtil.isNullOrEmpty(item.firstName) && StringUtil.isNullOrEmpty(item.name)) {
                    viewHolder.nameTextView.setVisibility(View.GONE);
                } else {
                    viewHolder.nameTextView.setVisibility(View.VISIBLE);

                    String name = StringUtil.isNullOrEmpty(item.firstName) ? "" : (item.firstName + " ");

                    name = name + (StringUtil.isNullOrEmpty(item.name) ? "" : item.name);

                    viewHolder.nameTextView.setText(name);
                }
            }

            if (viewHolder.statusTextView != null) {
                if (StringUtil.isNullOrEmpty(item.department)) {
                    if (StringUtil.isNullOrEmpty(item.simsmeId)) {
                        viewHolder.statusTextView.setVisibility(View.GONE);
                    } else {
                        viewHolder.statusTextView.setVisibility(View.VISIBLE);
                        String label = getContext().getString(R.string.label_simsme_id_with_ph, item.simsmeId);
                        viewHolder.statusTextView.setText(label);
                    }
                } else {
                    viewHolder.statusTextView.setVisibility(View.VISIBLE);
                    viewHolder.statusTextView.setText(item.department);
                }
            }

            if (viewHolder.secondDetailTextView != null) {
                if (StringUtil.isNullOrEmpty(item.mail)) {
                    if (StringUtil.isNullOrEmpty(item.phone)) {
                        viewHolder.secondDetailTextView.setVisibility(View.GONE);
                    } else {
                        viewHolder.secondDetailTextView.setVisibility(View.VISIBLE);
                        viewHolder.secondDetailTextView.setText(item.phone);
                    }
                } else {
                    viewHolder.secondDetailTextView.setVisibility(View.VISIBLE);
                    viewHolder.secondDetailTextView.setText(item.mail);
                }
            }

            viewHolder.itemView.setEnabled(true);
            viewHolder.itemView.setAlpha(1.0f);
        } catch (LocalizedException e) {
            LogUtil.w(TAG, "onBindViewHolder()", e);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        if (mInflater == null) {
            mInflater = ViewExtensionsKt.themedInflater(LayoutInflater.from(getContext()), getContext());
        }

        View itemView = mInflater.inflate(R.layout.contact_item_overview_layout
                , parent, false);

        return new ViewHolder(itemView);
    }

    @Override
    public String getRowIdColumnName() {
        return FtsDatabaseHelper.COLUMN_ROW_ID;
    }

    public void setItemClickListener(View.OnClickListener clickListener) {
        mOnItemClickListener = clickListener;
    }

    public ContactListItem getItemAt(final int position) {
        if (getCursor() != null && getCursor().moveToPosition(position)) {
            return ContactListItem.fromCursor(getCursor());
        }
        return null;
    }

    public void onContactItemClick(final int position) {
        if (mSelectedContacts == null) {
            return;
        }

        SearchContactsCursorAdapter.ContactListItem selectedContact = getItemAt(position);

        if (selectedContact == null) {
            return;
        }

        if (mSelectedContacts.containsSelectedContactGuid(selectedContact.getAccountGuid())) {
            mSelectedContacts.removeSelectedContactGuid(selectedContact.getAccountGuid());
        } else {
            mSelectedContacts.addSelectedContactGuid(selectedContact.getAccountGuid());
        }

        notifyItemChanged(position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        View trustedStateDivider;
        ImageView profileImageView;
        TextView mandantTextView;
        TextView nameTextView;
        TextView statusTextView;
        TextView secondDetailTextView;

        ViewHolder(View view) {
            super(view);

            trustedStateDivider = view.findViewById(R.id.trust_state_divider);
            profileImageView = view.findViewById(R.id.contact_item_mask_image_view_image);
            mandantTextView = view.findViewById(R.id.contact_item_mandant_label);
            nameTextView = view.findViewById(R.id.contact_item_text_view_name);
            statusTextView = view.findViewById(R.id.contact_item_text_view_status);
            secondDetailTextView = view.findViewById(R.id.contact_item_text_view_detail_second);

            View clickContainer = view.findViewById(R.id.click_container);
            clickContainer.setTag(this);
            clickContainer.setOnClickListener(mOnItemClickListener);
        }
    }

    public static class ContactListItem {
        String accountGuid;
        String name;
        String firstName;
        String simsmeId;
        String mail;
        String phone;
        String department;
        String trustState;
        String mandant;
        String classType;

        static ContactListItem fromCursor(@NonNull final Cursor cursor) {
            ContactListItem item = new ContactListItem();
            item.accountGuid = cursor.getString(ContactControllerBusiness.FtsSearchColumnIndex.ACCOUNT_GUID_CURSOR_POS);

            String json = cursor.getString(ContactControllerBusiness.FtsSearchColumnIndex.JSON_ATTRIBUTES_CURSOR_POS);
            if (!StringUtil.isNullOrEmpty(json)) {
                JsonObject jo = JsonUtil.getJsonObjectFromString(json);
                if (jo != null) {
                    item.name = JsonUtil.stringFromJO(JsonConstants.LASTNAME, jo);
                    item.firstName = JsonUtil.stringFromJO(JsonConstants.FIRSTNAME, jo);
                    item.trustState = JsonUtil.stringFromJO(JsonConstants.TRUST_STATE, jo);
                    item.mail = JsonUtil.stringFromJO(JsonConstants.EMAIL, jo);
                    item.phone = JsonUtil.stringFromJO(JsonConstants.PHONE, jo);
                    item.department = JsonUtil.stringFromJO(JsonConstants.DEPARTMENT, jo);
                    item.simsmeId = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, jo);
                    item.mandant = JsonUtil.stringFromJO(JsonConstants.MANDANT, jo);
                }
            }

            item.classType = cursor.getString(ContactControllerBusiness.FtsSearchColumnIndex.CLASS_TYPE_CURSOR_POS);

            return item;
        }

        public String getAccountGuid() {
            return accountGuid;
        }

        public String getClassType() {
            return classType;
        }

    }
}
