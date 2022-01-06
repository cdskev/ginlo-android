// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.chat;

import android.graphics.Bitmap;

import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import ezvcard.VCard;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class VCardChatItemVO
        extends BaseChatItemVO {

    public Bitmap photo;

    public String photoUrl;

    public String displayInfo;

    public VCard vCard;

    public String accountId;

    public String accountGuid;

    public String phonenumber;

    public VCardChatItemVO() {
    }
}
