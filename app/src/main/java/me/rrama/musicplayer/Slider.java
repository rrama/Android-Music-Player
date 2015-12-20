package me.rrama.musicplayer;

import android.Manifest;
import android.app.Fragment;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.gms.cast.CastMediaControlIntent;

import me.rrama.musicplayer.cast.CastBack;
import me.rrama.musicplayer.cast.RouteBack;
import me.rrama.musicplayer.controls.embedded.SongController;
import me.rrama.musicplayer.fragments.Currently;
import me.rrama.musicplayer.fragments.Playlists;
import me.rrama.musicplayer.fragments.Selector;
import me.rrama.musicplayer.services.PlaySongs;
import me.rrama.musicplayer.util.CheckPerm;
import me.rrama.musicplayer.util.ServiceBinder;

/**
 * The main activity.
 * A pager holding all the fragment activities.
 * Holds the {@link PlaySongs} service.
 */
public class Slider extends AppCompatActivity {

    private PlaySongs playSongs;
    private final ServiceConnection connection = new PlaySongsConnect();
    private SongController songController;
    private ViewPager viewPager;
    private final Selector selector = new Selector();
    private final Currently currently = new Currently();
    private final Playlists playlists = new Playlists();
    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    private RouteBack routeBack = new RouteBack(this);

    public PlaySongs playSongs() {
        return playSongs;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.slider);

        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(new SliderAdapter());
        viewPager.setCurrentItem(1);

        Intent intent = new Intent(this, PlaySongs.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        startService(intent);

        mediaRouter = MediaRouter.getInstance(getApplicationContext());
        mediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .build();
    }

    /**
     * Adds the Chromecast button.
     * @param menu The options menu in which you place your items.
     * @return <code>true</code>.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.slider, menu);

        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider =
                (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(this.mediaRouteSelector);
        return true;
    }

    /**
     * Must use this to handle the back button,
     * since {@link android.widget.MediaController} steals it.
     */
    public void handleBack() {
        if (viewPager.getCurrentItem() == 1) {
            moveTaskToBack(true);
        } else {
            viewPager.setCurrentItem(1);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        CheckPerm.getPerm(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        CheckPerm.getPerm(this, Manifest.permission.WAKE_LOCK);
        mediaRouter.addCallback(mediaRouteSelector, routeBack, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    @Override
    public void onStop() {
        if (songController != null) {
            songController.stop();
        }
        mediaRouter.removeCallback(routeBack);
        CastBack.closeWebServer();
        super.onStop();
    }

    @Override
    public void onRestart() {
        super.onRestart();
        if (songController != null) {
            songController.show(0);
        }
    }

    @Override
    public void onDestroy() {
        unbindService(connection);
        super.onDestroy();
    }

    /**
     * Selects which {@link Fragment} should be selected for showing.
     */
    private class SliderAdapter extends FragmentPagerAdapter {

        public SliderAdapter() {
            super(getFragmentManager());
        }

        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case 0:
                    return playlists;
                case 1:
                    return selector;
                case 2:
                    return currently;
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

    }

    /**
     * The {@link ServiceConnection connection}
     * for the {@link PlaySongs} {@link android.app.Service}.
     */
    private class PlaySongsConnect implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!(service instanceof ServiceBinder)) return;
            Service serv = ((ServiceBinder) service).getService();
            if (!(serv instanceof PlaySongs)) return;
            playSongs = (PlaySongs) serv;
            if (songController != null) {
                songController.stop();
            }
            songController = new SongController(Slider.this, playSongs, findViewById(R.id.pager));
            songController.show(0);
            currently.serviceRegistered(playSongs);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("SERVICE-m9", "Service disconnect");
        }

    }

}
