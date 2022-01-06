// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.text.Editable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;
import android.util.Patterns;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import eu.ginlo_apps.ginlo.log.LogUtil;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {
    public static boolean isNullOrEmpty(@Nullable String testString) {
        if (testString == null) {
            return true;
        }
        return ("".equals(testString));
    }

    public static boolean isEqual(@Nullable String val1,
                                  @Nullable String val2) {
        if ((val1 == null) && (val2 == null)) {
            return true;
        }
        if ((val1 == null) || (val2 == null)) {
            return false;
        }
        return val1.equals(val2);
    }

    public static boolean isInList(String val1,
                                   List<String> val2,
                                   boolean ignoreCase) {
        if ((val2 == null) || (val1 == null)) {
            return false;
        }

        if (val2.size() == 0) {
            return false;
        }

        for (int i = 0; i < val2.size(); ++i) {
            if (ignoreCase) {
                if (val1.equalsIgnoreCase(val2.get(i))) {
                    return true;
                }
            } else {
                if (val1.equals(val2.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isInArray(String val1,
                                    String[] val2,
                                    boolean ignoreCase) {
        if ((val2 == null) || (val1 == null)) {
            return false;
        }

        if (val2.length == 0) {
            return false;
        }

        for (int i = 0; i < val2.length; ++i) {
            if (ignoreCase) {
                if (val1.equalsIgnoreCase(val2[i])) {
                    return true;
                }
            } else {
                if (val1.equals(val2[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    public static SpannableString replaceUrlNew(final String messageText,
                                                final String replacementText,
                                                final Pattern aPattern,
                                                final boolean replaceOnlyLastLink) {
        if (StringUtil.isNullOrEmpty(messageText)) {
            return null;
        }

        SpannableString ss = SpannableString.valueOf(messageText);
        return replaceUrlNew(ss, replacementText, aPattern, replaceOnlyLastLink);
    }

    /**
     * ersetzt eine URL durch einen Text
     */
    public static SpannableString replaceUrlNew(final SpannableString messageText,
                                                final String replacementText,
                                                final Pattern aPattern,
                                                final boolean replaceOnlyLastLink) {

        if (StringUtil.isNullOrEmpty(messageText.toString())) {
            return null;
        }

        SpannableString ss = SpannableString.valueOf(messageText);
        if (StringUtil.isNullOrEmpty(replacementText)) {
            return ss;
        }

        List<Hyperlink> links;

        if (replacementText.startsWith("[") && replacementText.endsWith("]")) {
            try {
                //pruefen, ob ueberhaupt ein json-array da ist
                final JsonParser parser = new JsonParser();
                final JsonElement jel = parser.parse(replacementText);
                final JsonArray jsonArray = jel.getAsJsonArray();

                links = gatherLinks(ss, aPattern, jsonArray, null);
            } catch (JsonParseException | IllegalStateException e) {
                links = gatherLinks(ss, aPattern, null, replacementText);
                LogUtil.w(StringUtil.class.getName(), e.getMessage(), e);
            }
        } else {
            links = gatherLinks(ss, aPattern, null, replacementText);
        }

        final int size = links.size();
        for (int i = 0; i < size; ++i) {
            final Hyperlink linkSpec = links.get(i);
            ss.setSpan(linkSpec.span, linkSpec.start, linkSpec.end, 0);
            final SpannableStringBuilder ssb = new SpannableStringBuilder();
            ssb.append(ss, 0, ss.length());

            if (!replaceOnlyLastLink || i == 0) {
                ssb.replace(linkSpec.start, linkSpec.end, linkSpec.replaceText);
            }
            ss = SpannableString.valueOf(ssb);
        }

        return ss;
    }

    private static List<Hyperlink> gatherLinks(SpannableString s, Pattern pattern, JsonArray replacementPattern, String replacementText) {
        List<Hyperlink> listOfLinks = new ArrayList<>();
        Matcher m;

        if (pattern == null) {
            m = Patterns.WEB_URL.matcher(s);
        } else {
            m = pattern.matcher(s);
        }

        while (m.find()) {
            String linkReplaceText = replacementText;
            int start = m.start();
            int end = m.end();

            Hyperlink link = new Hyperlink();
            link.span = new URLSpan(m.group());
            link.start = start;
            link.end = end;

            if (replacementPattern != null) {
                for (JsonElement jsonElement : replacementPattern) {
                    try {
                        String regex = jsonElement.getAsJsonObject().get("regex").getAsString();

                        // regex muss mit http starten, sonst wird der egsmate text ersetzt
                        if (!regex.startsWith("http")) {
                            regex = "http" + regex;
                        }

                        //preufen, ob die url den regex enthaellt
                        final Pattern linkPattern = Pattern.compile(regex);
                        final Matcher regExMatcher = linkPattern.matcher(m.group());

                        if (regExMatcher.matches()) {
                            // pattern ersetzen und span bauen (nutzen der bereits vorhandenen funktion)
                            linkReplaceText = jsonElement.getAsJsonObject().get("value").getAsString();
                            break;
                        }
                    } catch (Exception e) {
                        LogUtil.e(StringUtil.class.getName(), e.getMessage(), e);
                    }
                }
            }

            link.replaceText = linkReplaceText;

            listOfLinks.add(0, link);
        }

        return listOfLinks;
    }

    public static String getReadableByteCount(long bytes) {
        int unit = 1024;

        if (bytes < unit) {
            return bytes + "B";
        }

        int exp = (int) (Math.log(bytes) / Math.log(unit));

        char pre = "KMGTPE".charAt(exp - 1);

        return String.format("%.1f%cB", bytes / Math.pow(unit, exp), pre);
    }

    public static String getStringFromList(@NonNull String separator, @NonNull List<String> stringList) {
        StringBuilder stringBuilder = new StringBuilder();

        for (String stringFromList : stringList) {
            if (!StringUtil.isNullOrEmpty(stringFromList)) {
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(separator);
                }

                stringBuilder.append(stringFromList);
            }
        }

        return stringBuilder.toString();
    }

    public static String getStringFromCollection(@NonNull String separator, @NonNull Collection<String> stringSet) {
        StringBuilder stringBuilder = new StringBuilder();

        for (String stringFromList : stringSet) {
            if (!StringUtil.isNullOrEmpty(stringFromList)) {
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(separator);
                }

                stringBuilder.append(stringFromList);
            }
        }

        return stringBuilder.toString();
    }

    public static String getStringFromArray(@NonNull String separator, @NonNull String[] stringArray) {
        StringBuilder stringBuilder = new StringBuilder();

        for (String stringFromList : stringArray) {
            if (!StringUtil.isNullOrEmpty(stringFromList)) {
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(separator);
                }

                stringBuilder.append(stringFromList);
            }
        }

        return stringBuilder.toString();
    }

    public static int getSecurityLevel(Editable s) {
        if (getSecurityLevelforPattern(s, "^(?=.{9,}$)(?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*\\W).*$")) {
            return 8;
        }

        if ((getSecurityLevelforPattern(s, "^(?=.{9,}$)(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{9,}$)(?=.*\\d)(?=.*[a-z])(?=.*\\W).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{9,}$)(?=.*\\d)(?=.*[A-Z])(?=.*\\W).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{9,}$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\W).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{7,}$)(?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*\\W).*$"))) {
            return 7;
        }

        if ((getSecurityLevelforPattern(s, "^(?=.{9,}$)(?=.*\\d)(?=.*[a-z]).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{9,}$)(?=.*\\d)(?=.*\\W).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{9,}$)(?=.*\\d)(?=.*[A-Z]).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{9,}$)(?=.*[a-z])(?=.*\\W).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{9,}$)(?=.*[a-z])(?=.*[A-Z]).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{9,}$)(?=.*[A-Z])(?=.*\\W).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{7,8}$)(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{7,8}$)(?=.*\\d)(?=.*[a-z])(?=.*\\W).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{7,8}$)(?=.*\\d)(?=.*[A-Z])(?=.*\\W).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{7,8}$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\W).*$"))) {
            return 6;
        }

        if ((getSecurityLevelforPattern(s, "^(?=.{7,8}$)(?=.*\\d)(?=.*[a-z]).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{7,8}$)(?=.*\\d)(?=.*[A-Z]).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{7,8}$)(?=.*\\W)(?=.*[a-z]).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{7,8}$)(?=.*\\W)(?=.*[A-Z]).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{7,8}$)(?=.*\\d)(?=.*\\W).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{7,8}$)(?=.*[a-z])(?=.*[A-Z]).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{9,}$).*$"))) {
            return 5;
        }

        if ((getSecurityLevelforPattern(s, "^(?=.{4,6}$)(?=.*\\w)(?=.*\\W).*$"))
                || (getSecurityLevelforPattern(s, "^(?=.{7,}$).*$"))) {
            return 4;
        }

        if (getSecurityLevelforPattern(s, "^(?=.{4,6}$)(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).*$")) {
            return 3;
        }

        if (getSecurityLevelforPattern(s, "^.{4,6}$")) {
            return 2;
        }

        if (getSecurityLevelforPattern(s, "^.{1,3}$")) {
            return 1;
        }
        return 0;
    }

    private static boolean getSecurityLevelforPattern(Editable s,
                                                      String patternString) {
        Pattern p = Pattern.compile(patternString);
        Matcher m = p.matcher(s.toString());

        return m.find();
    }

    public static String generatePassword(int length) {
        String validChars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new SecureRandom();

        for (int i = 0; i < length; i++) {
            sb.append(validChars.charAt(rnd.nextInt(validChars.length())));
        }
        return sb.toString();
    }

    public static boolean isEmailValid(final String email) {
        final String expression = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-+]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";

        final Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
        final Matcher matcher = pattern.matcher(email);

        return matcher.matches();
    }

    private static class Hyperlink {
        URLSpan span;
        int start;
        int end;
        String replaceText;
    }
}
