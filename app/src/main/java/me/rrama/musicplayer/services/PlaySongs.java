package me.rrama.musicplayer.services;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import me.rrama.musicplayer.controls.ControlReceiver;
import me.rrama.musicplayer.controls.notification.SongNotification;
import me.rrama.musicplayer.controls.session.SongSession;
import me.rrama.musicplayer.players.LocalSongPlayer;
import me.rrama.musicplayer.players.Player;
import me.rrama.musicplayer.util.ServiceBinder;

/**
 * The {@link Service} to hold the {@link Player}
 * and add extra capabilities including;
 * <ul>
 *     <li>A song list.</li>
 *     <li>Skipping songs.</li>
 *     <li>Adding/removing songs.</li>
 *     <li>Updating listeners to player/song list changes.</li>
 * </ul>
 */
public class PlaySongs extends Service implements Runnable {

    private Player player;
    private ControlReceiver controlReceiver;
    private final IBinder binder = new ServiceBinder<>(this);
    private final ArrayList<String> songs = new ArrayList<>();
    private int pos = -1;
    private SongSession songSession;
    private SongNotification notification;
    private final ArrayList<Runnable> updateListeners = new ArrayList<>();

    public Player getPlayer() {
        return player;
    }

    /**
     * Sets the output {@link Player}.
     * E.g. to Chromecast or local.
     * @param player The new {@link Player} to output music to.
     */
    public void setPlayer(Player player) {
        this.player.stop();
        this.player.release();
        this.player = player;
    }

    public ArrayList<String> getSongs() {
        return songs;
    }

    /**
     * Gets the position in the current song list of the current song.
     * @return The position of the current song.
     *         <code>-1</code> if no song is yet to be selected from the list.
     */
    public int getPos() {
        return pos;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        player = new LocalSongPlayer(this);
        player.setOnCompletionListener(this);

        controlReceiver = new ControlReceiver(this);
        songSession = new SongSession(this);

        notification = new SongNotification(this);
    }

    /**
     * Overrides the song list with new songs and starts the first one.
     * @param songs The new song list.
     */
    public void setSongs(ArrayList<String> songs) {
        pos = -1;
        player.stop();
        this.songs.clear();
        this.songs.addAll(songs);
        playNext();
    }

    /**
     * Adds songs to the song list.
     * Will play the first song if previous list had finished.
     * @param songs The song list to append.
     */
    public void addSongs(ArrayList<String> songs) {
        boolean first = this.songs.isEmpty();
        this.songs.addAll(songs);
        if (player.isPlaying()) {
            updateListeners();
        } else if (first) {
            playNext();
        }
    }

    public void removeSong(int index) {
        songs.remove(index);
        if (index < pos) {
            pos--;
        } else if (index == pos) {
            if (pos == songs.size()) {
                pos = -1;
                player.stop();
            } else {
                pos--;
            }
            playNext();
            return;
        }
        updateListeners();
    }

    public void skipTo(int pos) {
        this.pos = pos - 1;
        playNext();
    }

    /**
     * Moves a song up <code>pos</code> number of positions.
     * @param pos Can be negative. No need to check for overflow.
     */
    public void moveSongUp(int pos) {
        if (pos < 1) return;
        if (pos >= songs.size()) return;
        Collections.swap(songs, pos, pos - 1);
        if (this.pos == pos) {
            this.pos--;
        } else if (this.pos == pos - 1) {
            this.pos++;
        }
        updateListeners();
    }

    /**
     * Removes all the played songs from the list.
     */
    public void purge() {
        while (pos > 0) {
            songs.remove(0);
            pos--;
        }
        updateListeners();
    }

    /**
     * Sets the song list as a shuffled version of all the songs in the music directory.
     */
    public void shuffle() {
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        String path = folder.getAbsolutePath();
        if (!path.endsWith("/")) {
            path += "/";
        }

        if (folder.list() == null) {
            Toast.makeText(this, "Failed perms. PlaySongs.", Toast.LENGTH_LONG).show();
            return;
        }

        ArrayList<String> songs = new ArrayList<>();
        for (String song : folder.list()) {
            if (!song.endsWith(".mp3")) continue;
            songs.add(path + song);
        }

        Collections.shuffle(songs);
        setSongs(songs);
    }

    /**
     * Shuffles the current song list.
     */
    public void reshuffle() {
        Collections.shuffle(songs);
        pos = -1;
        playNext();
        updateListeners();
    }

    public void playNext() {
        if (songs.isEmpty()) return;
        if (pos + 1 >= songs.size()) return;
        String nextSong = songs.get(++pos);
        String[] tempSplit = nextSong.split("/");
        String songName = tempSplit[tempSplit.length - 1].replaceAll("\\.mp3$", "");
        player.reset();
        try {
            player.setDataSource(nextSong);
            notification.update(songName);
            player.prepare();
        } catch (IOException e) {
            Toast.makeText(this, "Song at '" + nextSong + "' deleted or moved.", Toast.LENGTH_LONG).show();
            playNext();
        }
    }

    public void back() {
        if (pos < 1 || player.getCurrentPosition() > 5000) {
            pos--;
        } else {
            pos -= 2;
        }
        playNext();
    }

    public void stop() {
        player.stop();
        pos = -1;
        songs.clear();
        notification.cancel();
        updateListeners();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        notification.cancel();
        songSession.release();
        unregisterReceiver(controlReceiver);
        player.release();
        super.onDestroy();
    }

    /**
     * On completion.
     */
    @Override
    public void run() {
        playNext();
    }

    public void updateListeners() {
        for (Runnable r : updateListeners) {
            r.run();
        }
    }

    public void addUpdateListener(Runnable r) {
        updateListeners.add(r);
    }

}
