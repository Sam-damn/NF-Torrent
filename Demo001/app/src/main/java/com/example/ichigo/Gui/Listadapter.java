package com.example.ichigo.Gui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.asus.Core.base.Torrent;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;


public class Listadapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    Context context;
    ArrayList<String> items;
    ArrayList<Uri> itemspaths;
    ArrayList<Torrent> torrents;
    ArrayList<Integer> progresses;
    ArrayList<String> states;
    ArrayList<Float> download;
    ArrayList<Float> upload;
    ArrayList<String> locations;
    TorrentManagingService service;

    public Listadapter(Context context, ArrayList<String> items, ArrayList<Uri> paths, ArrayList<Torrent> torrents, ArrayList<Integer> progresses,
                       ArrayList<String> state , ArrayList<Float> download, ArrayList<Float> upload, ArrayList<String> locations)
    {
        this.context = context;
        this.items = items;
        this.itemspaths =paths;
        this.torrents =torrents;
        this.progresses = progresses;
        this.states = state;
        this.download = download;
        this.upload = upload;
        this.locations = locations;
    }

    public void setService(TorrentManagingService service){

        this.service = service;

    }
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View row = inflater.inflate(R.layout.row_layout,parent,false);
        Item item = new Item(row);
        return item;
    }
    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
            ((Item)holder).textView.setText(items.get(position));
            ((Item)holder).speed_state.setText("Down:" + download.get(position) +"KB" + "/" + "upload: " + upload.get(position) +"KB");
            ((Item) holder).rowlayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(context, ((Item) holder).textView.getText(),Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(context,Torrent_Activity.class);
                    intent.putExtra("percentage",progresses.get(position));
                    intent.putExtra("id",position);
                    intent.putExtra("path",itemspaths.get(position).toString());
                    intent.putExtra("name",items.get(position));
                    intent.putExtra("torrent",torrents.get(position));
                    context.startActivity(intent);

                }
            });

            ((Item)holder).progressBar.setProgress(progresses.get(position));
            ((Item)holder).download_state.setText(states.get(position));
            ((Item) holder).button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(((Item) holder).togglebutton) {
                        ((Item) holder).button.setImageResource(R.drawable.ic_pause_icon);
                        ((Item)holder).togglebutton = !((Item)holder).togglebutton;
                        Log.i("location:",Environment.getExternalStorageDirectory() + "/"+locations.get(position));
                        try {
                            service.add_torrent(torrents.get(position),new File(Environment.getExternalStorageDirectory() +"/"+ locations.get(position)),position);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }


                    }
                    else
                    {
                        ((Item) holder).button.setImageResource(R.drawable.ic_play_icon);
                        ((Item)holder).togglebutton = !((Item)holder).togglebutton;
                        service.stopTorrent(position);
                        ((Item)holder).download_state.setText("stopped");


                    }
                }
            });
        }

    @Override
    public int getItemCount() {
        return itemspaths.size();
    }

    public class Item extends RecyclerView.ViewHolder{
        TextView textView;
        TextView download_state;
        ImageButton button;
        ProgressBar progressBar;
        TextView speed_state;
        RelativeLayout rowlayout;
        boolean togglebutton;
        public Item(View itemView) {
            super(itemView);

            textView = (TextView) itemView.findViewById(R.id.nametext);
            download_state = (TextView) itemView.findViewById(R.id.downloading_state);
            button = (ImageButton) itemView.findViewById(R.id.downloadbutton);
            progressBar = (ProgressBar) itemView.findViewById(R.id.torrentprogressbar);
            speed_state = (TextView) itemView.findViewById(R.id.speedtext_on_the_mainscreen);
            rowlayout = (RelativeLayout) itemView.findViewById(R.id.rowlayout);}
    }
}
