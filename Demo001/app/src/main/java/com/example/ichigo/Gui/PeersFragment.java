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

import com.example.asus.Core.peer.SharingPeer;

import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link PeersFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link PeersFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PeersFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "list_of_peers";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public ArrayList<SharingPeer> list_of_peers;
    public ArrayList<String> peer_ip = new ArrayList<>();
    public ArrayList<Integer> port = new ArrayList<>();
    public Torrent_Activity mActivity;
    RecyclerView recyclerView;

    View view;
    private OnFragmentInteractionListener mListener;

    public PeersFragment() {
        // Required empty public constructor
    }

    // TODO: Rename and change types and number of parameters
    public static PeersFragment newInstance(ArrayList<SharingPeer> list_peers) {
        PeersFragment fragment = new PeersFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PARAM1,list_peers);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
             list_of_peers = (ArrayList<SharingPeer>) getArguments().getSerializable(ARG_PARAM1);
            for(SharingPeer g:list_of_peers)
            {
                peer_ip.add(g.getIp());
                port.add(g.getPort());
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        view = inflater.inflate(R.layout.fragment_peers, container, false);
        recyclerView = (RecyclerView) view.findViewById(R.id.peer_list);
        if(peer_ip != null)
        {
            Peers_list_adapter adapter = new Peers_list_adapter(this.getContext(), peer_ip, port);
            recyclerView.setAdapter(adapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        }
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(getActivity().getApplicationContext()));
        return  view;
    }

    // TODO: Rename method, update argument and hook method into UI event
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
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }
}
