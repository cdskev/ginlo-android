// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber;

public class PhoneNumberUtil {
    private static String[] gAllCountryNames;

    private static String[] gAllCountryCodes;

    public static String normalizePhoneNumberNew(final Context context,
                                                 final String aCountryCode,
                                                 String number) {
        if (eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(number)) {
            return number;
        }

        final String countryCode;
        if (eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(aCountryCode)) {
            countryCode = getCountryCodeForDevice(context);
        } else {
            countryCode = aCountryCode;
        }

        if (number.startsWith("00")) {
            number = "+" + number.substring(2);
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < number.length(); i++) {
            char ch = number.charAt(i);

            if (ch == '+') {
                sb.append(ch);
            }
            if ((ch >= '0') && (ch <= '9')) {
                sb.append(ch);
            }
        }
        number = sb.toString();
        if (!number.startsWith("+")) {
            if (number.startsWith("0")) {
                number = countryCode + number.substring(1);
            } else {
                number = countryCode + number;
            }
        }

        String[] allCountryCodes = getCountryCodes(context);

        // +49 0361 4407 korrigieren
        for (String allCountryCode : allCountryCodes) {
            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(aCountryCode) && number.startsWith(allCountryCode)) {
                String tempNumber = number.substring((allCountryCode.length()));

                if (tempNumber.startsWith("0")) {
                    number = normalizePhoneNumberNew(context, allCountryCode, tempNumber);
                }
                break;
            }
        }
        return number;
    }

    private static String[] getCountryCodes(final Context context) {
        if (gAllCountryCodes == null || gAllCountryNames == null) {
            createCountryCodesAndNames(context);
        }
        return gAllCountryCodes;
    }

    private static void createCountryCodesAndNames(final Context context) {
        final String[] tempArray = context.getResources().getStringArray(R.array.intro_country_codes);
        final String[] allCountryCodes;
        final String[] allCountryNames;

        if (RuntimeConfig.isTestCountryEnabled()) {
            allCountryCodes = new String[tempArray.length];
            allCountryNames = new String[tempArray.length];
            for (int i = 0; i < tempArray.length; i++) {
                final String[] temp = tempArray[i].split(",");

                allCountryCodes[i] = "+" + temp[0];
                allCountryNames[i] = temp[1];
            }
        } else {
            allCountryCodes = new String[tempArray.length - 1];
            allCountryNames = new String[tempArray.length - 1];
            for (int i = 1; i < tempArray.length; i++) {
                final String[] temp = tempArray[i].split(",");

                allCountryCodes[i - 1] = "+" + temp[0];
                allCountryNames[i - 1] = temp[1];
            }
        }
        gAllCountryCodes = allCountryCodes;
        gAllCountryNames = allCountryNames;
    }

    public static boolean isNormalizedPhoneNumber(final String phoneNumber) {
        return !eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(phoneNumber) && phoneNumber.startsWith("+");
    }

    private static String getCountryCodeForDevice(final Context context) {

        final String country = context.getResources().getConfiguration().locale.getCountry();
        int index = 0;
        getCountryCodes(context);
        if (!StringUtil.isNullOrEmpty(country)) {

            for (int i = 0; i < gAllCountryNames.length; i++) {
                if (gAllCountryNames[i].trim().equals(country)) {
                    index = i;
                    break;
                }
            }
        }
        return gAllCountryCodes[index];
    }

    public static String getCountryCodeForPhoneNumber(final String phoneNumber)
    {
        String countryCode = "";
        com.google.i18n.phonenumbers.PhoneNumberUtil phoneUtil = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
        try {
            Phonenumber.PhoneNumber numberProto = phoneUtil.parse(phoneNumber, "");

            countryCode = "+" + String.valueOf(numberProto.getCountryCode());
            boolean isValid = phoneUtil.isValidNumber(numberProto);
        } catch (NumberParseException e) {
            System.err.println("NumberParseException was thrown: " + e.toString());
        }
        return countryCode;
    }

}
