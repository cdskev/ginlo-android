// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.greendao;

/**
 * wird angelegt, wenn für die Nachricht eine Notification erstellt wird
 */
public class Notification
        implements java.io.Serializable {

    private Long mId;

    /**
     * guid der nachricht
     */
    private String mMessageGuid;

    /**
     * def con
     */
    public Notification() {

    }

    public Notification(final Long id) {
        mId = id;
    }

    /**
     * Notification
     *
     * @param messageGuid
     */
    public Notification(final Long id,
                        final String messageGuid) {
        mId = id;
        mMessageGuid = messageGuid;
    }

    public Long getId() {
        return mId;
    }

    public void setId(Long id) {
        this.mId = id;
    }

    /**
     * getMessageGuid
     *
     * @return
     */
    public String getMessageGuid() {
        return mMessageGuid;
    }

    public void setMessageGuid(final String messageGuid) {
        mMessageGuid = messageGuid;
    }
}
