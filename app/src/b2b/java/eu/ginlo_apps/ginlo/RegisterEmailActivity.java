// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import eu.ginlo_apps.ginlo.context.SimsMeApplicationBusiness;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.contracts.PhoneOrEmailActionListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.RegisterEmailFragment;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by SGA on 25.10.2016.
 */

public class RegisterEmailActivity
        extends BaseActivity {

    public static final String EXTRA_PREFILLED_FIRST_NAME = "RegisterEmailActivity.prefilledFirstname";

    public static final String EXTRA_PREFILLED_LAST_NAME = "RegisterEmailActivity.prefilledLastname";

    public static final String EXTRA_PREFILLED_EMAIL_ADDRESS = "RegisterEmailActivity.prefilledEmailAddress";

    public static final String EXTRA_FIRST_RUN = "RegisterEmailActivity.ExtraFirstRun";

    public static final String EXTRA_RUN_AFTER_REGISTRATION = "RegisterEmailActivity.ExtraRunAfterRegistration";

    private static final boolean PAGE_TYPE_ENTER_NAME = false;

    private static final boolean PAGE_TYPE_ENTER_MAIL = true;

    private boolean mMode;

    private String mFirstname;

    private String mLastname;

    private String mEmailAddress;

    private LinearLayout mErrorView;

    private ArrayList<RegisterEmailFragment> mFragments;

    private ViewPager mPager;

    private AccountController mAccountController;

    private boolean mAllowFreeMailer;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        mAccountController = getSimsMeApplication().getAccountController();

        try {
            mAllowFreeMailer = StringUtil.isNullOrEmpty(getSimsMeApplication().getContactController().getOwnContact().getDomain());
            if (mAccountController.needEmailRegistrationForManaging()) {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                }
                mMode = PAGE_TYPE_ENTER_MAIL;
            } else {
                mMode = PAGE_TYPE_ENTER_NAME;
            }

            final String prefilledEmailAddress;
            final Intent intent = getIntent();
            if (intent.hasExtra(EXTRA_PREFILLED_EMAIL_ADDRESS)) {
                prefilledEmailAddress = intent.getStringExtra(EXTRA_PREFILLED_EMAIL_ADDRESS);
                if (!StringUtil.isNullOrEmpty(prefilledEmailAddress)) {
                    mMode = PAGE_TYPE_ENTER_MAIL;
                }
            } else {
                prefilledEmailAddress = null;
            }

            if (intent.hasExtra(EXTRA_PREFILLED_FIRST_NAME) &&
                    intent.hasExtra(EXTRA_PREFILLED_LAST_NAME)) {
                mFirstname = intent.getStringExtra(EXTRA_PREFILLED_FIRST_NAME);
                mLastname = intent.getStringExtra(EXTRA_PREFILLED_LAST_NAME);

                if (!StringUtil.isNullOrEmpty(mFirstname) && !StringUtil.isNullOrEmpty(mLastname)) {
                    mMode = PAGE_TYPE_ENTER_MAIL;
                }
            }

            mFragments = new ArrayList<>();

            mPager = findViewById(R.id.register_email_pager);

            final RegisterEmailFragment fragment2 = new RegisterEmailFragment();

            fragment2.init(getResources().getString(R.string.register_email_address_text_enter_email),
                    getResources().getString(R.string.register_email_address_enter_email_hint),
                    null,
                    getResources().getString(R.string.register_email_address_text_enter_email2),
                    true,
                    getResources().getString(R.string.register_email_address_button_get_code),
                    prefilledEmailAddress
            );

            if (mMode != PAGE_TYPE_ENTER_MAIL) {
                final RegisterEmailFragment fragment1 = new RegisterEmailFragment();
                fragment1.init(getResources().getString(R.string.register_email_address_text_enter_names),
                        getResources().getString(R.string.register_email_address_enter_firstname_hint),
                        getResources().getString(R.string.register_email_address_enter_lastname_hint),
                        getResources().getString(R.string.register_email_address_text_enter_names2),
                        false,
                        getResources().getString(R.string.register_email_address_button_continue),
                        null
                );
                mFragments.add(fragment1);
            } else {
                if (!mAccountController.isDeviceManaged()) {
                    fragment2.showRemoveButton();
                }
            }
            mFragments.add(fragment2);

            // SGA moechte nicht extra dafuer eine eigene Klasse bauen...
            final FragmentPagerAdapter adapter = new FragmentPagerAdapter(getSupportFragmentManager()) {
                @Override
                public int getCount() {
                    return mFragments.size();
                }

                @Override
                public Fragment getItem(final int position) {
                    return mFragments.get(position);
                }

            };

            mPager.setAdapter(adapter);
            mErrorView = findViewById(R.id.register_email_top_warning);

            final Intent callerIntent = getIntent();
            if (callerIntent.hasExtra(EXTRA_FIRST_RUN) && callerIntent.getBooleanExtra(EXTRA_FIRST_RUN, false)) {
                DialogInterface.OnClickListener positiveListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        requestPermission(PermissionUtil.PERMISSION_FOR_READ_CONTACTS, R.string.permission_rationale_contacts, new PermissionUtil.PermissionResultCallback() {
                            @Override
                            public void permissionResult(int permission, boolean permissionGranted) {

                                boolean hasPerm = permission == PermissionUtil.PERMISSION_FOR_READ_CONTACTS && permissionGranted;
                                if (hasPerm) {
                                    Toast.makeText(RegisterEmailActivity.this, R.string.chat_overview_wait_hint_loading2, Toast.LENGTH_SHORT).show();
                                }

                                //setOverlay(false);
                                final ContactController contactController = ((SimsMeApplicationBusiness) getApplication()).getContactController();
                                contactController.syncContacts(null, false, hasPerm);
                            }
                        });
                    }
                };

                DialogInterface.OnClickListener negativeListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //
                    }
                };

                DialogBuilderUtil.buildResponseDialog(this,
                        getString(R.string.sync_phonebook_contacts_request),
                        null,
                        getString(R.string.general_yes),
                        getString(R.string.general_no),
                        positiveListener,
                        negativeListener
                ).show();
            }
        } catch (final LocalizedException e) {
            finish();
        }

    }

    @Override
    public void onBackPressed() {
        try {
            if (mAccountController.needEmailRegistrationForManaging()) {
                //jetzt ist man auf seite zwei und solld a auch bleiben, bis man die mail erfolgreich eingebene hat
                return;
            }
        } catch (final LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            return;
        }

        if (mMode == PAGE_TYPE_ENTER_MAIL && mFragments.size() > 1) {
            mPager.setCurrentItem(0, true);
            mMode = PAGE_TYPE_ENTER_NAME;
            mFirstname = null;
            mLastname = null;
            mEmailAddress = null;

        } else {
            super.onBackPressed();
        }
    }

    public void handleNextClick(View v) {
        if (mMode == PAGE_TYPE_ENTER_NAME) {
            RegisterEmailFragment nameFragment = mFragments.get(0);
            String firstname = nameFragment.getTextFromEditText1();
            String lastname = nameFragment.getTextFromEditText2();

            if (!StringUtil.isNullOrEmpty(firstname) &&
                    !StringUtil.isNullOrEmpty(lastname)) {
                mFirstname = firstname;
                mLastname = lastname;
                mPager.setCurrentItem(1, true);
                mMode = PAGE_TYPE_ENTER_MAIL;
            } else {
                DialogBuilderUtil.buildErrorDialog(this, getResources().getString(R.string.register_email_address_alert_name_empty)).show();

            }
        } else if (mMode == PAGE_TYPE_ENTER_MAIL) {
            RegisterEmailFragment mailFragment = mFragments.get(mFragments.size() - 1);

            final String editTextResult = mailFragment.getTextFromEditText1();
            if (!StringUtil.isNullOrEmpty(editTextResult)) {
                final String emailAddress = editTextResult.toLowerCase(Locale.US);

                if (StringUtil.isEmailValid(emailAddress)) {
                    mEmailAddress = emailAddress;

                    requestConfirmationMail(emailAddress, false);
                } else {
                    DialogBuilderUtil.buildErrorDialog(this, getResources().getString(R.string.register_email_address_alert_email_empty)).show();
                }
            } else {
                DialogBuilderUtil.buildErrorDialog(this, getResources().getString(R.string.register_email_address_alert_email_empty)).show();
            }
        }
    }

    private void showMailAlreadyUse(final String emailAdress) {
        DialogInterface.OnClickListener positiveListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                requestConfirmationMail(emailAdress, true);
            }
        };

        DialogBuilderUtil.buildResponseDialog(this,
                getString(R.string.register_email_address_mail_already_used),
                getString(R.string.std_warning),
                getString(R.string.next),
                getString(R.string.std_cancel),
                positiveListener,
                null
        ).show();
    }

    private void requestConfirmationMail(final String emailAddress, final boolean forceCreation) {
        final AccountController accountControllerBusiness = getSimsMeApplication().getAccountController();

        //falls der errorView vorher angezeigt wurde, blenden wir es zu Ueberpruefung erst einmal aus
        mErrorView.setVisibility(View.GONE);
        showIdleDialog(-1);

        final PhoneOrEmailActionListener listenerRequestConfirmMail = new PhoneOrEmailActionListener() {
            //FIXME diese methode wird unter Umstaenden 3 mal aufgerufen
            private boolean onCloseCalled = false;

            @Override
            public void onSuccess(String result) {
                dismissIdleDialog();
                //weiterleiten zur eingabe
                try {
                    Contact ownContact = getSimsMeApplication().getContactController().getOwnContact();
                    getSimsMeApplication().getContactController().saveContactInformation(ownContact, mLastname, mFirstname, null, null, null, null, null, null, -1, false);
                } catch (LocalizedException e) {
                    DialogBuilderUtil.buildErrorDialog(RegisterEmailActivity.this, getString(R.string.service_tryAgainLater)).show();
                }

                String text = String.format(getResources().getString(R.string.dialog_email_activiation_code_sent_text),
                        mEmailAddress);

                DialogBuilderUtil.OnCloseListener onCloseListener = new DialogBuilderUtil.OnCloseListener() {
                    @Override
                    public void onClose(int ref) {
                        if (!onCloseCalled) {
                            onCloseCalled = true;
                            Intent intent = new Intent(RegisterEmailActivity.this, EnterEmailActivationCodeActivity.class);

                            final Intent callerIntent = getIntent();
                            if (callerIntent.hasExtra(EXTRA_RUN_AFTER_REGISTRATION) && callerIntent.getBooleanExtra(EXTRA_RUN_AFTER_REGISTRATION, false)) {
                                intent.putExtra(EXTRA_RUN_AFTER_REGISTRATION, true);
                            }
                            startActivity(intent);
                            finish();
                        }

                    }
                };

                DialogBuilderUtil.buildErrorDialog(RegisterEmailActivity.this, text, 0, onCloseListener).show();

            }

            @Override
            public void onFail(String errorMsg, boolean emailIsInUse) {
                if (StringUtil.isEqual(errorMsg, "ERR-0124")) {
                    mErrorView.setVisibility(View.VISIBLE);
                } else if (emailIsInUse) {
                    showMailAlreadyUse(emailAddress);
                } else {
                    DialogBuilderUtil.buildErrorDialog(RegisterEmailActivity.this, errorMsg).show();
                }
                dismissIdleDialog();

            }
        };

        IBackendService.OnBackendResponseListener listenerValidateMail = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.msgException != null) {
                    // FreeMailer downgrade
                    if (LocalizedException.BLACKLISTED_EMAIL_DOMAIN.equals(response.msgException.getIdent())) {
                        if (mAllowFreeMailer) {
                            accountControllerBusiness.requestConfirmationMail(mEmailAddress, listenerRequestConfirmMail, forceCreation);
                        } else {
                            DialogInterface.OnClickListener positiveListener = new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    showIdleDialog(-1);
                                    mAllowFreeMailer = true;
                                    accountControllerBusiness.requestConfirmationMail(mEmailAddress, listenerRequestConfirmMail, forceCreation);
                                }
                            };

                            DialogBuilderUtil.buildResponseDialog(RegisterEmailActivity.this,
                                    getString(R.string.register_email_address_mail_freemailer),
                                    getString(R.string.std_warning),
                                    getString(R.string.next),
                                    getString(R.string.std_cancel),
                                    positiveListener,
                                    null
                            ).show();
                            dismissIdleDialog();
                        }
                    } else {
                        String errorMsg = response.errorMessage;
                        DialogBuilderUtil.buildErrorDialog(RegisterEmailActivity.this, errorMsg).show();
                        dismissIdleDialog();
                    }

                } else {
                    accountControllerBusiness.requestConfirmationMail(mEmailAddress, listenerRequestConfirmMail, forceCreation);
                }
            }
        };

        BackendService.withAsyncConnection(getSimsMeApplication())
                .validateMail(mEmailAddress, true, listenerValidateMail);

    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_register_email;
    }

    @Override
    protected void onResumeActivity() {
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        if (mEmailAddress != null) {
            bundle.putString("mEmailAddress", mEmailAddress);
        }
        if (mFirstname != null) {
            bundle.putString("mFirstname", mFirstname);
        }

        if (mLastname != null) {
            bundle.putString("mLastname", mLastname);
        }

        super.onSaveInstanceState(bundle);
    }

    public void handleRemoveEmailClick(final View view) {
        final PhoneOrEmailActionListener phoneOrEmailActionListener = new PhoneOrEmailActionListener() {
            @Override
            public void onSuccess(final String result) {
                dismissIdleDialog();
                Toast.makeText(getSimsMeApplication(), getResources().getString(R.string.remove_email_address_success), Toast.LENGTH_SHORT).show();

                //E-Mail entfernt --> Passtoken neu generieren
                getSimsMeApplication().getPreferencesController().checkRecoveryCodeToBeSet(true);

                final Intent intent = new Intent(RegisterEmailActivity.this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
            }

            @Override
            public void onFail(final String errorMsg, boolean emailIsInUse) {
                dismissIdleDialog();
                DialogBuilderUtil.buildErrorDialog(RegisterEmailActivity.this, errorMsg).show();
            }
        };

        final String title = getResources().getString(R.string.remove_email_address_title);
        final String text = getResources().getString(R.string.remove_email_address_text);
        final String yes = getResources().getString(R.string.remove_email_address_yes);
        final String cancel = getResources().getString(R.string.std_cancel);

        final DialogInterface.OnClickListener positiveClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                showIdleDialog();
                mAccountController.removeConfirmedMail(phoneOrEmailActionListener);
            }
        };

        final DialogInterface.OnClickListener negativeClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                // nix zu tun
            }
        };
        DialogBuilderUtil.buildResponseDialog(RegisterEmailActivity.this, text, title, yes, cancel, positiveClickListener, negativeClickListener).show();
    }
}
