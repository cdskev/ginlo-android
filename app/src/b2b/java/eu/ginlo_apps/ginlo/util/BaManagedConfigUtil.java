// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.contracts.AppLifecycleCallbacks;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import org.mindrot.jbcrypt.BCrypt;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * Created by yves1 on 19.09.16.
 */
public class BaManagedConfigUtil implements IManagedConfigUtil, AppLifecycleCallbacks {
    private final SimsMeApplication mApplication;
    private Bundle mAppRestrictions;
    private String mVersionKey;

    private BaManagedConfigUtil(SimsMeApplication application) {
        LogUtil.i(this.getClass().getSimpleName(), "Creating Instance");
        mApplication = application;

        loadConfig();

        mApplication.getAppLifecycleController().registerAppLifecycleCallbacks(this);
    }

    public void loadConfig() {
        synchronized (this) {
            final AccountController accountController = mApplication.getAccountController();
            JsonObject companyAppConfigJO = accountController.getCompanyMDMConfig();

            RestrictionsManager restrictionsManager = (RestrictionsManager) mApplication.getSystemService(Context.RESTRICTIONS_SERVICE);
            Bundle appRestrictions = restrictionsManager.getApplicationRestrictions();

            mergeConfigs(appRestrictions, companyAppConfigJO);

            StringBuilder sb = new StringBuilder();
            for (String key : mAppRestrictions.keySet()) {
                sb.append(key);
                sb.append(mAppRestrictions.get(key));
                LogUtil.d(this.getClass().getSimpleName(), "Bundle AppRestrictions" + key + " = \"" + mAppRestrictions.get(key) + "\"");
            }
            mVersionKey = ChecksumUtil.getSHA256ChecksumForString(sb.toString());
        }
    }

    private void mergeConfigs(@Nullable Bundle appRestrictions, @Nullable JsonObject companyMDMConfig) {
        mAppRestrictions = new Bundle();

        if (companyMDMConfig != null) {
            Set<Map.Entry<String, JsonElement>> entries = companyMDMConfig.entrySet();

            for (Map.Entry<String, JsonElement> entry : entries) {
                put(entry.getKey(), entry.getValue());
            }
        }

        if (appRestrictions != null) {
            mAppRestrictions.putAll(appRestrictions);
        }
    }

    private void put(String key, JsonElement el) {
        if (el.isJsonPrimitive()) {
            JsonPrimitive jp = el.getAsJsonPrimitive();
            if (jp.isBoolean()) {
                mAppRestrictions.putBoolean(key, jp.getAsBoolean());
            } else if (jp.isNumber()) {
                mAppRestrictions.putInt(key, jp.getAsInt());
            } else if (jp.isString()) {
                mAppRestrictions.putString(key, jp.getAsString());
            }
        }
    }

    @Override
    public void appDidEnterForeground() {
        LogUtil.i(this.getClass().getSimpleName(), "Loading new Config");
        loadConfig();
    }

    @Override
    public void appGoesToBackGround() {

    }

    private static BaManagedConfigUtil mSingleton = null;

    public static BaManagedConfigUtil getInstance(SimsMeApplication application) {
        if (mSingleton == null) {
            mSingleton = new BaManagedConfigUtil(application);
        }
        return mSingleton;
    }

    private boolean getManagedValueBoolean(String name, boolean defaultValue) {
        synchronized (this) {
            if (!isManaged(name)) {
                return defaultValue;
            }
            Object o = mAppRestrictions.get(name);
            if (o == null) {
                return defaultValue;
            }
            if (o instanceof Boolean) {
                return (boolean) o;
            }
            if (o instanceof String) {
                String s = (String) o;
                return "true".equalsIgnoreCase(s) || "1".equalsIgnoreCase(s);
            }
            return defaultValue;
        }
    }

    private int getManagedValueInt(String name, int defaultValue) {
        synchronized (this) {
            if (!isManaged(name)) {
                return defaultValue;
            }
            Object o = mAppRestrictions.get(name);
            if (o == null) {
                return defaultValue;
            }
            if (o instanceof Integer) {
                return (int) o;
            }
            if (o instanceof String) {
                String s = (String) o;
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException ex) {
                    return defaultValue;
                }
            }
            return defaultValue;
        }
    }

    private String getManagedValueString(String name, String defaultValue) {
        synchronized (this) {
            if (!isManaged(name)) {
                return defaultValue;
            }
            Object o = mAppRestrictions.get(name);
            if (o == null) {
                return defaultValue;
            }
            if (o instanceof String) {
                return (String) o;
            }
            return defaultValue;
        }
    }

    private boolean isManaged(String name) {
        synchronized (this) {
            if (mAppRestrictions != null && mAppRestrictions.containsKey(name)) {
                Object value = mAppRestrictions.get(name);
                if (value == null) {
                    return false;
                }
                if (value instanceof String) {
                    return !StringUtil.isNullOrEmpty((String) value);
                }

                // it is enough to know that a Boolean or Integer value is set. If the Boolean or Integer is set
                // then it is in fact managed. The actual "value" is irrelevant.
                return value instanceof Boolean || value instanceof Integer;
            }
            return false;
        }
    }

    @Override
    public boolean canExportChat() {
        // disableExportChat
        return !getManagedValueBoolean("disableExportChat", false);
    }

    @Override
    public boolean isOpenInAllowed() {
        // disableOpenIn
        return !getManagedValueBoolean("disableOpenIn", false);
    }

    @Override
    public boolean canSendMedia() {
        // disableSendMedia
        return !getManagedValueBoolean("disableSendMedia", false);
    }

    /**
     * Wurde die Einstellung für Speichern in die Gallerie festgelegt ?!
     *
     * @return
     */
    @Override
    public boolean isSaveMediaToGalleryManaged() {
        // disableSaveToCameraRoll != null
        return isManaged("disableSaveToCameraRoll") && getManagedValueBoolean("disableSaveToCameraRoll", false);
    }

    /**
     * Wie wurde die Einstellung für Speichern in die Gallerie festgelegt
     *
     * @return
     */
    @Override
    public boolean isSaveMediaToGalleryManagedValue() {
        // disableSaveToCameraRoll
        return !getManagedValueBoolean("disableSaveToCameraRoll", false);
    }

    public boolean isSaveMediaToCameraRollDisabled() {
        // disableSaveToCameraRoll != null
        return isManaged("disableSaveToCameraRoll") && getManagedValueBoolean("disableSaveToCameraRoll", false);
    }

    /**
     * Wurde die Einstellung für Speichern in die Gallerie festgelegt ?!
     *
     * @return
     */
    @Override
    public boolean isLockApplicationDelayManaged() {
        // simsLockApplicationDelay != null
        return isManaged("simsLockApplicationDelay");
    }

    /**
     * Wie wurde die Einstellung für Speichern in die Gallerie festgelegt
     *
     * @return
     */
    @Override
    public int getLockApplicationDelayManagedValue() {
        // simsLockApplicationDelay
        return getManagedValueInt("simsLockApplicationDelay", 0);
    }

    /**
     * Wird zum Start der Anwendung ein Passwort abgefragt
     *
     * @return
     */
    @Override
    public boolean isPasswordOnStartRequired() {
        // disableNoPwLogin
        return getManagedValueBoolean("disableNoPwLogin", false);
    }

    /**
     * Wurde festgelegt, ob die Daten nach Passwortfehleinfgaben gelöscht werden sollen
     *
     * @return
     */
    @Override
    public boolean isDeleteDataAfterTriesManaged() {
        // simsPasswordTries
        return isManaged("simsPasswordTries");
    }

    /**
     * nach wieviel fehlerhaften Passworteingaben wird der Account zurückgesetzt ?
     *
     * @return
     */
    @Override
    public int getDeleteDataAfterTriesManagedValue() {
        // simsPasswordTries
        return getManagedValueInt("simsPasswordTries", 0);
    }

    private boolean getPasswordMinLengthManaged() {
        return isManaged("passwordMinLength");
    }

    private int getPasswordMinLength() {
        return getManagedValueInt("passwordMinLength", 0);
    }

    private boolean getPasswordMinDigitManaged() {
        return isManaged("passwordMinDigit");
    }

    private int getPasswordMinDigit() {
        return getManagedValueInt("passwordMinDigit", 0);
    }

    private boolean getPasswordMinLowercaseManaged() {
        return isManaged("passwordMinLowercase");
    }

    private int getPasswordMinLowercase() {
        return getManagedValueInt("passwordMinLowercase", 0);
    }

    private boolean getPasswordMinUppercaseManaged() {
        return isManaged("passwordMinUppercase");
    }

    private int getPasswordMinUppercase() {
        return getManagedValueInt("passwordMinUppercase", 0);
    }

    private boolean getPasswordMinSpecialCharManaged() {
        return isManaged("passwordMinSpecialChar");
    }

    private int getPasswordMinSpecialChar() {
        return getManagedValueInt("passwordMinSpecialChar", 0);
    }

    private boolean getPasswordMinClassesManaged() {
        return isManaged("passwordMinClasses");
    }

    private int getPasswordMinClasses() {
        return getManagedValueInt("passwordMinClasses", 0);
    }

    private boolean getPasswordMaxDurationManaged() {
        return isManaged("passwordMaxDuration");
    }

    private int getPasswordMaxDuration() {
        return getManagedValueInt("passwordMaxDuration", 0);
    }

    private boolean getPasswordReuseEntriesManaged() {
        return isManaged("passwordReuseEntries");
    }

    private int getPasswordReuseEntries() {
        return getManagedValueInt("passwordReuseEntries", 0);

    }

    private boolean getPasswordForceComplexPinManaged() {
        return isManaged("forceComplexPin");
    }

    private boolean getPasswordForceComplexPin() {
        return getManagedValueBoolean("forceComplexPin", false);
    }

    /**
     * Kann der Nutzer ein einfaches Passwort nutzen
     *
     * @return
     */
    @Override
    public boolean canUseSimplePassword() {
        // forceComplexPin
        if (getPasswordForceComplexPinManaged() && getPasswordForceComplexPin()) {
            return false;
        }

        if (getPasswordMinLengthManaged() && getPasswordMinLength() > 4) {
            return false;
        }
        if (getPasswordMinDigitManaged() && getPasswordMinDigit() > 4) {
            return false;
        }
        if (getPasswordMinLowercaseManaged() && getPasswordMinLowercase() > 0) {
            return false;
        }
        if (getPasswordMinUppercaseManaged() && getPasswordMinUppercase() > 0) {
            return false;
        }
        if (getPasswordMinSpecialCharManaged() && getPasswordMinSpecialChar() > 0) {
            return false;
        }
        return !getPasswordMinClassesManaged() || getPasswordMinClasses() <= 1;
    }

    /**
     * Prüft das Passwort auf die Passwortanforderungen und gibt ggf. einen Fehlertext zurück
     *
     * @param password
     * @return
     */
    @Override
    public String checkPassword(String password, boolean isPwChange) {
        do {
            if (getPasswordMinLengthManaged() && password.length() < getPasswordMinLength()) {
                break;
            }
            int numberDigit = 0;
            int numberUpperCase = 0;
            int numberLowerCase = 0;
            int numberSpecialChar = 0;
            for (int i = 0; i < password.length(); i++) {
                char ch = password.charAt(i);
                if (Character.isDigit(ch)) {
                    numberDigit++;
                }
                if (Character.isLowerCase(ch)) {
                    numberLowerCase++;
                }
                if (Character.isUpperCase(ch)) {
                    numberUpperCase++;
                }
                if (!Character.isLetterOrDigit(ch)) {
                    numberSpecialChar++;
                }

            }

            if (getPasswordMinDigitManaged() && numberDigit < getPasswordMinDigit()) {
                break;
            }
            if (getPasswordMinLowercaseManaged() && numberLowerCase < getPasswordMinLowercase()) {
                break;
            }
            if (getPasswordMinUppercaseManaged() && numberUpperCase < getPasswordMinUppercase()) {
                break;
            }
            if (getPasswordMinSpecialCharManaged() && numberSpecialChar < getPasswordMinSpecialChar()) {
                break;
            }
            int numberClasses = 0;
            if (numberDigit > 0) {
                numberClasses++;
            }
            if (numberLowerCase > 0) {
                numberClasses++;
            }
            if (numberUpperCase > 0) {
                numberClasses++;
            }
            if (numberSpecialChar > 0) {
                numberClasses++;
            }
            if (getPasswordMinClassesManaged() && numberClasses < getPasswordMinClasses()) {
                break;
            }

            if (getPasswordReuseEntriesManaged() && isPwChange) {
                String savedPasswords = mApplication.getPreferencesController().getHashedPasswords();
                if (!StringUtil.isNullOrEmpty(savedPasswords)) {
                    String[] split = savedPasswords.split(",");
                    for (int i = 0; i < split.length; i++) {
                        if (BCrypt.checkpw(password, split[i])) {
                            return mApplication.getString(R.string.registration_validation_pwd_is_used_again);
                        }

                    }

                }
            }
            return null;
        } while (false);
        StringBuilder sb = new StringBuilder();
        sb.append(mApplication.getString(R.string.registration_validation_pwd_policies_fails));

        if (getPasswordMinLengthManaged() && getPasswordMinLength() != 0) {
            sb.append("\n").append(mApplication.getString(R.string.registration_validation_pwd_policies_min_length)).append(" (").append(getPasswordMinLength()).append(")");
        }
        if (getPasswordMinDigitManaged() && getPasswordMinDigit() != 0) {
            sb.append("\n").append(mApplication.getString(R.string.registration_validation_pwd_policies_min_digit)).append(" (").append(getPasswordMinDigit()).append(")");
        }

        if (getPasswordMinLowercaseManaged() && getPasswordMinLowercase() != 0) {
            sb.append("\n").append(mApplication.getString(R.string.registration_validation_pwd_policies_min_lowercase)).append(" (").append(getPasswordMinLowercase()).append(")");
        }

        if (getPasswordMinUppercaseManaged() && getPasswordMinUppercase() != 0) {
            sb.append("\n").append(mApplication.getString(R.string.registration_validation_pwd_policies_min_uppercase)).append(" (").append(getPasswordMinUppercase()).append(")");
        }

        if (getPasswordMinSpecialCharManaged() && getPasswordMinSpecialChar() != 0) {
            sb.append("\n").append(mApplication.getString(R.string.registration_validation_pwd_policies_min_special_char)).append(" (").append(getPasswordMinSpecialChar()).append(")");
        }

        if (getPasswordMinClassesManaged() && getPasswordMinClasses() != 0) {
            sb.append("\n").append(mApplication.getString(R.string.registration_validation_pwd_policies_min_classes)).append(" (").append(getPasswordMinClasses()).append(")\n").append(mApplication.getString(R.string.registration_validation_pwd_policies_min_classes2));
        }

        return sb.toString();
    }

    /**
     * Hasht die Passwörter um zu prüfen, ob das Passwort bereits verwendet wurde.
     *
     * @param password
     * @return
     */
    @Override
    public void onPasswordChanged(String password) throws LocalizedException {
        LogUtil.d(this.getClass().getSimpleName(), "onPasswordChanged: false");
        // Datum der Änderung speichern
        mApplication.getPreferencesController().rememberPasswordDate();

        if (getPasswordReuseEntriesManaged()) {

            // Passwort hashen
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(4));

            // Liste der bereits gehashten Passworte ermitteln
            String savedPasswords = mApplication.getPreferencesController().getHashedPasswords();

            if (savedPasswords == null) {
                savedPasswords = hashedPassword;
            } else {
                savedPasswords = savedPasswords + "," + hashedPassword;
            }
            String[] split = savedPasswords.split(",");

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < split.length; i++) {
                if (i == getPasswordReuseEntries()) {
                    break;
                }
                if (i != 0) {
                    sb.insert(0, ",");
                }
                sb.insert(0, split[split.length - i - 1]);

            }
            mApplication.getPreferencesController().setHashedPasswords(sb.toString());
        }
    }

    /**
     * muss der Nutzer das Passwort ändern
     *
     * @param password
     * @return
     */
    @Override
    public boolean needToChangePassword(String password, boolean isPwChange)
            throws LocalizedException {
        if (password != null && checkPassword(password, isPwChange) != null) {
            LogUtil.d(this.getClass().getSimpleName(), "needToChangePassword: true");
            return true;
        }
        if (getPasswordMaxDurationManaged() && getPasswordMaxDuration() != 0) {
            Date lastDate = mApplication.getPreferencesController().getPasswordChangeDate();
            if (lastDate == null) {
                LogUtil.d(this.getClass().getSimpleName(), "needToChangePassword: true");
                return true;
            }
            Calendar cal = DateUtil.getCalendarFromDate(lastDate);
            cal.add(Calendar.DAY_OF_YEAR, getPasswordMaxDuration());
            if (cal.before(DateUtil.getCalendarFromDate(new Date()))) {
                LogUtil.d(this.getClass().getSimpleName(), "needToChangePassword: true");
                return true;
            }
        }
        LogUtil.d(this.getClass().getSimpleName(), "needToChangePassword: false");
        return false;
    }

    /**
     * Wurde der Zugriff auf die Kamera gesperrt
     *
     * @return disableCamera, default: false
     */
    @Override
    public boolean isCameraDisabled() {
        return getManagedValueBoolean("disableCamera", false);
    }

    /**
     * Wurde der Zugriff auf das Mikrofon gesperrt
     *
     * @return disableMicrophone, default: false
     */
    @Override
    public boolean isMicrophoneDisabled() {
        return getManagedValueBoolean("disableMicrophone", false);
    }

    /**
     * Wurde das Senden von Kontakten gesperrt
     *
     * @return isSendContactsDisabled, default: false
     */
    @Override
    public boolean isSendContactsDisabled() {
        return getManagedValueBoolean("disableSendVCard", false);
    }

    /**
     * Wurde das Senden von Standortdaten gesperrt
     *
     * @return isLocationDisabled, default: false
     */
    @Override
    public boolean isLocationDisabled() {
        return getManagedValueBoolean("disableLocation", false);
    }

    /**
     * Wurde das Kopieren von Textnachrichten in die Zwischenablage gesperrt
     *
     * @return isCopyPasteDisabled, default: false
     */
    @Override
    public boolean isCopyPasteDisabled() {
        return getManagedValueBoolean("disableCopyPaste", false);
    }

    /**
     * Wurde der RecoveryCode vom Admin grunsaetzlich gesperrt
     *
     * @return isCopyPasteDisabled, default: false
     */
    @Override
    public boolean isRecoveryDisabled() {
        return getManagedValueBoolean("disableRecoveryCode", false);
    }

    /**
     * Gibt dne Wert zurueck, den der Admin in der Konsole fuer den Recovery Code (on/off) festgelegt hat
     *
     * @return isCopyPasteDisabled, default: false
     */
    @Override
    public boolean getRecoveryByAdmin() {
        return getManagedValueBoolean("disableSimsmeRecovery", false);
    }

    /**
     * Gibt zurueck, ob das Preview der Notifications vom MC gamaged wird, oder nicht
     *
     * @return
     */
    @Override
    public boolean isNotificationPreviewDisabled() {
        return getManagedValueBoolean("disablePushPreview", false);
    }

    @Override
    public boolean hasAutomaticMdmRegistrationKeys() {
        return !StringUtil.isNullOrEmpty(getLastName()) && !StringUtil.isNullOrEmpty(getFirstName()) && !StringUtil.isNullOrEmpty(getEmailAddress()) && !StringUtil.isNullOrEmpty(getLoginCode());
    }

    @Override
    public String getLastName() {
        return getManagedValueString("lastName", null);
    }

    @Override
    public String getFirstName() {
        return getManagedValueString("firstName", null);
    }

    @Override
    public String getEmailAddress() {
        return getManagedValueString("emailAddress", null);
    }

    @Override
    public String getLoginCode() {
        return getManagedValueString("loginCode", null);
    }

    @Override
    public String getVersionKey() {
        return mVersionKey;
    }

    @Override
    public boolean getDisableBiometricLogin() {
        return getManagedValueBoolean("disableFaceAndTouchID", false);
    }
}
