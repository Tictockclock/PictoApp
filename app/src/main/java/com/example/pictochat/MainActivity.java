package com.example.pictochat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.net.wifi.WifiManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    //Init empty variables
    Button btnOnOff, btnDiscover, btnSend; // :)
    ListView listView, read_msg_box;
    TextView connectionStatus;
    EditText writeMsg;
    EditText userName;
    WifiManager wifiManager;
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;
    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    String[] deviceNameArray;
    String[] messageArray;
    int messageArraySize = 0;
    WifiP2pDevice[] deviceArray;
    static final int MESSAGE_READ = 1;

    ServerClass serverClass;
    ClientClass clientClass;
    SendReceive sendReceive;
    StrictMode.ThreadPolicy policy;
    {
        policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
    }

    KeyPairGenerator kpg;
    KeyPair kp;
    Key publicKey, privateKey, connectedDevicePublicKey;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialWork();
        exqListener();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    Handler handler = new Handler(new Handler.Callback(){
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what){
                case MESSAGE_READ:
                    if(connectedDevicePublicKey == null) {
                        byte[] readBuff = (byte[]) msg.obj;
                        receivePublicKey(readBuff);
                        break;
                    }
                    else {
                        byte[] readBuff = decryptData((byte[]) msg.obj);
                        String tempMsg = new String(readBuff, 0, msg.arg1);
                        ChangeMessagesList(tempMsg);
                        break;
                    }
            }
            return true;
        }
    });
    private void exqListener() { //listens for button presses
        btnOnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { //Toggle wifi button
                if (wifiManager.isWifiEnabled()) {
                    wifiManager.setWifiEnabled(false);
                    btnOnOff.setText("Turn ON WiFI");
                } else {
                    wifiManager.setWifiEnabled(true);
                    btnOnOff.setText("Turn OFF WiFi");
                }
            }
        });

        btnDiscover.setOnClickListener(new View.OnClickListener() { //Discover new peers button
            @Override
            public void onClick(View view) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("test discover", "No Fine Location Permissions");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION }, 0);
                }
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("test discover", "No Nearby Wifi Device Permissions");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] { android.Manifest.permission.NEARBY_WIFI_DEVICES }, 1);
                }
                mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        connectionStatus.setText("Discovery Started");
                    }

                    @Override
                    public void onFailure(int reason) {
                        connectionStatus.setText("Discovery Starting Failed");
                        Log.d("discoverFail", String.valueOf(reason));
                    }
                });
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final WifiP2pDevice device = deviceArray[i];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("test connect", "no access fine location permission");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION }, 0);

                }
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("test connect", "no access fine location permission");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] { android.Manifest.permission.NEARBY_WIFI_DEVICES }, 1);
                }
                mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(getApplicationContext(), "Connected to " + device.deviceName, Toast.LENGTH_SHORT).show();
                        sendPublicKey();
                    }

                    @Override
                    public void onFailure(int reason) {
                        Toast.makeText(getApplicationContext(), "Not Connected", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = userName.getText().toString() + ": " + writeMsg.getText().toString();

                ChangeMessagesList(msg);
                if (sendReceive != null) {
                    sendReceive.write(encryptData(msg.getBytes()));
                }
                else {
                    Toast.makeText(getApplicationContext(), "No Connected Device", Toast.LENGTH_SHORT).show();
                }
                writeMsg.getText().clear();
                
            }
        });
    }

    private void initialWork() {
        //Initialize XML element variables
        btnOnOff = findViewById(R.id.onOff);
        btnDiscover = findViewById(R.id.discover);
        btnSend = findViewById(R.id.sendButton);
        listView = findViewById(R.id.peerListView);
        read_msg_box = findViewById(R.id.readMsg);
        connectionStatus = findViewById(R.id.connectionStatus);
        writeMsg = findViewById(R.id.writeMsg);
        userName = findViewById(R.id.displayName);

        //Initialize other variables
        messageArray = new String[messageArraySize];

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        mReceiver = new WifiDirectBroadcastReceiver(mManager, mChannel, this);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        //Initialize Security variables, generate public and private key
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        kpg.initialize(2048);
        kp = kpg.genKeyPair();
        publicKey = kp.getPublic();
        privateKey = kp.getPrivate();
    }

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            //If available peers do not match current peer list, clear and add all peers to list
            if(!peerList.getDeviceList().equals(peers))
            {
                peers.clear();
                peers.addAll(peerList.getDeviceList());

                deviceNameArray = new String[peerList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[peerList.getDeviceList().size()];

                int index = 0;

                for(WifiP2pDevice device : peerList.getDeviceList())
                {
                    deviceNameArray[index] = device.deviceName;
                    deviceArray[index] = device;
                    index++;
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, deviceNameArray);
                listView.setAdapter(adapter);
            }

            //If no peers found
            if(peers.size()==0)
            {
                Toast.makeText(getApplicationContext(), "No Device Found", Toast.LENGTH_SHORT).show();
                return;
            }
        }
    };

    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;

            if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                connectionStatus.setText("Host");
                serverClass = new ServerClass();
                serverClass.start();
            }
            else if (wifiP2pInfo.groupFormed){
                connectionStatus.setText("Client");
                clientClass = new ClientClass(groupOwnerAddress);
                clientClass.start();

            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }
    public class ServerClass extends Thread{
        Socket socket;
        ServerSocket serverSocket;
        @Override
        public void run(){
            try {
                serverSocket = new ServerSocket(8888);
                socket = serverSocket.accept();
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class SendReceive extends Thread{
        private  Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public SendReceive(Socket skt){
            socket = skt;
            try{
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e){
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (socket != null) {
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        handler.obtainMessage(MESSAGE_READ, bytes, -1,  buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    public class ClientClass extends Thread{
        Socket socket;
        String hostAdd;

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAdd,8888),500);
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public ClientClass(InetAddress hostAddress){
            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();


        }
    }

    private void ChangeMessagesList(String msg){
        messageArraySize++;
        String[] tempArray = new String[messageArraySize];
        for (int i=0; i < messageArraySize-1; i++)
        {
            tempArray[i] = messageArray[i];
        }
        tempArray[messageArraySize-1] = msg;
        messageArray = new String[messageArraySize];
        for (int i=0; i < messageArraySize; i++)
        {
            messageArray[i] = tempArray[i];
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.text_white_text, messageArray);
        read_msg_box.setAdapter(adapter);
        read_msg_box.setSelection(adapter.getCount() - 1);
    }

    private byte[] encryptData(byte[] data)
    {
        return data;
    }

    private byte[] decryptData(byte[] data)
    {
        return data;
    }

    private void sendPublicKey()
    {
        if(publicKey == null)
        {
            Log.d("test public key", "publicKey is null");
        }
        if(sendReceive == null)
        {
            Log.d("test public key", "sendReceive is null");
        }
        sendReceive.write(publicKey.getEncoded());
    }

    private void receivePublicKey(byte[] bytes)
    {
        String tempString = new String(bytes, 0);
        Log.d("test public key", tempString);
        try {
            connectedDevicePublicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
