// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import java.util.Date;

import eu.ginlo_apps.ginlo.model.backend.BaseModel;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class BaseMessageModel
        extends BaseModel {

    public int messageType;

    public String data;

    public String attachment;

    public String key2Iv;

    //public SignatureModel signature;
    public byte[] signatureBytes;

    //public SignatureModel Sha256 ssignature;
    public byte[] signatureSha256Bytes;

    public Date datesend;

    public Date datedownloaded;

    public Date dateread;

    public String requestGuid;

    public Boolean isSystemMessage;

    public String features;

    public String mimeType;

    public Boolean isSentMessage;

    public Long databaseId;

    public String errorIdentifier;

    public Boolean isPriority;

    public long replaceMessageId = -1;

    public boolean isAbesntMessage;

    BaseMessageModel() {
    }
}
