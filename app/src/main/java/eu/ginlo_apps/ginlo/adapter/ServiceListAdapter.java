// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.adapter;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.fragment.ServiceItemFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by SGA on 26.01.2017.
 */

public class ServiceListAdapter
        extends FragmentStatePagerAdapter {

    private final List<Fragment> mPages;

    private final BaseActivity mActivity;

    public ServiceListAdapter(final BaseActivity activity, final FragmentManager fm) {
        super(fm);
        mActivity = activity;
        mPages = new ArrayList<>();
    }

    @Override
    public Fragment getItem(final int position) {
        if (mPages.size() <= position) {
            mActivity.finish();
            return new ServiceItemFragment();
        }
        return mPages.get(position);
    }

    public void add(Fragment fragment) {
        mPages.add(fragment);
    }

    @Override
    public int getCount() {
        return mPages.size();
    }
}
