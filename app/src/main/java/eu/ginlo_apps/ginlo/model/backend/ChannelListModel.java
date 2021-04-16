// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend;

import eu.ginlo_apps.ginlo.model.backend.BaseModel;

public class ChannelListModel
        extends BaseModel {

    public String shortDesc;

    public String checksum;

    public String localChecksum;

    public boolean isSubscribed;

    public boolean promotion;

    public String description;

    public boolean isFirstItemOfGroup;

    public ChannelListModel() {
        super();
    }
}
