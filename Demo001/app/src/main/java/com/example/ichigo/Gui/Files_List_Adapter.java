package com.example.ichigo.Gui;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.util.ArrayList;


public class Files_List_Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    Context context;
    ArrayList<String> items;
    public Files_List_Adapter(Context context, ArrayList<String> items)
    {
        this.context = context;
        this.items = items;
    }
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View row = inflater.inflate(R.layout.file_row, parent,false);
        Element1 element = new Element1(row);
        return element;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ((Element1)holder).textView.setText(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
    class Element1 extends RecyclerView.ViewHolder {
        public TextView textView;
        public CheckBox checkBox;
        RelativeLayout relativeLayout;
        public Element1(View itemView) {
            super(itemView);
            textView = (TextView) itemView.findViewById(R.id.filename);
            checkBox = (CheckBox) itemView.findViewById(R.id.selectfile);
        }
    }
}
