// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.adapter;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
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
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.drawer.DrawerListItemVO;
import eu.ginlo_apps.ginlo.util.BitmapUtil;
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.List;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class DrawerListAdapter
        extends ArrayAdapter<DrawerListItemVO> {

    private final AccountController mAccountController;

    private final ChatImageController mChatImageController;

    private final SimsMeApplication mApplication;

    /**
     *
     * @param context     activity for layoutinflater
     * @param application application for coloring
     */
    public DrawerListAdapter(Activity context,
                             SimsMeApplication application,
                             int layoutResourceId,
                             List<DrawerListItemVO> data,
                             AccountController accountController,
                             ChatImageController chatImageController) {
        super(context, layoutResourceId, data);
        mApplication = application;
        mChatImageController = chatImageController;
        mAccountController = accountController;
    }

    @NonNull
    @Override
    public View getView(int position,
                        View convertView,
                        @NonNull ViewGroup parent) {
        final Context context = getContext();
        if (position >= this.getCount()) {
            return new RelativeLayout(context);
        }

        DrawerListItemVO drawerListItemVO = getItem(position);

        if (drawerListItemVO == null) {
            return new RelativeLayout(context);
        }

        View relativeLayout = convertView;
        final LayoutInflater inflater = ViewExtensionsKt.themedInflater(LayoutInflater.from(context), context);
        if ((relativeLayout == null) || (relativeLayout.getTag() == null)) {
            if (drawerListItemVO.getImage() == -1) {
                relativeLayout = inflater.inflate(R.layout.drawer_list_item_first, null, false);
            } else if (drawerListItemVO.getImage() == -2) {
                    relativeLayout = inflater.inflate(R.layout.drawer_list_item_version, null, false);
            } else {
                relativeLayout = inflater.inflate(R.layout.drawer_list_item, null, false);
            }
        }

        final String contentDescription = drawerListItemVO.getContentDescription();
        if (!StringUtil.isNullOrEmpty(contentDescription)) {
            relativeLayout.setContentDescription(contentDescription);
        }

        TextView titleTextView = relativeLayout.findViewById(R.id.drawer_list_item_text_view_title);
        TextView hintTextView = relativeLayout.findViewById(R.id.drawer_list_item_text_view_hint);
        View statusView = relativeLayout.findViewById(R.id.drawer_list_item_status_view);

        titleTextView.setText(drawerListItemVO.getTitle());
        if (hintTextView != null) {
            if (RuntimeConfig.isBAMandant()) {
                if (drawerListItemVO.getIsAbsent()) {
                    hintTextView.setTextColor(ScreenDesignUtil.getInstance().getLowColor((Application) getContext().getApplicationContext()));
                } else {
                    hintTextView.setTextColor(ScreenDesignUtil.getInstance().getHighColor((Application) getContext().getApplicationContext()));
                }
            }

            hintTextView.setText(drawerListItemVO.getHint());
        }

        if (statusView != null) {
            if (RuntimeConfig.isBAMandant()) {
                Drawable statusDrawable = BitmapUtil.getConfiguredStateDrawable(mApplication, drawerListItemVO.getIsAbsent(), true);
                if (statusDrawable != null) {
                    statusView.setBackground(statusDrawable);
                }
            } else {
                statusView.setVisibility(View.GONE);
            }
        }

        if (drawerListItemVO.getImage() == -1) {
            try {
                ImageView maskImageView = relativeLayout.findViewById(R.id.drawer_list_item_mask_image_view);
                Account account = mAccountController.getAccount();
                Bitmap profileImage = mChatImageController.getImageByGuid(account.getAccountGuid(),
                        ChatImageController.SIZE_DRAWER);

                if (maskImageView != null && profileImage != null) {
                    maskImageView.setImageBitmap(profileImage);
                }
            } catch (LocalizedException e) {
                LogUtil.w(this.getClass().getName(), e.getMessage(), e);
            }
        } else if (drawerListItemVO.getImage() == -2) {
            // No image for version info
        } else {
            ImageView imageView = relativeLayout.findViewById(R.id.drawer_list_item_image_view);

            if (imageView != null) {
                imageView.setImageResource(drawerListItemVO.getImage());
            }
        }
        return relativeLayout;
    }
}
