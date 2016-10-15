package bachelorgogo.com.robotsimulator;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Created by rasmus on 10/13/2016.
 */

public class SettingsClient {static final String TAG = "SettingsClient";
    private InetAddress mHostAddress;
    private int mPort;
    private Socket mSocket;
    ServerSocket mServerSocket;
    private AsyncTask<Void, Void, Void> async_client;
    private boolean mSettinsTransmitted = false;
    private boolean mManualStop = false;
    MainActivity mActivity;

    SettingsClient(int port, MainActivity activity) {
        mPort = port;
        mActivity = activity;
    }

    public void start() {
        mManualStop = false;
        async_client = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                Log.d(TAG,"Started listening for settings");
                while (!isCancelled())
                    listenOnSocket();
                return null;
            }

            protected void onPostExecute(Void result)
            {
                Log.d(TAG,"Stopped listening for settings");
                super.onPostExecute(result);
            }
        };
        // http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            async_client.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
        else
            async_client.execute((Void[]) null);
    }

    public void stop() {
        mManualStop = true;
        if(async_client != null) {
            async_client.cancel(true);
            try {
                mSocket.close();
                mServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void listenOnSocket()
    {
        try {
            mServerSocket = new ServerSocket(mPort);
            Log.d(TAG, "Listening for clients on port " + Integer.toString(mPort));
            mSocket = mServerSocket.accept();
            DataOutputStream out = new DataOutputStream(mSocket.getOutputStream());
            DataInputStream in = new DataInputStream(mSocket.getInputStream());

            //Read settings from client
            String recv = in.readUTF();
            Log.d(TAG,"Settings received: " + recv);
            //Broadcast received data
            Intent notifyActivity = new Intent(MainActivity.SETTINGS_RECEIVED);
            notifyActivity.putExtra(MainActivity.SETTINGS_RECEIVED_KEY, recv);
            LocalBroadcastManager.getInstance(mActivity.getApplicationContext()).sendBroadcast(notifyActivity);

            //Write ACK
            out.writeUTF("ACK");

        } catch (Exception e) {
            mSettinsTransmitted = false;
            Log.e(TAG, "Error occurred while sending settings ");
            e.printStackTrace();
        } finally {
            try{
                mSocket.close();
            }catch (IOException e){
                Log.d(TAG, "Error closing socket on port " + mPort );
                e.printStackTrace();
            }
        }
    }
}
