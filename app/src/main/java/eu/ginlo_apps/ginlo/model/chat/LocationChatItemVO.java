// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.chat;

import android.graphics.Bitmap;

import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class LocationChatItemVO
        extends BaseChatItemVO {

    public Bitmap image;

    public double longitude;

    public double latitude;

    public LocationChatItemVO() {
    }
}
