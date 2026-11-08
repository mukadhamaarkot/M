package com.example.offlinekeyboard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(34), dp(24), dp(24));
        page.setBackgroundColor(Color.rgb(244, 247, 251));
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("מקלדת מקומית");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(28, 43, 62));
        title.setGravity(Gravity.CENTER);
        page.addView(title, new LinearLayout.LayoutParams(-1, dp(54)));

        TextView subtitle = new TextView(this);
        subtitle.setText("מקלדת עם חיזוי מילים שעובד גם ללא אינטרנט");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(88, 103, 122));
        subtitle.setGravity(Gravity.CENTER);
        page.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView privacy = new TextView(this);
        privacy.setText("החיזוי והלמידה נשארים במכשיר. האפליקציה אינה מבקשת הרשאת אינטרנט.");
        privacy.setTextSize(14);
        privacy.setTextColor(Color.rgb(49, 82, 117));
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable privacyBg = new GradientDrawable();
        privacyBg.setColor(Color.rgb(226, 237, 249));
        privacyBg.setCornerRadius(dp(14));
        privacy.setBackground(privacyBg);
        LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(-1, -2);
        privacyLp.setMargins(0, dp(18), 0, dp(28));
        page.addView(privacy, privacyLp);

        TextView steps = new TextView(this);
        steps.setText("איך מפעילים?\n\n1. לוחצים על «הפעלת המקלדת» ומפעילים את «מקלדת מקומית».\n\n2. חוזרים לכאן, לוחצים על «בחירת המקלדת» ובוחרים בה.\n\n3. פותחים כל שדה כתיבה ומתחילים להקליד.");
        steps.setTextSize(16);
        steps.setTextColor(Color.rgb(42, 55, 72));
        steps.setLineSpacing(dp(4), 1f);
        steps.setGravity(Gravity.RIGHT);
        page.addView(steps, new LinearLayout.LayoutParams(-1, -2));

        Button enable = makeButton("הפעלת המקלדת");
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, dp(54));
        buttonLp.setMargins(0, dp(30), 0, dp(12));
        page.addView(enable, buttonLp);
        enable.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });

        Button choose = makeButton("בחירת המקלדת");
        page.addView(choose, new LinearLayout.LayoutParams(-1, dp(54)));
        choose.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });

        TextView footer = new TextView(this);
        footer.setText("גרסה ראשונית · Android 6 ומעלה · עברית ואנגלית");
        footer.setTextSize(12);
        footer.setTextColor(Color.rgb(125, 137, 152));
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, dp(50));
        footerLp.setMargins(0, dp(20), 0, 0);
        page.addView(footer, footerLp);

        setContentView(page);
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(49, 94, 155));
        bg.setCornerRadius(dp(14));
        button.setBackground(bg);
        return button;
    }
}
