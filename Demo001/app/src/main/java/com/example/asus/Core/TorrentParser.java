package com.example.asus.Core;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;

import com.example.asus.Core.base.Torrent;
//import com.example.asus.torrenttesting.client.SharedTorrent;
import com.example.asus.Core.util.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;



public class TorrentParser extends AsyncTask<Uri,Void,Torrent>{
    ParserResultUpdateCallback mListener;
    Context context;
    // TorrentsManagingService torrentsManagingService;


    public TorrentParser(Context context)
    {
        this.context = context;
    }

/*    public TorrentParser(Context context,TorrentsManagingService torrentsManagingService){
        this.torrentsManagingService = torrentsManagingService;
        this.context=context;
    }*/

    @Override
    protected Torrent doInBackground(Uri... params) {
        try {

            InputStream in=context.getContentResolver().openInputStream(params[0]);

            byte[] array= Utils.readArrayFromStream(in);
            File dir = new File(Environment.getExternalStorageDirectory(),"/Download");

            //SharedTorrent torrent = new SharedTorrent(array,dir);

            Torrent torrent = new Torrent(array,true);


            return torrent;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Torrent torrent) {
        if(torrent!=null) {

            mListener.updateResults(torrent);
        }
    }

    public void RegisterParser(ParserResultUpdateCallback listener){
        mListener=listener;


    }

    public static interface ParserResultUpdateCallback{

        abstract public void updateResults(Torrent torrent);

    }
}