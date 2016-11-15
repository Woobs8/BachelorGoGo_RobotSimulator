package bachelorgogo.com.robotsimulator;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by THP on 04-10-2016.
 */

public class SendStatusClient {
    private final String TAG = "SendStatusClient";
    private InetAddress mHostAddress;
    private int mPort;
    private String mCommand;
    private DatagramSocket mDatagramSocket;
    private AsyncTask<Void, Void, Void> async_client;
    private int mPacketSize = 255;

    SendStatusClient(InetAddress host, int port) {
        mHostAddress = host;
        mPort = port;
        mDatagramSocket = null;
    }

    public void sendCommand(String command)
    {
        mCommand = command;
        async_client = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                try
                {
                    if(mCommand.length() <= mPacketSize) {
                        mDatagramSocket = new DatagramSocket();

                        byte[] packet = new byte[mPacketSize];
                        Arrays.fill( packet, (byte) 0 );
                        byte[] message = mCommand.getBytes();
                        System.arraycopy(message,0,packet,0,message.length);
                        DatagramPacket dp = new DatagramPacket(packet, packet.length, mHostAddress, mPort);
                        mDatagramSocket.send(dp);
                    }
                }
                catch (Exception e)
                {
                    Log.d(TAG,"Error sending status");
                    e.printStackTrace();
                }
                finally
                {
                    if (mDatagramSocket != null)
                    {
                        mDatagramSocket.close();
                    }
                }
                return null;
            }

            protected void onPostExecute(Void result)
            {
                Log.d(TAG,"Finished sending status");
                super.onPostExecute(result);
            }
        };
        // http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            async_client.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            async_client.execute((Void[])null);
    }
}
