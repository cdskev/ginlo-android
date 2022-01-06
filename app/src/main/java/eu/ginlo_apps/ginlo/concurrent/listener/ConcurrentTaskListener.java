// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.listener;

import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public abstract class ConcurrentTaskListener {

    public abstract void onStateChanged(ConcurrentTask task,
                                        int state);
}
