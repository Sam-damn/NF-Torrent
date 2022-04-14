package com.example.ichigo.Gui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.asus.Core.TorrentParser;
import com.example.asus.Core.base.Torrent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

public class Torrent_Activity extends AppCompatActivity implements StatusFragment.OnFragmentInteractionListener,
        PiecesFragment.OnFragmentInteractionListener,
        TrackersFragment.OnFragmentInteractionListener,
        Files.OnFragmentInteractionListener,
        PeersFragment.OnFragmentInteractionListener,
        ServiceConnection{

    TabLayout tabLayout;
    ViewPager viewPager;

    Files filesFragment;
    StatusFragment statusFragment;
    TorrentParser test;
    ArrayList<String> files;
    Tracker_details [] trackers;
    Handler handler;

    TorrentManagingService.Torrent_data torrent_data;

    //for the service
    TorrentManagingService torrent_service;
    boolean isbound = false;

    ProgressBar progressBar;
    TextView progress_percentage;
    Torrent torrent;
    //the id of the current torrent
    int id =0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torrent_);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progress_percentage = (TextView) findViewById(R.id.downloading_info);


        handler = new Handler()
        {
            @Override
            public void handleMessage(Message msg)
            {
                Bundle b = msg.getData();
                String event = b.getString("event");
                if(event.equals("pieceCompletion"))
                {
                    int id1 = b.getInt("id");
                    if(id1 == id)
                    {
                        int temp = b.getInt("percentage");
                        progressBar.setProgress(temp);
                        progress_percentage.setText(temp + "%");
                    }

                }
            }
        };

        files = new ArrayList<>();
        files.add("motherfuck");

        Intent intent = getIntent();
        String name = intent.getExtras().getString("name");
        String path = intent.getExtras().getString("path");
         torrent = (Torrent) intent.getExtras().get("torrent");
        this.id = intent.getExtras().getInt("id");
        int percent = intent.getExtras().getInt("percentage");
        progressBar.setProgress(percent);
        progress_percentage.setText(percent + "%");

       // Uri uri = Uri.parse(path);
        Toolbar toolbar = (Toolbar) findViewById(R.id.torrent_toolbar);
        toolbar.setTitle(name);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);


        tabLayout = (TabLayout) findViewById(R.id.tablayout1);
        viewPager = (ViewPager) findViewById(R.id.secondpager);


        Intent startmainservice = new Intent(this,TorrentManagingService.class);
        //bindService(startmainservice,this, Context.BIND_AUTO_CREATE);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.torrent_menu, menu);
        return true;
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isbound) {
            unbindService(this);
            isbound = false;
        }
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        TorrentManagingService.MyBinder binder = (TorrentManagingService.MyBinder) service;
        torrent_service = binder.getservice();
        isbound = true;
        torrent_service.settorrenthandler(handler );
        torrent_data = torrent_service.gettorrentdata(id);

        SecondPagerAdapter adapter = new SecondPagerAdapter(getSupportFragmentManager(), torrent , torrent_data);
        viewPager.setAdapter(adapter);


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

    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        isbound = false;

    }

    public void updateResults(Torrent torrent) {
        files = torrent.getFilenames();
        filesFragment.update(files);
    }

    public void setFilesFragment(Files listener){
        this.filesFragment= listener;
    }


    class Tracker_details{
        Set<String> tracker_name;
        boolean tracker_state;
    }
}