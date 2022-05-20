// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.model.backend.DeviceModel;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.List;

/**
 * Created by Florian on 25.01.18.
 */

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {
    private final List<DeviceModel> mDataSet;
    private final String mOwnDeviceGuid;
    private final OnDeviceItemClickListener mClickListener;

    private LayoutInflater mInflater;

    public DeviceListAdapter(@NonNull final List<DeviceModel> dataSet, @NonNull final String ownDeviceGuid, final OnDeviceItemClickListener clickListener) {
        mDataSet = dataSet;
        mOwnDeviceGuid = ownDeviceGuid;
        mClickListener = clickListener;
    }

    /**
     * Called when RecyclerView needs a new {@link ViewHolder} of the given type to represent
     * an item.
     * <p>
     * This new ViewHolder should be constructed with a new View that can represent the items
     * of the given type. You can either create a new View manually or inflate it from an XML
     * layout file.
     * <p>
     * The new ViewHolder will be used to display items of the adapter using
     * {@link #onBindViewHolder(RecyclerView.ViewHolder, int, List)}}. Since it will be re-used to display
     * different items in the data set, it is a good idea to cache references to sub views of
     * the View to avoid unnecessary {@link View#findViewById(int)} calls.
     *
     * @param parent   The ViewGroup into which the new View will be added after it is bound to
     *                 an adapter position.
     * @param viewType The view type of the new View.
     * @return A new ViewHolder that holds a View of the given view type.
     * @see #getItemViewType(int)
     * @see #onBindViewHolder(RecyclerView.ViewHolder, int)
     */
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (mInflater == null) {
            mInflater = ViewExtensionsKt.themedInflater(LayoutInflater.from(parent.getContext()), parent.getContext());
        }

        View v = mInflater.inflate(R.layout.device_list_item, parent, false);

        return new ViewHolder(v, mClickListener);
    }

    /**
     * Called by RecyclerView to display the data at the specified position. This method should
     * update the contents of the {@link ViewHolder#itemView} to reflect the item at the given
     * position.
     * <p>
     * Note that unlike {@link ListView}, RecyclerView will not call this method
     * again if the position of the item changes in the data set unless the item itself is
     * invalidated or the new position cannot be determined. For this reason, you should only
     * use the <code>position</code> parameter while acquiring the related data item inside
     * this method and should not keep a copy of it. If you need the position of an item later
     * on (e.g. in a click listener), use {@link ViewHolder#getAdapterPosition()} which will
     * have the updated adapter position.
     * <p>
     * Override {@link #onBindViewHolder(RecyclerView.ViewHolder, int, List)} instead if Adapter can
     * handle efficient partial bind.
     *
     * @param holder   The ViewHolder which should be updated to represent the contents of the
     *                 item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     */
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (mDataSet.size() > position) {
            DeviceModel dm = mDataSet.get(position);

            holder.deviceNameTV.setText(dm.name == null ? holder.itemView.getContext().getString(R.string.device_unknown_device_name) : dm.name);
            //holder.deviceNameTV.setTextColor(ScreenDesignUtil.getInstance().getMainContrastColor((Application) holder.itemView.getContext().getApplicationContext()));

            if (StringUtil.isEqual(dm.guid, mOwnDeviceGuid)) {
                holder.infoTV.setText(R.string.device_own_device);
            } else {
                holder.infoTV.setText(dm.getLastOnlineDateString());
            }

            //holder.infoTV.setTextColor(ScreenDesignUtil.getInstance().getHighColor((Application) holder.itemView.getContext().getApplicationContext()));

            holder.versionTV.setText(dm.getVersionString());
            //holder.versionTV.setTextColor(ScreenDesignUtil.getInstance().getMainContrast50Color((Application) holder.itemView.getContext().getApplicationContext()));

            holder.deviceIconIV.setImageResource(dm.getDeviceImageRessource());
        }
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     *
     * @return The total number of items in this adapter.
     */
    @Override
    public int getItemCount() {
        return mDataSet.size();
    }

    /**
     * [!INTERFACE_DESCRIPTION!]
     *
     * @author Florian
     * @version $Id$
     */
    public interface OnDeviceItemClickListener {

        void onDeviceItemClick(int position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {
        final ImageView deviceIconIV;
        final TextView deviceNameTV;
        final TextView versionTV;
        final TextView infoTV;

        private final OnDeviceItemClickListener mClickListener;

        ViewHolder(View itemView, OnDeviceItemClickListener listener) {
            super(itemView);
            deviceIconIV = itemView.findViewById(R.id.device_list_item_device_icon);
            deviceNameTV = itemView.findViewById(R.id.device_list_item_device_name_tv);
            versionTV = itemView.findViewById(R.id.device_list_item_version_tv);
            infoTV = itemView.findViewById(R.id.device_list_item_info_tv);
            mClickListener = listener;
            itemView.setOnClickListener(this);
        }

        /**
         * Called when a view has been clicked.
         *
         * @param v The view that was clicked.
         */
        @Override
        public void onClick(View v) {
            if (mClickListener != null) {
                mClickListener.onDeviceItemClick(getAdapterPosition());
            }
        }
    }
}
