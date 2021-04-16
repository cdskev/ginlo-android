// Copyright (c) 2020-2021 ginlo.net GmbH

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
import eu.ginlo_apps.ginlo.model.PurchaseItemModel;

import java.util.List;

/**
 *
 * @author  Florian
 * @version $Revision$, $Date$, $Author$
 *
 */
public class PurchaseAdapter
   extends ArrayAdapter<PurchaseItemModel>
{

   public PurchaseAdapter(Context                     context,
                            int                       layoutResourceId,
                            List<PurchaseItemModel>   data

                          )
   {
      super(context, layoutResourceId, data);
   }

   @Override public View getView(int       position,
                                 View      convertView,
                                 ViewGroup parent)
   {
      if (position >= this.getCount())
      {
         return new RelativeLayout(getContext());
      }

      PurchaseItemModel purchaseItemModel = getItem(position);

      LinearLayout   mainLayout   = (LinearLayout)convertView;
      LayoutInflater   layoutInflater   = LayoutInflater.from(getContext());

      if (mainLayout == null)
      {
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
