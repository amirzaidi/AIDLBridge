package amirz.aidlbridge;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.google.android.libraries.launcherclient.ILauncherOverlay;

public class BridgeService extends Service {
    private static final String TAG = "BridgeService";
    private ILauncherOverlay.Stub mOverlay;

    @Override
    public void onCreate() {
        super.onCreate();
        mOverlay = new LauncherFeed(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind LauncherFeed");
        return mOverlay;
    }
}
