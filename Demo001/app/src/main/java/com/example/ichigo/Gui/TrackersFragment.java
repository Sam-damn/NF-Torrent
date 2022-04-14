package com.example.ichigo.Gui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Set;


public class TrackersFragment extends Fragment {
    private static final String ARG_PARAM1 = "param1";

    private String mParam1;
    private String mParam2;
    public Torrent_Activity mActivity;
    RecyclerView recyclerView;
    ArrayList<URI> trackers;
    View view;
    private OnFragmentInteractionListener mListener;

    public TrackersFragment() {
    }

    public static TrackersFragment newInstance(ArrayList<URI> trackers) {
        TrackersFragment fragment = new TrackersFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PARAM1, (Serializable) trackers);
        //args.putString(ARG_PARAM1, param1);
        //args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity=(Torrent_Activity) getActivity();
        if (getArguments() != null) {
            trackers = (ArrayList<URI>) getArguments().getSerializable(ARG_PARAM1);
            //mParam1 = getArguments().getString(ARG_PARAM1);
            //mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_trackers, container, false);
        recyclerView = (RecyclerView) view.findViewById(R.id.tracker_list);
        if(trackers != null)
        {
            Tracker_List_Adapter tracker_list_adapter = new Tracker_List_Adapter(this.getContext() , trackers);
            recyclerView.setAdapter(tracker_list_adapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        }
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(getActivity().getApplicationContext()));
        return  view;   }

    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(Uri uri);
    }
}
