// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.backup;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.LocalBackupHelper;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BaseFragment.OnFragmentInteractionListener;
import eu.ginlo_apps.ginlo.model.Mandant;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RecyclerView.Adapter} that display a Backup Item and makes a call to the
 * specified {@link OnFragmentInteractionListener}.
 * TODO: Replace the implementation with code for your data type.
 */
public class BackupItemRecyclerViewAdapter extends RecyclerView.Adapter<BackupItemRecyclerViewAdapter.ViewHolder> {
    private final List<Bundle> mValues;
    private final OnFragmentInteractionListener mListener;
    private final List<OnBackupItemClickable> mClickableItems;
    private final PreferencesController mPreferencesController;
    private final boolean mShowMandant;
    private final Context mContext;

    public BackupItemRecyclerViewAdapter(List<Bundle> items, OnFragmentInteractionListener listener, SimsMeApplication application, Context nContext ) {
        mValues = items;
        mListener = listener;
        mClickableItems = new ArrayList<>(items.size());
        mPreferencesController = application.getPreferencesController();
        if(BuildConfig.NEED_PHONENUMBER_VALIDATION) {
            mShowMandant = true;
        } else {
            mShowMandant = false;
        }
        mContext = nContext;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_backup_restore_select_backup_item, parent, false);

        ViewHolder vh = new ViewHolder(view);

        mClickableItems.add(vh);

        return vh;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        if (holder.mItem != null) {
            long size = holder.mItem.getLong(AppConstants.LOCAL_BACKUP_ITEM_SIZE);
            holder.mSizeView.setText(StringUtil.getReadableByteCount(size));

            long dateInMillis = holder.mItem.getLong(AppConstants.LOCAL_BACKUP_ITEM_MOD_DATE);
            holder.mDateView.setText(DateUtil.getDateAndTimeStringFromMillis(dateInMillis));

            holder.mMandantView.setVisibility(View.INVISIBLE);

            if (mShowMandant) {
                String flavour = holder.mItem.getString(AppConstants.LOCAL_BACKUP_FLAVOUR);
                if (!StringUtil.isNullOrEmpty(flavour)) {
                    String backupFlavour = "";
                    if (flavour.equals(LocalBackupHelper.B2B))
                        backupFlavour = mContext.getString(R.string.backup_restore_select_business_label);
                    else if(flavour.equals(LocalBackupHelper.B2C))
                        backupFlavour = mContext.getString(R.string.backup_restore_select_private_label);
                    holder.mMandantView.setText(backupFlavour);
                    holder.mMandantView.setAllCaps(true);
                    holder.mMandantView.setTextColor(ColorUtil.getInstance().getAppAccentContrastColor((Application) mContext.getApplicationContext()));
                    holder.mMandantView.setBackgroundColor(ColorUtil.getInstance().getAppAccentColor((Application) mContext.getApplicationContext()));
                    holder.mMandantView.setVisibility(View.VISIBLE);
                }
            } else {
                String id = holder.mItem.getString(AppConstants.BACKUP_DRIVE_ITEM_ID);
                holder.mMandantView.setText(id);
                holder.mMandantView.setAllCaps(true);
                holder.mMandantView.setTextColor(ColorUtil.getInstance().getMainColor((Application) mContext.getApplicationContext()));
                holder.mMandantView.setBackgroundColor(ColorUtil.getInstance().getMainContrastColor((Application) mContext.getApplicationContext()));
                holder.mMandantView.setVisibility(View.VISIBLE);
            }

            holder.mSelectIconView.setVisibility(View.INVISIBLE);
        }

        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    if (holder.mSelectIconView.getVisibility() == View.VISIBLE) {
                        mListener.onFragmentInteraction(AppConstants.BACKUP_RESTORE_ACTION_BACKUP_DESELECTED, holder.mItem);
                    } else {
                        mListener.onFragmentInteraction(AppConstants.BACKUP_RESTORE_ACTION_BACKUP_SELECTED, holder.mItem);
                    }

                    for (OnBackupItemClickable clickable : mClickableItems) {
                        clickable.onItemClick(v);
                    }
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    interface OnBackupItemClickable {
        void onItemClick(View v);
    }

    class ViewHolder extends RecyclerView.ViewHolder implements OnBackupItemClickable {
        final View mView;
        final TextView mSizeView;
        final TextView mDateView;
        final TextView mMandantView;
        final View mSelectIconView;

        Bundle mItem;

        ViewHolder(View view) {
            super(view);
            mView = view;
            mSizeView = view.findViewById(R.id.backup_restore_select_item_size);
            mDateView = view.findViewById(R.id.backup_restore_select_item_date);
            mMandantView = view.findViewById(R.id.backup_restore_select_item_mandant);
            mSelectIconView = view.findViewById(R.id.backup_restore_select_item_icon);
        }

        @Override
        public void onItemClick(View v) {
            if (mSelectIconView.getVisibility() == View.VISIBLE) {
                mSelectIconView.setVisibility(View.INVISIBLE);
            } else if (mView == v) {
                mSelectIconView.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mDateView.getText() + "'";
        }
    }
}
