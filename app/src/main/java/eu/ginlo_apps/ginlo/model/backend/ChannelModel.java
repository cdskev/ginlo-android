// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import eu.ginlo_apps.ginlo.model.backend.ChannelLayoutModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleModel;

public class ChannelModel {
    public String shortDesc;

    public String options;

    public String desc;

    public String aesKey;

    public String iv;

    public ToggleModel[] toggles;

    public ChannelLayoutModel layout;

    public String checksum;

    public String channelJsonObject;

    public String shortLinkText;

    public Boolean promotion;

    public String externalUrl;

    public String searchText;

    public String category;

    public String welcomeText;

    public String suggestionText;

    public String feedbackContact;

    public ChannelModel() {
        super();
    }
}
