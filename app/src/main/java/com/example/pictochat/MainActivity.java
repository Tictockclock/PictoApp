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
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

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
    Cipher cipher;
    // End of Variable Declaration


    @Override //Functions to run at startup including initializing object instances and setting up button press listeners
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialWork();
        exqListener();
        //Turning off strict mode to allow network communication code in main activity
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    //New message handler function
    Handler handler = new Handler(new Handler.Callback(){
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what){
                case MESSAGE_READ:
                    //If no stored public key from connected device, 1st message is the public key
                    if(connectedDevicePublicKey == null) {
                        byte[] readBuff = (byte[]) msg.obj;
                        receivePublicKey(readBuff);
                        break;
                    }
                    //Else read byte data, decrypt, and add to message history
                    else {
                        byte[] readBuff = decryptData((byte[]) msg.obj);
                        String tempMsg = new String(readBuff, 0);
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
                //If required permissions are missing request permission
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("test discover", "No Fine Location Permissions");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION }, 0);
                }
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("test discover", "No Nearby Wifi Device Permissions");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] { android.Manifest.permission.NEARBY_WIFI_DEVICES }, 1);
                }
                //Listen for available devices
                mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                    @Override //Successfully able to listen to peers
                    public void onSuccess() {
                        connectionStatus.setText("Discovery Started");
                    }

                    @Override //Unable to listen for peers (usually permissions missing or wifip2p not supported)
                    public void onFailure(int reason) {
                        connectionStatus.setText("Discovery Starting Failed");
                        //On fail log reason to debug window
                        Log.d("discoverFail", String.valueOf(reason));
                    }
                });
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() { //Listen for selection of device from available devices
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                //Configure WifiP2pDevice and set device address
                final WifiP2pDevice device = deviceArray[i];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;

                //If missing permissions request them
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("test connect", "no access fine location permission");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION }, 0);

                }
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("test connect", "no access fine location permission");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] { android.Manifest.permission.NEARBY_WIFI_DEVICES }, 1);
                }
                //Connect to selected device
                mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() { //On success display connected device name
                        Toast.makeText(getApplicationContext(), "Connected to " + device.deviceName, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reason) { //On fail
                        Toast.makeText(getApplicationContext(), "Not Connected", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        btnSend.setOnClickListener(new View.OnClickListener() { //Listener for message send button
            @Override
            public void onClick(View v) {
                //Get message text from EditText field, append user name to beginning
                String msg = userName.getText().toString() + ": " + writeMsg.getText().toString();
                //Add message to local chat history
                ChangeMessagesList(msg);
                //If connected to device encrypt using public key and send to message write handler
                if (sendReceive != null) {
                    sendReceive.write(encryptData(msg.getBytes()));
                }
                //No connected device, save chat to local history
                else {
                    Toast.makeText(getApplicationContext(), "No Connected Device", Toast.LENGTH_SHORT).show();
                }
                //Clear edit text field
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
        try {
            cipher = Cipher.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    //Detects changes to list of available peers when device discovery is on, updates list of devices
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
                //update xml list with new list of peers
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

    //On device connection sets device to server or client based on WiFiP2p group owner
    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;

            //If group owner set device as server and start server thread
            if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                connectionStatus.setText("Host");
                serverClass = new ServerClass();
                serverClass.start();
            }
            //If not group owner set device as client and start client thread
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
                //On start initialize the server socket, and send public key to other device
                serverSocket = new ServerSocket(8888);
                socket = serverSocket.accept();
                sendReceive = new SendReceive(socket);
                sendReceive.start();
                sendPublicKey();
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
                //Initialize socket input and output stream threads
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e){
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            //While socket is connected listen for data from input stream
            byte[] buffer = new byte[1024];
            int bytes;

            while (socket != null) {
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        //If new bytes in input stream add to buffer and indicate new message to message handler
                        handler.obtainMessage(MESSAGE_READ, bytes, -1,  buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

        public void write(byte[] bytes) {
            //When called writes given bytes to output stream
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
                //On start connect to server socket on specified port, send over public key to connected device
                socket.connect(new InetSocketAddress(hostAdd,8888),500);
                sendReceive = new SendReceive(socket);
                sendReceive.start();
                sendPublicKey();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        //Constructor for class
        public ClientClass(InetAddress hostAddress){
            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();


        }
    }

    //Handler for modifying local chat history
    private void ChangeMessagesList(String msg){
        //Increase message count
        messageArraySize++;
        //allocate temp array of new size
        String[] tempArray = new String[messageArraySize];
        //copy existing message array to temp array
        for (int i=0; i < messageArraySize-1; i++)
        {
            tempArray[i] = messageArray[i];
        }
        //add new message to end of temp array
        tempArray[messageArraySize-1] = msg;
        //allocate new messageArray and copy temp array back to it
        messageArray = new String[messageArraySize];
        for (int i=0; i < messageArraySize; i++)
        {
            messageArray[i] = tempArray[i];
        }
        //Send changes to xml element
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.text_white_text, messageArray);
        read_msg_box.setAdapter(adapter);
        //Scroll to bottom of chat list
        read_msg_box.setSelection(adapter.getCount() - 1);
    }

    private byte[] encryptData(byte[] data)
    {
        //test logs to check data before encryption
        Log.d("test unencrypted bytes size", "bytesToSend size: " + data.length);
        Log.d("test unencrypted bytes", new String(data, 0));
        try { //Initialize cipher object to encryption mode using connected devices public key
            cipher.init(Cipher.ENCRYPT_MODE, connectedDevicePublicKey);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        byte[] bytesToSend;
        try { //Encrypt message
            bytesToSend = cipher.doFinal(data);
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }
        //test logs to check data after encryption
        Log.d("test RSA bytes size", "bytesToSend size: " + bytesToSend.length);
        Log.d("test RSA bytes", new String(bytesToSend, 0));
        //return encrypted bytes
        return bytesToSend;
    }

    private byte[] decryptData(byte[] bytesReceived)
    {
        //Copy first 256 bytes from bytesReceived to temp array (bytes received is padded with 0's at end)
        byte[] temp = new byte[256];
        System.arraycopy(bytesReceived, 0, temp, 0, 256);
        //Test logs of to check data before decryption
        Log.d("test RSA bytes size", "bytesReceived size: " + temp.length);
        Log.d("test RSA bytes", new String(temp, 0));
        try {//Set cipher object to decryption mode using devices private key
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        byte[] decrypted;
        try { //Decrypt data
            decrypted = cipher.doFinal(temp);
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }
        //Test logs to check data after decryption
        Log.d("test unencrypted bytes size", "bytesToSend size: " + decrypted.length);
        Log.d("test unencrypted bytes", new String(decrypted, 0));
        //return decrypted data
        return decrypted;
    }

    private void sendPublicKey()
    {
        //Test logs to check public key bytes before sending
    //        if(publicKey == null)
    //        {
    //            Log.d("test public key", "publicKey is null");
    //        }
    //        if(sendReceive == null)
    //        {
    //            Log.d("test public key", "sendReceive is null");
    //        }
        //Send the public key to message write handler
        sendReceive.write(publicKey.getEncoded());
    }

    private void receivePublicKey(byte[] bytes)
    {
        //Sets local copy of connected devices public key
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
