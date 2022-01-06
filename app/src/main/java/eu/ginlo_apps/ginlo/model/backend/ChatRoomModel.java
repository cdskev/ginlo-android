// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import eu.ginlo_apps.ginlo.model.backend.BaseModel;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChatRoomModel
        extends BaseModel {

    public String owner;
    public String data;
    public String keyIv;
    public String[] member;
    public int maxmember;
    public boolean confirmed;
    public String[] admins;

    /**
     * guids mit Schreibberechtigung in einer Restricted-Gruppe
     */
    public String[] writers;
    public String roomType;
    public boolean isReadonly;
    public String pushSilentTill;

    public ChatRoomModel() {
        super();
        confirmed = true;
    }
}
