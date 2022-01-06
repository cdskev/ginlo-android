// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model;


import eu.ginlo_apps.ginlo.billing.GinloSkuDetailsImpl;

/**
 * Created by SGA on 11.07.2016.
 */
public class PurchaseItemModel
{
   private final String  mName;
   private final String  mPrice;
   private final GinloSkuDetailsImpl mSkuDetail;
   public PurchaseItemModel(final String name,  final String price, final GinloSkuDetailsImpl skuDetail) {
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

   public GinloSkuDetailsImpl getSkuDetail()
   {
      return mSkuDetail;
   }

}
