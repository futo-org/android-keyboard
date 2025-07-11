package org.futo.inputmethod.latin.uix;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.futo.inputmethod.latin.R;

public class TextEditPopupActivity extends AppCompatActivity {

    @ColorInt
    private static int adjustAlpha(@ColorInt final int color, final float alpha) {
        final int a = Math.round(Color.alpha(color) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        @ColorInt final int bgColor = Color.WHITE;
        @ColorInt final int fgColor = Color.BLACK;
        @ColorInt final int primaryColor = 0xFF6200EE;

        final EditText editText = new EditText(this);
        editText.setHint(R.string.settings_try_typing_here);
        editText.setBackgroundColor(bgColor);
        editText.setTextColor(fgColor);
        editText.setHintTextColor(adjustAlpha(fgColor, 0.7f));
        editText.setHighlightColor(adjustAlpha(primaryColor, 0.7f));
        editText.setMinLines(1);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        if (Build.VERSION_CODES.Q <= Build.VERSION.SDK_INT) {
            Drawable d;
            if (null != (d = editText.getTextCursorDrawable())) d.setTint(primaryColor);
            if (null != (d = editText.getTextSelectHandle())) d.setTint(primaryColor);
            if (null != (d = editText.getTextSelectHandleLeft())) d.setTint(primaryColor);
            if (null != (d = editText.getTextSelectHandleRight())) d.setTint(primaryColor);
        }

        final CardView card = new CardView(this);
        card.setCardBackgroundColor(bgColor);
        card.setRadius(dp(16));
        card.setCardElevation(0);
        card.setUseCompatPadding(true);
        card.setContentPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(editText, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final FrameLayout root = new FrameLayout(this);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(card);

        setContentView(root);

        editText.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private int dp(final int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
