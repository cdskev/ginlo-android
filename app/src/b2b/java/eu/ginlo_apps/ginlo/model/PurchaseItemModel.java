// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model;

import eu.ginlo_apps.ginlo.billing.SkuDetails;

/**
 * Created by SGA on 11.07.2016.
 */
public class PurchaseItemModel
{
   private final String  mName;
   private final String  mPrice;
   private final SkuDetails mSkuDetail;
   public PurchaseItemModel(final String name,  final String price, final SkuDetails skuDetail)
   {
      mName = name;
      mPrice = price;
      mSkuDetail = skuDetail;
   }

   public String getName()
   {
      return mName;
   }

   public String getPrice()
   {
      return mPrice;
   }

   public SkuDetails getSkuDetail()
   {
      return mSkuDetail;
   }

}
