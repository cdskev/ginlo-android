// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintManager.AuthenticationCallback;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.DialogFragment;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import javax.crypto.Cipher;

public class FingerprintFragment extends DialogFragment implements View.OnClickListener {
    public static final int MODE_LOGIN = 1;
    public static final int MODE_AUTH = 2;

    private CancellationSignal mCancellationSignal;
    private int mMode;
    private AuthenticationListener mAuthListener;

    public static FingerprintFragment newInstance(final int mode, @NonNull final AuthenticationListener listener) {
        FingerprintFragment fpf = new FingerprintFragment();
        fpf.mMode = mode;
        fpf.mAuthListener = listener;

        return fpf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Do not create a new Fragment when the Activity is re-created such as orientation changes.
        setRetainInstance(true);
        mCancellationSignal = new CancellationSignal();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fingerprint_auth, container, false);

        final Context context = getContext();
        if (context == null) {
            FingerprintFragment.this.dismiss();
            return view;
        }

        final TextView hintText = view.findViewById(R.id.fragment_fingerprint_hint_textview);

        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            FingerprintFragment.this.dismiss();
            return view;
        }

        AppCompatTextView titleTV = view.findViewById(R.id.fragment_fingerprint_title);
        AppCompatTextView descTV = view.findViewById(R.id.fragment_fingerprint_descrition);
        view.findViewById(R.id.fragment_fingerprint_cancel).setOnClickListener(this);

        switch (mMode) {
            case MODE_AUTH: {
                titleTV.setText(R.string.dialog_fingerprint_auth_title);
                descTV.setText(R.string.dialog_fingerprint_auth_hint);
                break;
            }
            case MODE_LOGIN:
            default: {
                titleTV.setText(R.string.dialog_fingerprint_title);
                descTV.setText(R.string.dialog_fingerprint_hint);
            }
        }

        startFingerprintAuth(hintText, context);

        return view;
    }

    public void onCancel(DialogInterface dialog) {
        if (mAuthListener != null) {
            mAuthListener.onAuthenticationCancelled();
        }
        super.onCancel(dialog);
    }

    @SuppressLint("NewApi")
    private void startFingerprintAuth(@NonNull final TextView hintText, @NonNull Context context) {
        Context appContext = context.getApplicationContext();

        if (!(appContext instanceof SimsMeApplication)) {
            FingerprintFragment.this.dismiss();
            return;
        }

        final SimsMeApplication application = (SimsMeApplication) appContext;

        final FingerprintManager fingerprintManager = context.getSystemService(FingerprintManager.class);
        if (fingerprintManager == null) {
            hintText.setText(context.getText(R.string.dialog_fingerprint_init_error));
            hintText.setTextColor(ColorUtil.getInstance().getLowColor(application));
            return;
        }

        try {
            Cipher cipher;
            if (mMode == MODE_AUTH) {
                cipher = SecurityUtil.getEncryptCipherForBiometricAuthKey(true);
            } else {
                cipher = application.getKeyController().getDecryptCipherFromBiometricKey();
            }

            final FingerprintManager.CryptoObject cryptoObject = new FingerprintManager.CryptoObject(cipher);

            AuthenticationCallback callback = new AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errMsgId, CharSequence errString) {
                    hintText.setText(errString);
                    hintText.setTextColor(ColorUtil.getInstance().getLowColor(application));
                }

                @Override
                public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                    hintText.setText(helpString);
                    hintText.setTextColor(ColorUtil.getInstance().getMainContrast50Color(application));
                }

                @Override
                public void onAuthenticationFailed() {
                    hintText.setText(application.getResources().getString(R.string.dialog_fingerprint_auth_failed));
                    hintText.setTextColor(ColorUtil.getInstance().getLowColor(application));
                }

                @Override
                public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                    FingerprintFragment.this.dismiss();
                    if (result.getCryptoObject() == null || result.getCryptoObject().getCipher() == null) {
                        hintText.setText(application.getResources().getString(R.string.dialog_fingerprint_auth_failed));
                        hintText.setTextColor(ColorUtil.getInstance().getLowColor(application));
                        return;
                    }

                    if (mAuthListener != null) {
                        mAuthListener.onAuthenticationSucceeded(result.getCryptoObject().getCipher());
                    }
                    mCancellationSignal.cancel();
                }
            };
            fingerprintManager.authenticate(cryptoObject, mCancellationSignal, 0 /* flags */, callback, null);
        } catch (LocalizedException e) {
            if (LocalizedException.ANDROID_BIOMETRIC_KEY_INVALIDATED.equals(e.getIdentifier())) {
                String errDetails = context.getString(R.string.fingerprint_error_invalidated);
                hintText.setText(errDetails);
                hintText.setTextColor(ColorUtil.getInstance().getLowColor(application));
                application.getAccountController().disableBiometricAuthentication(null);
            } else {
                String errMsg = context.getString(R.string.dialog_fingerprint_init_error);
                String errDetails = context.getString(R.string.dialog_fingerprint_init_error_detail, errMsg, e.getIdentifier());
                hintText.setText(errDetails);
                hintText.setTextColor(ColorUtil.getInstance().getLowColor(application));
            }
        }
    }

    public void cancelListening() {
        mCancellationSignal.cancel();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.fragment_fingerprint_cancel) {
            mAuthListener.onAuthenticationCancelled();
        }
    }

    public interface AuthenticationListener {
        void onAuthenticationSucceeded(final Cipher cipher);

        void onAuthenticationCancelled();
    }
}
