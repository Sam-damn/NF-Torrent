package com.example.ichigo.Gui;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by ichigo on 7/22/2018.
 */

public class Peers_list_adapter  extends RecyclerView.Adapter<RecyclerView.ViewHolder>  {
    Context context;
    ArrayList<String> peer_ip;
    ArrayList<Integer> peer_port;
    public Peers_list_adapter(Context context, ArrayList<String> peer_ip, ArrayList<Integer> peer_port)
    {
        this.context = context;
        this.peer_ip = peer_ip;
        this.peer_port = peer_port;
    }
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.peer_row_layout, parent, false);
        Item item = new Item(view);
        return item;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ((Item)holder).peer_ip.setText(peer_ip.get(position));
        ((Item)holder).peer_port.setText(String.valueOf(peer_port.get(position)));

    }

    @Override
    public int getItemCount() {
        return peer_ip.size();
    }

    public class Item extends RecyclerView.ViewHolder{
        TextView peer_ip;
        TextView peer_port;
        public Item(View itemView) {
            super(itemView);
            peer_ip = (TextView) itemView.findViewById(R.id.peer_ip);
            peer_port = (TextView) itemView.findViewById(R.id.peer_port);
        }
    }
}
