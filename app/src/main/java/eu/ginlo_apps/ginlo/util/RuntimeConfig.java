// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import eu.ginlo_apps.ginlo.util.notobfuscate.IClassUtil;
import java.util.Arrays;
import java.util.List;

import static eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty;

/**
 * @author yves1 Hilfsklasse, um die verschiedenen Konfigurationen (Debug gegen
 * Test, Release gegen Test, Debug gegen Prod, Release gegen Prod) zu
 * verwalten und zu Zentralisieren
 */
public class RuntimeConfig {

    private static RuntimeConfig mInstance;

    private static IClassUtil mClassUtil;
    private final int SCHEME_DEBUG_TEST = 0;

    private final int SCHEME_RELEASE_TEST = 1;

    private final int SCHEME_DEBUG_PROD = 100;

    private final int SCHEME_RELEASE_PROD = 101;

    private static final String ENVIRONMENT = "environment";

    private final String mMandant;
    private static String mGinloServerBase;
    private int mConfig;
    private List<String> mHandleMandantBackups;
    private List<String> allowedBackupSalts;

    private RuntimeConfig() {
        if (BuildConfig.BUILD_TYPE.equals("debug")) {
            mConfig = SCHEME_DEBUG_PROD;
        } else if (BuildConfig.BUILD_TYPE.equals("debugStg")) {
            mConfig = SCHEME_DEBUG_TEST;
        } else if (BuildConfig.BUILD_TYPE.equals("release")) {
            mConfig = SCHEME_RELEASE_PROD;
        } else if (BuildConfig.BUILD_TYPE.equals("releaseStg")) {
            mConfig = SCHEME_RELEASE_TEST;
        }
        mMandant = BuildConfig.SIMSME_MANDANT;
    }

    private static RuntimeConfig getInstance() {
        synchronized (RuntimeConfig.class) {
            if (mInstance == null) {
                mInstance = new RuntimeConfig();
            }
            return mInstance;
        }
    }

    public static boolean isScreenshotEnabled() {
        // If allowed, user decides
        if(BuildConfig.ALWAYS_ALLOW_SCREENSHOTS) {
            return SimsMeApplication.getInstance().getPreferencesController().getScreenshotsEnabled();
        }

        // Other builds than production always allow screenshots
        return !getInstance().isReleaseOnProd();
    }

    public static String getBaseUrl() {
        if(isNullOrEmpty(mGinloServerBase)) {
            mGinloServerBase = BuildConfig.GINLO_BASE_URL;
        }
        return mGinloServerBase;
    }

    public static void setServerBase(String serverBase) {
        mGinloServerBase = serverBase;
    }

    public static String getGcmPrefix() {
        if (getInstance().isProd()) {
            //KS: return "prod.de.dpag.simsme";
            return "eu.ginlo_apps.ginlo";
        }

        //KS: return "test.de.dpag.simsme";
        return "eu.ginlo_apps.ginlo.debug";
    }

    private static boolean testCountryCodeEnabledByEasterEgg = false;

    public static void enableTestCountryCode() {
        testCountryCodeEnabledByEasterEgg = true;
    }

    public static boolean setEnvironment(String env) {
        SharedPreferences.Editor preferencesEditor =
                SimsMeApplication.getInstance().getApplicationContext().getSharedPreferences(ENVIRONMENT, Context.MODE_PRIVATE).edit();
        preferencesEditor.putString(ENVIRONMENT, env);
        return preferencesEditor.commit();
    }

    public static boolean isTestCountryEnabled() {
        return testCountryCodeEnabledByEasterEgg || !getInstance().isReleaseOnProd();
    }

    static int getDeriveIterations() {
        if (BuildConfig.DEBUG) {
            return 1;
        } else {
            return 20000;
        }
    }

    public static boolean isB2c() {
        return StringUtil.isEqual("default", getInstance().getMandantInternal());
    }

    public static String getMandant() {
        return getInstance().getMandantInternal();
    }

    public static @NonNull
    IClassUtil getClassUtil() {
        if (mClassUtil != null) {
            return mClassUtil;
        }
        try {
            Class<?> classUtil = SystemUtil.getClassForBuildConfigClassname(BuildConfig.CLASS_UTIL);

            mClassUtil = (IClassUtil) classUtil.newInstance();
            return mClassUtil;
        } catch (Exception e) {
            throw new RuntimeException("Can not load Class", e);
        }
    }

    public static String getApplicationPublicKey() {
        return BuildConfig.APPLICATION_PUBLIC_KEY;
    }

    public static List<String> getAllowedBackupMandantIdents() {
        return getInstance().getAllowedBackupMandantIdentsInternal();
    }

    public static List<String> getAllowedBackupSalts() {
        return getInstance().getAllowedBackupSaltsInternal();
    }

    public static boolean isBAMandant() {
        return BuildConfig.SIMSME_MANDANT.equals(BuildConfig.SIMSME_MANDANT_BA);
    }

    public static boolean supportMultiDevice() {
        return BuildConfig.MULTI_DEVICE_SUPPORT.booleanValue();
    }

    private boolean isProd() {
        String defValue = (mConfig == SCHEME_RELEASE_PROD) || (mConfig == SCHEME_DEBUG_PROD) ? "Production" : "Testing";
        return SimsMeApplication
                .getInstance()
                .getApplicationContext()
                .getSharedPreferences(ENVIRONMENT, Context.MODE_PRIVATE)
                .getString(ENVIRONMENT, defValue)
                .equals("Production");
    }

    private boolean isReleaseOnProd() {
        return (mConfig == SCHEME_RELEASE_PROD);
    }

    private String getMandantInternal() {
        return mMandant;
    }

    private List<String> getAllowedBackupMandantIdentsInternal() {
        if (mHandleMandantBackups == null) {
            mHandleMandantBackups = Arrays.asList(BuildConfig.ALLOWED_MANDANT_BACKUPS.split(","));
        }

        return mHandleMandantBackups;
    }

    private List<String> getAllowedBackupSaltsInternal() {
        if (allowedBackupSalts == null)
            allowedBackupSalts = Arrays.asList(BuildConfig.ALLOWED_BACKUP_SALTS.split(","));

        return allowedBackupSalts;
    }
}
