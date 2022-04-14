package com.example.ichigo.Gui;

import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.asus.Core.base.Torrent;

import static android.app.Activity.RESULT_OK;


public class DetailsFragment extends Fragment {
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;
    Torrent torrent;
    Add_torrent mActivity;
    String name = "nono";
    View view;


    private OnFragmentInteractionListener mListener;

    public DetailsFragment() {
        // Required empty public constructor
    }


    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment Detailsfragment.
     */
    public static DetailsFragment newInstance(String param1, String param2) {
        DetailsFragment fragment = new DetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = (Add_torrent) getActivity();
        mActivity.setDetailsFragment(this);

        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_detailsfragment, container, false);
        Button mButton = (Button) view.findViewById(R.id.addnewtorrent);
        final EditText torrentnameedit = (EditText) view.findViewById(R.id.nameedittext);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = String.valueOf(torrentnameedit.getText());
                Intent i = new Intent(getActivity(), MainActivity.class);
                i.putExtra("F", name);
                i.putExtra("path",path);
                getActivity().setResult(RESULT_OK, i);
                getActivity().finish();
            }
        });
        ImageButton location = (ImageButton) view.findViewById(R.id.savebutton);
        location.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addCategory(Intent.CATEGORY_DEFAULT);
                startActivityForResult(i, 1);
            }
        });
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = String.valueOf(torrentnameedit.getText());
                Intent i = new Intent(getActivity(), MainActivity.class);
                i.putExtra("F", name);
                i.putExtra("torrent", torrent);
                i.putExtra("path",path);
                getActivity().setResult(RESULT_OK, i);
                getActivity().finish();
            }
        });
        //Button add = (Button) view.findViewById(R.id.addnewtorrent);

        /*add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(,TorrentWindow.class);
                startActivity(i);
            }
        });*/

        return view;

    }

    String path = "Download";

    @Override
    public void onActivityResult(int requestcode, int resultcode, Intent data) {
        switch (requestcode) {
            case 1:
                if (resultcode == RESULT_OK) {
                    Log.i("info","the path befor modify"+data.getData().getPath());
                    path =  data.getData().getPath().substring(data.getData().getPath().lastIndexOf(":")+1);
                    Log.i("info", "the uri for the directory chosen is: ================ " + path);
                    EditText location = (EditText) view.findViewById(R.id.downloadlocationedit);
                    location.setText(path);
                }
        }
    }

    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    public void updateGUI(Add_torrent.torrentdetails data, Torrent torrent) {
        this.torrent = torrent;
        Toast.makeText(getContext(), data.creatorname, Toast.LENGTH_SHORT).show();
        Log.i("info", "this is  test for pasing data to a fragment ============================");
        EditText torrentnameedit = (EditText) view.findViewById(R.id.nameedittext);
        torrentnameedit.setText(data.torrentname);

        EditText locationsite = (EditText) view.findViewById(R.id.downloadlocationedit);
        locationsite.setText(path);

        TextView count = (TextView) view.findViewById(R.id.filecountvalue);
        count.setText(String.valueOf(data.filecount));

        TextView size = (TextView) view.findViewById(R.id.sizevalue);
        data.filesize = data.filesize / 1000000;
        String size1 = String.valueOf(data.filesize) + "MB";
        size.setText(size1);
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