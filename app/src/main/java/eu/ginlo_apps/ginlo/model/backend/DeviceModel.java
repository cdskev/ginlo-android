// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import android.util.Base64;
import androidx.annotation.NonNull;
import com.google.gson.JsonObject;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BaseModel;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;

public class DeviceModel
        extends BaseModel {
    public String accountGuid;

    public String publicKey;

    public String pkSign;

    public String passtoken;

    public String language;

    public String apnIdentifier;

    public String appName;

    public String appVersion;

    public String os;

    public int[] featureVersion;
    //device infos
    public String name;
    private String type;
    private String lastOnline;
    private String parsedLastOnline;
    private String version;

    public DeviceModel() {
        super();

        guid = "";
        accountGuid = "";
        publicKey = "";
        pkSign = "";
        passtoken = "";
        language = "";
        apnIdentifier = "";
        appName = "";
        appVersion = "";
        os = "";
        featureVersion = new int[0];
    }

    public DeviceModel(@NonNull final JsonObject deviceJO) {
        guid = JsonUtil.stringFromJO(JsonConstants.GUID, deviceJO);
        accountGuid = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_GUID, deviceJO);
        publicKey = JsonUtil.stringFromJO(JsonConstants.PUBLIC_KEY, deviceJO);
        pkSign = JsonUtil.stringFromJO(JsonConstants.PK_SIGN, deviceJO);
        appName = JsonUtil.stringFromJO(JsonConstants.APP_DATA, deviceJO);
        appVersion = JsonUtil.stringFromJO(JsonConstants.APP_VERSION, deviceJO);
        os = JsonUtil.stringFromJO(JsonConstants.APP_OS, deviceJO);
        type = JsonUtil.stringFromJO(JsonConstants.DEVICE_TYPE, deviceJO);
        lastOnline = JsonUtil.stringFromJO(JsonConstants.LAST_ONLINE, deviceJO);
        String base64Name = JsonUtil.stringFromJO(JsonConstants.DEVICE_NAME, deviceJO);
        if (!StringUtil.isNullOrEmpty(base64Name)) {
            name = new String(Base64.decode(base64Name, Base64.DEFAULT), StandardCharsets.UTF_8);
        }
    }

    public static int getDeviceImageRessource(String os) {
        if (StringUtil.isNullOrEmpty(os)) {
            return R.drawable.device_smartphone;
        }

        if (os.toLowerCase(Locale.US).contains("android")) {
            return R.drawable.device_android;
        }

        if (os.toLowerCase(Locale.US).contains("ios")) {
            return R.drawable.device_i_phone;
        }

        return R.drawable.device_computer;
    }

    public int getDeviceImageRessource() {
        return DeviceModel.getDeviceImageRessource(os);
    }

    public String getLastOnlineDateString() {
        if (!StringUtil.isNullOrEmpty(parsedLastOnline)) {
            return parsedLastOnline;
        }

        if (lastOnline != null) {
            try {
                Date date = DateUtil.utcWithoutMillisStringToDate(lastOnline);
                if (date == null) {
                    LogUtil.w(getClass().getSimpleName(), "Parse Date String failed");
                    parsedLastOnline = "";
                    return parsedLastOnline;
                }
                parsedLastOnline = DateUtil.getDateAndTimeStringFromMillis(date.getTime());
            } catch (Exception e) {
                LogUtil.w(getClass().getSimpleName(), "Parse Date String failed", e);
                parsedLastOnline = "";
            }
        }

        return parsedLastOnline;
    }

    public String getVersionString() {
        if (!StringUtil.isNullOrEmpty(version)) {
            return version;
        }

        version = (StringUtil.isNullOrEmpty(appName) ? BuildConfig.APP_NAME_INTERNAL : appName) + " " + (StringUtil.isNullOrEmpty(appVersion) ? "2.1" : appVersion) +
                " | " + (StringUtil.isNullOrEmpty(os) ? "-" : os);

        return version;
    }
}
