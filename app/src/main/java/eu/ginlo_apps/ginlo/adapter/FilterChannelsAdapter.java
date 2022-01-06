// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.ChannelCategoryModel;
import java.util.List;

/**
 * @author SGA
 * @version $Id$
 */
public class FilterChannelsAdapter
        extends ArrayAdapter<ChannelCategoryModel> {

    private final Context mContext;

    private int mCheckedPosition;

    private LayoutInflater mInflater;

    public FilterChannelsAdapter(Context context,
                                 int resource,
                                 List<ChannelCategoryModel> data) {
        super(context, resource, data);
        mContext = context;
    }

    @NonNull
    @Override
    public View getView(final int position,
                        final View convertView,
                        @NonNull final ViewGroup parent) {
        if (position >= this.getCount()) {
            return new RelativeLayout(getContext());
        }

        ChannelCategoryModel channelCategoryModel = getItem(position);

        RelativeLayout relativeLayout = (RelativeLayout) convertView;
        if (mInflater == null) {
            mInflater = ViewExtensionsKt.themedInflater(LayoutInflater.from(mContext), mContext);
        }

        if ((relativeLayout == null) || (relativeLayout.getTag() == null)) {
            relativeLayout = (RelativeLayout) mInflater.inflate(R.layout.channel_filter_list_item, null, false);
        }

        TextView titleTextView = relativeLayout.findViewById(R.id.channel_filter_list_item_text_view_title);

        int resourceId = mContext.getResources().getIdentifier(channelCategoryModel.titleKey, "string",
                mContext.getPackageName());

        String title;

        try {
            title = mContext.getString(resourceId);
        } catch (Resources.NotFoundException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            title = channelCategoryModel.titleKey;
        }

        titleTextView.setText(title);
        titleTextView.setTypeface(null, Typeface.BOLD);

        ImageView imageView = relativeLayout.findViewById(R.id.channel_filter_list_item_image_view);

        if (position == mCheckedPosition) {
            imageView.setImageResource(R.drawable.ico_check);
        } else {
            imageView.setImageResource(0);
        }
        String imageKey = (channelCategoryModel.imageKey != null) ? channelCategoryModel.imageKey : "category_all";

        if (imageKey.endsWith("ImageKey")) {
            imageKey = "category_" + imageKey.substring(0, imageKey.length() - "ImageKey".length());
        }

        int imageResourceId = mContext.getResources().getIdentifier(imageKey, "drawable", mContext.getPackageName());

        if (imageResourceId == 0) {
            imageResourceId = R.drawable.category_all;
        }
        imageView.setBackgroundResource(imageResourceId);
        return relativeLayout;
    }

    public void setcheckedPosition(int position) {
        mCheckedPosition = position;
    }
}
