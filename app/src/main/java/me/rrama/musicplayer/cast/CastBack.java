package me.rrama.musicplayer.cast;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import me.rrama.musicplayer.players.CastSongPlayer;
import me.rrama.musicplayer.players.LocalSongPlayer;

public class CastBack extends Cast.Listener implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    
    private String TAG = "MPCC";
    private boolean waitingForReconnect;
    private final RouteBack routeBack;
    public boolean applicationStarted = false;
    public String sessionId = null;

    public CastBack(RouteBack routeBack) {
        this.routeBack = routeBack;
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        if (waitingForReconnect) {
            waitingForReconnect = false;
//            reconnectChannels();
        } else {
            try {
                Cast.CastApi
                        .launchApplication(routeBack.apiClient, CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID, false)
                        .setResultCallback(new RCB());
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch application", e);
            }
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        waitingForReconnect = true;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "Failed.");
        teardown();
    }

    @Override
    public void onApplicationStatusChanged() {
        if (routeBack.apiClient != null) {
            Log.d(TAG, "onApplicationStatusChanged: "
                    + Cast.CastApi.getApplicationStatus(routeBack.apiClient));
        }
    }

    @Override
    public void onVolumeChanged() {
        if (routeBack.apiClient != null) {
            Log.d(TAG, "onVolumeChanged: " + Cast.CastApi.getVolume(routeBack.apiClient));
        }
    }

    @Override
    public void onApplicationDisconnected(int errorCode) {
        Log.d(TAG, "ADis");
        teardown();
    }
    
    public void teardown() {
        Log.d(TAG, "teardown");
        if (routeBack.apiClient != null) {
            if (applicationStarted) {
                if (routeBack.apiClient.isConnected() || routeBack.apiClient.isConnecting()) {
                    Cast.CastApi.stopApplication(routeBack.apiClient, sessionId);
//                    try {
//                        if (mHelloWorldChannel != null) {
//                            Cast.CastApi.removeMessageReceivedCallbacks(
//                                    mApiClient,
//                                    mHelloWorldChannel.getNamespace());
//                            mHelloWorldChannel = null;
//                        }
//                    } catch (IOException e) {
//                        Log.e(TAG, "Exception while removing channel", e);
//                    }
                    routeBack.apiClient.disconnect();
                }
                applicationStarted = false;
            }
            routeBack.apiClient = null;
        }
        routeBack.device = null;
        waitingForReconnect = false;
        sessionId = null;

        // Set our local player back on.
        routeBack.slider.playSongs().setPlayer(new LocalSongPlayer(routeBack.slider.playSongs()));
    }

    public class RCB implements ResultCallback<Cast.ApplicationConnectionResult> {

        @Override
        public void onResult(Cast.ApplicationConnectionResult result) {
            Status status = result.getStatus();
            if (status.isSuccess()) {
//                ApplicationMetadata applicationMetadata =
//                        result.getApplicationMetadata();
                sessionId = result.getSessionId();
//                String applicationStatus = result.getApplicationStatus();
//                boolean wasLaunched = result.getWasLaunched();
                applicationStarted = true;
                routeBack.slider.playSongs().setPlayer(new CastSongPlayer(routeBack.slider.playSongs(), routeBack.apiClient));
            } else {
                Log.d(TAG, "CDCSe?" + status.getStatusMessage() + status.getStatusCode() + status.toString());
                teardown();
            }
        }

    }

}
