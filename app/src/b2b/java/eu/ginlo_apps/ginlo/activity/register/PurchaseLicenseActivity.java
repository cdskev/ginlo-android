// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.NotNull;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.DeleteAccountActivity;
import eu.ginlo_apps.ginlo.LoginActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.adapter.PurchaseAdapter;
import eu.ginlo_apps.ginlo.billing.GinloBillingImpl;
import eu.ginlo_apps.ginlo.billing.GinloBillingResult;
import eu.ginlo_apps.ginlo.billing.GinloPurchaseImpl;
import eu.ginlo_apps.ginlo.billing.GinloSkuDetailsImpl;
import eu.ginlo_apps.ginlo.context.SimsMeApplicationBusiness;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.contracts.OnGetPurchasedProductsListener;
import eu.ginlo_apps.ginlo.data.network.AppConnectivity;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.log.Logger;
import eu.ginlo_apps.ginlo.model.PurchaseItemModel;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class PurchaseLicenseActivity extends BaseActivity {
    private final static String TAG = PurchaseLicenseActivity.class.getSimpleName();
    private static final int DELETE_ACCOUNT_RESULT_CODE = 500;
    public static final String EXTRA_DONT_FORWARD_TO_OVERVIEW = "PurchaseLicenseActivity.extraDontForwardIfLicenceIsAboutToExpire";

    private AccountController mAccountController;

    private JsonArray mProducts = null;
    private List<String> mProductIds = null;
    private boolean mDontForwardIfLicenceIsAboutToExpire;
    private int mNumberofPurchasesToSave;

    @Inject
    public AppConnectivity appConnectivity;

    @Inject
    Logger logger;

    @Inject
    Router router;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        SimsMeApplicationBusiness application = (SimsMeApplicationBusiness) getApplicationContext();

        mAccountController = application.getAccountController();

        final Account account = mAccountController.getAccount();

        if (account == null) {
            finish();
            return;
        }

        if (account.getState() < Account.ACCOUNT_STATE_FULL) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        }

        Intent intent = getIntent();
        if (intent.getExtras() != null && intent.hasExtra(EXTRA_DONT_FORWARD_TO_OVERVIEW)) {
            mDontForwardIfLicenceIsAboutToExpire = intent.getBooleanExtra(EXTRA_DONT_FORWARD_TO_OVERVIEW, false);
        }
    }

    @Override
    public void onBackPressed() {
        try {
            final Account account = mAccountController.getAccount();

            if (account.getState() >= Account.ACCOUNT_STATE_FULL && account.getHasLicence()) {
                super.onBackPressed();
            } else {
                getSimsMeApplication().getAppLifecycleController().restartApp();
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, "onBackPressed: Failed to get account state: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG).show();
            return;
        }

        final GinloBillingImpl.OnQuerySkuDetailsFinishedListener inventoryFinishedListener = new GinloBillingImpl.OnQuerySkuDetailsFinishedListener() {
            @Override
            public void onQuerySkuDetailsFinished(@NotNull GinloBillingResult result, ArrayList<String> skuDetailsAsJson) {
                boolean bDismissIdle = true;
                LogUtil.d(TAG, "onQuerySkuDetailsFinished: returned with " + result.getResponseCode());
                try {

                    if (result.isFailure()) {
                        LogUtil.w(TAG, "onQuerySkuDetailsFinished: returned with error: " + result.geResponsetMessage());
                        DialogBuilderUtil.buildErrorDialog(PurchaseLicenseActivity.this, result.geResponsetMessage()).show();
                        return;
                    }

                    if (skuDetailsAsJson == null) {
                        LogUtil.w(TAG, "onQuerySkuDetailsFinished: returned with zero inventory!");
                        DialogBuilderUtil.buildErrorDialog(PurchaseLicenseActivity.this, getString(R.string.connecting_playstore_failed)).show();
                        return;
                    }

                    // Vorhandene Käufe ermitteln
                    List<GinloPurchaseImpl> allPurchases = ginloBillingImpl.getAllPurchases();
                    LogUtil.d(TAG, "onQuerySkuDetailsFinished: getAllPurchases returned " + allPurchases);
                    List<GinloPurchaseImpl> purchaseToSend = new ArrayList<>();

                    for (int i = 0; i < allPurchases.size(); i++) {
                        GinloPurchaseImpl p = allPurchases.get(i);
                        LogUtil.i(TAG, "onQuerySkuDetailsFinished: Existing purchase: " + p.getOriginalJson());

                        if (!getSimsMeApplication().getPreferencesController().isPurchaseSaved(p)) {
                            synchronized (this) {
                                mNumberofPurchasesToSave++;
                            }
                            bDismissIdle = false;
                            purchaseToSend.add(p);
                        }
                    }

                    // Send collected purchases to backend and consume them to acknowledge billing.
                    for (int i = 0; i < purchaseToSend.size(); i++) {
                        consumeNewPurchase(purchaseToSend.get(i));
                        savePurchaseToBackend(purchaseToSend.get(i));
                    }
                } catch (Exception e) {
                    LogUtil.e(TAG, "onQuerySkuDetailsFinished: " + e.getMessage(), e);
                } finally {
                    if (bDismissIdle) {
                        dismissIdleDialog();
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buildPurchaseView();
                    }
                });
            }
        };

        final IBackendService.OnBackendResponseListener productResponseListener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                boolean bDismissIdle = true;
                try {
                    if(response != null) {
                        if (response.errorMessage != null) {
                            LogUtil.w(TAG, "productResponseListener: Got error from backend: " + response.errorMessage);
                            DialogBuilderUtil.buildErrorDialog(PurchaseLicenseActivity.this, response.errorMessage).show();
                        } else if (response.jsonArray != null) {
                            mProducts = response.jsonArray;
                            LogUtil.d(TAG, "productResponseListener: Got products: " + mProducts.toString());
                            bDismissIdle = false;
                            parseProducts();
                            ginloBillingImpl.querySkuDetails(mProductIds, inventoryFinishedListener);
                        }
                    } else {
                        LogUtil.w(TAG, "productResponseListener: Got null response from backend!");
                    }
                } catch (Exception e) {
                    LogUtil.e(TAG, "onQuerySkuDetailsFinished: " + e.getMessage(), e);
                } finally {
                    if (bDismissIdle) {
                        dismissIdleDialog();
                    }
                }
            }
        };

        final OnGetPurchasedProductsListener onRegisterVoucherListener = new OnGetPurchasedProductsListener() {
            @Override
            public void onGetPurchasedProductsSuccess() {
                LogUtil.i(TAG, "Successfully retrieved purchased products.");
                boolean bDismissIdle = true;
                try {
                    final Account account = mAccountController.getAccount();

                    if (account.getHasLicence()) {
                        showErrorLayout(false);

                        if (!mDontForwardIfLicenceIsAboutToExpire) {
                            if (account.getState() == Account.ACCOUNT_STATE_FULL) {
                                final Intent intent = PurchaseLicenseActivity.this.getIntentFromCallerIntent(RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
                                startActivity(intent);
                                finish();
                            } else if (account.getState() == Account.ACCOUNT_STATE_CONFIRMED) {
                                final Intent intent;
                                final PreferencesController preferencesController = getSimsMeApplication().getPreferencesController();
                                final boolean simsmeIdShownAtReg = preferencesController.getSimsmeIdShownAtReg();
                                if (simsmeIdShownAtReg) {
                                    intent = new Intent(PurchaseLicenseActivity.this, RuntimeConfig.getClassUtil().getInitProfileActivityClass());
                                } else {
                                    intent = new Intent(PurchaseLicenseActivity.this, ShowSimsmeIdActivity.class);
                                    preferencesController.setSimsmeIdShownAtReg();
                                }

                                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                                startActivity(intent);
                                finish();
                            } else {
                                throw new IllegalStateException("EnterLicenceCodeActivity::GetPurchasedProducts: Account in illegal state: " + account.getState());
                            }
                            return;
                        }
                    } else {
                        showErrorLayout(true);
                    }

                    if (mProducts == null) {
                        LogUtil.i(TAG, "Local product list empty. Retrieve products from backend");
                        bDismissIdle = false;
                        BackendService.withAsyncConnection(getSimsMeApplication())
                                .getProducts(productResponseListener);
                    }

                } catch (LocalizedException e) {
                    LogUtil.e(TAG, "onGetPurchasedProductsSuccess: " + e.getMessage());
                } finally {
                    if (bDismissIdle) {
                        dismissIdleDialog();
                    }
                }

            }

            @Override
            public void onGetPurchasedProductsFail(String errorMessage) {
                LogUtil.w(TAG, "Get purchased products failed with error: " + errorMessage);
                dismissIdleDialog();

                if (StringUtil.isEqual(errorMessage, "AND-0055")) {
                    onOwnAccountWasDeleteOnServer();
                } else {
                    DialogBuilderUtil.buildErrorDialog(PurchaseLicenseActivity.this, errorMessage).show();
                }
            }
        };

        if (mApplication.getLoginController().isLoggedIn()) {
            LogUtil.i(TAG, "Loading licensed product");

            mAccountController.getPurchasedProducts(onRegisterVoucherListener);
            showIdleDialog(R.string.dialog_licence_check);
        }
    }

    /**
     * Build the purchase listView - must be running on UI thread!
     * mProductIds is parsed from mProducts which is the product list retrieved from the backend
     */
    private void buildPurchaseView() {
        if(mProductIds != null) {
            List<PurchaseItemModel> purchaseItemModels = new ArrayList<>();
            for (int i = 0; i < mProductIds.size(); i++) {
                String productId = mProductIds.get(i);
                GinloSkuDetailsImpl details = ginloBillingImpl.getSkuDetails(productId);
                if (details != null) {
                    LogUtil.i(TAG, "onContentChanged: Parsing PlayStore inventory found: " + details);
                    String title = details.getTitle();
                    if (title.contains("(")) {
                        title = title.substring(0, title.indexOf("("));
                    }
                    PurchaseItemModel pim = new PurchaseItemModel(title, details.getPrice(), details);
                    purchaseItemModels.add(pim);
                }
            }

            PurchaseAdapter purchaseAdapter = new PurchaseAdapter(PurchaseLicenseActivity.this, R.layout.purchase_list_item, purchaseItemModels);

            ListView listView = findViewById(R.id.purchase_licence_listview);
            listView.setAdapter(purchaseAdapter);
            setDynamicHeight(listView, 0);
            purchaseAdapter.notifyDataSetChanged();
        }
    }

        /**
         * Parse the complete product list from backend to extract two list of productIds (skus)
         * containing skus relevant for Android clients only:
         * list of all IDs (-> mProductIds)
         *
         * The source list is expected to be in mProducts.
         */
    private void parseProducts() {
        LogUtil.d(TAG, "parseProducts: ");
        List<String> productIds = new ArrayList<>();
        // mProducts contains product list from the backend
        for (int i = 0; i < mProducts.size(); i++) {
            JsonElement jse = mProducts.get(i);
            if (!(jse instanceof JsonObject)) {
                continue;
            }
            JsonObject outerProduct = (JsonObject) jse;
            if (!outerProduct.has("Product")) {
                continue;
            }
            JsonObject product = outerProduct.getAsJsonObject("Product");
            if(product.has("feature")) {
                String feature = product.get("feature").getAsString();
                if (!feature.equalsIgnoreCase("usage")) {
                    continue;
                }
            }
            String os = product.has("os") ? product.get("os").getAsString() : null;
            if (os != null && os.equalsIgnoreCase("Android")) {
                String productId = product.has("productId") ? product.get("productId").getAsString() : "";
                String duration = product.has("duration") ? product.get("duration").getAsString() : null;
                productIds.add(productId);
                LogUtil.i(TAG, "Parsing products found: " + productId);
            }
        }
        mProductIds = productIds;
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_purchase_licence_code;
    }

    @Override
    protected void onResumeActivity() {
    }

    public void handleRestorePurchasesClick(final View view) {
    }

    public void handleEnterLicenceCodeClick(final View view) {
        Intent intent = new Intent(this, EnterLicenceCodeActivity.class);
        intent.putExtra(EXTRA_DONT_FORWARD_TO_OVERVIEW, mDontForwardIfLicenceIsAboutToExpire);
        startActivity(intent);
    }

    public void handleSendLogFileClick(final View view) {
        File logFile = logger.getLog();
        if (logFile == null) {
            Toast.makeText(
                    this,
                    R.string.unexpected_error_label,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        router.sendLog(
                getString(R.string.settings_support_customerCare_email),
                getString(R.string.settings_support_logs_send_emailSubjectPrivate),
                logFile,
                getString(R.string.settings_support_logs_send_appChooser)
        );
    }

    private void showErrorLayout(final boolean show) {
        final View errorLayout = findViewById(R.id.purchase_licence_top_warning);
        if (errorLayout != null) {
            if (show) {
                errorLayout.setVisibility(View.VISIBLE);
            } else {
                errorLayout.setVisibility(View.GONE);
            }
        }
    }

    private void savePurchaseToBackend(@Nonnull final GinloPurchaseImpl info) {
        LogUtil.i(TAG, "Sending purchase to backend: " + info.getPurchaseToken());
        IBackendService.OnBackendResponseListener backendResponseListener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.errorMessage != null) {
                    LogUtil.w(TAG, "savePurchaseToBackend returned error: " + response.errorMessage);
                    // ERR-0042 means that the item has already been registered.
                    // TODO: Check, if it is safe to ignore ERR-0042 at this point
                    if(!response.errorMessage.endsWith("(ERR-0042)")) {
                        DialogBuilderUtil.buildErrorDialog(PurchaseLicenseActivity.this, response.errorMessage).show();
                    }
                } else {
                    getSimsMeApplication().getPreferencesController().markPurchaseSaved(info);
                    LogUtil.w(TAG, "savePurchaseToBackend markPurchaseSaved done.");
                }
                boolean bWasLast;
                synchronized (this) {
                    mNumberofPurchasesToSave--;
                    bWasLast = (mNumberofPurchasesToSave <= 0);
                }
                if (bWasLast) {
                    dismissIdleDialog();
                    onResume();
                }
            }
        };
        BackendService.withAsyncConnection(getSimsMeApplication())
                .registerPayment(info, backendResponseListener);
    }

    private void consumeNewPurchase(@Nonnull final GinloPurchaseImpl purchase) {
        ginloBillingImpl.consumePurchase(purchase.getPurchaseToken(), new GinloBillingImpl.OnConsumePurchaseFinishedListener() {
            @Override
            public void onConsumePurchaseFinished(@NotNull GinloBillingResult billingResult, String purchaseToken) {
                if(billingResult.isSuccess()) {
                    LogUtil.i(TAG, "onResume: onConsumePurchaseFinished successful for token = " + purchaseToken);
                } else {
                    LogUtil.e(TAG, "onResume: onConsumePurchaseFinished returned " +
                            billingResult.getResponseCode() + " (" + billingResult.geResponsetMessage() + ")");
                }
            }
        });
    }

    public void handleBuyClick(final View view) {
        if (view.getTag() != null && view.getTag() instanceof PurchaseItemModel) {
            final PurchaseItemModel pim = (PurchaseItemModel) view.getTag();
            LogUtil.i(TAG, "Start Purchase Flow");

            ginloBillingImpl.launchBillingFlow(this, pim.getSkuDetail().getSku(), new GinloBillingImpl.OnPurchasesUpdatedListener() {
                @Override
                public void onPurchasesUpdated(@NotNull GinloBillingResult result, ArrayList<GinloPurchaseImpl> updatedPurchases) {
                    if(result.isSuccess()) {
                        if(updatedPurchases != null && updatedPurchases.size() > 0) {
                            for (GinloPurchaseImpl p : updatedPurchases) {
                                LogUtil.i(TAG, "onPurchasesUpdated: Saving purchase: " + p.getOriginalJson());
                                synchronized (this) {
                                    mNumberofPurchasesToSave++;
                                }
                                consumeNewPurchase(p);
                                savePurchaseToBackend(p);
                            }

                            Toast.makeText(PurchaseLicenseActivity.this, getString(R.string.purchase_licence_done_title), Toast.LENGTH_LONG).show();
                            ListView listView = findViewById(R.id.purchase_licence_listview);
                            listView.setVisibility(View.GONE);
                        }
                    } else {
                        LogUtil.w(TAG, "onPurchasesUpdated returned error: " +
                                result.getResponseCode() + " (" + result.geResponsetMessage() + ")");
                        DialogBuilderUtil.buildErrorDialog(PurchaseLicenseActivity.this, "Error").show();
                    }
                }
            });
        }
    }

    @Override
    protected void onActivityPostLoginResult(int requestCode,
                                             int resultCode,
                                             Intent data) {
        super.onActivityPostLoginResult(requestCode, resultCode, data);

        if (requestCode == DELETE_ACCOUNT_RESULT_CODE) {
            if (resultCode == RESULT_OK) {
                final Intent intent = new Intent(this, DeleteAccountActivity.class);
                startActivity(intent);
            }
        }
    }

    private void startDeleteAccountRequest() {
        final Intent intent = new Intent(this, RuntimeConfig.getClassUtil().getLoginActivityClass());
        intent.putExtra(LoginActivity.EXTRA_MODE, LoginActivity.EXTRA_MODE_CHECK_PW);
        startActivityForResult(intent, PurchaseLicenseActivity.DELETE_ACCOUNT_RESULT_CODE);
    }

    public void handleDeleteAccountClick(final View view) {
        startDeleteAccountRequest();
    }
}
