package eu.ginlo_apps.ginlo.billing;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryRecord;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import eu.ginlo_apps.ginlo.log.LogUtil;

public class GinloBillingImpl
        implements PurchasesUpdatedListener,
        SkuDetailsResponseListener,
        PurchasesResponseListener,
        PurchaseHistoryResponseListener,
        ConsumeResponseListener {

    private final static String TAG = GinloBillingImpl.class.getSimpleName();
    private final Context mContext;
    private BillingClient billingClient = null;
    private ArrayList<SkuDetails> mSkuDetailsList = null;
    private ArrayList<PurchaseHistoryRecord> mPurchaseHistory = null;

    private final Map<String, GinloSkuDetailsImpl> mSkuMap = new HashMap<>();
    private final Map<String, GinloPurchaseImpl> mPurchaseMap = new HashMap<>();

    private boolean isInitialized = false;

    public static final int RESPONSE_OK = 0;
    public static final int RESPONSE_CANCELLED = 1;
    public static final int RESPONSE_DISCONNECTED = 2;
    public static final int RESPONSE_ERROR = -1;

    public static final String INAPP = BillingClient.SkuType.INAPP;
    public static final String SUBS = BillingClient.SkuType.SUBS;

    OnBillingInitializeFinishedListener mOnBillingInitializeFinishedListener = null;
    OnPurchasesUpdatedListener mOnPurchasesUpdatedListener = null;
    OnQuerySkuDetailsFinishedListener mOnQuerySkuDetailsFinishedListener = null;
    OnQueryPurchasesFinishedListener mOnQueryPurchasesFinishedListener = null;
    OnConsumePurchaseFinishedListener mOnConsumePurchaseFinishedListener = null;
    OnPurchaseHistoryResponseListener mOnPurchaseHistoryResponseListener = null;

    public GinloBillingImpl(Context context) {
        this.mContext = context;
    }

    public void initialize(@Nonnull OnBillingInitializeFinishedListener listener) {
        mOnBillingInitializeFinishedListener = listener;

        LogUtil.d(TAG, "initialize: Starting in-app billing setup.");
        mSkuDetailsList = null;
        mOnPurchasesUpdatedListener = null;
        mOnQuerySkuDetailsFinishedListener = null;
        mOnQueryPurchasesFinishedListener = null;
        mOnConsumePurchaseFinishedListener = null;
        mOnPurchaseHistoryResponseListener = null;

        if (isInitialized && billingClient != null && billingClient.isReady()) {
            LogUtil.i(TAG, "initialize: Trying to re-initialize billingClient ...");
            mOnBillingInitializeFinishedListener.onBillingInitializeFinished(
                    new GinloBillingResult(BillingClient.BillingResponseCode.OK, "Re-initialization not necessary."));
            return;
        }

        if(billingClient == null) {
            billingClient = BillingClient.newBuilder(mContext).setListener(this).enablePendingPurchases().build();
        }

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NotNull BillingResult billingResult) {
                LogUtil.d(TAG, "onBillingSetupFinished: returned with responseCode " +
                        billingResult.getResponseCode() +
                        " (" + billingResult.getDebugMessage() + ")");
                isInitialized = true;
                mOnBillingInitializeFinishedListener.onBillingInitializeFinished(new GinloBillingResult(billingResult));
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Logic from ServiceConnection.onServiceDisconnected should be moved here.
                LogUtil.d(TAG, "onBillingServiceDisconnected: called.");
                isInitialized = false;
                mOnBillingInitializeFinishedListener.onBillingInitializeFinished(
                        new GinloBillingResult(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, "Service disconnected."));
            }
        });
    }

    public boolean launchBillingFlow(@Nonnull Activity activity, @Nonnull String sku, @Nonnull OnPurchasesUpdatedListener listener) {
        if(!isInitialized) {
            LogUtil.e(TAG, "launchBillingFlow: Fatal: BillingClient not initialized!");
        }

        mOnPurchasesUpdatedListener = listener;
        SkuDetails skuDetails = null;
        try {
            skuDetails = new SkuDetails(getSkuDetails(sku).getOriginalJson());
        } catch (JSONException e) {
            LogUtil.e(TAG, "launchBillingFlow: Got " + e.getMessage());
            return false;
        }

        if(skuDetails == null) {
            LogUtil.w(TAG, "launchBillingFlow: No skuDetails available for " + sku);
            return false;
        }

        BillingFlowParams purchaseParams = BillingFlowParams.newBuilder().setSkuDetails(skuDetails).build();
        billingClient.launchBillingFlow(activity, purchaseParams);
        return true;
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
        final int responseCode = billingResult.getResponseCode();
        LogUtil.d(TAG, "onPurchasesUpdated: returned with responseCode " +
                responseCode + " (" + billingResult.getDebugMessage() + ")");

        ArrayList<GinloPurchaseImpl> purchases = null;

        if (responseCode == BillingClient.BillingResponseCode.OK) {
            if (list != null && list.size() > 0) {
                // Update purchases cache map
                for (Purchase p : list) {
                    LogUtil.d(TAG, "onPurchasesUpdated: Received " + p.getOriginalJson());
                    try {
                        addPurchase(new GinloPurchaseImpl(BillingClient.SkuType.INAPP, p.getOriginalJson(), p.getSignature()));
                    } catch (JSONException e) {
                        LogUtil.w(TAG, "onPurchasesUpdated: Got " + e.getMessage());
                    }
                }
                purchases = convertPurchases(new ArrayList<Purchase>(list));
            } else {
                LogUtil.i(TAG, "onPurchasesUpdated: List is empty.");
            }
        } else {
            LogUtil.w(TAG, "onPurchasesUpdated: returned with responseCode " +
                    responseCode + " (" + billingResult.getDebugMessage() + ")");
        }

        mOnPurchasesUpdatedListener.onPurchasesUpdated(new GinloBillingResult(billingResult), purchases);
    }

    public void querySkuDetails(@Nonnull List<String> skuList, @Nonnull OnQuerySkuDetailsFinishedListener listener) {
        LogUtil.d(TAG, "querySkuDetails: " + skuList.toString());
        if(!isInitialized) {
            LogUtil.e(TAG, "querySkuDetails: Fatal: BillingClient not initialized!");
        }

        mOnQuerySkuDetailsFinishedListener = listener;

        LogUtil.d(TAG, "querySkuDetails: Query INAPP ... ");
        billingClient.querySkuDetailsAsync(SkuDetailsParams.newBuilder()
                .setType(BillingClient.SkuType.INAPP)
                .setSkusList(skuList)
                .build(), this);

        /* We don't have any subscription products yet
        LogUtil.d(TAG, "querySkuDetails: Query SUBS ... ");
        billingClient.querySkuDetailsAsync(SkuDetailsParams.newBuilder()
                .setType(BillingClient.SkuType.SUBS)
                .setSkusList(skuList)
                .build(), this);

         */
    }

    @Override
    public void onSkuDetailsResponse(@NonNull BillingResult billingResult, @Nullable List<SkuDetails> list) {

        final int responseCode = billingResult.getResponseCode();
        LogUtil.d(TAG, "onSkuDetailsResponse: returned with responseCode " +
                responseCode + " (" + billingResult.getDebugMessage() + ")");

        ArrayList<String> skusAsJson = null;

        if (responseCode == BillingClient.BillingResponseCode.OK) {
            if (list != null && list.size() > 0) {
                mSkuDetailsList = new ArrayList<>(list);
                skusAsJson = new ArrayList<>();

                // Update skuDetails cache map
                for (SkuDetails skuDetails : mSkuDetailsList) {
                    String s = skuDetails.getOriginalJson();
                    LogUtil.d(TAG, "onSkuDetailsResponse: Received " + s);
                    skusAsJson.add(s);
                    try {
                        addSkuDetails(new GinloSkuDetailsImpl(s));
                    } catch (JSONException e) {
                        LogUtil.w(TAG, "onSkuDetailsResponse: Got " + e.getMessage());
                    }
                }
            } else {
                LogUtil.i(TAG, "onSkuDetailsResponse: List is empty.");
            }
        } else {
            LogUtil.w(TAG, "onSkuDetailsResponse: Returned with BillingResponseCode " +
                    responseCode + " (" + billingResult.getDebugMessage() + ")");
        }

        mOnQuerySkuDetailsFinishedListener.onQuerySkuDetailsFinished(new GinloBillingResult(billingResult), skusAsJson);
    }

    public void queryPurchases(@Nonnull String skuType, @Nonnull OnQueryPurchasesFinishedListener listener) {
        LogUtil.d(TAG, "queryPurchases: Query for skuType = " + skuType);
        if(!isInitialized) {
            LogUtil.e(TAG, "queryPurchases: Fatal: BillingClient not initialized!");
        }

        mOnQueryPurchasesFinishedListener = listener;
        if (billingClient != null) {
            billingClient.queryPurchasesAsync(skuType,this);
        }
    }

    @Override
    public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> list) {

        final int responseCode = billingResult.getResponseCode();
        LogUtil.d(TAG, "onQueryPurchasesResponse: returned with responseCode " +
                responseCode + " (" + billingResult.getDebugMessage() + ")");

        ArrayList<GinloPurchaseImpl> purchases = null;

        if (responseCode == BillingClient.BillingResponseCode.OK) {
            if (list.size() > 0) {
                // Update purchases cache map
                for (Purchase p : list) {
                    LogUtil.d(TAG, "onQueryPurchasesResponse: Received " + p.getOriginalJson());
                    try {
                        addPurchase(new GinloPurchaseImpl("", p.getOriginalJson(), p.getSignature()));
                    } catch (JSONException e) {
                        LogUtil.w(TAG, "onQueryPurchasesResponse: Got " + e.getMessage());
                    }
                }
                purchases = convertPurchases(new ArrayList<Purchase>(list));
            } else {
                LogUtil.i(TAG, "onQueryPurchasesResponse: List is empty.");
            }
        } else {
            LogUtil.w(TAG, "onQueryPurchasesResponse: Returned with " + billingResult.getResponseCode());
        }

        mOnQueryPurchasesFinishedListener.onQueryPurchasesFinished(new GinloBillingResult(billingResult), purchases);
    }

    public void consumePurchases(@Nonnull ArrayList<GinloPurchaseImpl> purchases, @Nonnull OnConsumePurchaseFinishedListener listener) {
        for (GinloPurchaseImpl purchase : purchases) {
            consumePurchase(purchase.getPurchaseToken(), listener);
        }
    }

    public void consumePurchase(@Nonnull String purchaseToken, @Nonnull OnConsumePurchaseFinishedListener listener) {
        mOnConsumePurchaseFinishedListener = listener;
        LogUtil.i(TAG, "consumePurchase: Consuming " + purchaseToken);

        if(!isInitialized) {
            LogUtil.e(TAG, "consumePurchase: Fatal: BillingClient not initialized!");
        }

        ConsumeParams.Builder params = ConsumeParams.newBuilder();
        params.setPurchaseToken(purchaseToken);
        billingClient.consumeAsync(params.build(),this);
    }

    @Override
    public void onConsumeResponse(@NonNull BillingResult billingResult, @NonNull String purchaseToken) {

        final int responseCode = billingResult.getResponseCode();
        LogUtil.d(TAG, "onConsumeResponse: returned with responseCode " +
                responseCode + " (" + billingResult.getDebugMessage() + ")");

        if (responseCode != BillingClient.BillingResponseCode.OK) {
            LogUtil.w(TAG, "onConsumeResponse: Returned with " + billingResult.getResponseCode());
        }

        mOnConsumePurchaseFinishedListener.onConsumePurchaseFinished(new GinloBillingResult(billingResult), purchaseToken);
    }

    public void queryPurchaseHistory(@Nonnull String skuType, @Nonnull OnQueryPurchasesFinishedListener listener) {
        if(!isInitialized) {
            LogUtil.e(TAG, "queryPurchaseHistory: Fatal: BillingClient not initialized!");
        }

        mOnQueryPurchasesFinishedListener = listener;
        billingClient.queryPurchaseHistoryAsync(skuType,this);
    }

    @Override
    public void onPurchaseHistoryResponse(@NonNull BillingResult billingResult, @Nullable List<PurchaseHistoryRecord> list) {
        final int responseCode = billingResult.getResponseCode();
        LogUtil.d(TAG, "onPurchaseHistoryResponse: returned with responseCode " +
                responseCode + " (" + billingResult.getDebugMessage() + ")");

        ArrayList<GinloPurchaseImpl> purchaseHistory = null;

        if (responseCode == BillingClient.BillingResponseCode.OK) {
            LogUtil.d(TAG, "onPurchaseHistoryResponse: Ok.");

            if (list != null && list.size() > 0) {
                mPurchaseHistory = new ArrayList<PurchaseHistoryRecord>(list);
                purchaseHistory = convertPurchaseHistory(new ArrayList<PurchaseHistoryRecord>(list));
            } else {
                LogUtil.i(TAG, "onPurchaseHistoryResponse: List is empty.");
            }
        } else {
            LogUtil.w(TAG, "onPurchaseHistoryResponse: Returned with " + billingResult.getResponseCode());
        }

        mOnPurchaseHistoryResponseListener.onPurchaseHistoryResponse(new GinloBillingResult(billingResult), purchaseHistory);

    }

    //
    // Inventory cache and helper functions
    //

    private ArrayList<GinloPurchaseImpl> convertPurchases(@Nonnull ArrayList<Purchase> purchaseArrayList) {
        ArrayList<GinloPurchaseImpl> ginloPurchaseList = new ArrayList<>();

        try {
            for (Purchase p : purchaseArrayList) {
                ginloPurchaseList.add (new GinloPurchaseImpl("", p.getOriginalJson(), p.getSignature()));
            }
        } catch (JSONException e) {
            LogUtil.e(TAG, "convertPurchases: " + e.getMessage());
            //
        }

        return ginloPurchaseList;
    }

    private ArrayList<GinloPurchaseImpl> convertPurchaseHistory(@Nonnull ArrayList<PurchaseHistoryRecord> purchaseArrayList) {
        ArrayList<GinloPurchaseImpl> ginloPurchaseList = new ArrayList<>();

        try {
            for (PurchaseHistoryRecord p : purchaseArrayList) {
                ginloPurchaseList.add (new GinloPurchaseImpl("", p.getOriginalJson(), p.getSignature()));
            }
        } catch (JSONException e) {
            LogUtil.e(TAG, "convertPurchaseHistory: " + e.getMessage());
            //
        }

        return ginloPurchaseList;
    }

    /**
     * Returns the listing details for an in-app product.
     */
    public GinloSkuDetailsImpl getSkuDetails(String sku) {
        return mSkuMap.get(sku);
    }

    /**
     * Returns purchase information for a given product, or null if there is no
     * purchase.
     */
    public GinloPurchaseImpl getPurchase(String sku) {
        return mPurchaseMap.get(sku);
    }

    /**
     * Returns whether or not there exists a purchase of the given product.
     */
    public boolean hasPurchase(String sku) {
        return mPurchaseMap.containsKey(sku);
    }

    /**
     * Return whether or not details about the given product are available.
     */
    public boolean hasDetails(String sku) {
        return mSkuMap.containsKey(sku);
    }

    /**
     * Erase a purchase (locally) from the inventory, given its product ID. This
     * just modifies the Inventory object locally and has no effect on the
     * server! This is useful when you have an existing Inventory object which
     * you know to be up to date, and you have just consumed an item
     * successfully, which means that erasing its purchase data from the
     * Inventory you already have is quicker than querying for a new Inventory.
     */
    public void erasePurchase(String sku) {
        mPurchaseMap.remove(sku);
    }

    /**
     * Returns a list of all owned product IDs.
     */
    public List<String> getAllOwnedSkus() {
        return new ArrayList<>(mPurchaseMap.keySet());
    }

    /**
     * Returns a list of all owned product IDs of a given type
     */
    public List<String> getAllOwnedSkus(String itemType) {
        List<String> result = new ArrayList<>();

        for (GinloPurchaseImpl p : mPurchaseMap.values()) {
            if (p.getItemType().equals(itemType)) {
                result.add(p.getSku());
            }
        }
        return result;
    }

    /**
     * Returns a list of all purchases.
     */
    public List<GinloPurchaseImpl> getAllPurchases() {
        return new ArrayList<>(mPurchaseMap.values());
    }

    void addSkuDetails(GinloSkuDetailsImpl d) {
        mSkuMap.put(d.getSku(), d);
    }

    void addPurchase(GinloPurchaseImpl p) {
        mPurchaseMap.put(p.getSku(), p);
    }

    //
    // Callback interface definitions
    //

    public interface OnBillingInitializeFinishedListener {

        /**
         * Called to notify that initialize is complete.
         *
         * @param billingResult The result of the setup process.
         */
        void onBillingInitializeFinished(@NotNull GinloBillingResult billingResult);
    }

    public interface OnPurchasesUpdatedListener {

        /**
         * Called to notify that updatePurchases is complete.
         *
         * @param billingResult The result of the setup process.
         */
        void onPurchasesUpdated(@NotNull GinloBillingResult billingResult, ArrayList<GinloPurchaseImpl> updatedPurchases);
    }

    public interface OnQuerySkuDetailsFinishedListener {

        /**
         * Called to notify that query skuDetails is complete.
         *
         * @param billingResult The result of the setup process.
         */
        void onQuerySkuDetailsFinished(@NotNull GinloBillingResult billingResult, ArrayList<String> skuDetailsAsJson);
    }

    public interface OnQueryPurchasesFinishedListener {

        /**
         * Called to notify that queryPurchases is complete.
         *
         * @param billingResult The result of the setup process.
         */
        void onQueryPurchasesFinished(@NotNull GinloBillingResult billingResult, ArrayList<GinloPurchaseImpl> queriedPurchases);
    }

    public interface OnConsumePurchaseFinishedListener {

        /**
         * Called to notify that consume purchases is complete.
         *
         * @param billingResult The result of the setup process.
         */
        void onConsumePurchaseFinished(@NotNull GinloBillingResult billingResult, String purchaseToken);
    }

    public interface OnPurchaseHistoryResponseListener {

        /**
         * Called to notify that queryPurchases is complete.
         *
         * @param billingResult The result of the setup process.
         */
        void onPurchaseHistoryResponse(@NotNull GinloBillingResult billingResult, ArrayList<GinloPurchaseImpl> purchaseHistory);
    }

}
