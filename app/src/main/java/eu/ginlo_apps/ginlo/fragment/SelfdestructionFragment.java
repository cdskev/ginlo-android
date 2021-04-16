// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.fragment;

import android.app.Application;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.NumberPicker.OnValueChangeListener;
import android.widget.TextView;
import android.widget.TimePicker;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.tabs.TabLayout;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.adapter.PageAdapterItemInfo;
import eu.ginlo_apps.ginlo.adapter.SimsmeFragmentPagerAdapter;
import eu.ginlo_apps.ginlo.model.param.MessageDestructionParams;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.util.Calendar;
import java.util.Date;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class SelfdestructionFragment
        extends Fragment {

    public static final boolean PICKER_MODE_DESTRUCTION = false;

    public static final boolean PICKER_MODE_TIMER = true;

    private Boolean countDownSelected;

    private Date mDestructionDate;

    private boolean mMode;

    private OnDestructionValueChangedListener mOnDestructionValueChangedListener;

    private OnTimerChangedLister mOnTimerChangedLister;

    private int mDestructionCountDown;

    private TabLayout mTabLayout;

    private ViewPager mViewPager;

    private NumberPickerFragment mNumberPickerFragment;

    private DateTimePickerFragment mDateTimePickerFragment;

    private boolean mIsCreated = false;

    private static Application simsmeapplication = null;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        LinearLayout linearLayout = (LinearLayout) inflater.inflate(R.layout.selfdestruction_picker_layout, container,
                false);

        mTabLayout = linearLayout.findViewById(R.id.selfdestruction_picker_tab_layout);
        mViewPager = linearLayout.findViewById(R.id.selfdestruction_picker_viewpager);

        final FragmentActivity activity = getActivity();
         if (activity != null) {
             simsmeapplication = activity.getApplication();
         }

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            /**
             * @param position
             */
            @Override
            public void onPageSelected(int position) {
                if ((mMode == PICKER_MODE_DESTRUCTION)) {
                    countDownSelected = (position == 0);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        mTabLayout.setupWithViewPager(mViewPager);

        SimsmeFragmentPagerAdapter pagerAdapter = new SimsmeFragmentPagerAdapter(getChildFragmentManager());
        mViewPager.setAdapter(pagerAdapter);


        mNumberPickerFragment = new NumberPickerFragment();
        mDateTimePickerFragment = new DateTimePickerFragment();
        mDateTimePickerFragment.mMode = mMode;

        if (mOnDestructionValueChangedListener != null) {
            mNumberPickerFragment.setValueChangedListener(mOnDestructionValueChangedListener);
            mDateTimePickerFragment.setValueChangedListener(mOnDestructionValueChangedListener);
        }

        if (mOnTimerChangedLister != null) {
            mDateTimePickerFragment.setTimerChangedListener(mOnTimerChangedLister);
        }

        pagerAdapter.addNewFragment(new PageAdapterItemInfo(getString(R.string.chats_selfDestruction_countdown_title), mNumberPickerFragment));
        pagerAdapter.addNewFragment(new PageAdapterItemInfo(getString(R.string.chats_selfDestruction_date_title), mDateTimePickerFragment));

        if (mMode == PICKER_MODE_TIMER) {
            mTabLayout.setVisibility(View.GONE);
            mViewPager.setCurrentItem(1);
        } else {
            mTabLayout.setVisibility(View.VISIBLE);
        }

//      if(RuntimeConfig.isBAMandant())
//
//      {
//         final FragmentActivity activity = getActivity();
//         if (activity != null)
//         {
//            final Application application = activity.getApplication();
//            final ColorUtil colorUtil = RuntimeConfig.getClassUtil().getColorUtil();
//            final int mainContrastColor = colorUtil.getMainContrastColor(application);
//            final int mainColor = colorUtil.getMainColor(application);
//
//            mTabLayout.setSelectedTabIndicatorColor(colorUtil.getAppAccentColor(application));
//            mTabLayout.setTabTextColors(mainContrastColor, mainContrastColor);
//            mTabLayout.setBackgroundColor(mainColor);
//         }
//      }

        if (mDestructionCountDown != 0) {
            countDownSelected = true;
            mNumberPickerFragment.setNumberValue(mDestructionCountDown);
            setPickerVisibility();
        } else if (mDestructionDate != null) {
            countDownSelected = false;
            mDateTimePickerFragment.setDestructionDate(mDestructionDate);
            setPickerVisibility();
        } else {
            countDownSelected = true;
        }

        if (mDateTimePickerFragment != null) {
            final Date timerDate = mDateTimePickerFragment.getTimerDate();
            if (timerDate != null) {
                mDateTimePickerFragment.callOnTimerDateChanged(timerDate, false);
            }
        }

        drawUI();

        mIsCreated = true;
        return linearLayout;
    }

    private void setPickerVisibility() {

        SimsmeFragmentPagerAdapter pagerAdapter = (SimsmeFragmentPagerAdapter)mViewPager.getAdapter();
        if(pagerAdapter.getCount() <= 1)
            return;

        if (countDownSelected) {
            mViewPager.setCurrentItem(0);
        } else {
            mViewPager.setCurrentItem(1);
        }
    }

    private void setDateSet(int year,
                            int month,
                            int day) {
        if (mDateTimePickerFragment != null) {
            mDateTimePickerFragment.setDateSet(year, month, day);
        }
    }

    /**
     * @return
     */
    private boolean getMode() {
        return mMode;
    }

    /**
     * @param mode
     */
    public void setMode(final boolean mode) {
        mMode = mode;

        if (!mIsCreated) {
            return;
        }

        if (mDateTimePickerFragment != null) {
            mDateTimePickerFragment.setMode(mode);
        }

        if (mMode == PICKER_MODE_TIMER) {
            mTabLayout.setVisibility(View.GONE);
            mViewPager.setCurrentItem(1);
        } else {
            mTabLayout.setVisibility(View.VISIBLE);
        }

        drawUI();
    }

    private void drawUI() {
        if (mMode == PICKER_MODE_TIMER) {
            if (mDateTimePickerFragment == null || !mDateTimePickerFragment.isCreated()) {
                return;
            }

            boolean today;
            final Date now = new Date();

            final Date timerDate;
            if (mDateTimePickerFragment != null) {
                timerDate = mDateTimePickerFragment.getTimerDate();
            } else {
                timerDate = null;
            }

            if (timerDate != null && timerDate.after(now)) {
                today = mDateTimePickerFragment.checkAndSetDateText(timerDate.getTime());
            } else {
                mDateTimePickerFragment.setCurrentTime();
                today = true;
            }

            mDateTimePickerFragment.callOnTimerDateChanged(mDateTimePickerFragment.getTimerDate(), today);
        } else {
            if (mViewPager.getCurrentItem() == 0) {
                if (mOnDestructionValueChangedListener != null) {
                    mOnDestructionValueChangedListener.onPickerChanged(mNumberPickerFragment.getNumberValue());
                }
            } else {
                if (mDateTimePickerFragment == null || !mDateTimePickerFragment.isCreated()) {
                    return;
                }

                final Date destructionDate = mDateTimePickerFragment.getDestructionDate();

                final boolean today = destructionDate == null ? true : mDateTimePickerFragment.checkAndSetDateText(destructionDate.getTime());

                mDateTimePickerFragment.callOnDestructionDateChanged(destructionDate == null ? new Date() : destructionDate, today);
            }
        }
    }

    /**
     * @param listener
     */
    public void setOnDestructionValueChangedListener(final OnDestructionValueChangedListener listener) {
        mOnDestructionValueChangedListener = listener;

        if (mDateTimePickerFragment != null) {
            mDateTimePickerFragment.setValueChangedListener(listener);
        }

        if (mNumberPickerFragment != null) {
            mNumberPickerFragment.setValueChangedListener(listener);
        }
    }

    /**
     * @param listener
     */
    public void setOnTimerChangedLister(final OnTimerChangedLister listener) {
        mOnTimerChangedLister = listener;

        if (mDateTimePickerFragment != null) {
            mDateTimePickerFragment.setTimerChangedListener(listener);
        }
    }

    private void setTimeSet(int hour,
                            int minutes) {
        if (mDateTimePickerFragment != null) {
            mDateTimePickerFragment.setTimeSet(hour, minutes);
        }
    }

    public MessageDestructionParams getDestructionConfiguration() {
        return new MessageDestructionParams(countDownSelected ? mNumberPickerFragment.getNumberValue() : null,
                countDownSelected ? null : mDateTimePickerFragment.getDestructionDate());
    }

    /**
     * @return
     */
    private Date getDestructionDate() {
        if (mDateTimePickerFragment != null) {
            if (mDateTimePickerFragment.getDestructionDate() != null) {
                return mDateTimePickerFragment.getDestructionDate();
            } else {
                mDateTimePickerFragment.setDestructionDate(DateUtil.utcStringToDate(DateUtil.getCurrentDate()));
                return mDateTimePickerFragment.getDestructionDate();
            }
        }
        return mDestructionDate;
    }

    /**
     * @param params
     */
    public void setDestructionParams(final MessageDestructionParams params) {
        if (params.countdown != null) {
            mDestructionCountDown = params.countdown;

            if (mNumberPickerFragment != null) {
                mNumberPickerFragment.setNumberValue(mDestructionCountDown);
            }
        } else if (params.date != null) {
            mDestructionDate = params.date;
            if (mDateTimePickerFragment != null) {
                mDateTimePickerFragment.setDestructionDate(mDestructionDate);
            }
        }
    }

    /**
     * @return
     */
    public Date getTimerDate() {
        if (mDateTimePickerFragment != null) {
            return mDateTimePickerFragment.getTimerDate();
        }
        return null;
    }

    public void setTimerDate(final Date date) {
        if (date != null && mDateTimePickerFragment != null) {
            mDateTimePickerFragment.setTimerDate(date);
        }
    }

    public interface OnDestructionValueChangedListener {
        void onDateChanged(final String date);

        void onPickerChanged(final int value);
    }

    public interface OnTimerChangedLister {
        void onDateChanged(final String date);
    }

    /**
     * @author Florian
     * @version $Revision$, $Date$, $Author$
     */
    public static class DatePickerFragment
            extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {

        private SelfdestructionFragment mFragment;

        /**
         * Fragmente muessen eine Konstruktor ohne Argumente haben
         */
        public DatePickerFragment() {
        }

        public void setFragment(SelfdestructionFragment fragment) {
            mFragment = fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current date as the default date in the picker
            final Calendar c = Calendar.getInstance();
            if (mFragment.getMode() == PICKER_MODE_TIMER) {
                c.setTime(mFragment.getTimerDate());
            } else {
                c.setTime(mFragment.getDestructionDate());
            }

            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            // Create a new instance of DatePickerDialog and return it
            return new DatePickerDialog(getActivity(), ColorUtil.getInstance().getAlertDialogStyle(simsmeapplication), this, year, month, day);
        }

        public void onDateSet(DatePicker view,
                              int year,
                              int month,
                              int day) {
            mFragment.setDateSet(year, month, day);
        }
    }

    /**
     * @author Florian
     * @version $Revision$, $Date$, $Author$
     */
    public static class TimePickerFragment
            extends DialogFragment
            implements TimePickerDialog.OnTimeSetListener {

        private SelfdestructionFragment mFragment;

        public TimePickerFragment() {
        }

        public void setFragment(SelfdestructionFragment fragment) {
            mFragment = fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current time as the default values for the picker
            final Calendar c = Calendar.getInstance();

            if (mFragment.getMode() == PICKER_MODE_TIMER) {
                c.setTime(mFragment.getTimerDate());
            } else {
                c.setTime(mFragment.getDestructionDate());
            }

            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);

            // Create a new instance of TimePickerDialog and return it
            return new TimePickerDialog(getActivity(), ColorUtil.getInstance().getAlertDialogStyle(simsmeapplication), this, hour, minute, DateFormat.is24HourFormat(getActivity()));
        }

        public void onTimeSet(TimePicker view,
                              int hourOfDay,
                              int minute) {
            mFragment.setTimeSet(hourOfDay, minute);
        }
    }

    public static class NumberPickerFragment extends Fragment {
        private NumberPicker numberPicker;
        private TextView secondTextView;
        private int numberPickerValue = -1;

        private OnDestructionValueChangedListener onValueChangedListener;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View root = ViewExtensionsKt.themedInflate(inflater, getContext(), R.layout.fragment_number_picker, container, false);

            numberPicker = root.findViewById(R.id.selfdestruction_picker_number_picker);
            numberPicker.setMinValue(1);
            numberPicker.setMaxValue(60);

            if (numberPickerValue > -1) {
                numberPicker.setValue(numberPickerValue);
            }

            secondTextView = root.findViewById(R.id.selfdestruction_picker_text_view_seconds);

            numberPicker.setOnValueChangedListener(new OnValueChangeListener() {
                @Override
                public void onValueChange(NumberPicker picker,
                                          int oldVal,
                                          int newVal) {

                    String second;
                    if (newVal < 2) {
                        second = getString(R.string.chats_selfdestruction_countdown_second);
                    } else {
                        second = getString(R.string.chats_selfdestruction_countdown_seconds);
                    }

                    secondTextView.setText(second);

                    if (onValueChangedListener != null) {
                        onValueChangedListener.onPickerChanged(newVal);
                    }
                }
            });

            return root;
        }

        int getNumberValue() {
            if (numberPicker != null) {
                return numberPicker.getValue();
            }

            return numberPickerValue;
        }

        void setNumberValue(final int value) {
            numberPickerValue = value;
            if (numberPicker != null) {
                numberPicker.setValue(value);
            }
        }

        void setValueChangedListener(final OnDestructionValueChangedListener listener) {
            onValueChangedListener = listener;
        }
    }

    public static class DateTimePickerFragment extends Fragment {
        private TextView dateTextView;

        private TextView timeTextView;

        private Date mDestructDate;

        private Date mTimerDate;

        private int mYear;

        private int mMonth;

        private int mDay;

        private int mHour;

        private int mMinutes;

        private boolean mMode;

        private OnTimerChangedLister mTimerChangedLister;

        private OnDestructionValueChangedListener onValueChangedListener;
        private boolean wasCreated;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View root = ViewExtensionsKt.themedInflate(inflater, getContext(), R.layout.fragment_date_picker, container, false);

            dateTextView = root.findViewById(R.id.selfdestruction_picker_text_date_view);
            timeTextView = root.findViewById(R.id.selfdestruction_picker_text_time_view);

            if (mDestructDate != null) {
                final boolean today = checkAndSetDateText(mDestructDate.getTime());

                callOnDestructionDateChanged(mDestructDate, today);
            }

            if (mTimerDate != null) {
                final boolean today = checkAndSetDateText(mTimerDate.getTime());
                callOnTimerDateChanged(mTimerDate, today);
            } else {
                setCurrentTime();
            }
            wasCreated = true;
            return root;
        }

        void setCurrentTime() {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MINUTE, 1);
            mDay = calendar.get(Calendar.DAY_OF_MONTH);
            mMonth = calendar.get(Calendar.MONTH);
            mYear = calendar.get(Calendar.YEAR);

            mHour = calendar.get(Calendar.HOUR_OF_DAY);
            mMinutes = calendar.get(Calendar.MINUTE);

            if (dateTextView != null) {
                setDateAndTimeViewText();
            }
        }

        void setDateAndTimeViewText() {
            Calendar calendar = Calendar.getInstance();

            calendar.set(mYear, mMonth, mDay, mHour, mMinutes, 0);

            final Date date = calendar.getTime();

            final boolean today = checkAndSetDateText(date.getTime());
            timeTextView.setText(DateUtil.getTimeStringFromMillis(date.getTime()));

            if (mMode == PICKER_MODE_TIMER) {
                mTimerDate = date;
                callOnTimerDateChanged(date, today);
            } else {
                mDestructDate = date;

                callOnDestructionDateChanged(date, today);
            }
        }

        /**
         * @param value
         * @return return if "today" is used
         */
        boolean checkAndSetDateText(final long value) {
            final String today = DateUtil.getDateStringFromMillis(new Date().getTime());
            final String date = DateUtil.getDateStringFromMillis(value);

            if (dateTextView != null) {
                if (StringUtil.isEqual(today, date)) {
                    dateTextView.setText(getContext().getResources().getString(R.string.chat_overview_date_today));
                    return true;
                } else {
                    dateTextView.setText(date);
                    return false;
                }
            } else {
                return StringUtil.isEqual(today, date);
            }
        }

        void setMode(boolean mode) {
            mMode = mode;
        }

        Date getTimerDate() {
            return mTimerDate;
        }

        void setTimerDate(Date timerDate) {
            mTimerDate = timerDate;
        }

        Date getDestructionDate() {
            return mDestructDate;
        }

        void setDestructionDate(Date destructionDate) {
            mDestructDate = destructionDate;
        }

        void setTimeSet(int hour,
                        int minutes) {
            mHour = hour;
            mMinutes = minutes;

            setDateAndTimeViewText();
        }

        void setDateSet(int year,
                        int month,
                        int day) {
            mYear = year;
            mMonth = month;
            mDay = day;
            setDateAndTimeViewText();
        }

        void setTimerChangedListener(OnTimerChangedLister listener) {
            mTimerChangedLister = listener;
        }

        void setValueChangedListener(final OnDestructionValueChangedListener listener) {
            onValueChangedListener = listener;
        }

        /**
         * @param date
         * @param today
         */
        void callOnTimerDateChanged(final Date date, final boolean today) {
            if (mTimerChangedLister != null) {
                if (today) {
                    mTimerChangedLister.onDateChanged(getContext().getResources().getString(R.string.chat_overview_date_today)
                            + " "
                            + DateUtil.getTimeStringFromMillis(date.getTime()));
                } else {
                    mTimerChangedLister.onDateChanged(DateUtil.getDateStringFromMillis(date.getTime())
                            + " "
                            + DateUtil.getTimeStringFromMillis(date.getTime()));
                }
            }
        }

        /**
         * @param date
         * @param today
         */
        private void callOnDestructionDateChanged(final Date date, final boolean today) {
            if (onValueChangedListener != null) {
                if (today) {
                    onValueChangedListener.onDateChanged(getContext().getResources().getString(R.string.chat_overview_date_today)
                            + " "
                            + DateUtil.getTimeStringFromMillis(date.getTime()));
                } else {
                    onValueChangedListener.onDateChanged(DateUtil.getDateStringFromMillis(date.getTime())
                            + " "
                            + DateUtil.getTimeStringFromMillis(date.getTime()));
                }
            }
        }

        boolean isCreated() {
            return wasCreated;
        }
    }
}
