package amirz.aidlbridge;

import android.content.Context;
import android.content.Intent;

public class SmartspaceBroadcastReceiver extends Forwarder {

    @Override
    protected void forwardIntent(Context context, Intent i) {
        super.forwardIntent(context, i);

        // Also forward this to SystemUI
        forwardIntentToPackage(context, i, "com.android.systemui");
    }
}
