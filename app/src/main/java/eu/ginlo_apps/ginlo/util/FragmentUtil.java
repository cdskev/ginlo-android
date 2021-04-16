// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class FragmentUtil {

    public static void toggleFragment(FragmentManager fragmentManager,
                                      Fragment fragment,
                                      int containerId,
                                      boolean showFragment) {
        FragmentTransaction fragmentTransaction;

        if (showFragment) {
            fragmentTransaction = fragmentManager.beginTransaction();
            if(fragment.isHidden())
                fragmentTransaction.show(fragment);
            if(!fragment.isAdded())
                fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            fragmentTransaction.replace(containerId, fragment).commit();
        } else {
            fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.hide(fragment);
            fragmentTransaction.commit();
        }
    }
}
