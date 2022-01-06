/* Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.ginlo_apps.ginlo.billing;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Represents an in-app billing purchase.
 */
public class GinloPurchaseImpl {

    final String mItemType;  // ITEM_TYPE_INAPP or ITEM_TYPE_SUBS
    private final String mOriginalJson;
    private final String mOrderId;
    private final String mPackageName;
    private final String mSku;
    private final ArrayList<String> mSkus;
    private final long mPurchaseTime;
    private final int mPurchaseState;
    private final String mDeveloperPayload;
    private final String mPurchaseToken;
    private final String mSignature;

    /**
     *
     * @throws JSONException [!EXC_DESCRIPTION!]
     */
    public GinloPurchaseImpl(String itemType,
                             String jsonPurchaseInfo,
                             String signature)
            throws JSONException {
        mItemType = itemType;
        mOriginalJson = jsonPurchaseInfo;

        JSONObject o = new JSONObject(mOriginalJson);

        mOrderId = o.optString("orderId");
        mPackageName = o.optString("packageName");
        mSkus = getSkus(o);
        mSku = mSkus.get(0); // This is deprecated, just return the first entry
        mPurchaseTime = o.optLong("purchaseTime");
        mPurchaseState = o.optInt("purchaseState");
        mDeveloperPayload = o.optString("developerPayload");
        mPurchaseToken = o.optString("token", o.optString("purchaseToken"));
        mSignature = signature;
    }

    public String getItemType() {
        return mItemType;
    }

    public String getOrderId() {
        return mOrderId;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getSku() {
        return mSku;
    }

    public ArrayList<String> getSkus() {
        return mSkus;
    }

    public long getPurchaseTime() {
        return mPurchaseTime;
    }

    public int getPurchaseState() {
        return mPurchaseState;
    }

    public String getDeveloperPayload() {
        return mDeveloperPayload;
    }

    public String getPurchaseToken() {
        return mPurchaseToken;
    }

    public String getOriginalJson() {
        return mOriginalJson;
    }

    public String getSignature() {
        return mSignature;
    }

    public ArrayList<String> getSkus(JSONObject o) {
        ArrayList<String> skus = new ArrayList<>();
        if (o.has("productIds")) {
            JSONArray ja = o.optJSONArray("productIds");
            if (ja != null) {
                for(int var3 = 0; var3 < ja.length(); ++var3) {
                    skus.add(ja.optString(var3));
                }
            }
        } else if (o.has("productId")) {
            skus.add(o.optString("productId"));
        }

        return skus;
    }


    @Override
    public String toString() {
        return "PurchaseInfo(type:" + mItemType + "):" + mOriginalJson;
    }
}
