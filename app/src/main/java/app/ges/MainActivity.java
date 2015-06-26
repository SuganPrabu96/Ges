package app.ges;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.ParcelUuid;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;


public class MainActivity extends ActionBarActivity {

    private final String macID = "00:06:66:6D:CB:CC";
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothDevice mDevice = null;
    private Thread mConnectThread = null;
    private Thread mConnectedThread = null;

    private Context context = null;

    void print(String str) {
        Log.i("bd", str);
    }

    android.os.Handler mHandler = new android.os.Handler() {
        @Override
        public void handleMessage(Message msg) {
            byte[] writeBuf = (byte[]) msg.obj;
            int begin = (int)msg.arg1;
            int end = (int)msg.arg2;

            switch(msg.what) {
                case 1:
                    String writeMessage = new String(writeBuf);
                    writeMessage = writeMessage.substring(begin, end);
                    Log.i("bd", writeMessage);
                    break;
            }
        }
    };

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final UUID MY_UUID =  UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        //UUID.randomUUID();
        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                //tmp = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(device,Integer.valueOf(1));
                Class<?> clazz = tmp.getRemoteDevice().getClass();
                Class<?>[] paramTypes = new Class<?>[] {Integer.TYPE};

                Method m = clazz.getMethod("createRfcommSocket", paramTypes);
                Object[] params = new Object[] {Integer.valueOf(1)};

                tmp = (BluetoothSocket) m.invoke(tmp.getRemoteDevice(), params);

            } catch (IOException e) {
                e.printStackTrace();
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            mmSocket = tmp;
        }
        public void run() {
            mBluetoothAdapter.cancelDiscovery();
            try {
                print("Connect thread run");

                Field field = BluetoothSocket.class.getDeclaredField("mPfd");
                field.setAccessible(true);
                ParcelFileDescriptor mPfd = (ParcelFileDescriptor)field.get(mmSocket);
                if(null == mPfd){
                    print("mpfd null");
                    return;
                }

                mmSocket.connect();
                mPfd.close();

                print("connected");
            } catch (IOException connectException) {
                print("Connect thread catch");
                connectException.printStackTrace();
                try {
                    print("Connect thread close socket");
                    mmSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
                return;
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            print("connected thread initialised");
        }
        public void run() {
            print("connected thread run");
            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;
            while (true) {
                try {
                    bytes += mmInStream.read(buffer, bytes, buffer.length - bytes);
                    for(int i = begin; i < bytes; i++) {
                        if(buffer[i] == "\n".getBytes()[0]) {
                            mHandler.obtainMessage(1, begin, i, buffer).sendToTarget();
                            begin = i + 1;
                            if(i == bytes - 1) {
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = getApplicationContext();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            print("Bluetooth not supported");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        mDevice = mBluetoothAdapter.getRemoteDevice(macID);
        ParcelUuid[] list_uuid = mDevice.getUuids();
        print(mDevice.toString());
        mConnectThread = new ConnectThread(mDevice);
        mConnectThread.start();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
