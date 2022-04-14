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
import android.widget.Toast;

import com.example.asus.Core.base.Torrent;

import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link Files.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link Files#newInstance} factory method to
 * create an instance of this fragment.
 */
public class Files extends Fragment {
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private Torrent filenames;
    Add_torrent mactivity;
    Torrent_Activity mactivity1;
    ArrayList<String>items;
    View row;
    RecyclerView recyclerView;
    String activity;
    private OnFragmentInteractionListener mListener;

    public Files() {
        // Required empty public constructor
    }

    public static Files newInstance(String param1, Torrent filenames) {
        Files fragment = new Files();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putSerializable(ARG_PARAM2, filenames);
        fragment.setArguments(args);
        return fragment;
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            filenames = (Torrent) getArguments().getSerializable(ARG_PARAM2);
        }
        activity= getArguments().getString(ARG_PARAM1);
        if(activity.equals("torrent"))
        {
            mactivity1 = (Torrent_Activity) getActivity();
            mactivity1.setFilesFragment(this);
            items = filenames.getFilenames();
            //items = mactivity1.files;
        }
        else {
            mactivity = (Add_torrent) getActivity();
            mactivity.setFilesFragment(this);
            items = mactivity.files;
        }
    }
    Files_List_Adapter custom_adapter;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        row = inflater.inflate(R.layout.fragment_files, container, false);
        recyclerView = (RecyclerView) row.findViewById(R.id.files_list);
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(getActivity().getApplicationContext()));
        if(items != null && activity.equals("torrent"))
        {
            custom_adapter = new Files_List_Adapter(this.getContext(), items);
            recyclerView.setAdapter(custom_adapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        }
        return row;
    }

    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }
    //for the file window in the add torrent only
    public void update(ArrayList<String> filesnames)
    {
        this.items = filesnames;
        custom_adapter = new Files_List_Adapter(this.getContext(), items);
        recyclerView.setAdapter(custom_adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
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

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(Uri uri);
    }
}
