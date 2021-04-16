// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.chat.overview;

import eu.ginlo_apps.ginlo.model.backend.ChannelLayoutModel;
import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO;

public class ChannelChatOverviewItemVO
        extends BaseChatOverviewItemVO {

    public long messageCount;

    public long dateread;

    public long datedownloaded;

    public ChannelLayoutModel channelLayoutModel;

    public String previewText;

    public long getLatestDate() {
        long returnValue = datesend;

        if (returnValue < datedownloaded) {
            returnValue = datedownloaded;
        }

        if (returnValue < dateread) {
            returnValue = dateread;
        }

        return returnValue;
    }
}
