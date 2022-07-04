// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import java.util.UUID;

import eu.ginlo_apps.ginlo.model.constant.AppConstants;

public class GuidUtil {

    public GuidUtil() {
    }

    public static boolean isChatRoom(String value) {
        return value.indexOf(AppConstants.GUID_ROOM_PREFIX) == 0;
    }

    public static boolean isChatSingle(String value) {
        return value.indexOf(AppConstants.GUID_ACCOUNT_PREFIX) == 0;
    }

    public static boolean isChatChannel(String value) {
        return value.indexOf(AppConstants.GUID_CHANNEL_PREFIX) == 0;
    }

    public static boolean isChatService(String value) {
        return value.indexOf(AppConstants.GUID_SERVICE_PREFIX) == 0;
    }

    public static boolean isSystemChat(String value) {
        return StringUtil.isEqual(value, AppConstants.GUID_SYSTEM_CHAT);
    }

    public static boolean isProfileUser(String value) {
        return StringUtil.isEqual(value, AppConstants.GUID_PROFILE_USER);
    }

    public static boolean isProfileGroup(String value) {
        return StringUtil.isEqual(value, AppConstants.GUID_PROFILE_GROUP);
    }

    public static boolean isRequestGuid(String value) {
        if (value == null) {
            return false;
        }
        return value.startsWith(AppConstants.GUID_BACKEND_REQUEST_PREFIX);
    }

    public static String generatePassToken() {
        return generateGuid(AppConstants.GUID_PASSTOKEN_PREFIX);
    }

    public static String generateAccountGuid() {
        return generateGuid(AppConstants.GUID_ACCOUNT_PREFIX);
    }

    public static String generateDeviceGuid() {
        return generateGuid(AppConstants.GUID_DEVICE_PREFIX);
    }

    public static String generateRoomGuid() {
        return generateGuid(AppConstants.GUID_ROOM_PREFIX);
    }

    public static String generateRequestGuid() {
        return generateGuid(AppConstants.GUID_BACKEND_REQUEST_PREFIX);
    }

    public static String generatePrivateIndexGuid() {
        return generateGuid(AppConstants.GUID_PRIVATE_INDEX);
    }

    public static String generateGuid(String prefix) {
        return prefix + "{" + UUID.randomUUID() + "}";
    }
}
