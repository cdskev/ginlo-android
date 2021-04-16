// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment;

import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.RegisterEmailActivity;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class RegisterEmailFragment
        extends Fragment {
    private String mHintTop;

    private String mHintBottom;

    private String mText1;

    private String mText2;

    private EditText mEditText1;

    private EditText mEditText2;

    private boolean mSetEditText1Email;

    private String mButtonText;

    private String mTextPrefilled;

    private boolean mShowRemoveButton;

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        final LinearLayout layoutRoot = (LinearLayout) ViewExtensionsKt.themedInflate(inflater, getContext(), R.layout.fragment_register_email, container, false);

        final FragmentActivity activity = getActivity();

        final TextView textView1 = layoutRoot.findViewById(R.id.register_email_textview_1);
        mEditText1 = layoutRoot.findViewById(R.id.register_email_edittext_1);
        mEditText2 = layoutRoot.findViewById(R.id.register_email_edittext_2);
        final TextView textView2 = layoutRoot.findViewById(R.id.register_email_textview_2);
        final Button button = layoutRoot.findViewById(R.id.register_email_button);

        if (activity != null) {
            final SimsMeApplication application = (SimsMeApplication) activity.getApplication();

            try {

                final String emailAddress = application.getContactController().getOwnContact().getEmail();
                if (!StringUtil.isNullOrEmpty(emailAddress) && (mText2 != null || mShowRemoveButton)) //2. fragment
                {
                    final View removeEmailButton = layoutRoot.findViewById(R.id.remove_email_address_button);
                    removeEmailButton.setVisibility(View.VISIBLE);
                }
            } catch (final LocalizedException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }
        }

        if (mTextPrefilled != null) {
            mEditText1.setText(mTextPrefilled);
            mEditText1.setEnabled(false);

            //TODO das hier ist nicht perfekt geloest. ueber prefilled wird geprueft, ob die Email-adresse vorgegeben ist, das bringt einige aenderungen im layout mit sich.
            // das ist aber so ueber den Parameternamen nicht unbedingt nachvollziehbar
            final View header = layoutRoot.findViewById(R.id.register_email_header);
            header.setVisibility(View.VISIBLE);
            textView1.setVisibility(View.GONE);
        } else {
            if (mHintTop != null) {
                textView1.setHint(mHintTop);
            }
            if (mText1 != null) {
                mEditText1.setHint(mText1);
            }
        }

        if (mText2 != null) {
            mEditText2.setHint(mText2);
            mEditText2.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(final View v, final int keyCode, final KeyEvent event) {
                    if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                        ((RegisterEmailActivity) getActivity()).handleNextClick(button);
                        return true;
                    }
                    return false;
                }
            });
        } else {
            mEditText2.setVisibility(View.GONE);
        }

        if (mHintBottom != null) {
            textView2.setHint(mHintBottom);
        }

        if (mSetEditText1Email) {
            mEditText1.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        }

        if (mButtonText != null) {
            button.setText(mButtonText);
        }

        mEditText1.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(final View v, final int keyCode, final KeyEvent event) {
                if (activity != null && (event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    if (mEditText2 != null && mEditText2.getVisibility() == View.VISIBLE) {
                        mEditText2.requestFocus();
                        KeyboardUtil.toggleSoftInputKeyboard(activity, mEditText2, true);
                        //TODO tastatur wird eingeklappt
                    } else {
                        ((RegisterEmailActivity) activity).handleNextClick(button);
                    }
                    return true;
                }
                return false;
            }
        });
        return layoutRoot;
    }

    /**
     * @param hintTop
     * @param text1
     * @param text2
     * @param hintBottom
     * @param setEditText2Email
     * @param buttonText
     * @param textPrefilled     Achtung: wenn prefilled != null -> aenderungen im layout
     */
    public void init(@NonNull final String hintTop,
                     @NonNull final String text1,
                     final String text2,
                     @NonNull final String hintBottom,
                     final boolean setEditText2Email,
                     @NonNull final String buttonText,
                     final String textPrefilled) {
        mHintTop = hintTop;
        mText1 = text1;
        mText2 = text2;
        mHintBottom = hintBottom;
        mSetEditText1Email = setEditText2Email;
        mButtonText = buttonText;
        mTextPrefilled = textPrefilled;
    }

    /**
     * getTextFromEditText1
     *
     * @return
     */
    public String getTextFromEditText1() {
        if (mEditText1 != null) {
            return mEditText1.getText().toString();
        } else {
            return null;
        }
    }

    /**
     * getTextFromEditText2
     *
     * @return
     */
    public String getTextFromEditText2() {
        if (mEditText2 != null) {
            return mEditText2.getText().toString();
        } else {
            return null;
        }
    }

    /**
     * loeschen-Button anzeigen
     */
    public void showRemoveButton() {
        mShowRemoveButton = true;
    }

}
