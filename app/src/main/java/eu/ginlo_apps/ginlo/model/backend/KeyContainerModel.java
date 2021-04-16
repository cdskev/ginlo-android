// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class KeyContainerModel {

    public final String guid;

    public final String keyContainer;

    public final String key2;

    public String nickname;

    public String tempDeviceGuid;

    public String tempDeviceKey;

    public String tempDeviceKey2;

    public KeyContainerModel(String guid,
                             String keyContainer,
                             String key2) {
        this.guid = guid;
        this.keyContainer = keyContainer;
        this.key2 = key2;
    }

    public KeyContainerModel(String guid,
                             String keyContainer,
                             String key2,
                             String tempDeviceGuid,
                             String tempDeviceKey,
                             String tempDeviceKey2) {
        this.guid = guid;
        this.keyContainer = keyContainer;
        this.key2 = key2;
        this.tempDeviceGuid = tempDeviceGuid;
        this.tempDeviceKey = tempDeviceKey;
        this.tempDeviceKey2 = tempDeviceKey2;
    }
}
