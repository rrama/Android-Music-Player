package me.rrama.musicplayer.cast;

import android.support.v7.media.MediaRouter;
import android.util.Log;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.common.api.GoogleApiClient;

import me.rrama.musicplayer.Slider;

public class RouteBack extends MediaRouter.Callback {

    private static final String TAG = "RBm9";
    public CastDevice device;
    public GoogleApiClient apiClient;
    private final CastBack castBack = new CastBack(this);
    public final Slider slider;

    public RouteBack(Slider slider) {
        this.slider = slider;
    }

    @Override
    public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
        device = CastDevice.getFromBundle(info.getExtras());
        if (device == null) return;

        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                .builder(device, castBack)
                .setVerboseLoggingEnabled(true);

        apiClient = new GoogleApiClient.Builder(slider)
                .addApi(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(castBack)
                .addOnConnectionFailedListener(castBack)
                .build();

        apiClient.connect();
    }

    @Override
    public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
        Log.d(TAG, "Unsel");
        castBack.teardown();
        device = null;
    }
}