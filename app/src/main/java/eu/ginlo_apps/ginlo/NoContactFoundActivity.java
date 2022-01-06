// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.UseCases.InviteFriendUseCase;
import eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivity;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import javax.inject.Inject;

public class NoContactFoundActivity extends BaseActivity {

    public static final String TAG = NoContactFoundActivity.class.getSimpleName();
    public static final String SEARCH_TYPE = "NoContactFoundActivity.searchType";
    public static final String SEARCH_VALUE = "NoContactFoundActivity.searchValue";

    private ContactUtil.SearchType mSearchType;
    private String mSearchValue;

    @Inject
    public Router router;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {

        final Intent intent = getIntent();
        if (intent == null || !intent.hasExtra(SEARCH_TYPE) || !intent.hasExtra(SEARCH_VALUE)) {
            finish();
            return;
        }

        final ContactUtil.SearchType searchType = (ContactUtil.SearchType) intent.getSerializableExtra(SEARCH_TYPE);
        final String searchValue = intent.getStringExtra(SEARCH_VALUE);

        mSearchType = searchType;
        mSearchValue = searchValue;

        final TextView textView = findViewById(R.id.no_contact_found_textview);

        switch (searchType) {
            case PHONE:
                textView.setText(getString(R.string.search_contact_no_phone_found, searchValue));
                break;
            case EMAIL:
                textView.setText(getString(R.string.search_contact_no_email_found, searchValue));
                break;
            case SIMSME_ID:
                textView.setText(getString(R.string.search_contact_no_simsmeid_found, searchValue));
                break;
        }
    }

    @Override
    public void onBackPressed() {
        LogUtil.d(TAG, "onBackPressed: Starting  SearchContactActivity ...");
        final Intent intent = new Intent(NoContactFoundActivity.this, SearchContactActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_contact_not_found;
    }

    @Override
    protected void onResumeActivity() {

    }

    public void onInviteClick(final View v) {
        InviteFriendUseCase inviteFriendUseCase = new InviteFriendUseCase();
        inviteFriendUseCase.execute(this);
    }
}
