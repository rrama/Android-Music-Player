package me.rrama.musicplayer.fragments;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import me.rrama.musicplayer.R;
import me.rrama.musicplayer.Slider;

public class Playlists extends Fragment implements View.OnClickListener, View.OnLongClickListener {

    private Slider slider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.playlists);
        inflater.inflate(R.menu.playlists, menu);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.playlists, container, false);
        fillView(view);
        return view;
    }

    private ViewGroup getLayoutViewGroup(View view) {
        return (ViewGroup) view.findViewById(R.id.playlists_layout);
    }

    private void fillView(View view) {
        ViewGroup vg = getLayoutViewGroup(view);
        File playlists = new File(Environment.getExternalStorageDirectory(), "/Documents/Playlists");
        String[] lists = playlists.list();
        Arrays.sort(lists);
        for (String playlist : lists) {
            if (!playlist.endsWith(".txt")) continue;
            playlist = playlist.replaceAll("\\.txt$", "");
            TextView tv = new TextView(getActivity());
            tv.setText(playlist);
            tv.setOnClickListener(this);
            tv.setOnLongClickListener(this);
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100);
            vg.addView(tv, lp);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.slider = (Slider) getActivity();
    }

    @Override
    public void onClick(View v) {
        String playlist = ((TextView) v).getText().toString();
        playSongs(playlist);
    }

    private void playSongs(String playlist) {
        ArrayList<String> songs = getPlaylistSongs(playlist);
        if (songs == null) return;
        slider.playSongs().setSongs(songs);
    }

    private ArrayList<String> getPlaylistSongs(String playlist) {
        File file = new File(Environment.getExternalStorageDirectory(), "/Documents/Playlists/" + playlist + ".txt");
        if (!file.exists() || !file.canRead() || !file.isFile()) {
            Toast.makeText(getActivity(), "Playlist unreadable.", Toast.LENGTH_LONG).show();
            return null;
        }
        ArrayList<String> songs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                songs.add(line);
            }
        } catch (Exception ex) {
            Toast.makeText(getActivity(), "Playlist reading error.", Toast.LENGTH_LONG).show();
            return null;
        }
        if (slider.playSongs() == null) {
            Toast.makeText(getActivity(), "Service seems to be down. Restart the app.", Toast.LENGTH_LONG).show();
            return null;
        }
        return songs;
    }

    @Override
    public boolean onLongClick(View v) {
        final String playlist = ((TextView) v).getText().toString();
        PopupMenu popupMenu = new PopupMenu(getActivity(), v);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.item_delete:
                        defDelete(playlist);
                        break;
                    case R.id.item_set:
                        playSongs(playlist);
                        break;
                    case R.id.item_append:
                        ArrayList<String> songs = getPlaylistSongs(playlist);
                        if (songs == null) break;
                        slider.playSongs().addSongs(songs);
                        Toast.makeText(getActivity(), "Appended playlist.", Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.item_set_shuffled:
                        songs = getPlaylistSongs(playlist);
                        if (songs == null) break;
                        Collections.shuffle(songs);
                        slider.playSongs().setSongs(songs);
                        break;
                    case R.id.item_append_shuffled:
                        songs = getPlaylistSongs(playlist);
                        if (songs == null) break;
                        Collections.shuffle(songs);
                        slider.playSongs().addSongs(songs);
                        Toast.makeText(getActivity(), "Appended playlist.", Toast.LENGTH_SHORT).show();
                        break;
                }
                return true;
            }
        });
        popupMenu.inflate(R.menu.playlists_menu);
        popupMenu.show();
        return true;
    }

    private void defDelete(final String playlist) {
        new AlertDialog.Builder(getActivity())
                .setTitle("Delete playlist '" + playlist + "'?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File file = new File(Environment.getExternalStorageDirectory(),
                                "/Documents/Playlists/" + playlist + ".txt");
                        if (file.delete()) {
                            Toast.makeText(getActivity(), "Playlist deleted.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getActivity(), "Playlist failed to delete!", Toast.LENGTH_LONG).show();
                        }
                        getLayoutViewGroup(getView()).removeAllViews();
                        fillView(getView());
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }).show();
    }

}
