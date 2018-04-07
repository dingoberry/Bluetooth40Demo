package demo.ble.self.com.ble40comdemo;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.UUID;

public class DeviceService extends Service implements Handler.Callback {

    private ThreadHandler mTh;

    private BluetoothServerSocket mServerSocket;

    public DeviceService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mTh = new ThreadHandler(this);
        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (null == bm) {
            stopSelf();
            return;
        }

        try {
            mServerSocket = bm.getAdapter()
                    .listenUsingInsecureRfcommWithServiceRecord(Constants.NAME_TRADITION_SERVER,
                            UUID.fromString(Constants.UUID_TRADITION_SERVER));
        } catch (IOException e) {
            L.e(e);
            stopSelf();
        }
        mTh.getSubHandler().sendEmptyMessage(0);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH) {
            // Create BLE4.0 Server
            new Thread() {
                @Override
                public void run() {
                    createBle4Server();
                }
            }.start();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mTh.destroy();
        try {
            mServerSocket.close();
        } catch (IOException e) {
            L.e(e);
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        BluetoothSocket socket = null;
        try {
            L.i("Start to wait for message!");
            socket = mServerSocket.accept();
            String line = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
            L.i("Read message:" + line);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            writer.println("Get client name:" + line);
            writer.flush();
        } catch (IOException e) {
            L.e(e);
        } finally {
            mTh.getSubHandler().sendEmptyMessage(0);
            if (null != socket) {
                try {
                    socket.close();
                } catch (IOException e) {
                    L.e(e);
                }
            }
        }
        return true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    private void createBle4Server() {
        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (null != bm) {
            Callback callback = new Callback();
            BluetoothGattServer server = bm.openGattServer(this, callback);
            callback.mServer = server;
            BluetoothGattService service = new BluetoothGattService(UUID.fromString(Constants.UUID_SERVER),
                    BluetoothGattService.SERVICE_TYPE_PRIMARY);
            BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString(Constants.UUID_DES),
                    BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ |
                            BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED);
            BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString(Constants.UUID_CHAR),
                    BluetoothGattCharacteristic.PROPERTY_BROADCAST |
                            BluetoothGattCharacteristic.PROPERTY_READ |
                            BluetoothGattCharacteristic.PROPERTY_WRITE |
                            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ |
                            BluetoothGattCharacteristic.PERMISSION_WRITE);
            characteristic.addDescriptor(descriptor);
            service.addCharacteristic(characteristic);
            server.addService(service);
            L.i("onStartCommand:openGattServer");
        }
    }

    private static class Callback extends BluetoothGattServerCallback {

        private BluetoothGattServer mServer;

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId,
                                                int offset, BluetoothGattCharacteristic characteristic) {
            L.i("onCharacteristicReadRequest");
            mServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            L.i("onCharacteristicWriteRequest");
            mServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            L.i("onCharacteristicWriteRequest");
            mServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            L.i("onCharacteristicWriteRequest");
            mServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }
    }
}
