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
import static bachelorgogo.com.robotsimulator.RobotProtocol.SEND_COMMANDS.*;
import static bachelorgogo.com.robotsimulator.RobotProtocol.DATA_TAGS.*;

public class SettingsClient {static final String TAG = "SettingsClient";
    private InetAddress mHostAddress;
    private int mPort;
    private Socket mSocket;
    ServerSocket mServerSocket;
    private AsyncTask<Void, Void, Void> async_client;
    private boolean mSettinsTransmitted = false;
    private boolean mManualStop = false;
    MainActivity mActivity;
    private String mReceivedString;

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
                while (!isCancelled()) {
                    listenOnSocket();
                    publishProgress();
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Void... values) {
                if(mReceivedString != null)
                    mActivity.parseSettingsInput(mReceivedString);
                super.onProgressUpdate(values);
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
                if(mSocket != null)
                    mSocket.close();
                if(mServerSocket != null)
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
            mReceivedString = in.readUTF();
            Log.d(TAG,"Settings received: " + mReceivedString);

            //Write ACK
            out.writeUTF(CMD_ACK);

        } catch (Exception e) {
            if(!mManualStop) {
                mSettinsTransmitted = false;
                Log.e(TAG, "Error occurred while sending settings ");
                e.printStackTrace();
            } else {
                Log.d(TAG,"Socket on port: " + mPort + " closed manually");
            }
        } finally {
            try{
                mSocket.close();
                mServerSocket.close();
            }catch (IOException e){
                Log.d(TAG, "Error closing socket on port " + mPort );
                e.printStackTrace();
            }
        }
    }
}
