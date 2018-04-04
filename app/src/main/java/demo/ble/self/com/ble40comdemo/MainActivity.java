package demo.ble.self.com.ble40comdemo;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends Activity implements Handler.Callback {

    private static final int REQ_CODE = 0x111;

    private static final int MSG_LOAD_PAIRED = 0x1;
    private static final int MSG_DISCOVER_DEVICES = 0x2;
    private static final int MSG_SCAN_DEVICES = 0x3;
    private static final int MSG_DEVICE_LIST_UPDATE = 0x4;

    private ListView mBleList;
    private BleListAdapter mAdapter;
    private BluetoothManager mBleManager;
    private BluetoothAdapter mBleAdapter;
    private ThreadHandler mTh;

    private ListView mPairedList;
    private BlePairedAdapter mPairedAdapter;

    private ArrayList<BluetoothDevice> mDeviceList;

    private Object mScanCallback;

    private BroadcastReceiver mReceiver;

    @SuppressWarnings("NullableProblems")
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (REQ_CODE == requestCode && null != grantResults) {
            for (int grantResult : grantResults) {
                if (PackageManager.PERMISSION_GRANTED != grantResult) {
                    Toast.makeText(this, R.string.no_permission, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (REQ_CODE == requestCode && RESULT_OK != resultCode) {
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissionIfNeed();

        mBleManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (null == mBleManager) {
            Toast.makeText(this, R.string.not_support_ble, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mBleAdapter = mBleManager.getAdapter();
        if (null == mBleAdapter) {
            Toast.makeText(this, R.string.not_support_ble, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (!mBleAdapter.isEnabled()) {
            L.i("Enable bluetooth");

            if (!mBleAdapter.enable()) {
                Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(i, REQ_CODE);
            }
        }
        mDeviceList = new ArrayList<>();
        mAdapter = new BleListAdapter();
        mBleList = findViewById(R.id.ble_list);
        mBleList.setAdapter(mAdapter);

        mPairedList = findViewById(R.id.ble_paired_list);
        mPairedAdapter = new BlePairedAdapter(this);
        mPairedList.setAdapter(mPairedAdapter);

        mTh = new ThreadHandler(this);
        mTh.getSubHandler().sendEmptyMessage(MSG_DISCOVER_DEVICES);
        mTh.getUiHandler().sendEmptyMessageDelayed(MSG_LOAD_PAIRED, 100);
        mTh.getUiHandler().sendEmptyMessageDelayed(MSG_DEVICE_LIST_UPDATE, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != mTh) {
            mTh.destroy();
        }
        if (null != mBleAdapter) {
            if (mBleAdapter.isDiscovering()) {
                mBleAdapter.cancelDiscovery();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBleAdapter.getBluetoothLeScanner().stopScan((ScanCallback) mScanCallback);
            } else {
                mBleAdapter.stopLeScan((LeScanCallback) mScanCallback);
            }
        }
        if (null != mReceiver) {
            unregisterReceiver(mReceiver);
        }
    }

    private void checkPermissionIfNeed() {
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.not_supported_to_4_0, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(Manifest.permission.BLUETOOTH)) {
            L.i("Request permission");
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN}, REQ_CODE);
        }
    }

    private void onFound(BluetoothDevice device) {
        L.i("onFound=" + device.toString());
        synchronized (mDeviceList) {
            for (BluetoothDevice d : mDeviceList) {
                if (d.getAddress().equals(device.getAddress())) {
                    return;
                }
            }
            mDeviceList.add(device);
        }
    }

    private void scanDevices() {
        L.i("scanDevices");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ScanCallback scanCallback = new ScanCallback() {
                @Override
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                public void onScanResult(int callbackType, ScanResult result) {
                    onFound(result.getDevice());
                }

                @Override
                public void onScanFailed(int errorCode) {
                    L.i("onScanFailed errorCode=" + errorCode);
                }
            };
            mBleAdapter.getBluetoothLeScanner().startScan(scanCallback);
            L.i("scan with new API");
            mScanCallback = scanCallback;
        } else {
            LeScanCallback scanCallback = new LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                    onFound(device);
                }
            };
            mBleAdapter.startLeScan(scanCallback);
            L.i("scan with old API");
            mScanCallback = scanCallback;
        }
    }

    private void discoverDevice() {
        L.i("discoverDevice");
        mBleAdapter.startDiscovery();
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    onFound((BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
                }
            }
        };
        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
    }

    private void print(Set<BluetoothDevice> list) {
        if (null == list) {
            L.i("empty");
            return;
        }
        for (BluetoothDevice b : list) {
            L.i("Print:" + mAdapter.resolveDevice(b));
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_LOAD_PAIRED:
//                L.i("GATT");
//                print(mBleManager.getConnectedDevices(BluetoothProfile.GATT));
//                L.i("GATT_SERVER");
                print(mBleAdapter.getBondedDevices());
                mPairedAdapter.update(new ArrayList<>(mBleAdapter.getBondedDevices()));
                break;
            case MSG_DISCOVER_DEVICES:
                discoverDevice();
                break;
            case MSG_SCAN_DEVICES:
                scanDevices();
                break;
            case MSG_DEVICE_LIST_UPDATE:
                ArrayList<BluetoothDevice> deviceList;
                synchronized (mDeviceList) {
                    deviceList = new ArrayList<>(mDeviceList);
                }
                mAdapter.update(deviceList);
                mAdapter.notifyDataSetChanged();
                if (isFinishing()) {
                    break;
                }
                mTh.getUiHandler().sendEmptyMessageDelayed(MSG_DEVICE_LIST_UPDATE, 1000);
                break;
            default:
                break;
        }
        return true;
    }

    private class BleListAdapter extends BaseAdapter {

        private ArrayList<BluetoothDevice> mDeviceList = new ArrayList<>();

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
            if (null == convertView) {
                convertView = getLayoutInflater().inflate(R.layout.ble_item, parent, false);
            }
            TextView info = convertView.findViewById(R.id.ble_item_info);
            info.setText(resolveDevice(mDeviceList.get(position)));
            return convertView;
        }

        private String resolveDevice(BluetoothDevice device) {
            String name = device.getName();
            return TextUtils.isEmpty(name) ? device.getAddress() : name;
        }
    }
}
