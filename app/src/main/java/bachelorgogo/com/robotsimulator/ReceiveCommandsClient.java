package bachelorgogo.com.robotsimulator;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Created by THP on 06-10-2016.
 */

public class ReceiveCommandsClient {
    private final int packetSize = 3;
    private int mPort;
    private String mReceivedString;
    private DatagramSocket mDatagramSocket;
    private AsyncTask<Void, Void, Void> async_client;
    private boolean mReceiveData;
    private MainActivity mActivity;

    ReceiveCommandsClient(int port, MainActivity activity) {
        mPort = port;
        mDatagramSocket = null;
        mReceiveData = false;
        mActivity = activity;
    }

    public void start() {
        mReceiveData = true;
        async_client = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                try {
                    mDatagramSocket = new DatagramSocket(mPort);
                    byte[] receiveData = new byte[packetSize];
                    while (mReceiveData) {
                        DatagramPacket recv_packet = new DatagramPacket(receiveData, receiveData.length);
                        Log.d("ReceiveCommandsClient", "receiving data");
                        mDatagramSocket.receive(recv_packet);
                        mReceivedString = new String(recv_packet.getData());
                        Log.d("ReceiveCommandsClient", "Received string: " + mReceivedString);
                        publishProgress();
                    }
                } catch (Exception e) {
                    Log.e("ReceiveCommandsClient", "Error receiving data");
                    e.printStackTrace();
                } finally {
                    if (mDatagramSocket != null)
                    {
                        mDatagramSocket.close();
                    }
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Void... values) {
                mActivity.xCoord_txt.setText(mReceivedString);
            }

            @Override
            protected void onPostExecute(Void result)
            {
                Log.d("ReceiveCommandsClient","Finished receiving data");
            }
        };
        // http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            async_client.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            async_client.execute((Void[])null);
    }

    public void stop() {
        mReceiveData = false;
    }
}
