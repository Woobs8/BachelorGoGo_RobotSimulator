package bachelorgogo.com.robotsimulator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "RobotSimulator";

    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;
    WifiP2pDnsSdServiceInfo mServiceInfo;

    //UI elements
    Button sendCustomCmd_btn;
    Button updateStatus_btn;
    EditText customCmd_field;
    SeekBar batteryCharge_sb;
    Switch cameraAvailability_sw;
    TextView xCoord_txt;
    TextView yCoord_txt;
    TextView deviceName_txt;
    TextView videoSettings_txt;
    TextView powerMode_txt;
    TextView assistedDrivingMode_txt;
    TextView connectStatus_txt;

    InetAddress mDeviceAddress;
    ServerSocket mServerSocket;
    Socket mSocket;
    private int mGroupOwnerPort = 9999;
    private int mLocalPort = 4998;
    private int mHostPort = -1;

    private final String mSystemName = "eROTIC";
    private String mDeviceName = "RoboGoGo";
    private String mCustomCmd;
    private String mVideoSettings = "";
    private String mPowerSaveMode = "";
    private String mAssistedDrivingMode = "";
    private int mBatteryLevel = 100;
    private boolean mCameraAvailable = false;
    private boolean mWiFiDirectEnabled = false;
    private boolean mConnected = false;
    private boolean mDiscoverPeers = false;

    ReceiveCommandsClient mControlCommandClient;
    SendStatusClient mRobotStatusClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sendCustomCmd_btn = (Button) findViewById(R.id.send_custom_cmd_btn);
        sendCustomCmd_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mRobotStatusClient != null && mCustomCmd != null) {
                    mRobotStatusClient.sendCommand(mCustomCmd);
                }
            }
        });
        sendCustomCmd_btn.setEnabled(false);

        updateStatus_btn = (Button) findViewById(R.id.update_status_btn);
        updateStatus_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
        updateStatus_btn.setEnabled(false);

        customCmd_field = (EditText) findViewById(R.id.custom_cmd_field);
        customCmd_field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mCustomCmd = s.toString();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        batteryCharge_sb = (SeekBar) findViewById(R.id.battery_status_sb);
        batteryCharge_sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mBatteryLevel = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        cameraAvailability_sw = (Switch) findViewById(R.id.camera_sw);
        cameraAvailability_sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mCameraAvailable = isChecked;
            }
        });

        xCoord_txt = (TextView) findViewById(R.id.x_coord_txt);
        yCoord_txt = (TextView) findViewById(R.id.y_coord_txt);
        deviceName_txt = (TextView) findViewById(R.id.device_name_txt);
        videoSettings_txt = (TextView) findViewById(R.id.video_settings_txt);
        powerMode_txt = (TextView) findViewById(R.id.power_save_mode_txt);
        assistedDrivingMode_txt = (TextView) findViewById(R.id.assisted_driving_txt);
        connectStatus_txt = (TextView) findViewById(R.id.connection_status_txt);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new MainActivity.WiFiDirectBroadcastReceiver(mManager, mChannel, this);

        //Intent filter with intents receiver checks for
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        mControlCommandClient = new ReceiveCommandsClient(mLocalPort, MainActivity.this);

        discoverPeers();
    }

    @Override
    protected void onDestroy() {
        mManager.removeGroup(mChannel,null);
        mControlCommandClient.stop();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        // Unregister since the activity is paused.
        unregisterReceiver(mReceiver);
        if(mManager != null)
            mManager.removeLocalService(mChannel, mServiceInfo, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {}

                @Override
                public void onFailure(int reason) {}
            });
        super.onPause();
    }

    @Override
    protected void onResume() {
        registerReceiver(mReceiver, mIntentFilter);
        if(mManager != null)
            startRegistration();
        super.onResume();
    }

    private void discoverPeers() {
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"Peers successfully discovered");
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG,"Failed to discover peers");
            }
        });
    }

    private void connectionEstablished() {
        Log.d(TAG,"Connection established");
        mRobotStatusClient = new SendStatusClient(mDeviceAddress,mHostPort);
        mControlCommandClient.start();
        connectStatus_txt.setText(mDeviceAddress.toString() + " (" + mHostPort + ")");
        sendCustomCmd_btn.setEnabled(true);
        updateStatus_btn.setEnabled(true);
        mDiscoverPeers = false;
    }

    private void connectionLost() {
        Log.d(TAG,"Connection lost");
        connectStatus_txt.setText("Waiting for connection...");
        sendCustomCmd_btn.setEnabled(false);
        updateStatus_btn.setEnabled(false);

        if(mControlCommandClient != null) {
            mControlCommandClient.stop();
        }
        mDiscoverPeers = true;
        discoverPeers();
    }

    private void startRegistration() {
        //  Create a string map containing information about your service.
        Map record = new HashMap();
        record.put("device_name", mDeviceName);

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        mServiceInfo = WifiP2pDnsSdServiceInfo.newInstance("_"+mSystemName, "_bachelorgogo_ctrl", record);
        Log.d(TAG,"Registering network service");
        // Add the local service, sending the service info, network channel,
        // and listener that will be used to indicate success or failure of
        // the request.
        mManager.addLocalService(mChannel, mServiceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"Succesfully added network service");
            }

            @Override
            public void onFailure(int arg0) {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                Log.d(TAG,"Failed to add network service");
            }
        });
    }

    public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
        private WifiP2pManager mManager;
        private WifiP2pManager.Channel mChannel;
        private MainActivity mActivity;

        public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel,
                                           MainActivity activity) {
            super();
            this.mManager = manager;
            this.mChannel = channel;
            this.mActivity = activity;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Check to see if Wi-Fi is enabled and notify appropriate activity
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    mWiFiDirectEnabled = true;
                } else {
                    mWiFiDirectEnabled = false;
                }
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                // Respond to new connection or disconnections
                NetworkInfo networkState = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                // Check if we connected or disconnected.
                if (networkState.isConnected()) {
                    //Establish connection if not already connected
                    if(!mConnected) {
                        mConnected = true;
                        mManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener() {
                            @Override
                            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                                if (info.groupFormed) {
                                    Log.d(TAG, "Group formed");
                                    if (info.isGroupOwner) {
                                        Log.d(TAG, "is group owner");
                                        //Listen for clients and exchange ports
                                        AsyncTask<Void, Void, Void> async_client = new AsyncTask<Void, Void, Void>() {
                                            @Override
                                            protected Void doInBackground(Void... params) {
                                                try {
                                                    mServerSocket = new ServerSocket(mGroupOwnerPort);
                                                    Log.d(TAG, "Listening for clients on port " + Integer.toString(mGroupOwnerPort));
                                                    mSocket = mServerSocket.accept();
                                                    mDeviceAddress = mSocket.getInetAddress();
                                                    DataOutputStream out = new DataOutputStream(mSocket.getOutputStream());
                                                    DataInputStream in = new DataInputStream(mSocket.getInputStream());

                                                    //read client port
                                                    String dataStr = in.readUTF();
                                                    int hostPort = Integer.valueOf(dataStr);
                                                    if (hostPort > 0 && hostPort < 9999)
                                                        mHostPort = hostPort;
                                                    Log.d(TAG, "client resolved to: " + mDeviceAddress + " (port " + mHostPort + ")");
                                                    publishProgress();

                                                    //Send port to client
                                                    out.writeUTF(Integer.toString(mLocalPort));

                                                    mSocket.close();
                                                    mServerSocket.close();
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                                return null;
                                            }

                                            @Override
                                            protected void onProgressUpdate(Void... values) {
                                                connectionEstablished();
                                            }
                                        };

                                        // http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                                            async_client.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                                        else
                                            async_client.execute((Void[]) null);
                                    } else {
                                        mDeviceAddress = info.groupOwnerAddress;
                                        Log.d(TAG, "host resolved to: " + mDeviceAddress + " (port " + mHostPort + ")");
                                        //transmit ip to group owner and exchange ports
                                        AsyncTask<Void, Void, Void> async_transmit_ip = new AsyncTask<Void, Void, Void>() {
                                            @Override
                                            protected Void doInBackground(Void... params) {
                                                try {
                                                    mSocket = new Socket();
                                                    mSocket.bind(null);
                                                    mSocket.connect((new InetSocketAddress(mDeviceAddress, mGroupOwnerPort)), 500);
                                                    DataInputStream in = new DataInputStream(mSocket.getInputStream());
                                                    DataOutputStream out = new DataOutputStream(mSocket.getOutputStream());

                                                    //Send port to server
                                                    out.writeUTF(Integer.toString(mLocalPort));

                                                    //read server port
                                                    String dataStr = in.readUTF();
                                                    int hostPort = Integer.valueOf(dataStr);
                                                    if (hostPort > 0 && hostPort < 9999)
                                                        mHostPort = hostPort;

                                                    mSocket.close();
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                                return null;
                                            }
                                        };
                                        // http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                                            async_transmit_ip.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                                        else
                                            async_transmit_ip.execute((Void[]) null);
                                    }
                                }
                            }
                        });
                    }
                } else {
                    mConnected = false;
                    connectionLost();
                }
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                // Respond to this device's wifi state changing
            } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
                // Peer discovery stopped or started
                int discovery = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1);
                if (discovery == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    Log.d(TAG, "Peer discovery started");
                    mDiscoverPeers = true;
                }
                //Continuously discover peers if enabled
                else if (discovery == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                    Log.d(TAG, "Peer discovery stopped");
                    if (mDiscoverPeers)
                        discoverPeers();
                }
            }
        }
    }
}
