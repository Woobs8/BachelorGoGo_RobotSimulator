package bachelorgogo.com.robotsimulator;

import static bachelorgogo.com.robotsimulator.RobotProtocol.SEND_COMMANDS.*;
import static bachelorgogo.com.robotsimulator.RobotProtocol.DATA_TAGS.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemClock;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "RobotSimulator";

    //WifiP2P related
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    BroadcastReceiver mReceiver;
    IntentFilter mWiFiDirectIntentFilter;
    WifiP2pDnsSdServiceInfo mServiceInfo;

    //UI elements
    Button sendCustomCmd_btn;
    Button updateStatus_btn;
    EditText customCmd_field;
    SeekBar batteryCharge_sb;
    SeekBar availableStorage_sb;
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
    Button disconnect_btn;

    //Networking related
    InetAddress mDeviceAddress;
    ServerSocket mServerSocket;
    Socket mSocket;
    private int mGroupOwnerPort = 9999;
    private int mLocalUDPPort = 4998;
    private int mLocalTCPPort = 4997;
    private int mLocalHTTPPort = 4996;
    private int mHostPort = -1;
    private int mEstablishConnectionTimeout = 5000; //5 sec * 1000 msec
    private int mPortPacketSize = 4;
    private int mSettingsPacketSize = 255;

    //Network clients
    ReceiveCommandsClient mControlCommandClient;
    SendStatusClient mRobotStatusClient;
    SettingsClient mSettingsClient;
    HttpVideoStreamingServer mVideoServer;

    // System defines
    private final String mSystemName = "RoboGoGo";
    private final String SYSTEM_IDENTIFICATION_STRING = "BachelorGoGo";
    private final String SYSTEM_IDENTIFICATION_SEPARATOR = "*";

    // Simulator variables
    private String mDeviceName = "RoboSim";
    private String mCustomCmd;
    private String mStorageCapacity = "250MB";
    private int mBatteryLevel = 100;
    private boolean mCameraAvailable = false;
    private int mVideoSettings = 1;
    private int mAvailableStorage = 200;
    private boolean mPowerSaveMode = false;
    private boolean mAssistedDrivingMode = false;


    //flags
    private boolean mWiFiDirectEnabled = false;
    private boolean mConnected = false;
    private boolean mDiscoverPeers = false;

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
                String sendString = RobotProtocol.getDataBroadcastString(mDeviceName,Integer.toString(mBatteryLevel), mStorageCapacity,Integer.toString(mAvailableStorage)+"MB",(mCameraAvailable==true ? TRUE : FALSE));
                mRobotStatusClient.sendCommand(sendString);
                Log.d(TAG,sendString);

            }
        });
        updateStatus_btn.setEnabled(false);

        disconnect_btn = (Button) findViewById(R.id.disconnect_btn);
        disconnect_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeWiFiDirectGroup();
            }
        });
        disconnect_btn.setEnabled(false);

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

        availableStorage_sb = (SeekBar) findViewById(R.id.available_storage_sb);
        availableStorage_sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mAvailableStorage = progress;
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

        mControlCommandClient = new ReceiveCommandsClient(mLocalUDPPort, this);
        mVideoServer = new HttpVideoStreamingServer(mLocalHTTPPort);
        discoverPeers();
        Log.d(TAG, "onCreate: Exiting onCreate");
    }

    @Override
    protected void onDestroy() {
        removeWiFiDirectGroup();
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
        registerReceiver(mReceiver, mWiFiDirectIntentFilter);
        if(mManager != null)
            startRegistration();
        super.onResume();
    }

    private void discoverPeers() {
        setDeviceName(SYSTEM_IDENTIFICATION_STRING + SYSTEM_IDENTIFICATION_SEPARATOR + mDeviceName);
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

    public void parseControlInput(String cmd) {
        if(cmd.contains(CMD_CONTROL)) {
            cmd = cmd.substring(cmd.indexOf("*") + 4);

            String mSegmentedRawData[] = cmd.split(";");

            for(int i = 0; i < mSegmentedRawData.length; i++) {   //ignore last segment, as it is junk
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

    public void parseSettingsInput(String settings) {
        if(settings.contains(CMD_SETTINGS)) {
            settings = settings.substring(settings.indexOf("*") + 4);

            String segmentedSettings[] = settings.split(";");

            for (int i = 0; i < segmentedSettings.length; i++) {
                String tempDataSegment[] = segmentedSettings[i].split(":");
                switch (tempDataSegment[0]) {
                    case CAR_NAME_TAG:
                        mDeviceName = tempDataSegment[1];
                        deviceName_txt.setText(mDeviceName);
                        break;
                    case CAMERA_VIDEO_QUALITY_TAG:
                        switch(tempDataSegment[1]) {
                            case "-1":
                                mVideoSettings = 1;
                                videoSettings_txt.setText(getString(R.string.video_quality_default));
                            case "1":
                                mVideoSettings = 1;
                                videoSettings_txt.setText(getString(R.string.video_quality_1));
                                break;
                            case "2":
                                mVideoSettings = 2;
                                videoSettings_txt.setText(getString(R.string.video_quality_2));
                                break;
                            case "3":
                                mVideoSettings = 3;
                                videoSettings_txt.setText(getString(R.string.video_quality_3));
                                break;
                            case "4":
                                mVideoSettings = 4;
                                videoSettings_txt.setText(getString(R.string.video_quality_4));
                                break;
                            default:
                                mVideoSettings = 1;
                                videoSettings_txt.setText(getString(R.string.video_quality_default));
                        }
                        mVideoServer.setVideoSettings(mVideoSettings);
                        break;
                    case POWER_SAVE_DRIVE_MODE_TAG:
                        switch(tempDataSegment[1]) {
                            case "0":
                                mPowerSaveMode = false;
                                powerMode_txt.setText(getString(R.string.setting_disabled));
                                break;
                            case "1":
                                mPowerSaveMode = true;
                                powerMode_txt.setText(getString(R.string.setting_enabled));
                                break;
                        }
                        break;
                    case ASSISTED_DRIVE_MODE_TAG:
                        switch(tempDataSegment[1]) {
                            case "0":
                                mAssistedDrivingMode = false;
                                assistedDrivingMode_txt.setText(getString(R.string.setting_disabled));
                                break;
                            case "1":
                                mAssistedDrivingMode = true;
                                assistedDrivingMode_txt.setText(getString(R.string.setting_enabled));
                                break;
                        }
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
        disconnect_btn.setEnabled(true);
        mDiscoverPeers = false;
        try {
            mVideoServer.start();
        } catch (IOException e) {
            Log.d(TAG, "Error starting http video server");
        }
    }

    private void connectionLost() {
        Log.d(TAG,"Connection lost");
        connectStatus_txt.setText("Waiting for connection...");
        sendCustomCmd_btn.setEnabled(false);
        updateStatus_btn.setEnabled(false);
        disconnect_btn.setEnabled(false);

        if(mControlCommandClient != null) {
            mControlCommandClient.stop();
        }
        if(mSettingsClient != null) {
            mSettingsClient.stop();
        }
        mDiscoverPeers = true;

        mVideoServer.stop();
        discoverPeers();
    }

    private void removeWiFiDirectGroup() {
        Log.d(TAG,"Removing WiFi Direct group");
        mManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(final WifiP2pGroup group) {
                Log.d(TAG,"onGroupInfoAvailable called");
                mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG,"removeGroup successfully called");
                        deletePersistentGroup(group);
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.d(TAG,"Error calling removeGroup");
                    }
                });
            }
        });
    }

    // Set Wi-Fi Direct device name
    // @http://stackoverflow.com/questions/24851961/how-change-the-device-name-in-wifi-direct-p2p
    public void setDeviceName(String devName) {
        try {
            Class[] paramTypes = new Class[3];
            paramTypes[0] = WifiP2pManager.Channel.class;
            paramTypes[1] = String.class;
            paramTypes[2] = WifiP2pManager.ActionListener.class;
            Method setDeviceName = mManager.getClass().getMethod(
                    "setDeviceName", paramTypes);
            setDeviceName.setAccessible(true);

            Object arglist[] = new Object[3];
            arglist[0] = mChannel;
            arglist[1] = devName;
            arglist[2] = new WifiP2pManager.ActionListener() {

                @Override
                public void onSuccess() {
                    Log.d("setDeviceName succeeded", "true");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d("setDeviceName failed", "true");
                }
            };
            setDeviceName.invoke(mManager, arglist);

        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    // @http://stackoverflow.com/questions/23653707/forgetting-old-wifi-direct-connections
    private void deletePersistentGroup(WifiP2pGroup wifiP2pGroup) {
        try {

            Method getNetworkId = WifiP2pGroup.class.getMethod("getNetworkId");
            Integer networkId = (Integer) getNetworkId.invoke(wifiP2pGroup);
            Method deletePersistentGroup = WifiP2pManager.class.getMethod("deletePersistentGroup",
                    WifiP2pManager.Channel.class, int.class, WifiP2pManager.ActionListener.class);
            deletePersistentGroup.invoke(mManager, mChannel, networkId, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.e(TAG, "deletePersistentGroup onSuccess");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "deletePersistentGroup failure: " + reason);
                }
            });
        } catch (NoSuchMethodException e) {
            Log.e("WIFI", "Could not delete persistent group", e);
        } catch (InvocationTargetException e) {
            Log.e("WIFI", "Could not delete persistent group", e);
        } catch (IllegalAccessException e) {
            Log.e("WIFI", "Could not delete persistent group", e);
        }
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
                                                    byte[] rcv = new byte[4];
                                                    in.read(rcv);
                                                    String dataStr = new String(rcv);
                                                    int hostPort = Integer.valueOf(dataStr);
                                                    if (hostPort > 0 && hostPort < 9999)
                                                        mHostPort = hostPort;
                                                    Log.d(TAG, "client resolved to: " + mDeviceAddress + " (port " + mHostPort + ")");
                                                    publishProgress();

                                                    //Send UDP port to client
                                                    byte[] send = Integer.toString(mLocalUDPPort).getBytes();
                                                    out.write(send);

                                                    //Send TCP port to client
                                                    send = Integer.toString(mLocalTCPPort).getBytes();
                                                    out.write(send);

                                                    //Send HTTP port to client
                                                    send = Integer.toString(mLocalHTTPPort).getBytes();
                                                    out.write(send);

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
                                                        if(mSocket != null)
                                                            mSocket.close();
                                                        if(mServerSocket != null)
                                                            mServerSocket.close();
                                                    } catch (IOException e) {
                                                        Log.d(TAG,"Error closing sockets");
                                                        e.printStackTrace();
                                                    }
                                                    if(!mConnected) {
                                                        Log.d(TAG,"Error establishing connection to client. Closing Wifi Direct Group");
                                                        mManager.cancelConnect(mChannel,null);
                                                        removeWiFiDirectGroup();
                                                    }
                                                }
                                                return null;
                                            }

                                            @Override
                                            protected void onProgressUpdate(Void... values) {
                                                connectionEstablished();
                                            }

                                            @Override
                                            protected void onPostExecute(Void aVoid) {
                                                if(!mConnected)
                                                    connectionLost();
                                                super.onPostExecute(aVoid);
                                            }
                                        };

                                        // http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                                            async_client.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                                        else
                                            async_client.execute((Void[]) null);
                                    } else {
                                        mDeviceAddress = info.groupOwnerAddress;
                                        //transmit ip to group owner and exchange ports
                                        AsyncTask<Void, Void, Void> async_transmit_ip = new AsyncTask<Void, Void, Void>() {
                                            @Override
                                            protected Void doInBackground(Void... params) {
                                                try {
                                                    mSocket = new Socket();
                                                    mSocket.setSoTimeout(mEstablishConnectionTimeout);
                                                    mSocket.bind(null);
                                                    SystemClock.sleep(100);
                                                    mSocket.connect((new InetSocketAddress(mDeviceAddress, mGroupOwnerPort)));
                                                    DataInputStream in = new DataInputStream(mSocket.getInputStream());
                                                    DataOutputStream out = new DataOutputStream(mSocket.getOutputStream());

                                                    //Send UDP port to owner
                                                    byte[] send = Integer.toString(mLocalUDPPort).getBytes();
                                                    out.write(send);

                                                    //Send TCP port to owner
                                                    send = Integer.toString(mLocalTCPPort).getBytes();
                                                    out.write(send);

                                                    //Send HTTP port to owner
                                                    send = Integer.toString(mLocalHTTPPort).getBytes();
                                                    out.write(send);

                                                    //read server UDP port
                                                    byte[] rcv = new byte[mPortPacketSize];
                                                    in.read(rcv);
                                                    String dataStr = new String(rcv);
                                                    int hostPort = Integer.valueOf(dataStr);
                                                    if (hostPort > 0 && hostPort < 9999)
                                                        mHostPort = hostPort;
                                                    Log.d(TAG, "host resolved to: " + mDeviceAddress + " (port " + mHostPort + ")");

                                                    //Send settings to remote control
                                                    SettingsObject settings = new SettingsObject(mDeviceName,
                                                                                                Integer.toString(mVideoSettings),
                                                                                                mPowerSaveMode,
                                                                                                mAssistedDrivingMode);
                                                    send = settings.getDataString().getBytes();
                                                    out.write(send);

                                                    publishProgress();
                                                } catch (SocketTimeoutException st) {
                                                    Log.d(TAG,"Attempt to establish connection timed out");
                                                    mConnected = false;
                                                } catch (IOException e) {
                                                    Log.e(TAG, "Error connecting to group owner");
                                                    e.printStackTrace();
                                                    mConnected = false;
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
                                                    if(!mConnected) {
                                                        Log.d(TAG,"Error establishing connection to client. Closing Wifi Direct Group");
                                                        mManager.cancelConnect(mChannel,null);
                                                        removeWiFiDirectGroup();
                                                    }
                                                }
                                                return null;
                                            }

                                            @Override
                                            protected void onProgressUpdate(Void... values) {
                                                connectionEstablished();
                                            }

                                            @Override
                                            protected void onPostExecute(Void aVoid) {
                                                if(!mConnected)
                                                    connectionLost();
                                                super.onPostExecute(aVoid);
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
