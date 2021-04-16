// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import com.google.gson.JsonObject;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class IOSMessageConversionUtil {

    private IOSMessageConversionUtil() {
    }

    public static JSONObject convertToJSON(String data) {
        JSONObject dataJSON = new JSONObject();

        if (data.contains("<")) {
            dataJSON = convertXMLToJSON(data);
        } else {
            try {
                dataJSON = new JSONObject(data);
            } catch (JSONException e) {
                LogUtil.e(IOSMessageConversionUtil.class.getName(), e.getMessage(), e);
            }
        }

        return dataJSON;
    }

    public static String convertJsonToXML(JsonObject data) {
        String xml = "";
        String key = data.get("key").getAsString();
        String iv = data.get("iv").getAsString();

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String timestamp = simpleDateFormat.format(new Date());

        xml = "<data name=\"iv\">" + iv + "</data><data name=\"key\">" + key + "</data><data name=\"timestamp\">"
                + timestamp + "</data>";
        return xml;
    }

    private static JSONObject convertXMLToJSON(String data) {
        JSONObject keyJSON = new JSONObject();

        try {
            InputStream is = new ByteArrayInputStream(data.getBytes("UTF-8"));

            Map<String, String> aesMap;

            aesMap = XMLUtil.parse(is);

            if ((aesMap == null) || (aesMap.size() < 2)) {
                return null;
            }
            keyJSON.put("key", aesMap.get("key"));
            keyJSON.put("iv", aesMap.get("iv"));
        } catch (XmlPullParserException | IOException | JSONException e) {
            LogUtil.e(IOSMessageConversionUtil.class.getName(), e.getMessage(), e);
        }

        return keyJSON;
    }
}
