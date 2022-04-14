package com.example.ichigo.Gui;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;


public class ViewPagerAdapter extends FragmentPagerAdapter {
    int tabscount;
    public ViewPagerAdapter(FragmentManager fm, int numberoftabs) {
        super(fm);
        tabscount = numberoftabs;
    }

    @Override
    public Fragment getItem(int position) {

        switch(position)
        {

            case 0: {
                DetailsFragment tab1 = new DetailsFragment();
                return tab1;
            }
            case 1:
                return  Files.newInstance("the first file",null);
            default:
                return null;
        }

    }

    @Override
    public int getCount() {
        return tabscount;
    }
}
