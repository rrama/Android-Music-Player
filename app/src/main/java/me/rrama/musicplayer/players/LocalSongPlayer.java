package me.rrama.musicplayer.players;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;

import java.io.IOException;

import me.rrama.musicplayer.services.PlaySongs;

public class LocalSongPlayer extends Player implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener  {

    private final AudioManager audioManager;
    private final MediaPlayer mediaPlayer = new MediaPlayer();

    public LocalSongPlayer(PlaySongs playSongs) {
        super(playSongs);
        this.audioManager = (AudioManager) playSongs.getSystemService(Context.AUDIO_SERVICE);

        mediaPlayer.setWakeMode(playSongs, PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        prepared = false;
        playSongs.updateListeners();
        if (completionListener != null) {
            completionListener.run();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        System.err.println("!!!!ERRE!!!! " + what + " " + extra);
        return false;
    }

    @Override
    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    @Override
    protected void seekToR(int pos) {
        mediaPlayer.seekTo(pos);
    }

    @Override
    public void setDataSource(String source) throws IOException {
        mediaPlayer.setDataSource(source);
    }

    @Override
    public void prepare() {
        mediaPlayer.prepareAsync();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        prepared = true;
        play();
    }

    @Override
    protected int getDurationR() {
        return mediaPlayer.getDuration();
    }

    @Override
    protected int getCurrentPositionR() {
        return mediaPlayer.getCurrentPosition();
    }

    @Override
    protected void playR() {
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            playAudio();
        }
    }

    private void playAudio() {
        stopped = false;
        mediaPlayer.setVolume(1F, 1F);
        if (!isPrepared()) return;
        if (!isPlaying()) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void pauseR() {
        wasPlaying = false;
        if (!isPrepared()) return;
        if (!isPlaying()) return;
        mediaPlayer.pause();
    }

    @Override
    protected void stopR() {
        mediaPlayer.stop();
    }

    @Override
    protected void resetR() {
        mediaPlayer.reset();
    }

    @Override
    public void release() {
        mediaPlayer.release();
    }

    private boolean wasPlaying = false;

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (wasPlaying) {
                    playAudio();
                    playSongs.updateListeners();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                audioManager.abandonAudioFocus(this);
                /* falls through */
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                wasPlaying = isPlaying();
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                wasPlaying = isPlaying();
                mediaPlayer.setVolume(0.1F, 0.1F);
                break;
        }
    }

    @Override
    public int getAudioSessionId() {
        return mediaPlayer.getAudioSessionId();
    }

}
