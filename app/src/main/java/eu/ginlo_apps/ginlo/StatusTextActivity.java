// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;
import org.jetbrains.annotations.NotNull;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.TextExtensionsKt;
import eu.ginlo_apps.ginlo.activity.profile.ProfileActivityBase;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.StatusTextController;
import eu.ginlo_apps.ginlo.controller.contracts.UpdateAccountInfoCallback;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerCallback;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerFragment;
import eu.ginlo_apps.ginlo.greendao.StatusText;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.FragmentUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.ArrayList;

public class StatusTextActivity
        extends BaseActivity
        implements EmojiPickerCallback {

    public static final String EXTRA_UPDATED_STATUS  = "updatedStatus";
    public static final String EXTRA_CURRENT_STATUS = "currentStatus";

    private AccountController mAccountController;

    private ArrayAdapter mStatusTextAdapter;

    private CheckBox mAddEmojiButton;

    private EditText mStatusEditText;

    private EmojiPickerFragment mEmojiconsFragment;

    private boolean mEmojiIsShown;

    private FrameLayout mEmojiContainer;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {

        StatusTextController statusTextController = getSimsMeApplication().getStatusTextController();
        mAccountController = getSimsMeApplication().getAccountController();

        mStatusEditText = findViewById(R.id.status_text_edit_text);
        final Intent callerIntent = getIntent();
        mStatusEditText.setText(callerIntent.getStringExtra(EXTRA_CURRENT_STATUS));

        if (RuntimeConfig.isBAMandant()) {
            mStatusEditText.getBackground().setColorFilter(getResources().getColor(R.color.app_accent), Mode.SRC_ATOP);
        }
        mAddEmojiButton = findViewById(R.id.status_text_check_box_add_emoji_status);

        mEmojiContainer = findViewById(R.id.status_text_frame_layout_emoji_container);

        initEmojiListener();

        final ListView list = findViewById(R.id.status_texts_list_view);
        final ArrayList<StatusText> items = statusTextController.getAllStatusTexts();

        mStatusTextAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        list.setAdapter(mStatusTextAdapter);
            list.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent,
                                    final View view,
                                    final int position,
                                    final long id) {
                try {
                    final StatusText item = items.get(position);
                    if (item != null) {
                        final String statusText = item.getText();
                        mStatusEditText.setText(statusText);
                    }
                } catch (final LocalizedException e) {
                    Toast.makeText(StatusTextActivity.this, R.string.settings_profile_set_status_failed,
                    Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_status_text;
    }

    @Override
    protected void onResumeActivity() {
    }

    @Override
    public void onBackPressed() {
        if (mEmojiIsShown) {
            mAddEmojiButton.setChecked(false);
        } else {
            super.onBackPressed();
        }
    }

    private void initEmojiListener() {
        OnCheckedChangeListener onCheckedChangeListener = new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton button,
                                         final boolean isChecked) {
                if (isChecked) {
                    if(mEmojiconsFragment == null)
                        mEmojiconsFragment = new EmojiPickerFragment();
                    KeyboardUtil.toggleSoftInputKeyboard(StatusTextActivity.this, mStatusEditText, false);
                    FragmentUtil.toggleFragment(getSupportFragmentManager(), mEmojiconsFragment,
                            R.id.status_text_frame_layout_emoji_container, true);
                    mEmojiContainer.setVisibility(View.VISIBLE);
                    mEmojiIsShown = true;
                } else {
                    FragmentUtil.toggleFragment(getSupportFragmentManager(), mEmojiconsFragment,
                            R.id.status_text_frame_layout_emoji_container, false);
                    if (mStatusEditText.hasFocus()) {
                        KeyboardUtil.toggleSoftInputKeyboard(StatusTextActivity.this, mStatusEditText, true);
                    }
                    mEmojiContainer.setVisibility(View.GONE);
                    mEmojiIsShown = false;
                }
            }
        };

        final OnClickListener clickListener = new OnClickListener() {
            @Override
            public void onClick(final View v) {
                mAddEmojiButton.setChecked(false);
            }
        };

        final OnFocusChangeListener focusChangeListener = new OnFocusChangeListener() {
            @Override
            public void onFocusChange(final View v,
                                      final boolean hasFocus) {
                if (hasFocus) {
                    mAddEmojiButton.setChecked(false);
                }
            }
        };
        mStatusEditText.setOnClickListener(clickListener);
        mStatusEditText.setOnFocusChangeListener(focusChangeListener);
        mAddEmojiButton.setOnCheckedChangeListener(onCheckedChangeListener);
    }

    @Override
    public void onEmojiSelected(@NotNull String unicode) {
        TextExtensionsKt.appendText(mStatusEditText, unicode);
    }

    @Override
    public void onBackSpaceSelected() {
        TextExtensionsKt.backspace(mStatusEditText);
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mEmojiContainer != null) {
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mEmojiContainer.getLayoutParams().height = (int) getResources().getDimension(R.dimen.emoji_container_size_landscape);
            } else {
                mEmojiContainer.getLayoutParams().height = (int) getResources().getDimension(R.dimen.emoji_container_size_portrait);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_profile_activity_save, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if(menu != null)
        {
            MenuItem applyMenuId = menu.findItem(R.id.profile_activity_menu_apply);
            if(applyMenuId != null)
            {
                Drawable menuIcon = applyMenuId.getIcon();
                if(menuIcon != null)
                {
                    menuIcon.setColorFilter(
                            ColorUtil.getInstance().getMainContrast80Color(
                                    getSimsMeApplication()
                            ), Mode.SRC_ATOP
                    );
                }
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        KeyboardUtil.toggleSoftInputKeyboard(this, mStatusEditText, false);

        if(item.getItemId() == R.id.profile_activity_menu_apply) {
            Intent intent = new Intent();
            intent.putExtra(EXTRA_UPDATED_STATUS, mStatusEditText.getText());
            setResult(RESULT_OK, intent);
            finish();
        }
        else
            onBackPressed();

        return true;
    }
}
