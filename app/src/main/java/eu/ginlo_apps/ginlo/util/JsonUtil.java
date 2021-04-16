// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.log.Logger;

import java.util.Map;
import java.util.Set;

/**
 * Created by Florian on 18.12.15.
 */
public class JsonUtil {
    public static String stringFromJO(@NonNull String objectKey, @NonNull JsonObject jsonObject) {
        try {
            if (jsonObject.has(objectKey)) {
                JsonElement el = jsonObject.get(objectKey);
                if (!el.isJsonNull()) {
                    if (el.isJsonPrimitive()) {
                        return el.getAsString();
                    } else {
                        return el.toString();
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.e("JsonUtil", e.getMessage(), e);
        }
        return null;
    }

    @Nullable
    public static JsonObject jsonObjectFromJO(@NonNull String objectKey, @NonNull JsonObject jsonObject)
    {
        try
        {
            if (jsonObject.has(objectKey))
            {
                JsonElement el = jsonObject.get(objectKey);
                if (el.isJsonNull())
                {
                    return null;
                }

                if (!el.isJsonObject())
                {
                    return null;
                }

                return el.getAsJsonObject();
            }
        }
        catch (Exception e)
        {
            LogUtil.e(JsonUtil.class.getSimpleName(), e.getMessage(), e);
        }
        return null;
    }

    public static boolean hasKey(@NonNull String objectKey, @NonNull JsonObject jsonObject) {
        try {
            return jsonObject.has(objectKey) && !jsonObject.get(objectKey).isJsonNull();
        } catch (Exception e) {
            LogUtil.e("JsonUtil", e.getMessage(), e);
        }

        return false;
    }

    public static boolean hasPrimitiveKey(@NonNull String objectKey, @NonNull JsonObject jsonObject) {
        try {
            return jsonObject.has(objectKey) && jsonObject.get(objectKey).isJsonPrimitive();
        } catch (Exception e) {
            LogUtil.e("JsonUtil", e.getMessage(), e);
        }

        return false;
    }

    public static @Nullable
    JsonObject getJsonObjectFromString(@NonNull String jsonString) {
        JsonElement element = getJsonElementFromString(jsonString);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }

        return null;
    }

    public static @Nullable
    JsonArray getJsonArrayFromString(@NonNull String jsonString) {
        JsonElement element = getJsonElementFromString(jsonString);
        if (element != null && element.isJsonArray()) {
            return element.getAsJsonArray();
        }

        return null;
    }

    private static @Nullable
    JsonElement getJsonElementFromString(@NonNull String jsonString) {
        try {
            JsonParser parser = new JsonParser();
            return parser.parse(jsonString);
        } catch (Exception e) {
            LogUtil.w("JsonUtil", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Siehe {@link #searchJsonElementRecursive(JsonElement, String)}
     *
     * @param rootJE    Root JSON Element
     * @param objectKey Key
     * @return erstes gefundenes JSON Object oder null
     */
    public static @Nullable
    JsonObject searchJsonObjectRecursive(@NonNull final JsonElement rootJE, @NonNull final String objectKey) {
        JsonElement je = searchJsonElementRecursive(rootJE, objectKey);

        if (je != null && je.isJsonObject()) {
            return je.getAsJsonObject();
        }

        return null;
    }

    /**
     * Durchsucht die uebergebene JSON Struktur nachdem angegebenen Key.
     *
     * @param rootJE    Root JSON Element
     * @param objectKey Key
     * @return erstes gefundenes JSON Element oder null
     */
    public static @Nullable
    JsonElement searchJsonElementRecursive(@NonNull final JsonElement rootJE, @NonNull final String objectKey) {
        if (rootJE.isJsonObject()) {
            JsonObject rootJO = rootJE.getAsJsonObject();
            if (rootJO.has(objectKey)) {
                return rootJO.get(objectKey);
            }
            Set<Map.Entry<String, JsonElement>> entries = rootJO.entrySet();
            if (entries != null && entries.size() > 0) {
                for (Map.Entry<String, JsonElement> entry : entries) {
                    JsonElement foundJE = searchJsonElementRecursive(entry.getValue(), objectKey);
                    if (foundJE != null) {
                        return foundJE;
                    }
                }
            }
        } else if (rootJE.isJsonArray()) {
            JsonArray rootJA = rootJE.getAsJsonArray();

            for (JsonElement je : rootJA) {
                JsonElement foundJE = searchJsonElementRecursive(je, objectKey);
                if (foundJE != null) {
                    return foundJE;
                }
            }
        }

        return null;
    }

    public static String[] getStringArrayFromJsonArray(JsonArray data) {
        if (data == null) {
            return null;
        }
        String[] rc = new String[data.size()];
        for (int i = 0; i < data.size(); i++) {
            JsonElement e = data.get(i);
            if (e == null || e.isJsonNull()) {
                rc[i] = null;
                continue;
            }
            if (e.isJsonPrimitive()) {
                rc[i] = e.getAsString();
                continue;
            }
            // Eigentlich ein Fehler
            rc[i] = e.toString();
        }
        return rc;
    }
}
