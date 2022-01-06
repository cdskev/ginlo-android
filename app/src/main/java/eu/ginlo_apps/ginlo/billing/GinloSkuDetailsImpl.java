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

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents an in-app product's listing details.
 */
public class GinloSkuDetailsImpl {

    private final String mItemType;
    private final String mJson;
    private final String mSku;
    private final String mType;
    private final String mPrice;
    private final String mTitle;
    private final String mDescription;
    private final String mPriceCurrency;
    private final String mSkuDetailsToken;

    private final long mPriceMicros;

    /**
     *
     * @throws JSONException [!EXC_DESCRIPTION!]
     */
    public GinloSkuDetailsImpl(String jsonSkuDetails)
            throws JSONException {
        this(GinloBillingImpl.INAPP, jsonSkuDetails);
    }

    /**
     *
     * @throws JSONException [!EXC_DESCRIPTION!]
     */
    public GinloSkuDetailsImpl(String itemType,
                               String jsonSkuDetails)
            throws JSONException {
        mItemType = itemType;
        mJson = jsonSkuDetails;

        JSONObject o = new JSONObject(mJson);

        mSku = o.optString("productId");
        mType = o.optString("type");
        mPrice = o.optString("price");
        mTitle = o.optString("title");
        mDescription = o.optString("description");
        mPriceMicros = o.optLong("price_amount_micros");
        mPriceCurrency = o.optString("price_currency_code");
        mSkuDetailsToken = o.optString("skuDetailsToken");
    }

    public String getOriginalJson() {
        return mJson;
    }

    public String getSku() {
        return mSku;
    }

    public String getType() {
        return mType;
    }

    public String getPrice() {
        return mPrice;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getPriceCurrency() {
        return mPriceCurrency;
    }

    public double getPriceMicros() {
        return mPriceMicros / 1000000d;
    }

    public String getDetailsToken() {
        return mSkuDetailsToken;
    }

    @Override
    public String toString() {
        return "GinloSkuDetailsImpl:" + mJson;
    }
}
