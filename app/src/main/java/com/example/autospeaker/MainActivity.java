package com.example.autospeaker;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.CallOption;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.OnCallback;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerOption;

public class MainActivity extends AppCompatActivity {

    private Peer peer;
    private String TAG = getClass().getSimpleName();
    private String currentId;
    private TextView idTextView;

    private ListView listView;
    private MyAdapter adapter;
    private List<String> idList = new ArrayList<String>();
    private MediaConnection connection;
    private static final int RECORD_AUDIO_REQUEST_ID = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        idTextView = (TextView) findViewById(R.id.id_textview);
        listView = (ListView) findViewById(R.id.listview);

        adapter = new MyAdapter(this, 0, idList);
        listView.setAdapter(adapter);

        PeerOption options = new PeerOption();
        // BuildConfigが認識されない時は、BuildConfig.javaでSyncする。
        options.key = BuildConfig.SKYWAY_API_KEY;
        options.domain = BuildConfig.SKKYWAY_HOST;
        peer = new Peer(this, options);
        Navigator.initialize(peer);

        showCurrentPeerId();
        refreshPeerList();

        peer.on(Peer.PeerEventEnum.CALL, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (object instanceof MediaConnection){
                    MediaConnection connection = (MediaConnection) object;

                    if(MainActivity.this.connection != null){
                        Log.d(TAG, "connection is already created");
                        connection.close();
                        return;
                    }

                    MediaStream stream = MainActivity.this.getMediaStream();
                    if(stream == null){
                        Log.d(TAG, "CALL Event received but MediaConnection is null");
                    }
                    connection.answer(stream);
                    setConnectionCallback(connection);
                    MainActivity.this.connection = connection;
                    Log.d(TAG, "CALL Event is Received and Set");
                }
            }
        });



        Button refreshBtn = (Button) findViewById(R.id.refresh_btn);
        refreshBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                refreshPeerList();
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l){
                String selectedPeerId = idList.get(i);
                if(selectedPeerId == null){
                    Log.d(TAG, "Selected PeerId == null");
                    return;
                }
                Log.d(TAG, "SelectedPeerId: " + selectedPeerId);
                call(selectedPeerId);
            }
        });

        Button closeBtn = (Button) findViewById(R.id.close_btn);
        closeBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                closeConnection();
            }
        });

        checkAudioPermission();
    }

    private void checkAudioPermission(){
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            Log.d(TAG, "Manifest.permission.RECORD_AUDIO is not GRANTED");
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.RECORD_AUDIO)){
                Log.d(TAG, "shouldShowRequestPermissionRationale = false");
            }else{
                Log.d(TAG, "request Permission RECORD_AUDIO");
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_REQUEST_ID);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RECORD_AUDIO_REQUEST_ID: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "request Permission RECORD_AUDIO GRANTED!");
                } else {
                    Log.d(TAG, "request Permission RECORD_AUDIO DENIED!");
                }
                return;
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(connection != null){
            closeConnection();
        }
        if (peer != null && !peer.isDestroyed()){
            peer.destroy();
            peer = null;
        }
    }

    private void showCurrentPeerId() {
        peer.on(Peer.PeerEventEnum.OPEN, new OnCallback() {
            @Override
            public void onCallback(Object o) {
                if (o instanceof String) {
                    currentId = (String) o;
                    Log.d(TAG, "currentId: " + currentId);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            idTextView.setText(currentId);
                        }
                    });
                }
            }
        });
    }

    private void refreshPeerList() {
        Log.d(TAG, "Refreshing");
        peer.listAllPeers(new OnCallback() {
            @Override
            public void onCallback(Object o) {
                if (o instanceof JSONArray) {
                    JSONArray array = (JSONArray) o;
                    idList.clear();
                    for (int i = 0; i < array.length(); i++) {
                        try {
                            String id = array.getString(i);
                            idList.add(id);
                            Log.d(TAG, "Fetched PeerId: " + id);
                        } catch (JSONException e) {
                            Log.e(TAG, "Parse ListAllPeer", e);
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        });
    }

    private void call(String peerId){
        Log.d(TAG, "Calling to id: " + peerId);
        if (peer == null){
            Log.d(TAG, "Call but peer is null");
            return;
        }

        if(peer.isDestroyed() || peer.isDisconnected()){
            Log.i(TAG, "Call but peer is not active");
            return;
        }

        if (connection != null) {
            Log.d(TAG, "Call but connection is already created");
            return;
        }

        MediaStream stream = getMediaStream();

        if(stream == null){
            Log.d(TAG, "Call but MediaConnection is null");
            return;
        }

        CallOption option = new CallOption();
        option.metadata = "test";
        MediaConnection connection = peer.call(peerId, stream, option);

        if(connection == null){
            Log.d(TAG, "Call but MediaConnection is null");
            return;
        }

        setConnectionCallback(connection);

        this.connection = connection;
        Log.d(TAG, "connection started!");
    }

    private MediaStream getMediaStream() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.videoFlag = false;
        constraints.audioFlag = true;
        return Navigator.getUserMedia(constraints);
    }

    private void setConnectionCallback(MediaConnection connection){
        connection.on(MediaConnection.MediaEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "Close Event is Received");
                closeConnection();
            }
        });
    }

    private void closeConnection(){
        if(connection != null){
            connection.close();
            MainActivity.this.connection = null;
            Log.d(TAG, "Connection is Closed");
        }
    }

    private class MyAdapter extends ArrayAdapter<String> {
        private LayoutInflater inflater;

        MyAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull List<String> objects){
            super(context, resource, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent){
            View view = inflater.inflate(R.layout.list_item, null, false);
            TextView textView = (TextView) view.findViewById(R.id.item_textview);
            String name = getItem(position);
            textView.setText(name);
            return view;
        }
    }
}