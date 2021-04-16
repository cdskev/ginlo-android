// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import android.util.Base64;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.model.backend.BaseMessageModel;
import eu.ginlo_apps.ginlo.model.backend.GroupMessageModel;
import eu.ginlo_apps.ginlo.model.backend.PrivateMessageModel;
import eu.ginlo_apps.ginlo.util.ChecksumUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class SignatureModel {

    private String fromGuid;

    private String fromGuidHash;

    private String fromKeyHash;

    private String fromTempDeviceGuid;

    private String fromTempDeviceGuidHash;

    private String fromTempDeviceKeyHash;

    private String toGuid;

    private String toGuidHash;

    private String toKeyHash;

    private String toTempDeviceGuid;

    private String toTempDeviceGuidHash;

    private String toTempDeviceKeyHash;

    private String dataHash;

    private String[] attachments;

    private String signature;

    /**
     * signature inklusive der Felder über die neuen felder für das Temp-Device
     */
    private String signatureWithTempInfo;

    /**
     * Signature über die Daten mit dem Schlüssel des Temp-Device
     */
    private String signatureTempDevice;

    public SignatureModel() {
    }

    public static SignatureModel parseModel(JsonObject jsonObject) {
        final SignatureModel signatureModel = new SignatureModel();

        final JsonObject hashesObject = jsonObject.get("hashes").getAsJsonObject();
        ArrayList<String> attachments = new ArrayList<>();

        for (Map.Entry<String, JsonElement> mapEntry : hashesObject.entrySet()) {
            String key = mapEntry.getKey();
            JsonElement element = mapEntry.getValue();
            if (element.isJsonNull() || !element.isJsonPrimitive()) {
                continue;
            }
            String value = mapEntry.getValue().getAsString();

            String[] keyParts = key.split("/");

            if (keyParts == null) {
                continue;
            }

            if (keyParts.length == 1) {
                if (keyParts[0].equals("data")) {
                    signatureModel.dataHash = value;
                }
            }
            if (keyParts.length == 2) {
                if (keyParts[0].equals("from")) {
                    signatureModel.fromGuidHash = value;
                    signatureModel.fromGuid = keyParts[1];
                }
                if (keyParts[0].equals("to")) {
                    signatureModel.toGuidHash = value;
                    signatureModel.toGuid = keyParts[1];
                }
                if (keyParts[0].equals("attachment")) {
                    int index = -1;

                    try {
                        index = Integer.parseInt(keyParts[1]);
                    } catch (NumberFormatException e) {
                        LogUtil.e(signatureModel.getClass().getName(), e.getMessage(), e);
                    }
                    if (index >= 0) {
                        attachments.add(index, value);
                    }
                }
            }
            if (keyParts.length == 3) {
                if (keyParts[0].equals("from") && keyParts[2].equals("key")) {
                    signatureModel.fromKeyHash = value;
                }

                if (keyParts[0].equals("to") && keyParts[2].equals("key")) {
                    signatureModel.toKeyHash = value;
                }
            }

            if (keyParts.length == 4) {
                if (keyParts[0].equals("from") && keyParts[2].equals("tempDevice") && keyParts[3].equals("guid")) {
                    signatureModel.fromTempDeviceGuidHash = value;
                }
                if (keyParts[0].equals("from") && keyParts[2].equals("tempDevice") && keyParts[3].equals("key")) {
                    signatureModel.fromTempDeviceKeyHash = value;
                }

                if (keyParts[0].equals("to") && keyParts[2].equals("tempDevice") && keyParts[3].equals("guid")) {
                    signatureModel.toTempDeviceGuidHash = value;
                }
                if (keyParts[0].equals("to") && keyParts[2].equals("tempDevice") && keyParts[3].equals("key")) {
                    signatureModel.toTempDeviceKeyHash = value;
                }
            }
        }

        signatureModel.attachments = attachments.toArray(new String[]{});

        if (jsonObject.has("signature") && !jsonObject.get("signature").isJsonNull()) {

            signatureModel.signature = jsonObject.get("signature").getAsString();
        }

        if (jsonObject.has("signature-tempdevice") && !jsonObject.get("signature-tempdevice").isJsonNull()) {
            signatureModel.signatureTempDevice = jsonObject.get("signature-tempdevice").getAsString();
        }

        return signatureModel;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getSignatureWithTempInfo() {
        return signatureWithTempInfo;
    }

    public void setSignatureWithTempInfo(String signatureWithTempInfo) {
        this.signatureWithTempInfo = signatureWithTempInfo;
    }

    public String getSignatureTempDevice() {
        return signatureTempDevice;
    }

    public void initWithMessage(BaseMessageModel messageModel, boolean useSha256) throws LocalizedException {
        if (messageModel instanceof PrivateMessageModel) {
            PrivateMessageModel singleMessageModel = (PrivateMessageModel) messageModel;

            this.fromGuid = singleMessageModel.from.guid;
            this.toGuid = singleMessageModel.to[0].guid;

            this.fromGuidHash = getChecksum(singleMessageModel.from.guid, useSha256);
            this.fromKeyHash = getChecksum(singleMessageModel.from.keyContainer, useSha256);
            this.toGuidHash = getChecksum(singleMessageModel.to[0].guid, useSha256);
            this.toKeyHash = getChecksum(singleMessageModel.to[0].keyContainer, useSha256);
            if (singleMessageModel.to[0].tempDeviceGuid != null) {
                this.toTempDeviceGuid = singleMessageModel.to[0].tempDeviceGuid;
                this.toTempDeviceGuidHash = getChecksum(this.toTempDeviceGuid, useSha256);
                this.toTempDeviceKeyHash = getChecksum(singleMessageModel.to[0].tempDeviceKey, useSha256);
            }
        } else if (messageModel instanceof GroupMessageModel) {
            GroupMessageModel groupMessageModel = (GroupMessageModel) messageModel;

            this.fromGuid = groupMessageModel.from.guid;
            this.toGuid = groupMessageModel.to;

            this.fromGuidHash = getChecksum(groupMessageModel.from.guid, useSha256);
            this.toGuidHash = getChecksum(groupMessageModel.to, useSha256);
        }

        if (messageModel.attachment != null) {
            if (GuidUtil.isRequestGuid(messageModel.attachment)) {
                File file = AttachmentController.getEncryptedBase64AttachmentFile(messageModel.attachment);
                if (file != null && file.exists()) {
                    this.attachments = new String[]{useSha256 ? ChecksumUtil.getSHA256ChecksumFromFile(file) : ChecksumUtil.getSHA1ChecksumFromFile(file)};
                }
            } else {
                this.attachments = new String[]{getChecksum(messageModel.attachment, useSha256)};
            }
        }

        this.dataHash = getChecksum(messageModel.data, useSha256);
    }

    public String getCombinedHashes(boolean bWithTempDevice) {
        final StringBuilder concatSignatureString = new StringBuilder();
        concatSignatureString.append(this.dataHash);
        concatSignatureString.append(this.fromGuidHash);
        if (this.fromKeyHash != null) {
            concatSignatureString.append(this.fromKeyHash);
        }
        if (bWithTempDevice) {
            if (this.fromTempDeviceGuidHash != null) {
                concatSignatureString.append(this.fromTempDeviceGuidHash);
            }
            if (this.fromTempDeviceKeyHash != null) {
                concatSignatureString.append(this.fromTempDeviceKeyHash);
            }
        }

        concatSignatureString.append(this.toGuidHash);
        if (this.toKeyHash != null) {
            concatSignatureString.append(this.toKeyHash);
        }
        if (bWithTempDevice) {
            if (this.toTempDeviceGuidHash != null) {
                concatSignatureString.append(this.toTempDeviceGuidHash);
            }
            if (this.toTempDeviceKeyHash != null) {
                concatSignatureString.append(this.toTempDeviceKeyHash);
            }
        }
        if (this.attachments != null) {
            for (int i = 0; i < this.attachments.length; i++) {
                concatSignatureString.append(this.attachments[i]);
            }
        }

        return concatSignatureString.toString();
    }

    public boolean hasTempDeviceInfo() {
        return (this.fromTempDeviceGuid != null || this.toTempDeviceGuid != null);
    }

    public JsonObject getModel(boolean bWithTempInfo) {
        final JsonObject signatureModelObject = new JsonObject();
        final JsonObject hashesObject = new JsonObject();

        hashesObject.addProperty("from/" + this.fromGuid, this.fromGuidHash);
        hashesObject.addProperty("from/" + this.fromGuid + "/key", this.fromKeyHash);
        hashesObject.addProperty("to/" + this.toGuid, this.toGuidHash);
        hashesObject.addProperty("to/" + this.toGuid + "/key", this.toKeyHash);

        if (bWithTempInfo && this.toTempDeviceGuid != null) {
            hashesObject.addProperty("to/" + this.toGuid + "/tempDevice/guid", this.toTempDeviceGuidHash);
            hashesObject.addProperty("to/" + this.toGuid + "/tempDevice/key", this.toTempDeviceKeyHash);
        }

        hashesObject.addProperty("data", this.dataHash);

        if (this.attachments != null) {
            for (int i = 0; i < this.attachments.length; i++) {
                hashesObject.addProperty("attachment/" + i, this.attachments[i]);
            }
        }

        signatureModelObject.add("hashes", hashesObject);
        if (bWithTempInfo) {
            signatureModelObject.addProperty("signature", this.signatureWithTempInfo);
        } else {
            signatureModelObject.addProperty("signature", this.signature);
        }

        return signatureModelObject;
    }

    private String getChecksum(String data, boolean bUseSha256) {
        if (bUseSha256) {
            return ChecksumUtil.getSHA256ChecksumForString(data);
        } else {
            return ChecksumUtil.getSHA1ChecksumForString(data);
        }
    }

    private String getChecksum(byte[] data, boolean bUseSha256) {
        String d = Base64.encodeToString(data, Base64.NO_WRAP);
        return getChecksum(d, bUseSha256);
    }

    public boolean checkMessage(Message message, boolean bUseSha256) {
        if ((message.getFrom() == null) || (this.fromGuid == null)) {
            return false;
        }
        if (!StringUtil.isEqual(message.getFrom(), fromGuid)) {
            return false;
        }

        if (!StringUtil.isEqual(getChecksum(message.getFrom(), bUseSha256), fromGuidHash)) {
            return false;
        }

        if ((message.getTo() == null) || (this.toGuid == null)) {
            return false;
        }

        if (!StringUtil.isEqual(getChecksum(message.getTo(), bUseSha256), toGuidHash)) {
            return false;
        }

        if (message.getType() != Message.TYPE_GROUP) {
            if ((message.getEncryptedFromKey() == null) || (this.fromKeyHash == null)) {
                return false;
            }
            if (!StringUtil.isEqual(getChecksum(message.getEncryptedFromKey(), bUseSha256), fromKeyHash)) {
                return false;
            }

            if ((message.getEncryptedToKey() == null) || (this.toKeyHash == null)) {
                return false;
            }
            if (!StringUtil.isEqual(getChecksum(message.getEncryptedToKey(), bUseSha256), toKeyHash)) {
                return false;
            }
        }

        if ((message.getData() == null) || (this.dataHash == null)) {
            return false;
        }

        return StringUtil.isEqual(getChecksum(message.getData(), bUseSha256), dataHash);
    }
}
