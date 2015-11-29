package me.rrama.musicplayer.controls.embedded;

import android.app.Activity;
import android.widget.MediaController;

import me.rrama.musicplayer.Slider;

/**
 * The link between the player and {@link SongController}.
 */
public class SongControls extends Activity implements MediaController.MediaPlayerControl {

    private final Slider slider;

    public SongControls(Slider slider) {
        this.slider = slider;
    }

    @Override
    public void start() {
        slider.playSongs().getPlayer().play();
    }

    @Override
    public void pause() {
        slider.playSongs().getPlayer().pause();
    }

    @Override
    public int getDuration() {
        return slider.playSongs().getPlayer().getDuration();
    }

    @Override
    public int getCurrentPosition() {
        return slider.playSongs().getPlayer().getCurrentPosition();
    }

    @Override
    public void seekTo(int pos) {
        slider.playSongs().getPlayer().seekTo(pos);
    }

    @Override
    public boolean isPlaying() {
        return slider.playSongs().getPlayer().isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        if (!slider.playSongs().getPlayer().isPrepared()) return 0;
        return 100;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return slider.playSongs().getPlayer().getAudioSessionId();
    }

}
