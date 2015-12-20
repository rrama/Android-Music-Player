package me.rrama.musicplayer.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * Simple {@link android.app.Dialog Dialogs} to get inputs from the user.
 */
public class DialogGet {

    public static void getText(Context context, String title, AfterGet afterGet) {
        get(context, title, afterGet, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    }

    public static void getNumber(Context context, String title, AfterGet afterGet) {
        get(context, title, afterGet, InputType.TYPE_CLASS_NUMBER);
    }

    public static void get(final Context context, String title, final AfterGet afterGet, int inputType) {
        final EditText input = new EditText(context);
        input.setInputType(inputType);
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        afterGet.afterGet(input.getText().toString());
                        InputMethodManager imm = (InputMethodManager)
                                context.getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }).create();

        // Requesting focus on EditText with keyboard.
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    dialog.getWindow().setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            }
        });

        dialog.show();
    }

    public interface AfterGet {

        void afterGet(String input);

    }

}
