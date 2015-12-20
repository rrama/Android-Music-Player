package me.rrama.musicplayer.players;

import java.io.IOException;

import me.rrama.musicplayer.services.PlaySongs;

public abstract class Player {

    protected final PlaySongs playSongs;
    protected Runnable completionListener;
    protected boolean prepared = false;
    protected boolean stopped = true;

    public Player(PlaySongs playSongs) {
        this.playSongs = playSongs;
    }

    public abstract boolean isPlaying();

    public boolean isPrepared() {
        return prepared;
    }

    public boolean isStopped() {
        return stopped;
    }

    public int getDuration() {
        return isPrepared() ? getDurationR() : 0;
    }

    protected abstract int getDurationR();

    public int getCurrentPosition() {
        return isPrepared() ? getCurrentPositionR() : 0;
    }

    protected abstract int getCurrentPositionR();

    public void seekTo(int pos) {
        if (!isPrepared()) return;
        seekToR(pos);
    }

    protected abstract void seekToR(int pos);

    public abstract void setDataSource(String source) throws IOException;

    public abstract void prepare();

    public void playPause() {
        if (isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public void play() {
        if (!isPrepared()) return;
        playR();
        playSongs.updateListeners();
    }

    protected abstract void playR();

    public void pause() {
        pauseR();
        playSongs.updateListeners();
    }

    protected abstract void pauseR();

    public void stop() {
        if (isStopped()) return;
        prepared = false;
        stopped = true;
        stopR();
        playSongs.updateListeners();
    }

    protected abstract void stopR();

    public void reset() {
        prepared = false;
        resetR();
    }

    protected abstract void resetR();

    public void release() {

    }

    public void setOnCompletionListener(Runnable completionListener) {
        this.completionListener = completionListener;
    }

    public abstract int getAudioSessionId();
}
