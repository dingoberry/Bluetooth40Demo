package demo.ble.self.com.ble40comdemo;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class BlePairedAdapter extends BaseAdapter implements View.OnClickListener {

    private ArrayList<BluetoothDevice> mDeviceList = new ArrayList<>();
    private LayoutInflater mInflater;
    private Context mContext;

    private UnboundCallback mCallback;
    private ThreadHandler mHandler;

    BlePairedAdapter(Context cxt, ThreadHandler handler) {
        mInflater = LayoutInflater.from(cxt);
        mContext = cxt;
        mHandler = handler;
    }

    void setUnboundCallback(UnboundCallback callback) {
        mCallback = callback;
    }

    void update(ArrayList<BluetoothDevice> deviceList) {
        mDeviceList = deviceList;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mDeviceList.size();
    }

    @Override
    public Object getItem(int position) {
        return mDeviceList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Button com;
        Button unpair;
        Button ble;
        if (null == convertView) {
            convertView = mInflater.inflate(R.layout.paired_item, parent, false);
            com = convertView.findViewById(R.id.ble_item_com);
            unpair = convertView.findViewById(R.id.ble_item_unpair);
            ble = convertView.findViewById(R.id.ble_item_ble);
            ble.setVisibility(Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH ? View.VISIBLE : View.GONE);
            com.setOnClickListener(this);
            unpair.setOnClickListener(this);
            ble.setOnClickListener(this);
        } else {
            com = convertView.findViewById(R.id.ble_item_com);
            unpair = convertView.findViewById(R.id.ble_item_unpair);
            ble = convertView.findViewById(R.id.ble_item_ble);
        }
        TextView info = convertView.findViewById(R.id.ble_item_info);
        BluetoothDevice device = mDeviceList.get(position);
        com.setTag(device);
        unpair.setTag(device);
        info.setText(resolveDevice(device));
        return convertView;
    }

    private String resolveDevice(BluetoothDevice device) {
        String name = device.getName();
        return TextUtils.isEmpty(name) ? device.getAddress() : name;
    }

    private void bleCommunication(BluetoothDevice device) {
        BluetoothGatt gatt = device.connectGatt(mContext, false, new BluetoothGattCallback() {

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                L.i("Discovered Services result = " + status);
                if (BluetoothGatt.GATT_SUCCESS == status) {
                    BluetoothGattService service = gatt.getService(UUID.fromString(Constants.UUID_SERVER));
                    if (null != service) {
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(Constants.UUID_CHAR));
                        if (null != characteristic) {
                            writeMessage(gatt, characteristic);
                        }
                    }
                }
            }

            private void writeMessage(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                gatt.setCharacteristicNotification(characteristic, true);
                characteristic.setValue("From BLE4.0");
                L.i("Write Msg: " + gatt.writeCharacteristic(characteristic));
            }

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        L.i("status:Connected");
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        L.i("status:Disconnected");
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        L.i("status:Disconnecting");
                        break;
                    case BluetoothProfile.STATE_DISCONNECTING:
                        L.i("status:Connecting");
                        break;
                    default:
                        L.i("newState=" + newState);
                        break;
                }
            }
        });
        L.i("Connect=" + gatt.connect());
    }

    private void traditionCommunication(final BluetoothDevice device) {
        mHandler.getSubHandler().post(new Runnable() {
            @Override
            public void run() {
                BluetoothSocket socket = null;
                try {
                    L.i("traditionCommunication");
                    socket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(Constants.UUID_TRADITION_SERVER));
                    socket.connect();
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    writer.println(device.getName());
                    writer.flush();
                    final String msg = reader.readLine();
                    mHandler.getUiHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            ToastUtils.show("Response:" + msg);
                        }
                    });
                } catch (IOException e) {
                    L.e(e);
                } finally {
                    if (null != socket) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            L.e(e);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        BluetoothDevice device = (BluetoothDevice) v.getTag();
        switch (v.getId()) {
            case R.id.ble_item_com:
                traditionCommunication(device);
                break;
            case R.id.ble_item_ble:
                bleCommunication(device);
                break;
            case R.id.ble_item_unpair:
                try {
                    boolean result = (boolean) device.getClass().getDeclaredMethod("removeBond").invoke(device);
                    UnboundCallback callback = mCallback;
                    if (null != callback) {
                        callback.unBound(result);
                    }
                    ToastUtils.show(result ? R.string.unbound_success : R.string.unbound_fail);
                } catch (Exception e) {
                    L.e(e);
                }
                break;
            default:
                break;
        }
    }

    interface UnboundCallback {
        void unBound(boolean success);
    }
}
