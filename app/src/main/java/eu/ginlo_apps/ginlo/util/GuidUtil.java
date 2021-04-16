// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import java.util.UUID;

import eu.ginlo_apps.ginlo.util.StringUtil;

public class GuidUtil {
    private static final String SYSTEM_CHAT_GUID = "0:{00000000-0000-0000-0000-000000000000}";
    public static final String COMPANY_INDEX_ENTRY_GUID_PREFIX = "10010:";
    private static final String PASSTOKEN_PREFIX = "";
    private static final String ACCOUNT_PREFIX = "0:";
    private static final String DEVICE_PREFIX = "3:";
    private static final String ROOM_PREFIX = "7:";
    private static final String CHANNEL_PREFIX = "21:";
    private static final String SERVICE_PREFIX = "22:";
    private static final String BACKEND_REQUEST_PREFIX = "3000:";
    private static final String PRIVATE_INDEX_GUID = "5001:";

    public GuidUtil() {
    }

    public static boolean isChatRoom(String value) {
        return value.indexOf(ROOM_PREFIX) == 0;
    }

    public static boolean isChatSingle(String value) {
        return value.indexOf(ACCOUNT_PREFIX) == 0;
    }

    public static boolean isChatChannel(String value) {
        return value.indexOf(CHANNEL_PREFIX) == 0;
    }

    public static boolean isChatService(String value) {
        return value.indexOf(SERVICE_PREFIX) == 0;
    }

    public static boolean isSystemChat(String value) {
        return StringUtil.isEqual(value, SYSTEM_CHAT_GUID);
    }

    public static boolean isRequestGuid(String value) {
        if (value == null) {
            return false;
        }
        return value.startsWith(BACKEND_REQUEST_PREFIX);
    }

    public static String generatePassToken() {
        return generateGuid(PASSTOKEN_PREFIX);
    }

    public static String generateAccountGuid() {
        return generateGuid(ACCOUNT_PREFIX);
    }

    public static String generateDeviceGuid() {
        return generateGuid(DEVICE_PREFIX);
    }

    public static String generateRoomGuid() {
        return generateGuid(ROOM_PREFIX);
    }

    public static String generateRequestGuid() {
        return generateGuid(BACKEND_REQUEST_PREFIX);
    }

    public static String generatePrivateIndexGuid() {
        return generateGuid(PRIVATE_INDEX_GUID);
    }

    public static String generateGuid(String prefix) {
        return prefix + "{" + UUID.randomUUID() + "}";
    }
}
