package me.rrama.musicplayer.controls.embedded;

import android.view.KeyEvent;
import android.view.View;
import android.widget.MediaController;

import me.rrama.musicplayer.services.PlaySongs;
import me.rrama.musicplayer.Slider;

/**
 * Custom {@link MediaController} to override unpleasant properties.
 */
public class SongController extends MediaController {

    private final Slider slider;

    public SongController(Slider slider, final PlaySongs playSongs, View anchorTo) {
        super(slider);
        this.slider = slider;
        setPrevNextListeners(new OnClickListener() {
            @Override
            public void onClick(View v) {
                playSongs.playNext();
            }
        }, new OnClickListener() {
            @Override
            public void onClick(View v) {
                playSongs.back();
            }
        });
        playSongs.addUpdateListener(new Runnable() {
            @Override
            public void run() {
                show(0);
            }
        });
        setMediaPlayer(new SongControls(slider));
        setAnchorView(anchorTo);
        setEnabled(true);
    }

    /**
     * Disable the auto hide.
     */
    @Override
    public void hide() {}

    public void stop() {
        super.hide();
    }

    /**
     * Have to override this since it swallows the event.
     *
     * @param event The key event to be dispatched.
     * @return <code>true</code> if the event was handled,
     *         <code>false</code> otherwise.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            // Back button pressed.
            slider.handleBack();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

}
