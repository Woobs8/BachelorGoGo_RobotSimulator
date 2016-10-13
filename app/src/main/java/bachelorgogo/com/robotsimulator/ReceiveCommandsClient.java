package bachelorgogo.com.robotsimulator;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

/**
 * Created by THP on 06-10-2016.
 */

public class ReceiveCommandsClient {
    private final String TAG = "ReceiveCommandsClient";
    private int mPacketSize;
    private int mPort;
    private String mReceivedString;
    private DatagramSocket mDatagramSocket;
    private AsyncTask<Void, Void, Void> async_client;
    private int mReceiveTimeout = 5000; //5sec * 1000 msec


    ReceiveCommandsClient(int port, MainActivity activity) {
        mPort = port;
        mDatagramSocket = null;
    }

    public void start() {
        async_client = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                Log.d(TAG,"Started listening for robot status messages");
                while (!isCancelled())
                    listenOnSocket();
                return null;
            }

            protected void onPostExecute(Void result)
            {
                Log.d(TAG,"Stopped listening for robot status messages");
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
        if(async_client != null)
            async_client.cancel(true);
    }

    private void listenOnSocket() {
        try {
            Log.d(TAG,"Opening socket on port " + mPort);
            mDatagramSocket = new DatagramSocket(mPort);
            byte[] packetSizeData = new byte[4];    //Max size = 2^32
            mDatagramSocket.setSoTimeout(mReceiveTimeout);

            //First read size of packet...
            DatagramPacket size_packet = new DatagramPacket(packetSizeData, packetSizeData.length);
            mDatagramSocket.receive(size_packet);
            ByteBuffer sizeBuffer = ByteBuffer.wrap(size_packet.getData()); // big-endian by default
            mPacketSize = sizeBuffer.getInt();
            Log.d(TAG,"Receiving packet of size: " + mPacketSize);

            //...Then the actual packet
            byte[] receiveData = new byte[mPacketSize];
            DatagramPacket recv_packet = new DatagramPacket(receiveData, receiveData.length);
            Log.d(TAG, "Receiving data");
            mDatagramSocket.receive(recv_packet);
            mReceivedString = new String(recv_packet.getData());
            Log.d(TAG, "Received string: " + mReceivedString);
        } catch (SocketTimeoutException se) {
            Log.d(TAG, "Receiving socket timed out");
        } catch (Exception e) {
            Log.e(TAG, "Error occurred while listening on port " + mPort);
            e.printStackTrace();
        } finally {
            Log.d(TAG,"Closing socket on port " + mPort);
            if (mDatagramSocket != null)
            {
                mDatagramSocket.close();
            }
        }
    }
}
