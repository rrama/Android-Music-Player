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

import me.rrama.musicplayer.services.PlaySongs;

public class CastSongPlayer extends Player {

    private final String TAG = "RSP";
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
            source = "192.168.0.158:8080/" + source.substring(mPath.length());
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
                                Log.d(TAG, "Media loaded successfully");
                            }
                        }
                    });
        } catch (IllegalStateException e) {
            Log.e(TAG, "Problem occurred with media during loading", e);
        } catch (Exception e) {
            Log.e(TAG, "Problem opening media during loading", e);
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
