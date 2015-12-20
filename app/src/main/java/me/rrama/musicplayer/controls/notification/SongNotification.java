package me.rrama.musicplayer.controls.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import me.rrama.musicplayer.services.PlaySongs;
import me.rrama.musicplayer.R;

/**
 * A {@link Notification} with player controls.
 */
public class SongNotification {

    private final Notification.Builder builder;
    private final RemoteViews nView;
    private final NotificationManager manager;

    public SongNotification(PlaySongs playSongs) {
        this.manager = (NotificationManager) playSongs.getSystemService(Context.NOTIFICATION_SERVICE);
        nView = new RemoteViews(playSongs.getPackageName(), R.layout.notification_controls);

        addPendingIntent(playSongs, R.id.not_play, "PLAY");
        addPendingIntent(playSongs, R.id.not_next, "NEXT");
        addPendingIntent(playSongs, R.id.not_back, "BACK");
        addPendingIntent(playSongs, R.id.not_stop, "STOP");

        builder = new Notification.Builder(playSongs)
                .setSmallIcon(R.drawable.play)
                .setContent(nView)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH);
    }

    private void addPendingIntent(PlaySongs playSongs, int id, String action) {
        PendingIntent intent = PendingIntent.getBroadcast(playSongs, 0, new Intent("me.rrama.musicplayer." + action), 0);
        nView.setOnClickPendingIntent(id, intent);
    }

    public void update(String songName) {
        nView.setTextViewText(R.id.not_songName, songName);
        manager.notify(10, builder.build());
    }

    public void cancel() {
        manager.cancel(10);
    }

}
