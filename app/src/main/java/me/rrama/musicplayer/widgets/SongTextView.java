package me.rrama.musicplayer.widgets;

import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import me.rrama.musicplayer.R;
import me.rrama.musicplayer.fragments.Selector;

/**
 * A song listed in the {@link Selector} with added features.
 * Including;
 * <ul>
 *     <li>Selecting multiple songs.</li>
 *     <li>Bringing up the option to append the song.</li>
 * </ul>
 */
public class SongTextView extends RelativeLayout implements View.OnLongClickListener {

    public final TextView tv;
    public final ImageView iv;
    private final Selector selector;
    private boolean selected = false;

    public SongTextView(final Selector selector, String songName) {
        super(selector.getActivity());
        this.selector = selector;

        tv = new TextView(selector.getActivity());
        tv.setText(songName);

        iv = new ImageView(selector.getActivity());
        iv.setVisibility(GONE);
        iv.setImageResource(R.drawable.checkbox);
        iv.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSelect();
            }
        });


        tv.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (iv.getVisibility() == VISIBLE) {
                    toggleSelect();
                } else {
                    select();
                    selector.play(false);
                    deselect();
                }
            }
        });

        LayoutParams lp = new LayoutParams(69, 66);
        lp.addRule(ALIGN_PARENT_END);
        addView(tv);
        addView(iv, lp);


        setOnLongClickListener(this);
        tv.setOnLongClickListener(this);
    }

    @Override
    public boolean onLongClick(View v) {
        selector.allVisibleSelection();
        toggleSelect();
        return true;
    }

    public String getSongName() {
        return tv.getText().toString();
    }

    public boolean selected() {
        return selected;
    }

    public void toggleSelect() {
        if (selected) {
            deselect();
        } else {
            select();
        }
    }

    public void select() {
        selected = true;
        iv.setImageResource(R.drawable.checkbox_tick);
    }

    public void deselect() {
        selected = false;
        iv.setImageResource(R.drawable.checkbox);
    }

    public void visibleSelection() {
        iv.setVisibility(VISIBLE);
    }

    public void invisibleSelection() {
        iv.setVisibility(GONE);
    }
}
