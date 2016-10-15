package bachelorgogo.com.robotsimulator;

import static bachelorgogo.com.robotsimulator.RobotProtocol.SEND_COMMANDS.*;
import static bachelorgogo.com.robotsimulator.RobotProtocol.DATA_TAGS.*;

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
import android.support.v4.content.LocalBroadcastManager;
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
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "RobotSimulator";
    static public final String SETTINGS_RECEIVED = "settings_received";
    static public final String SETTINGS_RECEIVED_KEY = "settings_received_key";
    static public final String CONTROL_INPUT_RECEIVED = "control_input_received";
    static public final String CONTROL_INPUT_RECEIVED_KEY = "control_input_received_key";

    private final String STEERING_XY_COORDINATE = "CS*XY";

    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    BroadcastReceiver mReceiver;
    IntentFilter mWiFiDirectIntentFilter;
    IntentFilter mLocalBroadcastIntentFilter;
    WifiP2pDnsSdServiceInfo mServiceInfo;

    //UI elements
    Button sendCustomCmd_btn;
    Button updateStatus_btn;
    EditText customCmd_field;
    SeekBar batteryCharge_sb;
    Switch cameraAvailability_sw;
    TextView xCoord_txt;
    TextView xCoord_header;
    TextView yCoord_txt;
    TextView yCoord_header;
    TextView deviceName_txt;
    TextView videoSettings_txt;
    TextView powerMode_txt;
    TextView assistedDrivingMode_txt;
    TextView connectStatus_txt;

    InetAddress mDeviceAddress;
    ServerSocket mServerSocket;
    Socket mSocket;
    private int mGroupOwnerPort = 9999;
    private int mLocalUDPPort = 4998;
    private int mLocalTCPPort = 4997;
    private int mHostPort = -1;
    private int mEstablishConnectionTimeout = 5000; //5 sec * 1000 msec

    private final String mSystemName = "eROTIC";
    private String mDeviceName = "RoboGoGo";
    private String mCustomCmd;
    private String mVideoSettings = "";
    private String mPowerSaveMode = "";
    private String mAssistedDrivingMode = "";
    private String mMemRemaining = "1TB";
    private String mMemSpace = "200TB";
    private int mBatteryLevel = 100;
    private boolean mCameraAvailable = false;
    private boolean mWiFiDirectEnabled = false;
    private boolean mConnected = false;
    private boolean mDiscoverPeers = false;

    ReceiveCommandsClient mControlCommandClient;
    SendStatusClient mRobotStatusClient;
    SettingsClient mSettingsClient;

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
                String sendString = RobotProtocol.getDataBroadcastString(mDeviceName,Integer.toString(mBatteryLevel),mMemSpace,mMemRemaining,(mCameraAvailable==true ? TRUE : FALSE));
                mRobotStatusClient.sendCommand(sendString);

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
        xCoord_header = (TextView) findViewById(R.id.coordinate_x_header);
        yCoord_header = (TextView) findViewById(R.id.coordinate_y_header);
        deviceName_txt = (TextView) findViewById(R.id.device_name_txt);
        videoSettings_txt = (TextView) findViewById(R.id.video_settings_txt);
        powerMode_txt = (TextView) findViewById(R.id.power_save_mode_txt);
        assistedDrivingMode_txt = (TextView) findViewById(R.id.assisted_driving_txt);
        connectStatus_txt = (TextView) findViewById(R.id.connection_status_txt);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new MainActivity.WiFiDirectBroadcastReceiver(mManager, mChannel, this);

        //Intent filter with intents receiver checks for
        mWiFiDirectIntentFilter = new IntentFilter();
        mWiFiDirectIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mWiFiDirectIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mWiFiDirectIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        //Intent filter for local broadcasts
        mLocalBroadcastIntentFilter = new IntentFilter();
        mLocalBroadcastIntentFilter.addAction(SETTINGS_RECEIVED);
        mLocalBroadcastIntentFilter.addAction(CONTROL_INPUT_RECEIVED);

        mControlCommandClient = new ReceiveCommandsClient(mLocalUDPPort, MainActivity.this);

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
        unregisterReceiver(mBroadcastReceiver);
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
        registerReceiver(mReceiver, mWiFiDirectIntentFilter);
        registerReceiver(mBroadcastReceiver, mLocalBroadcastIntentFilter);

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

    // Broadcast handler for received Intents.
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            String recv;
            switch (intent.getAction()) {
                case SETTINGS_RECEIVED:
                    recv = intent.getStringExtra(SETTINGS_RECEIVED_KEY);
                    parseSettingsInput(recv);
                    break;
                case CONTROL_INPUT_RECEIVED:
                    recv = intent.getStringExtra(CONTROL_INPUT_RECEIVED_KEY);
                    parseControlInput(recv);
                    break;
            }
        }
        }
    };

    private void parseControlInput(String cmd) {
        if(cmd.contains(CMD_CONTROL)) {
            cmd.substring(cmd.indexOf("*") + 2);

            String mSegmentedRawData[] = cmd.split(";");

            for(int i = 0; i < mSegmentedRawData.length; i++) {
                String tempDataSegment[] = mSegmentedRawData[i].split(":");
                switch (tempDataSegment[0]) {
                    case STEERING_X_COORDINATE_TAG:
                        Log.d(TAG, "Setting x coordinate");
                        xCoord_txt.setText(tempDataSegment[1]);
                        break;
                    case STEERING_Y_COORDINATE_TAG:
                        Log.d(TAG, "Setting y coordinate");
                        yCoord_txt.setText(tempDataSegment[1]);
                        break;
                    case STEERING_POWER_TAG:
                        Log.d(TAG, "Setting power coordinate");
                        xCoord_header.setText("Power:");
                        xCoord_txt.setText(tempDataSegment[1]);
                        break;
                    case SEERING_ANGLE_TAG:
                        Log.d(TAG, "Setting angle coordinate");
                        yCoord_header.setText("Power:");
                        yCoord_txt.setText(tempDataSegment[1]);
                        break;
                }

            }
        } else {
            Log.d(TAG,"Unknown control packet");
        }
    }

    private void parseSettingsInput(String settings) {
        if(settings.contains(CMD_SETTINGS)) {
            settings.substring(settings.indexOf("*") + 2);

            String segmentedSettings[] = settings.split(";");

            for (int i = 0; i < segmentedSettings.length; i++) {
                String tempDataSegment[] = segmentedSettings[i].split(":");
                switch (tempDataSegment[0]) {
                    case CAR_NAME_TAG:
                        deviceName_txt.setText(tempDataSegment[1]);
                        break;
                    case CAMERA_VIDEO_QUALITY_TAG:
                        videoSettings_txt.setText(tempDataSegment[1]);
                        break;
                    case POWER_SAVE_DRIVE_MODE_TAG:
                        powerMode_txt.setText(tempDataSegment[1]);
                        break;
                    case ASSERTED_DRIVE_MODE_TAG:
                        assistedDrivingMode_txt.setText(tempDataSegment[1]);
                        break;
                }
            }
        }
        else {
            Log.d(TAG,"Unknown control packet");
        }
    }

    private void connectionEstablished() {
        Log.d(TAG,"Connection established");
        mRobotStatusClient = new SendStatusClient(mDeviceAddress,mHostPort);
        mControlCommandClient.start();
        mSettingsClient = new SettingsClient(mLocalTCPPort,this);
        mSettingsClient.start();
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
        if(mSettingsClient != null) {
            mSettingsClient.stop();
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
                                                    mServerSocket.setSoTimeout(mEstablishConnectionTimeout);
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

                                                    //Send UDP port to client
                                                    out.writeUTF(Integer.toString(mLocalUDPPort));

                                                    //Send TCP port to client
                                                    out.writeUTF(Integer.toString(mLocalTCPPort));

                                                } catch (SocketTimeoutException st) {
                                                    Log.d(TAG,"Attempt to establish connection timed out");
                                                    mConnected = false;
                                                } catch (IOException e) {
                                                    Log.e(TAG, "Error listening for client IPs");
                                                    e.printStackTrace();
                                                    mConnected = false;
                                                } finally {
                                                    try {
                                                        Log.d(TAG,"Closing socket " + mGroupOwnerPort);
                                                        mSocket.close();
                                                        mServerSocket.close();
                                                    } catch (IOException e) {
                                                        Log.d(TAG,"Error closing sockets");
                                                        e.printStackTrace();
                                                    }
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
                                                    mSocket.setSoTimeout(mEstablishConnectionTimeout);
                                                    mSocket.bind(null);
                                                    mSocket.connect((new InetSocketAddress(mDeviceAddress, mGroupOwnerPort)));
                                                    DataInputStream in = new DataInputStream(mSocket.getInputStream());
                                                    DataOutputStream out = new DataOutputStream(mSocket.getOutputStream());

                                                    //Send UDP port to server
                                                    out.writeUTF(Integer.toString(mLocalUDPPort));

                                                    //Send TCP port to server
                                                    out.writeUTF(Integer.toString(mLocalTCPPort));

                                                    //read server UDP port
                                                    String dataStr = in.readUTF();
                                                    int hostPort = Integer.valueOf(dataStr);
                                                    if (hostPort > 0 && hostPort < 9999)
                                                        mHostPort = hostPort;
                                                } catch (SocketTimeoutException st) {
                                                    Log.d(TAG,"Attempt to establish connection timed out");
                                                    mConnected = false;
                                                    connectionLost();
                                                } catch (IOException e) {
                                                    Log.e(TAG, "Error connecting to group owner");
                                                    e.printStackTrace();
                                                    mConnected = false;
                                                    connectionLost();
                                                } finally {
                                                    try {
                                                        if (mSocket != null)
                                                            mSocket.close();
                                                        if(mServerSocket != null)
                                                            mServerSocket.close();
                                                    } catch (IOException e) {
                                                        Log.d(TAG,"Error closing sockets");
                                                        e.printStackTrace();
                                                    }
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
