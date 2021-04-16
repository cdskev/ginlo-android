// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.chat.overview;

import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO;

public class ChatOverviewItemVO extends BaseChatOverviewItemVO {
    public String previewText;

    public boolean isSentMessage;

    public long messageCount;

    public boolean hasRead;

    public boolean hasDownloaded;

    public boolean isSendConfirm;

    public boolean isPriority;
}
