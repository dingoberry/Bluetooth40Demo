package demo.ble.self.com.ble40comdemo;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Message;

import java.lang.ref.WeakReference;

/**
 * Created by tf on 12/20/2017.
 */
class ThreadHandler implements Callback {

    private HandlerThread mHt;
    private Handler mSubHandler;
    private Handler mUiHandler;
    private WeakReference<Callback> mRef;

    ThreadHandler(Callback callback) {
        HandlerThread ht = new HandlerThread(this.getClass().getCanonicalName());
        ht.start();
        mHt = ht;

        mSubHandler = new Handler(ht.getLooper(), this);
        mUiHandler = new Handler(this);
        mRef = new WeakReference<>(callback);
    }

    void destroy() {
        mHt.quit();
    }

    Handler getSubHandler() {
        return mSubHandler;
    }

    Handler getUiHandler() {
        return mUiHandler;
    }

    @Override
    public boolean handleMessage(Message msg) {
        Callback callback = mRef.get();
        return null != callback && callback.handleMessage(msg);
    }
}
