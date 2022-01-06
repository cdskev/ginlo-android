// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend;

/**
 * Created by Florian on 07.09.16.
 */
public class MessageReceiverModel {
    public String guid;
    public int sendsReadConfirmation = 0;
    public long dateDownloaded = 0;
    public long dateRead = 0;
}
