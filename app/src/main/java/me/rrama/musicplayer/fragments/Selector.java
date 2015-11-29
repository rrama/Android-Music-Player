package me.rrama.musicplayer.fragments;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import me.rrama.musicplayer.services.PlaySongs;
import me.rrama.musicplayer.R;
import me.rrama.musicplayer.Slider;
import me.rrama.musicplayer.widgets.SongTextView;

public class Selector extends Fragment {

    private Slider slider;
    private Menu menu;
    private boolean crossTickVisible = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        this.menu = menu;
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.music);
        inflater.inflate(R.menu.song_select, menu);
        if (crossTickVisible) {
            crossTickVisible();
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.shuffle:
                slider.playSongs().shuffle();
                return true;
            case R.id.select_all:
                selectAll();
                return true;
            case R.id.cross:
                deselectAll();
                return true;
            case R.id.tick:
                play(false);
                return true;
            case R.id.plus:
                play(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.song_select, container, false);

        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        String[] songs = folder.list();
        Arrays.sort(songs);
        for (String songName : songs) {
            if (!songName.endsWith(".mp3")) continue;
            songName = songName.replaceAll("\\.mp3$", "");
            SongTextView stv = new SongTextView(this, songName);
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100);
            ((LinearLayout) view.findViewById(R.id.song_select_layout)).addView(stv, lp);
        }

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.slider = (Slider) getActivity();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().stopService(new Intent(getActivity(), PlaySongs.class));
    }

    public void selectAll() {
        crossTickVisible();
        for (SongTextView stv : getSongTextViews()) {
            stv.visibleSelection();
            stv.select();
        }
    }

    public void deselectAll() {
        crossTickInvisible();
        for (SongTextView stv : getSongTextViews()) {
            stv.invisibleSelection();
            stv.deselect();
        }
    }

    public void allVisibleSelection() {
        crossTickVisible();
        for (SongTextView stv : getSongTextViews()) {
            stv.visibleSelection();
        }
    }

    public ArrayList<SongTextView> getSongTextViews() {
        ArrayList<SongTextView> stvs = new ArrayList<>();
        LinearLayout lay = (LinearLayout) getView().findViewById(R.id.song_select_layout);
        for (int i = 0; i < lay.getChildCount(); i++) {
            View child = lay.getChildAt(i);
            if (!(child instanceof SongTextView)) continue;
            stvs.add((SongTextView) child);
        }
        return stvs;
    }

    public void crossTickVisible() {
        crossTickVisible = true;
        menu.findItem(R.id.cross).setVisible(true);
        menu.findItem(R.id.plus).setVisible(true);
        menu.findItem(R.id.tick).setVisible(true);
    }

    public void crossTickInvisible() {
        crossTickVisible = false;
        menu.findItem(R.id.cross).setVisible(false);
        menu.findItem(R.id.plus).setVisible(false);
        menu.findItem(R.id.tick).setVisible(false);
    }

    public void play(boolean add) {
        final ArrayList<String> songs = new ArrayList<>();
        String folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath();
        if (!folder.endsWith("/")) {
            folder += "/";
        }
        for (SongTextView stv : getSongTextViews()) {
            if (stv.selected()) {
                songs.add(folder + stv.getSongName() + ".mp3");
            }
        }

        if (slider.playSongs() == null) {
            Toast.makeText(getActivity(), "Service seems to be down. Restart the app.", Toast.LENGTH_LONG).show();
            return;
        }
        if (add) {
            slider.playSongs().addSongs(songs);
            Toast.makeText(getActivity(), "Added.", Toast.LENGTH_SHORT).show();
        } else {
            slider.playSongs().setSongs(songs);
        }
    }

}