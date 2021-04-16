// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter

class SimsmeFragmentPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {

   private var mFragments : MutableList<PageAdapterItemInfo> = mutableListOf<PageAdapterItemInfo>()

    override fun getCount(): Int {
        return mFragments.count()
    }

    override fun getItem(position: Int): Fragment {
        return mFragments.get(position).mPageFragment
    }


    override fun getPageTitle(position: Int): CharSequence? {
        if (mFragments.count() < position)
            return super.getPageTitle(position)
        return mFragments.get(position).mPageTitle
    }

    fun addNewFragment(pageAdapterItemInfo : PageAdapterItemInfo)
    {
        mFragments.add(pageAdapterItemInfo)
        notifyDataSetChanged()
    }

    fun removeFragment(pageAdapterItemInfo : PageAdapterItemInfo)
    {
        mFragments.remove(pageAdapterItemInfo)
        notifyDataSetChanged()
    }
}

class PageAdapterItemInfo(val mPageTitle: String, val mPageFragment : Fragment){

}
