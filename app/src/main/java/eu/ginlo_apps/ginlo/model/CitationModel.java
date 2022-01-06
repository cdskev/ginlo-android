// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model;

import android.graphics.Bitmap;
import android.util.Base64;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.util.*;

/**
 * Created by SGA on 20.10.2016.
 */

public class CitationModel {
    public final String msgGuid;
    public final String contentType;
    public final String contentDesc;
    public final String toGuid;
    public final String nickname;
    public final String fromGuid;
    public Long datesend;
    public String text;
    public Bitmap previewImage;

    public CitationModel(final JsonElement dataJson) {
        final JsonObject obj = dataJson.getAsJsonObject();

        msgGuid = obj.has("msgGuid") && obj.get("msgGuid").isJsonPrimitive() ? obj.get("msgGuid").getAsString() : null;
        contentType = obj.has("Content-Type") && obj.get("Content-Type").isJsonPrimitive() ? obj.get("Content-Type").getAsString() : null;
        contentDesc = obj.has("Content-Desc") && obj.get("Content-Desc").isJsonPrimitive() ? obj.get("Content-Desc").getAsString() : null;
        toGuid = obj.has("toGuid") && obj.get("toGuid").isJsonPrimitive() ? obj.get("toGuid").getAsString() : null;

        if (StringUtil.isEqual(contentType, MimeType.MODEL_LOCATION)) {
            try {
                if (obj.has("Content") && !obj.get("Content").isJsonNull()) {
                    final String wrappedObj = obj.get("Content").getAsString();
                    if (!StringUtil.isNullOrEmpty(wrappedObj)) {
                        final JsonObject parsedContent = new JsonParser().parse(wrappedObj).getAsJsonObject();
                        if (JsonUtil.hasKey("preview", parsedContent)) {
                            final String preview = parsedContent.get("preview").getAsString();
                            previewImage = BitmapUtil.decodeByteArray(Base64.decode(preview, Base64.DEFAULT));
                        }
                    }
                }
            } catch (final IllegalStateException | UnsupportedOperationException | JsonParseException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }
        } else {
            final String content;
            if (obj.has("Content") && !obj.get("Content").isJsonNull()) {
                content = obj.get("Content").getAsString();
            } else {
                content = null;
            }

            if (content != null) {
                if (StringUtil.isEqual(contentType, MimeType.TEXT_PLAIN)
                        || StringUtil.isEqual(contentType, MimeType.TEXT_RSS)) {
                    text = content;
                } else if (StringUtil.isEqual(contentType, MimeType.IMAGE_JPEG)
                        || StringUtil.isEqual(contentType, MimeType.VIDEO_MPEG)
                ) {
                    previewImage = BitmapUtil.decodeByteArray(Base64.decode(content, Base64.DEFAULT));
                }
            }
        }
        nickname = obj.has("Nickname") ? obj.get("Nickname").getAsString() : null;
        String dateString = obj.has("datesend") ? obj.get("datesend").getAsString() : null;
        if (!StringUtil.isNullOrEmpty(dateString)) {
            datesend = DateUtil.utcWithoutMillisStringToMillis(dateString);
        }
        fromGuid = obj.has("fromGuid") ? obj.get("fromGuid").getAsString() : null;
    }

    public CitationModel(final String msgGuid,
                         final String contentType,
                         final String contentDesc,
                         final String toGuid,
                         final String nickname,
                         final Long datesend,
                         final String fromGuid,
                         final String text,
                         final Bitmap previewImage
    ) {

        this.msgGuid = msgGuid;
        this.contentType = contentType;
        this.contentDesc = contentDesc;
        this.toGuid = toGuid;
        this.nickname = nickname;
        this.datesend = datesend;
        this.fromGuid = fromGuid;
        this.text = text;
        this.previewImage = previewImage;
    }
}
