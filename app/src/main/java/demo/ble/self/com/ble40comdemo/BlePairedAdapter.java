package demo.ble.self.com.ble40comdemo;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by tf on 12/20/2017.
 */
class BlePairedAdapter extends BaseAdapter implements View.OnClickListener {

    private ArrayList<BluetoothDevice> mDeviceList = new ArrayList<>();
    private LayoutInflater mInflater;
    private Context mContext;

    BlePairedAdapter(Context cxt) {
        mInflater = LayoutInflater.from(cxt);
        mContext = cxt;
    }

    void update(ArrayList<BluetoothDevice> deviceList) {
        mDeviceList = deviceList;
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
        if (null == convertView) {
            convertView = mInflater.inflate(R.layout.paired_item, parent, false);
            com = convertView.findViewById(R.id.ble_item_com);
            com.setOnClickListener(this);
        } else {
            com = convertView.findViewById(R.id.ble_item_com);
        }
        TextView info = convertView.findViewById(R.id.ble_item_info);
        BluetoothDevice device = mDeviceList.get(position);
        com.setTag(device);
        info.setText(resolveDevice(device));
        return convertView;
    }

    private String resolveDevice(BluetoothDevice device) {
        String name = device.getName();
        return TextUtils.isEmpty(name) ? device.getAddress() : name;
    }

    @Override
    public void onClick(View v) {
        BluetoothDevice device = (BluetoothDevice) v.getTag();

        BluetoothGatt gatt = device.connectGatt(mContext, true, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

                switch (status) {
                    case BluetoothProfile.STATE_CONNECTED:
                        L.i("status:Connected");
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        L.i("status:Disconnected");
                        break;
                    default:
                        L.i("newState=" + newState);
                        break;
                }
            }
        });
        L.i("Connect=" + gatt.connect());
    }
}
