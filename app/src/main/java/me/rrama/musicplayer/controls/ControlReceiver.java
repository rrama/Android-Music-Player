package me.rrama.musicplayer.controls;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import me.rrama.musicplayer.services.PlaySongs;

/**
 * An {@link BroadcastReceiver} to translate the {@link Intent Intents}
 * into actions on the player.
 */
public class ControlReceiver extends BroadcastReceiver {

    private final PlaySongs ps;

    public ControlReceiver(PlaySongs ps) {
        this.ps = ps;
        IntentFilter filter = new IntentFilter();
        String pack = "me.rrama.musicplayer.";
        filter.addAction(pack + "PLAY");
        filter.addAction(pack + "NEXT");
        filter.addAction(pack + "BACK");
        filter.addAction(pack + "STOP");
        filter.addAction(pack + "SHUFFLE");
        filter.addAction(pack + "OTHER");
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        ps.registerReceiver(this, filter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String[] tempSplit = intent.getAction().split("\\.");
        String action = tempSplit[tempSplit.length - 1].toUpperCase();
        switch (action) {
            case "PLAY":
                ps.getPlayer().playPause();
                break;
            case "NEXT":
                ps.playNext();
                break;
            case "BACK":
                ps.back();
                break;
            case "STOP":
                ps.stop();
                break;
            case "SHUFFLE":
                ps.shuffle();
                break;
            case "HEADSET_PLUG":
                if (intent.getIntExtra("state", -1) == 0) {
                    ps.getPlayer().pause();
                }
                break;
            default:
                Log.d("ControlReceiver", "Unhandled: " + action);
        }
    }

}
