// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.param;

import java.io.Serializable;
import java.util.Date;

public class MessageDestructionParams
        implements Serializable {

    private static final long serialVersionUID = -7428238458385786018L;

    public Integer countdown;

    public Date date;

    public MessageDestructionParams(Integer countdown,
                                    Date date) {
        this.countdown = countdown;
        this.date = date;
    }

    public MessageDestructionParams(final MessageDestructionParams oldParams) {
        this.countdown = oldParams.countdown;
        this.date = oldParams.date;
    }

    public Date convertTimerToDate() {
        if (countdown == null) {
            return this.date;
        }
        this.date = new Date(System.currentTimeMillis() + (countdown * 1000));
        this.countdown = null;
        return this.date;
    }
}
