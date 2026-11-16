package com.mitturkeyhack.kumanda;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ekranları XML'siz, kodla çizmek için küçük yardımcılar. */
public final class Ui {

    public static final int BG = 0xFFF1F5F9;
    public static final int CARD = 0xFFFFFFFF;
    public static final int TEXT = 0xFF0F172A;
    public static final int MUTED = 0xFF64748B;
    public static final int BLUE = 0xFF2563EB;
    public static final int RED = 0xFFDC2626;
    public static final int GREEN = 0xFF16A34A;
    public static final int ORANGE = 0xFFEA580C;
    public static final int PURPLE = 0xFF7C3AED;
    public static final int SLATE = 0xFF475569;
    public static final int PINK = 0xFFDB2777;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable round(Context c, int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, r));
        return g;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static Button btn(Context c, String s, int color) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(c, 54));
        b.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
        b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), round(c, color, 14), null));
        return b;
    }

    public static Button flatBtn(Context c, String s, int color) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(color);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), round(c, 0x00000000, 10), round(c, Color.WHITE, 10)));
        return b;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(c, CARD, 18));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        l.setElevation(dp(c, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 12));
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView section(Context c, String s) {
        TextView t = text(c, s, 15, TEXT, true);
        t.setPadding(0, 0, 0, dp(c, 10));
        return t;
    }

    /** İki düğmeyi yan yana koyar. */
    public static LinearLayout row(Context c, View... views) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, 0, 0, dp(c, 8));
        r.setLayoutParams(rlp);
        for (int i = 0; i < views.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMargins(dp(c, 8), 0, 0, 0);
            r.addView(views[i], lp);
        }
        return r;
    }
}
