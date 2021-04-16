// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentTransaction;
import android.view.View;
import android.widget.TextView;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.fragment.error.BaseErrorFragment;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ErrorActivity extends BaseActivity {
    public static final String TITLE = "ErrorActivity.Title";

    public static final String ERRORTEXT_RESOURCE = "ErrorActivity.ErrorTextResource";

    public static final String TEXTVIEW_1_TITLE_RESOURCE = "ErrorActivity.TextView1TitleResource";

    public static final String TEXTVIEW_1_TEXT_RESOURCE = "ErrorActivity.TextView1TextResource";

    public static final String TEXTVIEW_1_HINT_RESOURCE = "ErrorActivity.TextView1HintResource";

    public static final String TEXTVIEW_2_TITLE_RESOURCE = "ErrorActivity.TextView2TitleResource";

    public static final String TEXTVIEW_2_TEXT_RESOURCE = "ErrorActivity.TextView2TextResource";
    public static final String FRAGMENT_CLASS_NAME = "ErrorActivity.FragmentClassName";
    public static final String FRAGMENT_BUTTON_COLOR = "ErrorActivity.FragmentButtonColor";
    public static final String FRAGMENT_BUTTON_TEXT_COLOR = "ErrorActivity.FragmentButtonTextColor";
    private static final String TEXTVIEW_2_HINT_RESOURCE = "ErrorActivity.TextView2HintResource";

    private String mFragmentClassName;

    private BaseErrorFragment mFragment;

    private int mFragmentButtonColor;

    private int mFragmentButtonTextColor;

    /**
     * onCreateActivity
     */
    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        final View errorHeader = findViewById(R.id.activity_error_generic_header);
        final TextView errorText = findViewById(R.id.activity_error_generic_header_errortext);
        final TextView textView1Title = findViewById(R.id.activity_error_generic_text1_title);
        final TextView textView1Text = findViewById(R.id.activity_error_generic_text1_text);
        final TextView textView1Hint = findViewById(R.id.activity_error_generic_text1_hint);
        final TextView textView2Title = findViewById(R.id.activity_error_generic_text2_title);
        final TextView textView2Text = findViewById(R.id.activity_error_generic_text2_text);
        final TextView textView2Hint = findViewById(R.id.activity_error_generic_text2_hint);

        final Intent intent = getIntent();
        if (intent != null) {
            final String title = intent.getStringExtra(TITLE);
            final String errorTextResource = intent.getStringExtra(ERRORTEXT_RESOURCE);
            final String textView1TitleResource = intent.getStringExtra(TEXTVIEW_1_TITLE_RESOURCE);
            final String textView1TextResource = intent.getStringExtra(TEXTVIEW_1_TEXT_RESOURCE);
            final String textView1HintResource = intent.getStringExtra(TEXTVIEW_1_HINT_RESOURCE);
            final String textView2TitleResource = intent.getStringExtra(TEXTVIEW_2_TITLE_RESOURCE);
            final String textView2TextResource = intent.getStringExtra(TEXTVIEW_2_TEXT_RESOURCE);
            final String textView2HintResource = intent.getStringExtra(TEXTVIEW_2_HINT_RESOURCE);
            mFragmentButtonColor = intent.getIntExtra(FRAGMENT_BUTTON_COLOR, 0);
            mFragmentButtonTextColor = intent.getIntExtra(FRAGMENT_BUTTON_TEXT_COLOR, 0);

            fillTextView(errorText, errorTextResource);
            fillTextView(textView1Title, textView1TitleResource);
            fillTextView(textView1Text, textView1TextResource);
            fillTextView(textView1Hint, textView1HintResource);
            fillTextView(textView2Title, textView2TitleResource);
            fillTextView(textView2Text, textView2TextResource);
            fillTextView(textView2Hint, textView2HintResource);

            errorHeader.setBackgroundColor(mFragmentButtonColor);

            mFragmentClassName = intent.getStringExtra(FRAGMENT_CLASS_NAME);

            if (!StringUtil.isNullOrEmpty(title)) {
                setTitle(title);
            }
        }

        if (mFragmentClassName == null) {
            finish();
            return;
        }

        final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        try {
            final Class<?> fragementClass = Class.forName(mFragmentClassName);

            final Constructor<?>[] constructors = fragementClass.getConstructors();
            if (constructors.length == 0) {
                finish();
                return;
            }
            final Constructor<?> constructor = constructors[0];
            if (constructor == null) {
                finish();
                return;
            }

            mFragment = (BaseErrorFragment) constructor.newInstance();
            if (mFragment == null) {
                finish();
                return;
            }

            if (mFragmentButtonColor == 0 || mFragmentButtonTextColor == 0) {
                mFragment.setButtonColor(getResources().getColor(R.color.button_border_text_color));
            } else {
                mFragment.setButtonColor(mFragmentButtonColor);
                mFragment.setButtonTextColor(mFragmentButtonTextColor);
            }
            transaction.add(R.id.activity_error_generic_fragment_container, mFragment);
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            transaction.commit();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            finish();
        }
    }

    /**
     * onPause
     */
    @Override
    public void onPause() {
        super.onPause();
    }

    /**
     * onButton1Click
     *
     * @param view view
     */
    public void onButton1Click(final View view) {
        if (mFragment != null) {
            mFragment.onButton1Click(view);
        }
    }

    /**
     * onButton2Click
     *
     * @param view view
     */
    public void onButton2Click(final View view) {
        if (mFragment != null) {
            mFragment.onButton2Click(view);
        }
    }

    /**
     * getActivityLayout
     *
     * @return ActivityLayoutId
     */
    @Override
    protected int getActivityLayout() {
        return R.layout.activity_error_generic;
    }

    @Override
    protected void onResumeActivity() {
        //do nothing
    }

    /**
     * fills the textview
     *
     * @param textView textView
     * @param text     text
     */
    private void fillTextView(final TextView textView, final String text) {
        if (textView == null || StringUtil.isNullOrEmpty(text)) {
            return;
        }

        if (!StringUtil.isNullOrEmpty(text)) {
            textView.setText(text);
            textView.setVisibility(View.VISIBLE);
        }
    }
}
