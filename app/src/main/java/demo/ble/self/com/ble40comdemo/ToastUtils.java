package demo.ble.self.com.ble40comdemo;

import android.widget.Toast;

class ToastUtils {

    static void show(String msg) {
        Toast.makeText(BleApplication.getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    static void show(int msgRes) {
        Toast.makeText(BleApplication.getContext(), msgRes, Toast.LENGTH_SHORT).show();
    }
}
