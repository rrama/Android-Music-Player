package me.rrama.musicplayer.fragments;

import android.Manifest;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;

import me.rrama.musicplayer.services.PlaySongs;
import me.rrama.musicplayer.R;
import me.rrama.musicplayer.Slider;
import me.rrama.musicplayer.util.CheckPerm;
import me.rrama.musicplayer.util.DialogGet;
import me.rrama.musicplayer.widgets.QueuedTextView;

public class Currently extends Fragment implements Runnable {

    private Slider slider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar == null) {
            Toast.makeText(getActivity(), "ActionBar null. Currently.", Toast.LENGTH_LONG).show();
        } else {
            actionBar.setTitle(R.string.queued);
        }
        ArrayList<View> views = new ArrayList<>();
        getActivity().getWindow().getDecorView()
                .findViewsWithText(views, getResources().getString(R.string.queued), View.FIND_VIEWS_WITH_TEXT);
        views.get(0).setOnCreateContextMenuListener(this);
        inflater.inflate(R.menu.currently, menu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getActivity().getMenuInflater().inflate(R.menu.currently_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.currently_save:
                saveDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_shuffle:
                slider.playSongs().reshuffle();
                break;
            case R.id.item_purge:
                slider.playSongs().purge();
                break;
        }
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.currently, container, false);
        updateView(view);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.slider = (Slider) getActivity();
    }

    public void serviceRegistered(PlaySongs playSongs) {
        playSongs.addUpdateListener(this);
        run();
    }

    @Override
    public void run() {
        updateView(getView());
    }

    public final void updateView(View view) {
        if (view == null) return;
        ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.playing_layout);
        viewGroup.removeAllViews();
        if (slider == null) return;
        PlaySongs playSongs = slider.playSongs();
        if (playSongs == null) return;
        for (String song : playSongs.getSongs()) {
            String[] tempSplit = song.split("/");
            String songName = tempSplit[tempSplit.length - 1].replaceAll("\\.mp3$", "");
            QueuedTextView qtv = new QueuedTextView(slider, songName);
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100);
            viewGroup.addView(qtv, lp);
        }
        int pos = playSongs.getPos();
        if (pos < 0 || pos >= viewGroup.getChildCount()) return;
        ((QueuedTextView) viewGroup.getChildAt(playSongs.getPos())).setPlaying();
    }

    public void saveDialog() {
        CheckPerm.getPerm(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        DialogGet.getText(getActivity(), "Playlist name:", new DialogGet.AfterGet() {
            @Override
            public void afterGet(String input) {
                save(input);
            }
        });
    }

    /**
     * Save the current queued songs as a playlist.
     * @param fileName The filename to save it as.
     */
    public void save(String fileName) {
        File file = new File(Environment.getExternalStorageDirectory(), "/Documents/Playlists/" + fileName + ".txt");
        try (FileWriter fw = new FileWriter(file)) {
            for (String song : slider.playSongs().getSongs()) {
                fw.write(song + "\n");
            }
        } catch (Exception ex) {
            Toast.makeText(getActivity(), "Error saving playlist..", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(getActivity(), "Playlist '" + fileName + "' saved.", Toast.LENGTH_SHORT).show();
    }

}
