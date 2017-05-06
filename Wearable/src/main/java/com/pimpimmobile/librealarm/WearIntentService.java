package com.pimpimmobile.librealarm;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Wearable;
import com.pimpimmobile.librealarm.shareddata.PreferencesUtil;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class WearIntentService extends IntentService {

    private static final String TAG = "LibreIntent";

    public static GoogleApiClient mGoogleApiClient;
    private static MessageApi.MessageListener remoteListener;
    private PowerManager.WakeLock mWakeLock;

    public WearIntentService() {
        super("WearIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mWakeLock = JoH.getWakeLock(this, "intent-service", 30000);

        final NfcManager nfcManager =
                (NfcManager) this.getSystemService(Context.NFC_SERVICE);
        final NfcAdapter nfcAdapter = nfcManager.getDefaultAdapter();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
                            final Intent i = new Intent(WearIntentService.this, WearActivity.class);
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                        } else {
                            Log.wtf(TAG, "NFC adapter was null or not enabled");
                            Toast.makeText(WearIntentService.this, "NFC Error", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Toast.makeText(WearIntentService.this, "Google API suspended", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Log.e(TAG, "Google Api failed to connect");
                        Toast.makeText(WearIntentService.this, "Google API failed to connect.", Toast.LENGTH_SHORT).show();
                    }
                })
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public void onStart(@Nullable Intent intent, int startId) {
        super.onStart(intent, startId);
        if (PreferencesUtil.shouldUseRoot(this)) {
            Log.d(TAG, "Using ROOT options");
            RootTools.executeScripts(true); // turn it on
        } else {
            Log.d(TAG, "Not using root options");
        }

        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    public static void newInstance(Context context) {
        Intent intent = new Intent(context, WearIntentService.class);
        context.startService(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mWakeLock != null) {
            JoH.releaseWakeLock(mWakeLock);
        }
    }

    public synchronized static void tryToAddListener(MessageApi.MessageListener listener) {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            removeExistingListener();
            Log.d(TAG, "Adding remote listener!");
            Wearable.MessageApi.addListener(mGoogleApiClient, listener);
        } else {
            Log.e(TAG, "Can't add listener as we are not connected!");
        }
    }

    public synchronized static void tryToRemoveListener(MessageApi.MessageListener listener) {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            removeExistingListener();
            Wearable.MessageApi.removeListener(mGoogleApiClient, listener);
        }
    }

    private static void removeExistingListener() {
        if (remoteListener != null) {
            Log.e(TAG, "First removing remote listener!");
            Wearable.MessageApi.removeListener(mGoogleApiClient, remoteListener);
            remoteListener = null;
        }
    }
}