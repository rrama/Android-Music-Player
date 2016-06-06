package me.rrama.musicplayer.players;

import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import me.rrama.musicplayer.services.PlaySongs;

public class CastSongPlayer extends Player {

    private final String TAG = "CSP";
    private final GoogleApiClient apiClient;
    private final RemoteMediaPlayer mediaPlayer = new RemoteMediaPlayer();
    private boolean playing = false;

    public CastSongPlayer(PlaySongs playSongs, GoogleApiClient apiClient) {
        super(playSongs);
        this.apiClient = apiClient;
        try {
            Cast.CastApi.setMessageReceivedCallbacks(apiClient,
                    mediaPlayer.getNamespace(), mediaPlayer);
        } catch (IOException e) {
            Log.e(TAG, "Exception while creating media channel", e);
        }
//        mediaPlayer
//                .requestStatus(apiClient)
//                .setResultCallback(
//                        new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
//                            @Override
//                            public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
//                                if (!result.getStatus().isSuccess()) {
//                                    Log.e(TAG, "Failed to request status.");
//                                }
//                            }
//                        });
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }

    @Override
    protected int getDurationR() {
        return (int) mediaPlayer.getStreamDuration();
    }

    @Override
    protected int getCurrentPositionR() {
        return (int) mediaPlayer.getApproximateStreamPosition();
    }

    @Override
    protected void seekToR(int pos) {
        mediaPlayer.seek(apiClient, pos);
    }

    @Override
    public void setDataSource(String source) {
        // Change local files its file server address.
        String mPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath();
        if (source.startsWith(mPath)) {
            try {
                source = "http://192.168.0.158:8080/" + URLEncoder.encode(source.substring(mPath.length() + 1), "UTF8");
            } catch (UnsupportedEncodingException ex) {
                Log.e(TAG, "Encoding URI Exception.", ex);
            }
        }

        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, source);
        MediaInfo mediaInfo = new MediaInfo.Builder(source)
                .setContentType("audio/mp3")
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();
        try {
            mediaPlayer.load(apiClient, mediaInfo, true)
                    .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                        @Override
                        public void onResult(@NonNull RemoteMediaPlayer.MediaChannelResult result) {
                            if (result.getStatus().isSuccess()) {
                                prepared = true;
                                playing = true;
                                playSongs.updateListeners();
                                Log.d(TAG, "Media loaded successfully");
                            }
                        }
                    });
        } catch (IllegalStateException ex) {
            Log.e(TAG, "Problem occurred with media during loading", ex);
        } catch (Exception ex) {
            Log.e(TAG, "Problem opening media during loading", ex);
        }
    }

    @Override
    public void prepare() {}

    @Override
    protected void playR() {
        stopped = false;
        mediaPlayer.play(apiClient);
        playing = true;
    }

    @Override
    protected void pauseR() {
        mediaPlayer.pause(apiClient);
        playing = false;
    }

    @Override
    protected void stopR() {
        mediaPlayer.stop(apiClient);
        playing = false;
    }

    @Override
    protected void resetR() {}

    @Override
    public int getAudioSessionId() {
        return 0;
    }

}
