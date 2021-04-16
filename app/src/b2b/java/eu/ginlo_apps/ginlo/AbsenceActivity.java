// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import androidx.appcompat.widget.SwitchCompat;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.contracts.UpdateAccountInfoCallback;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.Calendar;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class AbsenceActivity extends BaseActivity {
    private AccountController mAccountController;

    private SwitchCompat mAbsenceSwitch;

    private EditText mMessageEditText;

    private TextView mHintTextView;

    private TextView mDateTextView;

    private TextView mMessageHeaderTextView;

    private TextView mMessageCharCountertView;

    private View mAbsenceDateContainer;

    private int mYear;

    private int mMonth;

    private int mDay;

    private long mAbsenceTime;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        try {
            mAccountController = getSimsMeApplication().getAccountController();

            mMessageEditText = findViewById(R.id.absence_edittext);
            mAbsenceSwitch = findViewById(R.id.absence_switch);

            mHintTextView = findViewById(R.id.absence_hint_textview);
            mDateTextView = findViewById(R.id.absence_date_textview);

            mAbsenceDateContainer = findViewById(R.id.absence_date_container);

            mMessageHeaderTextView = findViewById(R.id.absence_message_header);
            mMessageCharCountertView = findViewById(R.id.absence_message_counter);

            final Contact ownContact = getSimsMeApplication().getContactController().getOwnContact();
            final boolean isAbsent;

            mAbsenceTime = DateUtil.utcWithoutMillisStringToMillis(ownContact.getAbsenceTimeUtcString());

            mMessageEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    mMessageCharCountertView.setText(String.format(getResources().getString(R.string.absence_char_counter),
                            s.length(),
                            getResources().getInteger(R.integer.profile_status_max_length)));
                }
            });

            final String oooStatusJsonAsString = ownContact.getOooStatus();

            if (oooStatusJsonAsString != null) {
                final JsonObject jsonObject = new JsonParser().parse(oooStatusJsonAsString).getAsJsonObject();

                isAbsent = JsonUtil.hasKey(JsonConstants.OOO_STATUS_STATE, jsonObject) &&
                        JsonConstants.OOO_STATUS_STATE_OOO.equals(jsonObject.get(JsonConstants.OOO_STATUS_STATE).getAsString());

                // time
                if (JsonUtil.hasKey(JsonConstants.OOO_STATUS_STATE_VALID, jsonObject)) {
                    final String dateString = jsonObject.get(JsonConstants.OOO_STATUS_STATE_VALID).getAsString();
                    mAbsenceTime = DateUtil.utcWithoutMillisStringToMillis(dateString);
                    if (mAbsenceTime != 0) {
                        mDateTextView.setText(DateUtil.getDateAndTimeStringFromMillis(mAbsenceTime));
                    } else {
                        mDateTextView.setText(getResources().getString(R.string.absence_notset));
                    }
                } else {
                    mDateTextView.setText(getResources().getString(R.string.absence_notset));
                }

                if (JsonUtil.hasKey(JsonConstants.OOO_STATUS_TEXT, jsonObject)) {
                    mMessageEditText.setText(jsonObject.get(JsonConstants.OOO_STATUS_TEXT).getAsString());
                } else {
                    mMessageEditText.setText("");
                }

                mAbsenceSwitch.setChecked(isAbsent);
            } else {
                mAbsenceSwitch.setChecked(false);
                mDateTextView.setText(getResources().getString(R.string.absence_notset));
                mMessageEditText.setText("");
            }

            mAbsenceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                    if (isChecked) {
                        enableElements();
                    } else {
                        disableElements();
                    }
                }
            });

            final View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    handleSaveAbsenceStatusClick();
                }
            };

            setRightActionBarImage(R.drawable.ic_done_white_24dp, onClickListener, getResources().getString(R.string.apply_changes), -1);
        } catch (final LocalizedException e) {
            LogUtil.w(AbsenceActivity.this.getClass().getSimpleName(), e.getMessage(), e);
            finish();
        }
    }

    /**
     * Elemente aktivieren
     */
    private void enableElements() {
        mHintTextView.setEnabled(true);
        mDateTextView.setEnabled(true);
        mMessageHeaderTextView.setEnabled(true);
        mMessageCharCountertView.setEnabled(true);
        mMessageEditText.setEnabled(true);
        mAbsenceDateContainer.setClickable(true);
    }

    /**
     * Elemente deaktivieren
     */
    private void disableElements() {
        mHintTextView.setEnabled(false);
        mDateTextView.setEnabled(false);
        mMessageHeaderTextView.setEnabled(false);
        mMessageCharCountertView.setEnabled(false);
        mMessageEditText.setEnabled(false);
        mAbsenceDateContainer.setClickable(false);
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_absence;
    }

    @Override
    protected void onResumeActivity() {
        try {
            final boolean isAbsent = getSimsMeApplication().getContactController().getOwnContact().isAbsent();
            if (isAbsent) {
                enableElements();
            } else {
                disableElements();

                final String statusText = mAccountController.getAccount().getStatusText();
                if (StringUtil.isNullOrEmpty(statusText)) {
                    if (mAbsenceTime == 0) {
                        mMessageEditText.setText(getResources().getString(R.string.absence_text_default2));
                    } else {
                        mMessageEditText.setText(String.format(getResources().getString(R.string.absence_text_default2), DateUtil.getDateAndTimeStringFromMillis(mAbsenceTime)));
                    }
                } else {
                    mMessageEditText.setText(statusText);
                }
            }
        } catch (final LocalizedException e) {
            LogUtil.w(AbsenceActivity.this.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    public void handleDatePickerClick(final View v) {
        try {
            final Calendar calendar = Calendar.getInstance();

            final String absenceTimeString = getSimsMeApplication().getContactController().getOwnContact().getAbsenceTimeUtcString();

            final Date now = new Date();
            if (StringUtil.isNullOrEmpty(absenceTimeString)) {
                calendar.setTime(now);
            } else {
                final Date date = DateUtil.utcStringToDate(absenceTimeString);
                if (date == null || date.getTime() < now.getTime()) {
                    calendar.setTime(now);
                } else {
                    calendar.setTime(date);
                }
            }

            final int year = calendar.get(Calendar.YEAR);
            final int month = calendar.get(Calendar.MONTH);
            final int day = calendar.get(Calendar.DAY_OF_MONTH);
            final int hour = calendar.get(Calendar.HOUR_OF_DAY);
            final int minute = calendar.get(Calendar.MINUTE);

            final DatePickerDialog.OnDateSetListener onDateSetListener = new DatePickerDialog.OnDateSetListener() {
                @Override
                public void onDateSet(final DatePicker view,
                                      final int year,
                                      final int month,
                                      final int dayOfMonth) {
                    mDay = dayOfMonth;
                    mMonth = month;
                    mYear = year;
                    openTimePicker(hour, minute);
                }
            };

            final DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                    ColorUtil.getInstance().getAlertDialogStyle(getSimsMeApplication()),
                    onDateSetListener, year, month, day);
            datePickerDialog.show();

        } catch (final LocalizedException e) {
            LogUtil.w(AbsenceActivity.this.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private void openTimePicker(final int hour, final int minute) {
        final TimePickerDialog.OnTimeSetListener onTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(final TimePicker view,
                                  final int hourOfDay,
                                  final int minute) {
                final Calendar calendar = Calendar.getInstance();
                calendar.set(mYear, mMonth, mDay, hourOfDay, minute);

                mAbsenceTime = calendar.getTime().getTime();
                mDateTextView.setText(DateUtil.getDateAndTimeStringFromMillis(mAbsenceTime));
            }
        };

        new TimePickerDialog(AbsenceActivity.this,
                ColorUtil.getInstance().getAlertDialogStyle(getSimsMeApplication()),
                onTimeSetListener,
                hour,
                minute,
                true).show();
    }

    private void handleSaveAbsenceStatusClick() {
        try {
            final Account account = mAccountController.getAccount();

            final long absenceTimeOld;

            final boolean absenceStateOld;
            final boolean checked = mAbsenceSwitch.isChecked();

            final String statusText = mMessageEditText.getText().toString();
            final String statusTextOld;

            final String oooStatusJsonAsString = getSimsMeApplication().getContactController().getOwnContact().getOooStatus();
            if (!StringUtil.isNullOrEmpty(oooStatusJsonAsString)) {
                final JsonObject jsonObject = new JsonParser().parse(oooStatusJsonAsString).getAsJsonObject();

                absenceStateOld = JsonUtil.hasKey(JsonConstants.OOO_STATUS_STATE, jsonObject) &&
                        JsonConstants.OOO_STATUS_STATE_OOO.equals(jsonObject.get(JsonConstants.OOO_STATUS_STATE).getAsString());

                // time
                if (JsonUtil.hasKey(JsonConstants.OOO_STATUS_STATE_VALID, jsonObject)) {
                    final String dateString = jsonObject.get(JsonConstants.OOO_STATUS_STATE_VALID).getAsString();
                    absenceTimeOld = DateUtil.utcWithoutMillisStringToMillis(dateString);
                } else {
                    absenceTimeOld = 0;
                }

                if (JsonUtil.hasKey(JsonConstants.OOO_STATUS_TEXT, jsonObject) && JsonUtil.hasKey(JsonConstants.OOO_STATUS_TEXT_IV, jsonObject)) {
                    statusTextOld = jsonObject.get(JsonConstants.OOO_STATUS_TEXT).getAsString();
                } else {
                    statusTextOld = null;
                }
            } else {
                statusTextOld = null;
                absenceTimeOld = 0;
                absenceStateOld = false;
            }

            // wenn sich Status oder Zeit geaendert hat
            if (checked != absenceStateOld
                    || absenceTimeOld != mAbsenceTime
                    || !StringUtil.isEqual(statusTextOld, statusText)) {

                if (checked && StringUtil.isNullOrEmpty(statusText)) {
                    DialogBuilderUtil.buildErrorDialog(this, getResources().getString(R.string.profile_set_status_empty)).show();
                } else if (checked && new Date().getTime() >= mAbsenceTime) {
                    DialogBuilderUtil.buildErrorDialog(this, getResources().getString(R.string.chats_selfdestruction_invalid_date)).show();
                } else {
                    final JsonObject encryptetOooStatus = new JsonObject();

                    encryptetOooStatus.addProperty(JsonConstants.OOO_STATUS_STATE_VALID, DateUtil.dateToUtcStringWithoutMillis(new Date(mAbsenceTime)));
                    if (checked) {
                        encryptetOooStatus.addProperty(JsonConstants.OOO_STATUS_STATE, JsonConstants.OOO_STATUS_STATE_OOO);
                    } else {
                        encryptetOooStatus.addProperty(JsonConstants.OOO_STATUS_STATE, JsonConstants.OOO_STATUS_STATE_AVAILABLE);
                    }

                    final IvParameterSpec iv = SecurityUtil.generateIV();
                    final String ivBase64 = android.util.Base64.encodeToString(iv.getIV(), android.util.Base64.NO_WRAP);

                    final SecretKey aesKeyFromBase64String = SecurityUtil.getAESKeyFromBase64String(account.getAccountInfosAesKey());
                    final byte[] bytes = SecurityUtil.encryptStringWithAES(statusText, aesKeyFromBase64String, iv);

                    encryptetOooStatus.addProperty(JsonConstants.OOO_STATUS_TEXT_IV, ivBase64);
                    encryptetOooStatus.addProperty(JsonConstants.OOO_STATUS_TEXT, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));

                    final JsonObject oooStatus = new JsonObject();
                    if (checked) {
                        oooStatus.addProperty(JsonConstants.OOO_STATUS_STATE, JsonConstants.OOO_STATUS_STATE_OOO);
                    } else {
                        oooStatus.addProperty(JsonConstants.OOO_STATUS_STATE, JsonConstants.OOO_STATUS_STATE_AVAILABLE);
                    }
                    oooStatus.addProperty(JsonConstants.OOO_STATUS_TEXT, statusText);
                    oooStatus.addProperty(JsonConstants.OOO_STATUS_STATE_VALID, DateUtil.dateToUtcStringWithoutMillis(new Date(mAbsenceTime)));

                    final UpdateAccountInfoCallback updateAccountInfoCallback = new UpdateAccountInfoCallback() {
                        @Override
                        public void updateAccountInfoFinished() {
                            dismissIdleDialog();
                            finish();
                        }

                        @Override
                        public void updateAccountInfoFailed(final String error) {
                            dismissIdleDialog();
                            DialogBuilderUtil.buildErrorDialog(AbsenceActivity.this, getResources().getString(R.string.absence_set_failed)).show();
                        }
                    };

                    showIdleDialog();
                    //TODO private index
                    mAccountController.updateAccountInfo(null,
                            statusText,
                            null,
                            null,
                            null,
                            null,
                            oooStatus.toString(),
                            encryptetOooStatus.toString(),
                            false,
                            updateAccountInfoCallback);
                }
            } else {
                finish();
            }
        } catch (final LocalizedException e) {
            LogUtil.w(AbsenceActivity.this.getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}
