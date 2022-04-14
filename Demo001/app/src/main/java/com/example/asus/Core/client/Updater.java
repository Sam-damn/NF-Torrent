package com.example.asus.Core.client;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.asus.Core.peer.Rate;
import com.example.asus.Core.peer.RatePerSec;

/**
 * Created by Asus on 7/21/2018.
 */

public class Updater implements Runnable{

    private Thread thread;
    private boolean stop;

    Handler mainHandler;
    Handler torrentHandler;
    int id;


    RatePerSec downloadRate;
    RatePerSec uploadRate;

    SharedTorrent torrent;
    Client client;


    public Updater(Handler mainHandler , Handler torrentHandler, SharedTorrent torrent, Client client, int id){

        this.mainHandler = mainHandler;
        this.torrentHandler = torrentHandler;
        this.torrent = torrent;
        this.client = client;
        this.id =id;
        thread = null;
        downloadRate = new RatePerSec();
        uploadRate = new RatePerSec();

    }

    public void stop() {
        this.stop = true;

        if (this.thread != null && this.thread.isAlive()) {
            try {
                this.thread.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        this.thread = null;
    }

    public void start(){

        stop=false;

        if (this.thread == null || !this.thread.isAlive()) {
            this.thread = new Thread(this);
            this.thread.setName("bt-serve");
            this.thread.start();
        }
    }


    @Override
    public void run() {
        while (!stop){

            Message m = new Message();
            Bundle b = new Bundle();
            Log.i("info","OMG FUCK U ICHIGO U SUCK DICKS!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" + "upRate:" +String.valueOf(uploadRate.get()/1024)+"downRate:" );
            b.putString("event","update_the_speed");
            b.putFloat("down_rate", downloadRate.get()/1024);
            b.putFloat("up_rate", uploadRate.get());
            b.putInt("id",id);
            m.setData(b);
            if(mainHandler!=null)
            mainHandler.sendMessage(m);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public void updatedownRate(long bytes){

        synchronized (downloadRate) {

            downloadRate.add(bytes);

        }
    }

    public void updateUpRate(long bytes){

        synchronized (uploadRate) {

            uploadRate.add(bytes);

        }
    }
}
