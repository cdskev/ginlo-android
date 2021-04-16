// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.DeleteAccountActivity;
import eu.ginlo_apps.ginlo.LoginActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.adapter.PurchaseAdapter;
import eu.ginlo_apps.ginlo.billing.IabHelper;
import eu.ginlo_apps.ginlo.billing.IabResult;
import eu.ginlo_apps.ginlo.billing.Inventory;
import eu.ginlo_apps.ginlo.billing.Purchase;
import eu.ginlo_apps.ginlo.billing.SkuDetails;
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
import javax.inject.Inject;
import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class PurchaseLicenseActivity extends BaseActivity {
    private final static String TAG = PurchaseLicenseActivity.class.getSimpleName();
    private static final int DELETE_ACCOUNT_RESULT_CODE = 500;
    public static final String EXTRA_DONT_FORWARD_TO_OVERVIEW = "PurchaseLicenseActivity.extraDontForwardIfLicenceIsAboutToExpire";

    private AccountController mAccountController;

    private JsonArray mProducts = null;
    private List<String> mProductIds = null;
    private IabHelper mIabHelper = null;
    private boolean mDontForwardIfLicenceIsAboutToExpire;
    private int mNumberofPurchasesToSave;
    private List<String> mMonthlyProductIds = null;

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
            LogUtil.e(TAG, e.toString(), e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mIabHelper == null) {
            mIabHelper = new IabHelper(this, RuntimeConfig.getApplicationPublicKey());
        }

        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG).show();
            return;
        }

        final IabHelper.QueryInventoryFinishedListener inventoryFinishedListener = new IabHelper.QueryInventoryFinishedListener() {
            @Override
            public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                boolean bDismissIdle = true;
                try {

                    if (result.isFailure()) {
                        LogUtil.w(TAG, "onQueryInventoryFinished returned with error: " + result.getMessage());
                        DialogBuilderUtil.buildErrorDialog(PurchaseLicenseActivity.this, result.getMessage()).show();
                        return;
                    }

                    if (inv == null) {
                        LogUtil.w(TAG, "onQueryInventoryFinished returned with zero inventory!");
                        DialogBuilderUtil.buildErrorDialog(PurchaseLicenseActivity.this, getString(R.string.connecting_playstore_failed)).show();
                        return;
                    }

                    // Initialize products
                    List<PurchaseItemModel> purchaseItemModels = new ArrayList<>();
                    for (int i = 0; i < mProductIds.size(); i++) {
                        String productId = mProductIds.get(i);
                        SkuDetails details = inv.getSkuDetails(productId);
                        if (details != null) {
                            LogUtil.i(TAG, "Parsing PlayStore inventory found: " + details);
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
                    purchaseAdapter.notifyDataSetChanged();
                    setDynamicHeight(listView, 0);

                    // Vorhandene Käufe ermitteln
                    List<Purchase> allPurchases = inv.getAllPurchases();
                    List<Purchase> purchaseToSend = new ArrayList<>();
                    for (int i = 0; i < allPurchases.size(); i++) {
                        Purchase p = allPurchases.get(i);
                        LogUtil.i(TAG, "Existing purchase: " + p.getOriginalJson());

                        if (!getSimsMeApplication().getPreferencesController().isPurchaseSaved(p)) {
                            synchronized (this) {
                                mNumberofPurchasesToSave++;
                            }
                            bDismissIdle = false;
                            purchaseToSend.add(p);
                        }
                    }
                    for (int i = 0; i < purchaseToSend.size(); i++) {
                        savePurchaseToBackend(purchaseToSend.get(i));
                    }

                } finally {
                    if (bDismissIdle) {
                        dismissIdleDialog();
                    }
                }
            }
        };

        final IabHelper.OnIabSetupFinishedListener purchaseInitListener = new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                try {
                    if (result.isFailure()) {
                        LogUtil.w(TAG, "onIabSetupFinished returned with error: " + result.getMessage());
                        DialogBuilderUtil.buildErrorDialog(PurchaseLicenseActivity.this, result.getMessage()).show();
                        return;
                    }

                    List<String> productIds = new ArrayList<>();
                    mMonthlyProductIds = new ArrayList<>();
                    // Aus der Liste der Produkte auslesen
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
                        String feature = product.get("feature").getAsString();
                        if (!feature.equalsIgnoreCase("usage")) {
                            continue;
                        }
                        String os = product.get("os").getAsString();
                        if (os.equalsIgnoreCase("Android")) {
                            String productId = product.get("productId").getAsString();
                            String duration = null;
                            if (product.has("duration")) {
                                duration = product.get("duration").getAsString();
                                try {

                                    if (Long.parseLong(duration) > 0 && Long.parseLong(duration) < 40) {
                                        mMonthlyProductIds.add(productId);
                                    }
                                } catch (NumberFormatException ignored) {
                                }
                            }
                            productIds.add(productId);
                            LogUtil.i(TAG, "Parsing products found: " + productId);
                        }
                    }
                    mProductIds = productIds;
                    LogUtil.i(TAG, "Query Google Play");
                    mIabHelper.queryInventoryAsync(true, productIds, inventoryFinishedListener);
                } finally {
                    dismissIdleDialog();
                }
            }
        };

        final IBackendService.OnBackendResponseListener productResponseListener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                boolean bDismissIdle = true;
                try {
                    if (response.errorMessage != null) {
                        LogUtil.w(TAG, "Got error from backend: " + response.errorMessage);
                        DialogBuilderUtil.buildErrorDialog(PurchaseLicenseActivity.this, response.errorMessage).show();
                    }
                    if (response.jsonArray != null) {
                        mProducts = response.jsonArray;
                        LogUtil.i(TAG, "Got products: " + mProducts.toString());
                        bDismissIdle = false;
                        mIabHelper.startSetup(purchaseInitListener);
                    }
                } finally {
                    if (bDismissIdle) {
                        dismissIdleDialog();
                    }
                }
            }
        };

        OnGetPurchasedProductsListener onRegisterVoucherListener = new OnGetPurchasedProductsListener() {
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
                    LogUtil.e(PurchaseLicenseActivity.this.getClass().getName(), "getHasLicence failed", e);
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

        if (getSimsMeApplication() != null && getSimsMeApplication().getLoginController().isLoggedIn()) {
            LogUtil.i(TAG, "Loading licensed product");

            mAccountController.getPurchasedProducts(onRegisterVoucherListener);
            showIdleDialog(R.string.dialog_licence_check);
        }
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

    private void savePurchaseToBackend(final Purchase info) {
        LogUtil.i(TAG, "Sending purchase token to backend: " + info.getToken());
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

    public void handleBuyClick(final View view) {
        if (view.getTag() != null && view.getTag() instanceof PurchaseItemModel) {
            final PurchaseItemModel pim = (PurchaseItemModel) view.getTag();
            LogUtil.i(TAG, "Start Purchase Flow");

            IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
                @Override
                public void onIabPurchaseFinished(IabResult result, Purchase info) {
                    LogUtil.i(TAG, "Purchase flow finished with " + result.toString());
                    if (info != null) {
                        LogUtil.i(TAG, "Purchase info: " + info.getOriginalJson());
                    }
                    if (result.isFailure()) {
                        LogUtil.w(TAG, "onIabPurchaseFinished returned error: " + result.getMessage());
                        DialogBuilderUtil.buildErrorDialog(PurchaseLicenseActivity.this, result.getMessage()).show();
                    } else {
                        synchronized (this) {
                            mNumberofPurchasesToSave++;
                        }
                        savePurchaseToBackend(info);
                    }
                }
            };

            int uniqueId = new SecureRandom().nextInt() & 0x0000ffff;
            // KS: Subscription? I think, ginlo license is in-app product!
            //mIabHelper.launchSubscriptionPurchaseFlow(this, pim.getSkuDetail().getSku(), uniqueId, purchaseFinishedListener, mAccountController.getAccount().getAccountGuid());
            mIabHelper.launchPurchaseFlow(this, pim.getSkuDetail().getSku(), uniqueId, purchaseFinishedListener, mAccountController.getAccount().getAccountGuid());
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
            return;
        }

        if (mIabHelper == null) {
            return;
        }

        LogUtil.i(TAG, "Purchase result: " + IabHelper.getResponseDesc(resultCode));

        // Pass on the activity result to the helper for handling
        if (!mIabHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
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
