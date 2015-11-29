package me.rrama.musicplayer.controls.session;

import android.media.session.MediaSession;
import android.media.session.PlaybackState;

import me.rrama.musicplayer.services.PlaySongs;

/**
 * Used by Android applications that use the default media controls.
 * Headphones with controls also use this.
 */
public class SongSession {

    private final MediaSession mediaSession;
    private final PlaybackState.Builder psb;
    private final PlaySongs playSongs;

    public SongSession(PlaySongs playSongs) {
        this.playSongs = playSongs;
        mediaSession = new MediaSession(playSongs, "MeediaM9");
        mediaSession.setCallback(new SongSessionCallback(playSongs));
        psb = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_STOP);
        updatePlaybackState();
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

        playSongs.addUpdateListener(new Runnable() {
            @Override
            public void run() {
                updatePlaybackState();
            }
        });

        mediaSession.setActive(true);
    }

    public void updatePlaybackState() {
        psb.setState(playSongs.getPlayer().isPlaying() ? PlaybackState.STATE_PLAYING
                        : playSongs.getPlayer().isStopped() ? PlaybackState.STATE_STOPPED
                        : PlaybackState.STATE_PAUSED,
                playSongs.getPlayer().isStopped() ? PlaybackState.PLAYBACK_POSITION_UNKNOWN
                        : playSongs.getPlayer().getCurrentPosition(), 1);
        mediaSession.setPlaybackState(psb.build());
    }

    public void release() {
        mediaSession.release();
    }

}
