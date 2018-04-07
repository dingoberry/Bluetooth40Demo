package demo.ble.self.com.ble40comdemo;

import android.app.Application;
import android.content.Context;

@SuppressWarnings("ALL")
public class BleApplication extends Application {

    private static Context sInstance;

    static Context getContext() {
        return sInstance;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        sInstance = this;
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onCreate() {
        super.onCreate();
    }
}
