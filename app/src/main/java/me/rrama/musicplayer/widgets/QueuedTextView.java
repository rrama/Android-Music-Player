package me.rrama.musicplayer.widgets;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import me.rrama.musicplayer.R;
import me.rrama.musicplayer.Slider;
import me.rrama.musicplayer.util.DialogGet;

/**
 * A song listed in the {@link me.rrama.musicplayer.fragments.Currently} with added features.
 * Including;
 * <ul>
 *     <li>Removing the song.</li>
 *     <li>Moving the song about in the list.</li>
 * </ul>
 */
public class QueuedTextView extends RelativeLayout implements View.OnLongClickListener {

    public final TextView tv;
    public final LinearLayout ll;
    public final ImageView ivDel, ivUp, ivDown;

    public QueuedTextView(final Slider slider, String songName) {
        super(slider);

        tv = new TextView(slider);
        tv.setText(songName);

        ll = new LinearLayout(slider);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setVisibility(GONE);

        ivDel = new ImageView(slider);
        ivDel.setImageResource(R.drawable.cross);
        ivDel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                slider.playSongs().removeSong(getIndex());
            }
        });

        ivUp = new ImageView(slider);
        ivUp.setImageResource(R.drawable.up);
        ivUp.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                slider.playSongs().moveSongUp(getIndex());
            }
        });
        ivUp.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                DialogGet.getNumber(getContext(), "How many to move up?", new DialogGet.AfterGet() {
                    @Override
                    public void afterGet(String input) {
                        int times = Integer.valueOf(input);
                        if (times < 1) return;
                        int index = getIndex();
                        for (int i = index; i > index - times; i--) {
                            slider.playSongs().moveSongUp(i);
                        }
                    }
                });
                return true;
            }
        });

        ivDown = new ImageView(slider);
        ivDown.setImageResource(R.drawable.down);
        ivDown.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                slider.playSongs().moveSongUp(getIndex() + 1);
            }
        });
        ivDown.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                DialogGet.getNumber(getContext(), "How many to move down?", new DialogGet.AfterGet() {
                    @Override
                    public void afterGet(String input) {
                        int times = Integer.valueOf(input);
                        if (times < 1) return;
                        int index = getIndex();
                        for (int i = index; i < times + index; i++) {
                            slider.playSongs().moveSongUp(i + 1);
                        }
                    }
                });
                return true;
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(69, 66);
        lp.setMargins(0, 0, (int) getResources().getDimension(R.dimen.margin_right), 0);
        ll.addView(ivUp, lp);
        ll.addView(ivDown, lp);
        ll.addView(ivDel, new LayoutParams(69, 66));

        tv.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ll.getVisibility() == VISIBLE) {
                    invisibleControls();
                } else {
                    slider.playSongs().skipTo(getIndex());
                }
            }
        });

        LayoutParams lp2 = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.addRule(ALIGN_PARENT_END);
        addView(tv);
        addView(ll, lp2);

        setOnLongClickListener(this);
        tv.setOnLongClickListener(this);
    }

    @Override
    public boolean onLongClick(View v) {
        visibleControls();
        return true;
    }

    public void setPlaying() {
        tv.setTextColor(getResources().getColor(R.color.yellow, null));
    }

    public int getIndex() {
        return ((ViewGroup) this.getParent()).indexOfChild(this);
    }

    public void visibleControls() {
        ViewGroup vg = (ViewGroup) getParent();
        for (int i = 0; i < vg.getChildCount(); i++) {
            ((QueuedTextView) vg.getChildAt(i)).invisibleControls();
        }
        ll.setVisibility(VISIBLE);
    }

    public void invisibleControls() {
        ll.setVisibility(GONE);
    }

}
