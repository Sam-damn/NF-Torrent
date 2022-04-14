package com.example.ichigo.Gui;

import android.net.Uri;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.util.Log;

import com.example.asus.Core.base.Torrent;

import java.net.URI;
import java.util.ArrayList;


public class SecondPagerAdapter extends FragmentPagerAdapter {
    Torrent torrent;
    TorrentManagingService.Torrent_data torrent_data;
    public SecondPagerAdapter(FragmentManager fm , Torrent torrent , TorrentManagingService.Torrent_data torrent_data) {
        super(fm);
        this.torrent =torrent;
        this.torrent_data =torrent_data;
    }

    @Override
    public Fragment getItem(int position) {
        switch (position)
        {
            case 0:
                return StatusFragment.newInstance("","");
            case 1:
                return Files.newInstance("torrent",torrent_data.torrent);
            case 2:
                return TrackersFragment.newInstance(torrent.getAlltrackers());
            case 3:
                return PeersFragment.newInstance(torrent_data.list_of_peers);
            case 4:
                return PiecesFragment.newInstance("","");
        }
        return null;
    }

    @Override
    public int getCount() {
        return 5;
    }
}
