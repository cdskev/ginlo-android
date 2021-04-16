// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.param;

import android.net.Uri;

import java.util.ArrayList;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class SendActionContainer {

    public static final int ACTION_SEND = 500;

    public static final int ACTION_FORWARD = 501;

    public static final int TYPE_TXT = 550;

    public static final int TYPE_IMAGE = 551;

    public static final int TYPE_FILE = 552;

    public static final int TYPE_VIDEO = 553;

    //oeffnen in action
    public int action = -1;

    public int type = -1;

    public ArrayList<Uri> uris;

    public String text;

    /**
     * Message zum Anzeigen(Bspw. wenn zu viele Dateien übergeben wurden) für Nutzer
     */
    public String displayMessage;

    //forward message
    public long forwardMessageId;

    public boolean forwardChannelMessageIsImage;

    public boolean forwardChannelMessageIsText;
}