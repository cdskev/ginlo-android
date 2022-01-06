// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.adapter;

import android.content.Context;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.PurchaseItemModel;
import eu.ginlo_apps.ginlo.util.StreamUtil;

import java.util.List;

import javax.annotation.Nonnull;

/**
 *
 * @author  Florian
 * @version $Revision$, $Date$, $Author$
 *
 */
public class PurchaseAdapter extends ArrayAdapter<PurchaseItemModel> {

   private static final String TAG = PurchaseAdapter.class.getSimpleName();

   public PurchaseAdapter(Context context, int layoutResourceId, @Nonnull List<PurchaseItemModel> data) {
      super(context, layoutResourceId, data);
      LogUtil.i(TAG, "Building purchase adapter with " + data.size() + " items.");
   }

   @Override public View getView(int position, View convertView, ViewGroup parent) {

      if (position >= this.getCount()) {
         return new RelativeLayout(getContext());
      }

      PurchaseItemModel purchaseItemModel = getItem(position);

      LinearLayout   mainLayout   = (LinearLayout)convertView;
      LayoutInflater   layoutInflater   = LayoutInflater.from(getContext());

      if (mainLayout == null) {
         mainLayout = (LinearLayout)layoutInflater.inflate(R.layout.purchase_list_item, null, false);
      }

      mainLayout.setTag(purchaseItemModel);
      TextView nameTextView = mainLayout.findViewById(R.id.purchase_item_name_textview);
      TextView priceTextView  = mainLayout.findViewById(R.id.purchase_item_price_textview);

      nameTextView.setText(purchaseItemModel.getName());
      priceTextView.setText(purchaseItemModel.getPrice());

      return mainLayout;
   }
}
