package me.rrama.musicplayer.controls.session;

import android.media.session.MediaSession;

import me.rrama.musicplayer.services.PlaySongs;

/**
 * The link between the player and {@link SongSession}.
 */
public class SongSessionCallback extends MediaSession.Callback {

    private final PlaySongs playSongs;

    public SongSessionCallback(PlaySongs playSongs) {
        this.playSongs = playSongs;
    }

    @Override
    public void onPlay() {
        playSongs.getPlayer().play();
    }

    @Override
    public void onPause() {
        playSongs.getPlayer().pause();
    }

    @Override
    public void onSkipToNext() {
        if (!playSongs.getPlayer().isPrepared()) return;
        playSongs.playNext();
    }

    @Override
    public void onSkipToPrevious() {
        if (!playSongs.getPlayer().isPrepared()) return;
        playSongs.back();
    }

    @Override
    public void onFastForward() {
    }

    @Override
    public void onRewind() {
    }

    @Override
    public void onStop() {
        playSongs.stop();
    }

    @Override
    public void onSeekTo(long pos) {
        playSongs.getPlayer().seekTo((int) pos);
    }

}
