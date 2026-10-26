package org.pooltool.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public final class MainActivity extends Activity {
    private static final String PREFS_NAME = "openpool_prefs";
    private static final String KEY_CUE_INDEX = "selected_cue_index";

    private static final int COLOR_BG_DARK = 0xFF06120E;
    private static final int COLOR_GLASS_PANEL = 0xF00D221A;
    private static final int COLOR_ACCENT_GREEN = 0xFF059669;
    private static final int COLOR_ACCENT_TEAL = 0xFF0284C7;

    private PoolSurface surface;
    private PoolRenderer renderer;
    private SoundPool soundPool;
    private int soundShotId;
    private int soundBallId;
    private int soundPocketId;
    private int soundCushionId;
    private FrameLayout root;
    private View introScreen;
    private View menuScreen;
    private View gameScreen;
    private View headerView;
    private TextView turnLabel;
    private float touchX;
    private float touchY;
    private int activeCueIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        activeCueIndex = prefs.getInt(KEY_CUE_INDEX, 0);

        AudioAttributes audioAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(audioAttrs)
                .build();
        soundShotId = soundPool.load(this, R.raw.cue_shot, 1);
        soundBallId = soundPool.load(this, R.raw.ball_hit, 1);
        soundPocketId = soundPool.load(this, R.raw.pocket, 1);
        soundCushionId = soundPool.load(this, R.raw.cushion, 1);

        renderer = new PoolRenderer();
        renderer.setCueStyle(activeCueIndex);
        renderer.setSoundListener(this::playSound);
        renderer.setGameStateListener((player, pocketed) -> runOnUiThread(() -> {
            if (turnLabel != null) {
                turnLabel.setText(getString(R.string.player_status, player, pocketed));
            }
        }));
        surface = new PoolSurface(this, renderer);

        root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG_DARK);
        root.addView(surface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        introScreen = createIntroScreen();
        menuScreen = createMenuScreen();
        gameScreen = createGameScreen();
        root.addView(introScreen);
        root.addView(menuScreen);
        root.addView(gameScreen);

        // Apply WindowInsets for Cutout Safe Insets (ignoring transient status bar to prevent jumping HUD)
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            int leftInset = cutoutInsets.left;
            int rightInset = cutoutInsets.right;

            if (headerView != null) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) headerView.getLayoutParams();
                params.setMargins(dp(16) + leftInset, dp(12), dp(16) + rightInset, 0);
                headerView.setLayoutParams(params);
            }
            return insets;
        });

        WindowInsetsControllerCompat windowInsetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());

        showScreen(introScreen);
        setContentView(root);
        
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    private View createIntroScreen() {
        LinearLayout screen = createScreenContainer();

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        screen.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.intro_subtitle);
        subtitle.setTextColor(0xFF00E6A1);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        subtitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(8);
        screen.addView(subtitle, subParams);

        View startButton = createMaterial3Button(R.string.continue_to_menu, 0xFF059669, 0xFF047857,
                () -> showScreen(menuScreen));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = dp(32);
        screen.addView(startButton, btnParams);

        return screen;
    }

    private View createMenuScreen() {
        LinearLayout screen = createScreenContainer();

        TextView title = new TextView(this);
        title.setText(R.string.main_menu);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        screen.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.menu_subtitle);
        subtitle.setTextColor(0xFF00E6A1);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(4);
        screen.addView(subtitle, subParams);

        // Menu Cards Container
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setGravity(Gravity.CENTER);

        View practiceCard = createMenuCard(R.string.practice_mode, R.string.practice_desc,
                0xFF0F3A2E, 0xFF0A2B21, () -> startGame(false));
        View twoPlayerCard = createMenuCard(R.string.two_player_mode, R.string.two_player_desc,
                0xFF0284C7, 0xFF0369A1, () -> startGame(true));

        LinearLayout.LayoutParams cardParams1 = new LinearLayout.LayoutParams(dp(320), LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams1.topMargin = dp(24);
        cards.addView(practiceCard, cardParams1);

        LinearLayout.LayoutParams cardParams2 = new LinearLayout.LayoutParams(dp(320), LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams2.topMargin = dp(12);
        cards.addView(twoPlayerCard, cardParams2);

        screen.addView(cards);

        // Sub Actions Row
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        View cueShopBtn = createMaterial3SubButton(R.string.cue_shop, this::showCueShopDialog);
        View howToPlayBtn = createMaterial3SubButton(R.string.how_to_play, this::showHowToPlay);
        View aboutBtn = createMaterial3SubButton(R.string.about, this::showAboutDialog);

        row.addView(cueShopBtn);

        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p1.leftMargin = dp(10);
        row.addView(howToPlayBtn, p1);

        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p2.leftMargin = dp(10);
        row.addView(aboutBtn, p2);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(20);
        screen.addView(row, rowParams);

        return screen;
    }

    private View createGameScreen() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.TRANSPARENT);

        // Compact Floating Top HUD Card
        LinearLayout header = new LinearLayout(this);
        headerView = header;
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(5), dp(10), dp(5)); // Reduced height by 35%
        header.setElevation(dp(8));

        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(COLOR_GLASS_PANEL);
        headerBg.setCornerRadius(dp(18));
        headerBg.setStroke(dp(1), 0x3000E6A1);
        header.setBackground(headerBg);

        turnLabel = new TextView(this);
        turnLabel.setText(R.string.player_status_initial);
        turnLabel.setTextColor(Color.WHITE);
        turnLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        turnLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        header.addView(turnLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        View cueBtn = createCompactMaterial3Button(R.string.cue_shop, 0xFF14382B, 0xFF0E2B20,
                this::showCueShopDialog);
        View menuButton = createCompactMaterial3Button(R.string.menu, 0xFF0F3A2E, 0xFF0A2B21,
                () -> showScreen(menuScreen));

        header.addView(cueBtn);
        LinearLayout.LayoutParams menuP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        menuP.leftMargin = dp(6);
        header.addView(menuButton, menuP);

        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        headerParams.setMargins(dp(16), dp(6), dp(16), 0);
        screen.addView(header, headerParams);

        // Bottom Floating Action Controls Bar
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(10), dp(6), dp(10), dp(6));
        controls.setElevation(dp(8));

        GradientDrawable controlsBg = new GradientDrawable();
        controlsBg.setColor(COLOR_GLASS_PANEL);
        controlsBg.setCornerRadius(dp(22));
        controlsBg.setStroke(dp(1), 0x3000E6A1);
        controls.setBackground(controlsBg);

        View rackButton = createCompactMaterial3Button(R.string.new_rack, COLOR_ACCENT_GREEN, 0xFF047857,
                () -> renderer.resetRack());
        View cameraButton = createCompactMaterial3Button(R.string.camera, COLOR_ACCENT_TEAL, 0xFF0369A1,
                () -> renderer.toggleCamera());
        View spinButton = createCompactMaterial3Button(R.string.spin, 0xFF8746B5, 0xFF6B21A8,
                this::showSpinDialog);
        View aboutButton = createCompactMaterial3Button(R.string.about, 0xFF14382B, 0xFF0E2B20,
                this::showAboutDialog);

        controls.addView(rackButton);

        LinearLayout.LayoutParams camParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        camParams.leftMargin = dp(8);
        controls.addView(cameraButton, camParams);

        LinearLayout.LayoutParams spinParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spinParams.leftMargin = dp(8);
        controls.addView(spinButton, spinParams);

        LinearLayout.LayoutParams aboutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        aboutParams.leftMargin = dp(8);
        controls.addView(aboutButton, aboutParams);

        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        controlsParams.bottomMargin = dp(10);
        screen.addView(controls, controlsParams);

        return screen;
    }

    private View createMaterial3Button(int textRes, int gradientStart, int gradientEnd, Runnable onClick) {
        TextView button = new TextView(this);
        button.setText(textRes);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(20), dp(12), dp(20), dp(12));
        button.setClickable(true);
        button.setFocusable(true);
        button.setElevation(dp(6));

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {gradientStart, gradientEnd});
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0x40FFFFFF);

        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(0x40FFFFFF),
                bg,
                createCornerMask(dp(16)));
        button.setBackground(ripple);

        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)
                            .setInterpolator(new OvershootInterpolator(1.8f)).start();
                    break;
            }
            return false;
        });

        button.setOnClickListener(v -> onClick.run());
        return button;
    }

    private View createCompactMaterial3Button(int textRes, int gradientStart, int gradientEnd, Runnable onClick) {
        TextView button = new TextView(this);
        button.setText(textRes);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        button.setClickable(true);
        button.setFocusable(true);
        button.setElevation(dp(4));

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {gradientStart, gradientEnd});
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0x40FFFFFF);

        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(0x40FFFFFF),
                bg,
                createCornerMask(dp(12)));
        button.setBackground(ripple);

        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)
                            .setInterpolator(new OvershootInterpolator(1.8f)).start();
                    break;
            }
            return false;
        });

        button.setOnClickListener(v -> onClick.run());
        return button;
    }

    private View createMaterial3SubButton(int textRes, Runnable onClick) {
        TextView button = new TextView(this);
        button.setText(textRes);
        button.setTextColor(0xFF00E6A1);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(18), dp(10), dp(18), dp(10));
        button.setClickable(true);
        button.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC0E261D);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x5000E6A1);

        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(0x3000E6A1),
                bg,
                createCornerMask(dp(14)));
        button.setBackground(ripple);

        button.setOnClickListener(v -> onClick.run());
        return button;
    }

    private View createMenuCard(int titleRes, int descRes, int gradientStart, int gradientEnd, Runnable onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(22), dp(16), dp(22), dp(16));
        card.setClickable(true);
        card.setFocusable(true);
        card.setElevation(dp(8));

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {gradientStart, gradientEnd});
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(2), 0x5000E6A1);

        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                bg,
                createCornerMask(dp(18)));
        card.setBackground(ripple);

        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText(descRes);
        desc.setTextColor(0xFFB0E2D2);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = dp(3);
        card.addView(desc, descParams);

        card.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)
                            .setInterpolator(new OvershootInterpolator()).start();
                    break;
            }
            return false;
        });

        card.setOnClickListener(v -> onClick.run());
        return card;
    }

    private ShapeDrawable createCornerMask(float radius) {
        float[] outerRadii = new float[] {
                radius, radius, radius, radius,
                radius, radius, radius, radius
        };
        return new ShapeDrawable(new RoundRectShape(outerRadii, null, null));
    }

    private LinearLayout createScreenContainer() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER);
        screen.setPadding(dp(24), dp(16), dp(24), dp(16));
        screen.setBackgroundColor(COLOR_BG_DARK);
        return screen;
    }

    private void startGame(boolean twoPlayers) {
        renderer.setTwoPlayerMode(twoPlayers);
        renderer.resetRack();
        turnLabel.setText(getString(R.string.player_status, 1, 0));
        showScreen(gameScreen);
    }

    private void showScreen(View visibleScreen) {
        View[] screens = {introScreen, menuScreen, gameScreen};
        for (View s : screens) {
            if (s == visibleScreen) {
                s.setVisibility(View.VISIBLE);
                s.setAlpha(0f);
                s.animate().alpha(1.0f).setDuration(220).start();
            } else {
                s.setVisibility(View.GONE);
            }
        }
        surface.setVisibility(visibleScreen == gameScreen ? View.VISIBLE : View.GONE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showCueShopDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle(getString(R.string.cue_shop));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF06120E); // Explicit dark background

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(12), dp(16), dp(12));

        for (int i = 0; i < PoolRenderer.CUE_NAMES.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), dp(12), dp(14), dp(12));

            GradientDrawable itemBg = new GradientDrawable();
            itemBg.setColor(index == activeCueIndex ? 0xFF0F3A2E : 0xFF141F1B);
            itemBg.setCornerRadius(dp(12));
            itemBg.setStroke(dp(1), index == activeCueIndex ? 0xFF00E6A1 : 0x30FFFFFF);
            item.setBackground(itemBg);

            // Cue Color Preview Indicator Box
            View colorBox = new View(this);
            GradientDrawable boxBg = new GradientDrawable();
            boxBg.setColor(PoolRenderer.CUE_COLORS[i]);
            boxBg.setCornerRadius(dp(6));
            colorBox.setBackground(boxBg);
            LinearLayout.LayoutParams boxP = new LinearLayout.LayoutParams(dp(28), dp(28));
            item.addView(colorBox, boxP);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textP.leftMargin = dp(12);

            TextView name = new TextView(this);
            name.setText(PoolRenderer.CUE_NAMES[i]);
            name.setTextColor(Color.WHITE);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            name.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
            textCol.addView(name);

            TextView desc = new TextView(this);
            desc.setText(PoolRenderer.CUE_DESCRIPTIONS[i]);
            desc.setTextColor(0xFFB0E2D2);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            textCol.addView(desc);

            item.addView(textCol, textP);

            TextView equipBtn = new TextView(this);
            equipBtn.setText(index == activeCueIndex ? R.string.equipped : R.string.equip);
            equipBtn.setTextColor(index == activeCueIndex ? 0xFF00E6A1 : Color.WHITE);
            equipBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            equipBtn.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
            equipBtn.setPadding(dp(10), dp(6), dp(10), dp(6));

            item.addView(equipBtn);

            item.setOnClickListener(v -> {
                activeCueIndex = index;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_CUE_INDEX, activeCueIndex)
                        .apply();
                renderer.setCueStyle(activeCueIndex);
                showCueShopDialog();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            list.addView(item, lp);
        }

        scroll.addView(list);
        builder.setView(scroll);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    private void showHowToPlay() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.how_to_play)
                .setMessage(R.string.instructions)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showSpinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle(getString(R.string.spin_title));

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        rootLayout.setPadding(dp(20), dp(16), dp(20), dp(16));
        rootLayout.setBackgroundColor(0xFF06120E);

        TextView hintLabel = new TextView(this);
        hintLabel.setText(R.string.spin_hint);
        hintLabel.setTextColor(0xFF80A699);
        hintLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hintLabel.setGravity(Gravity.CENTER);
        rootLayout.addView(hintLabel);

        final TextView statusLabel = new TextView(this);
        statusLabel.setTextColor(0xFF00E6A1);
        statusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        statusLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(8);
        statusParams.bottomMargin = dp(12);
        rootLayout.addView(statusLabel, statusParams);

        updateSpinStatusLabel(statusLabel, renderer.getTipOffsetX(), renderer.getTipOffsetY());

        final SpinSelectorView selector = new SpinSelectorView(this,
                renderer.getTipOffsetX(), renderer.getTipOffsetY(), (offX, offY) -> {
            renderer.setTipOffset(offX, offY);
            updateSpinStatusLabel(statusLabel, offX, offY);
        });
        rootLayout.addView(selector);

        // Quick Preset Buttons Row
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams presetRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        presetRowParams.topMargin = dp(16);

        View btnCenter = createCompactMaterial3Button(R.string.spin_reset, 0xFF14382B, 0xFF0E2B20, () -> {
            selector.setSpin(0f, 0f);
            renderer.setTipOffset(0f, 0f);
            updateSpinStatusLabel(statusLabel, 0f, 0f);
        });

        presets.addView(btnCenter);
        rootLayout.addView(presets, presetRowParams);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(rootLayout);
        builder.setView(scroll);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    private void updateSpinStatusLabel(TextView label, float offX, float offY) {
        if (Math.hypot(offX, offY) < 0.08f) {
            label.setText(R.string.spin_center);
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (Math.abs(offY) >= 0.08f) {
            int pct = Math.round(Math.abs(offY) * 100);
            sb.append(offY > 0 ? "Follow (Top): " : "Draw (Back): ").append(pct).append("%");
        }
        if (Math.abs(offX) >= 0.08f) {
            if (sb.length() > 0) sb.append("  |  ");
            int pct = Math.round(Math.abs(offX) * 100);
            sb.append(offX > 0 ? "Right: " : "Left: ").append(pct).append("%");
        }
        label.setText(sb.toString());
    }

    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF06120E); // Explicit dark background

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(22), dp(18), dp(22), dp(18));

        TextView title = new TextView(this);
        title.setText("Open Pool Android Port");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        container.addView(title);

        TextView verBadge = new TextView(this);
        verBadge.setText(String.format("Version Name: %s\nVersion Code: %d\nGit Commit: %s\nRelease Channel: Experimental Pre-Release",
                org.pooltool.android.BuildConfig.VERSION_NAME,
                org.pooltool.android.BuildConfig.VERSION_CODE,
                org.pooltool.android.BuildConfig.GIT_COMMIT_HASH));
        verBadge.setTextColor(0xFF00E6A1);
        verBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        verBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams badgeP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeP.topMargin = dp(6);
        container.addView(verBadge, badgeP);

        TextView devInfo = new TextView(this);
        devInfo.setText("Developer:\nAbhinav Kishore (XanDron45)\n\n" +
                "About This Project:\n" +
                "Open Pool Android Port\n\n" +
                "This project is an experimental Android port of an open-source pool game developed using Android Studio and Google AI Studio (Gemini).\n\n" +
                "Features:\n" +
                "• Physics-based gameplay\n" +
                "• Modern Material UI\n" +
                "• Cue customization\n" +
                "• GitHub automated releases\n" +
                "• Android optimization\n\n" +
                "Status:\nExperimental Pre-Release");
        devInfo.setTextColor(0xFFD0E6DC);
        devInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams devP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        devP.topMargin = dp(14);
        container.addView(devInfo, devP);

        scroll.addView(container);
        builder.setView(scroll);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    private void playSound(int soundType, float volume) {
        if (soundPool == null) {
            return;
        }
        float vol = Math.max(0.12f, Math.min(1.0f, volume));
        switch (soundType) {
            case PoolRenderer.SOUND_SHOT:
                soundPool.play(soundShotId, vol, vol, 1, 0, 1.0f);
                break;
            case PoolRenderer.SOUND_BALL:
                float pitch = 0.94f + (float) Math.random() * 0.12f;
                soundPool.play(soundBallId, vol, vol, 1, 0, pitch);
                break;
            case PoolRenderer.SOUND_POCKET:
                soundPool.play(soundPocketId, 1.0f, 1.0f, 2, 0, 1.0f);
                break;
            case PoolRenderer.SOUND_CUSHION:
                soundPool.play(soundCushionId, vol * 0.85f, vol * 0.85f, 1, 0, 1.0f);
                break;
            default:
                break;
        }
    }

    private boolean handleTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchX = event.getX();
                touchY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                renderer.previewAim(event.getX() - touchX, event.getY() - touchY,
                        view.getWidth(), view.getHeight());
                return true;
            case MotionEvent.ACTION_UP:
                float dx = event.getX() - touchX;
                float dy = event.getY() - touchY;
                float distance = (float) Math.hypot(dx, dy);
                if (distance > dp(18)) {
                    renderer.shoot(dx, dy, distance, view.getWidth(), view.getHeight());
                } else {
                    view.performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (surface != null) {
            surface.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (surface != null) {
            surface.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        super.onDestroy();
    }

    private static final class SpinSelectorView extends View {
        private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float offsetX;
        private float offsetY;
        private final SpinChangeListener listener;

        interface SpinChangeListener {
            void onSpinChanged(float offX, float offY);
        }

        SpinSelectorView(Context context, float initialX, float initialY, SpinChangeListener listener) {
            super(context);
            this.offsetX = initialX;
            this.offsetY = initialY;
            this.listener = listener;

            ballPaint.setColor(0xFFF6F3EB);
            ballPaint.setStyle(Paint.Style.FILL);

            shadowPaint.setColor(0x40000000);
            shadowPaint.setStyle(Paint.Style.FILL);

            linePaint.setColor(0x30000000);
            linePaint.setStrokeWidth(2.5f);
            linePaint.setStyle(Paint.Style.STROKE);

            dotPaint.setColor(0xFFE53935); // Chalk Red strike dot
            dotPaint.setStyle(Paint.Style.FILL);

            dotInnerPaint.setColor(0xFFFFFFFF);
            dotInnerPaint.setStyle(Paint.Style.FILL);

            textPaint.setColor(0xFF80A699);
            textPaint.setTextSize(26f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        }

        void setSpin(float offX, float offY) {
            this.offsetX = offX;
            this.offsetY = offY;
            invalidate();
            if (listener != null) {
                listener.onSpinChanged(offsetX, offsetY);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = (int) (180 * getResources().getDisplayMetrics().density);
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float radius = Math.min(cx, cy) * 0.74f;

            canvas.drawCircle(cx, cy + radius * 0.08f, radius, shadowPaint);
            canvas.drawCircle(cx, cy, radius, ballPaint);

            canvas.drawLine(cx - radius * 0.85f, cy, cx + radius * 0.85f, cy, linePaint);
            canvas.drawLine(cx, cy - radius * 0.85f, cx, cy + radius * 0.85f, linePaint);

            canvas.drawText("FOLLOW", cx, cy - radius - 10f, textPaint);
            canvas.drawText("DRAW", cx, cy + radius + 30f, textPaint);
            canvas.drawText("L", cx - radius - 18f, cy + 9f, textPaint);
            canvas.drawText("R", cx + radius + 18f, cy + 9f, textPaint);

            float dotX = cx + offsetX * radius;
            float dotY = cy - offsetY * radius; // Invert Y
            canvas.drawCircle(dotX, dotY, radius * 0.16f, dotPaint);
            canvas.drawCircle(dotX, dotY, radius * 0.06f, dotInnerPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    float cx = getWidth() * 0.5f;
                    float cy = getHeight() * 0.5f;
                    float radius = Math.min(cx, cy) * 0.74f;

                    float dx = (event.getX() - cx) / radius;
                    float dy = -(event.getY() - cy) / radius; // Invert Y

                    float dist = (float) Math.hypot(dx, dy);
                    float maxR = 0.82f;
                    if (dist > maxR) {
                        dx = (dx / dist) * maxR;
                        dy = (dy / dist) * maxR;
                    }
                    offsetX = dx;
                    offsetY = dy;
                    invalidate();
                    if (listener != null) {
                        listener.onSpinChanged(offsetX, offsetY);
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    private static final class PoolSurface extends GLSurfaceView {
        PoolSurface(Activity activity, PoolRenderer renderer) {
            super(activity);
            setEGLContextClientVersion(2);
            setRenderer(renderer);
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            setFocusable(true);
            setOnTouchListener(((MainActivity) activity)::handleTouch);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
