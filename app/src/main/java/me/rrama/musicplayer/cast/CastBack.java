package me.rrama.musicplayer.cast;

import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

import me.rrama.musicplayer.cast.server.SimpleWebServer;
import me.rrama.musicplayer.players.CastSongPlayer;
import me.rrama.musicplayer.players.LocalSongPlayer;

public class CastBack extends Cast.Listener implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    
    private static final String TAG = "CastBack";
    private boolean waitingForReconnect;
    private final RouteBack routeBack;
    public boolean applicationStarted = false;
    public String sessionId = null;

    private static SimpleWebServer webServer = null;
    private static final Object LOCK_WEB_SERVER = new Object();

    public CastBack(RouteBack routeBack) {
        this.routeBack = routeBack;
    }

    public static void closeWebServer() {
        if (webServer != null) {
            webServer.stop();
        }
        webServer = null;
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        if (waitingForReconnect) {
            waitingForReconnect = false;
//            reconnectChannels();
        } else {
            try {
                Cast.CastApi
                        .launchApplication(routeBack.apiClient, CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                        .setResultCallback(new RCB());
            } catch (Exception ex) {
                Log.e(TAG, "Failed to launch application", ex);
            }
    
            initialiseWebServer();
        }
    }
    
    public static void initialiseWebServer() {
        if (webServer == null || webServer.isDead()) {
            synchronized (LOCK_WEB_SERVER) {
                if (webServer == null || webServer.isDead()) {
                    webServer = new SimpleWebServer(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC));
                    try {
                        webServer.start();
                        Log.i(TAG, "WebServer started.");
                    } catch (IOException ex) {
                        Log.e(TAG, "WebServer failed to start.", ex);
                    }
                }
            }
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        waitingForReconnect = true;
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
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
        closeWebServer();
    }

    public class RCB implements ResultCallback<Cast.ApplicationConnectionResult> {

        @Override
        public void onResult(@NonNull Cast.ApplicationConnectionResult result) {
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
