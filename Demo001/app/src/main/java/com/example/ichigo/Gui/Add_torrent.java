package com.example.ichigo.Gui;

import android.content.Intent;
import android.net.Uri;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;

import com.example.asus.Core.TorrentParser;
import com.example.asus.Core.base.Torrent;

import java.util.ArrayList;

public class Add_torrent extends AppCompatActivity implements DetailsFragment.OnFragmentInteractionListener , Files.OnFragmentInteractionListener, TorrentParser.ParserResultUpdateCallback{

    DetailsFragment detailsFragment;
    Files filesFragment;
    TabLayout tabLayout;
    ViewPager viewPager;
    TorrentParser test;
    ArrayList<String> files;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_torrent);
        files = new ArrayList<>();
        files.add("motherfuck");

        tabLayout = (TabLayout) findViewById(R.id.tablayout);
        tabLayout.addTab(tabLayout.newTab().setText("Details"));
        tabLayout.addTab(tabLayout.newTab().setText("Files"));
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        viewPager = (ViewPager) findViewById(R.id.pager);
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager(), tabLayout.getTabCount());
        viewPager.setAdapter(adapter);

        Intent i =getIntent();
        String teststring=i.getExtras().getString("F");
        Uri uri = Uri.parse(teststring);

        test = new TorrentParser(this);
        test.RegisterParser(this);
        test.execute(uri);


        viewPager.setOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));

        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        //get device window size (width, height) then desgin the layout according to the device
        DisplayMetrics dm= new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width =dm.widthPixels;
        int height = dm.heightPixels;
        getWindow().setLayout(width - 75,  height - 50);

    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    class torrentdetails{
        String torrentname, creatorname ;
        int filecount;
        long filesize;

    }
    @Override
    public void updateResults(Torrent torrent) {
        torrentdetails data = new torrentdetails();
        data.torrentname = torrent.getName();
        data.creatorname = torrent.getCreatedBy();
        files = torrent.getFilenames();
        data.filecount = files.size();
        data.filesize = torrent.getSize();
        filesFragment.update(files);
        detailsFragment.updateGUI(data, torrent);
    }




    public void setDetailsFragment(DetailsFragment listener) {
        this.detailsFragment = listener;
    }
    public void setFilesFragment(Files listener){
        this.filesFragment= listener;
    }
}
