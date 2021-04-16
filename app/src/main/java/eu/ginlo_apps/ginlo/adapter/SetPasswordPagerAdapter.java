// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.adapter;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import eu.ginlo_apps.ginlo.fragment.PasswordFragment;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class SetPasswordPagerAdapter
        extends FragmentStatePagerAdapter {

    private final Fragment[] pages = new Fragment[2];

    public SetPasswordPagerAdapter(FragmentManager fm,
                                   PasswordFragment setPasswordFragment,
                                   PasswordFragment confirmPasswordFragment) {
        super(fm);

        pages[0] = setPasswordFragment;
        pages[1] = confirmPasswordFragment;
    }

    @Override
    public Fragment getItem(int position) {
        return pages[position];
    }

    @Override
    public int getCount() {
        return pages.length;
    }
}
