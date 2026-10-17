package com.star.app.anim;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.star.app.R;

public class AnimStudioActivity extends Activity {

    private static final int BG = 0xFF0F0F0F;
    private static final int CARD = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(buildTree());
    }

    private View buildTree() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(placeholder(R.id.top_bar, MATCH, dp(32), 0f));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        middle.setLayoutParams(new LinearLayout.LayoutParams(MATCH, 0, 1f));

        middle.addView(placeholder(R.id.tool_rail, dp(56), MATCH, 0f));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH, 1f));
        center.addView(placeholder(R.id.tab_bar, MATCH, dp(32), 0f));
        center.addView(placeholder(R.id.canvas_host, MATCH, 0, 1f));
        middle.addView(center);

        middle.addView(placeholder(R.id.right_dock, dp(280), MATCH, 0f));

        root.addView(middle);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setId(R.id.bottom_dock);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(300)));
        bottom.addView(placeholder(R.id.onion_bar, MATCH, dp(36), 0f));
        bottom.addView(placeholder(R.id.timeline_area, MATCH, 0, 1f));
        root.addView(bottom);

        return root;
    }

    private FrameLayout placeholder(int id, int width, int height, float weight) {
        FrameLayout f = new FrameLayout(this);
        f.setId(id);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.weight = weight;
        f.setLayoutParams(lp);
        f.setBackground(new PanelBackground(CARD, BORDER,
            getResources().getDisplayMetrics().density));
        return f;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class PanelBackground extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float thickness;

        PanelBackground(int fillColor, int borderColor, float density) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(fillColor);
            border.setStyle(Paint.Style.FILL);
            border.setColor(borderColor);
            this.thickness = Math.max(1f, density);
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawRect(b.left, b.top, b.right, b.bottom, fill);
            c.drawRect(b.left, b.top, b.right, b.top + thickness, border);
        }

        @Override public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            border.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
            border.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
