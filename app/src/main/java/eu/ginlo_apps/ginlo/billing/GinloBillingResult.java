package eu.ginlo_apps.ginlo.billing;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;

/**
 * Currently known response codes:
 * BillingClient.BillingResponseCode.OK
 * BillingClient.BillingResponseCode.USER_CANCELED
 * BillingClient.BillingResponseCode.BILLING_UNAVAILABLE:
 * BillingClient.BillingResponseCode.DEVELOPER_ERROR:
 * BillingClient.BillingResponseCode.ERROR:
 * BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED:
 * BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED:
 * BillingClient.BillingResponseCode.ITEM_NOT_OWNED:
 * BillingClient.BillingResponseCode.ITEM_UNAVAILABLE:
 * BillingClient.BillingResponseCode.SERVICE_DISCONNECTED:
 * BillingClient.BillingResponseCode.SERVICE_TIMEOUT:
 * BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE:
 */
public class GinloBillingResult {
    private final BillingResult mBillingResult;

    public GinloBillingResult(BillingResult billingResult) {
        mBillingResult = billingResult;
    }

    public GinloBillingResult(int responseCode, String responseMessage) {
        mBillingResult = BillingResult.newBuilder()
                .setResponseCode(responseCode)
                .setDebugMessage(responseMessage)
                .build();
    }

    public int getResponseCode() {
        return mBillingResult.getResponseCode();
    }

    public String geResponsetMessage() {
        return mBillingResult.getDebugMessage();
    }

    public boolean isSuccess() {
        return mBillingResult.getResponseCode() == BillingClient.BillingResponseCode.OK;
    }

    public boolean isCancelled() {
        return mBillingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED;
    }

    public boolean isFailure() {
        return !isSuccess();
    }
}
