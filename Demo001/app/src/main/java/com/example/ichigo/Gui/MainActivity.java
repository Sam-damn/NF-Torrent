package com.example.ichigo.Gui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.asus.Core.base.Torrent;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements ServiceConnection  {
    //for the main list
    RecyclerView recyclerView;
    Listadapter listadapter;
    ArrayList items = new ArrayList();
    ArrayList <Uri> itemspaths = new ArrayList<>();
    ArrayList <Torrent> torrents = new ArrayList<>();
    ArrayList<Integer> progresses = new ArrayList<>();
    ArrayList<String> states = new ArrayList<>();
    ArrayList<Float> download = new ArrayList<>();
    ArrayList<Float> upload = new ArrayList<>();
    ArrayList<String> locations = new ArrayList<>();
    //
    private int storage_permission_code =1;

    //for the sidebar menu
    DrawerLayout drawerLayout;
    NavigationView navigationView;
    //

    //for the service
    TorrentManagingService torrent_service;
    boolean isbound =false;


    //id tracking
    int id = 0;

    Handler handler;

    Torrent torrent;
    boolean permissiongranted =false;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //
        Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);


        handler = new Handler()
        {
            @Override
            public void handleMessage(Message msg)
            {
                Bundle bundle = msg.getData();
                // the code for getting the progress


                String event= (String) bundle.get("event");
                if(event.equals("pieceCompletion")){
                    int id1 = bundle.getInt("id");
                    int temp = bundle.getInt("percentage");
                    progresses.set(id1,temp);
                    Log.i("info","this is the percentage of the download" +temp);


                }

                else if(event.equals("stateChange")){
                    int id1 = bundle.getInt("id");
                    String text =bundle.getString("state");
                    states.set(id1, text);
                }
                else {
                    int id1 = bundle.getInt("id");
                    float down = bundle.getFloat("down_rate");
                    Log.i("forororororororor", String.valueOf(down));
                    float up = bundle.getFloat("up_rate");
                    download.set(id1,down);
                    upload.set(id1,up);
                }

                listadapter.notifyDataSetChanged();
            }
        };

        itemspaths = getItemspaths();
        items = getItems();
        try {
            progresses = getprogress();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        listadapter = new Listadapter(this, items,itemspaths,torrents,progresses, states, download, upload, locations);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(listadapter);
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(getApplicationContext()));

        //
        drawerLayout = (DrawerLayout) findViewById(R.id.drawerlayout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this,drawerLayout,toolbar,R.string.navigation_open_drawer,R.string.navigation_close_drawer);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        //
        Intent startmainservice = new Intent(this,TorrentManagingService.class);
        startService(startmainservice);
        bindService(startmainservice,this, Context.BIND_AUTO_CREATE);
    }
    public ArrayList<Integer> getprogress() throws FileNotFoundException {

        Gson gson = new Gson();
        JsonReader jsonReader = new JsonReader(new FileReader("progress.json"));
        ArrayList<Integer> temp = gson.fromJson(jsonReader,Integer[].class);
        return temp;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isbound) {
            unbindService(this);
            try {
                saveItems();
            } catch (IOException e) {
                e.printStackTrace();
            }
            isbound = false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.app_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    { switch(item.getItemId()) {
        case R.id.bookmsrk:
        {
            Intent i=new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("application/x-bittorrent");
            startActivityForResult(i, 1);
        }
        return(true);
        case R.id.search_but:
            //add the function to perform here
            return(true);
    }
        return(super.onOptionsItemSelected(item));
    }


    Uri path;
    String save_location = "Download";
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub

        switch(requestCode){

            case 1:

                if(resultCode==RESULT_OK){
                    path = data.getData();
                    Intent i=new Intent(this,Add_torrent.class);
                    i.putExtra("F",path.toString());
                    startActivityForResult(i,2);
                }
                break;
            case 2:
                if(resultCode == RESULT_OK) {
                    save_location =data.getExtras().getString("path");
                    if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED)
                    {
                        permissiongranted =true;
                    }
                    else
                    {
                        requestStoragePermission();
                    }
                    String name = data.getExtras().getString("F");
                    torrent = (Torrent) data.getSerializableExtra("torrent");
                    Log.i("info",torrent.getName());
                    if(permissiongranted) {
                        try {
                            torrent_service.add_torrent(torrent, new File(Environment.getExternalStorageDirectory(), "/"+ save_location), id);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }
                        id++;
                    }
                    items.add(name);
                    itemspaths.add(path);
                    torrents.add(torrent);
                    progresses.add(0);
                    states.add("waiting");
                    download.add(0.0f);
                    upload.add(0.0f);
                    locations.add(save_location);

                    listadapter.notifyItemInserted(itemspaths.size() - 1);
                }
                break;

        }
    }
    public void requestStoragePermission()
    {
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        {
         new AlertDialog.Builder(this)
                 .setTitle("fuck you Google")
                 .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                     @Override
                     public void onClick(DialogInterface dialog, int which) {

                         ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, storage_permission_code);
                     }
                 })
                 .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                     @Override
                     public void onClick(DialogInterface dialog, int which) {
                         dialog.dismiss();
                     }
                 }).create().show();
        }
        else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, storage_permission_code);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,  String[] permissions,  int[] grantResults)
    {
        if (requestCode == storage_permission_code)  {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                try {
                    torrent_service.add_torrent(torrent, new File(Environment.getExternalStorageDirectory(), "/" +save_location), id);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                id++;
            }
            } else {

            }
    }
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TorrentManagingService.MyBinder binder = (TorrentManagingService.MyBinder) service;
            torrent_service = binder.getservice();
            isbound = true;
            torrent_service.setMainHandler(handler);
            this.listadapter.setService(torrent_service);

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isbound = false;

        }

    public void saveItems() throws IOException {
        SharedPreferences list_saver = getSharedPreferences("x",MODE_APPEND);
        SharedPreferences.Editor editor = list_saver.edit();
        Set<String> set = new HashSet<>(items);
        Set<Uri> set1 = new HashSet<>(itemspaths);
        ArrayList<String> tempo = new ArrayList<>();
        for(Uri uri: set1)
        {
            tempo.add(uri.toString());
        }
        Set<String> set2 = new HashSet<>(tempo);

        Set<Integer> progresses1 = new HashSet<>(progresses);
        Gson gson = new Gson();
        String save_progress = gson.toJson(progresses);


        FileWriter writer = new FileWriter("progress.json");
        writer.write(save_progress);
        editor.putStringSet("names", set);
        editor.putStringSet("paths", set2);
        editor.apply();
    }
    public  ArrayList<String> getItems()
    {
        SharedPreferences list_retriever = getSharedPreferences("x",MODE_PRIVATE);
        Set <String> default_set = new HashSet<>();
        Set<String> set = list_retriever.getStringSet("names",default_set);
        ArrayList <String> temp = new ArrayList<>(set);
        return temp;
    }
    public ArrayList<Uri> getItemspaths()
    {
        SharedPreferences list_retriever = getSharedPreferences("x",MODE_PRIVATE);
        Set <String> default_set = new HashSet<>();
        Set<String> set = list_retriever.getStringSet("paths",default_set);
        ArrayList <Uri> temp = new ArrayList<>();
        for(String s :set)
        {
            Uri uri = Uri.parse(s);
            temp.add(uri);
        }
        return temp;
    }
}

