package org.pooltool.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int COLOR_BACKGROUND = 0xFF071611;
    private static final int COLOR_PANEL = 0xE6173025;
    private static final int COLOR_BUTTON = 0xFF287A53;

    private PoolSurface surface;
    private PoolRenderer renderer;
    private ToneGenerator sound;
    private FrameLayout root;
    private View introScreen;
    private View menuScreen;
    private View gameScreen;
    private TextView turnLabel;
    private float touchX;
    private float touchY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sound = new ToneGenerator(AudioManager.STREAM_MUSIC, 55);
        renderer = new PoolRenderer();
        renderer.setSoundListener(this::playSound);
        renderer.setGameStateListener((player, pocketed) -> runOnUiThread(() -> {
            if (turnLabel != null) {
                turnLabel.setText(getString(R.string.player_status, player, pocketed));
            }
        }));
        surface = new PoolSurface(this, renderer);

        root = new FrameLayout(this);
        root.addView(surface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        introScreen = createIntroScreen();
        menuScreen = createMenuScreen();
        gameScreen = createGameScreen();
        root.addView(introScreen);
        root.addView(menuScreen);
        root.addView(gameScreen);
        showScreen(introScreen);
        setContentView(root);
    }

    private View createIntroScreen() {
        LinearLayout screen = createScreen();
        TextView title = makeText(R.string.app_name, 42, 0xFFF5E9C8);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        screen.addView(title);

        TextView subtitle = makeText(R.string.intro_subtitle, 18, 0xFFD0DCCF);
        LinearLayout.LayoutParams subtitleParams = centeredParams();
        subtitleParams.topMargin = dp(8);
        screen.addView(subtitle, subtitleParams);

        TextView start = makeButton(R.string.continue_to_menu);
        LinearLayout.LayoutParams buttonParams = centeredParams();
        buttonParams.topMargin = dp(28);
        screen.addView(start, buttonParams);
        start.setOnClickListener(view -> showScreen(menuScreen));
        return screen;
    }

    private View createMenuScreen() {
        LinearLayout screen = createScreen();
        TextView title = makeText(R.string.main_menu, 32, 0xFFF5E9C8);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        screen.addView(title);

        TextView subtitle = makeText(R.string.menu_subtitle, 16, 0xFFD0DCCF);
        LinearLayout.LayoutParams subtitleParams = centeredParams();
        subtitleParams.topMargin = dp(6);
        screen.addView(subtitle, subtitleParams);

        TextView practiceButton = makeButton(R.string.practice_mode);
        TextView twoPlayerButton = makeButton(R.string.two_player_mode);
        LinearLayout.LayoutParams buttonParams = centeredParams();
        buttonParams.topMargin = dp(20);
        screen.addView(practiceButton, buttonParams);
        LinearLayout.LayoutParams secondButtonParams = centeredParams();
        secondButtonParams.topMargin = dp(10);
        screen.addView(twoPlayerButton, secondButtonParams);

        LinearLayout links = new LinearLayout(this);
        links.setGravity(Gravity.CENTER);
        TextView howToPlay = makeButton(R.string.how_to_play);
        howToPlay.setBackgroundColor(COLOR_PANEL);
        TextView about = makeButton(R.string.about);
        about.setBackgroundColor(COLOR_PANEL);
        links.addView(howToPlay);
        LinearLayout.LayoutParams aboutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        aboutParams.leftMargin = dp(8);
        links.addView(about, aboutParams);
        LinearLayout.LayoutParams linksParams = centeredParams();
        linksParams.topMargin = dp(16);
        screen.addView(links, linksParams);

        practiceButton.setOnClickListener(view -> startGame(false));
        twoPlayerButton.setOnClickListener(view -> startGame(true));
        howToPlay.setOnClickListener(view -> showHowToPlay());
        about.setOnClickListener(view -> showAboutDialog());
        return screen;
    }

    private View createGameScreen() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(6), dp(8), dp(6));
        header.setBackgroundColor(COLOR_PANEL);
        turnLabel = makeText(R.string.player_status_initial, 15, Color.WHITE);
        turnLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(turnLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView menuButton = makeButton(R.string.menu);
        header.addView(menuButton);
        menuButton.setOnClickListener(view -> showScreen(menuScreen));
        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        headerParams.setMargins(dp(10), dp(10), dp(10), 0);
        screen.addView(header, headerParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(6), dp(6), dp(6), dp(6));
        controls.setBackgroundColor(COLOR_PANEL);

        TextView rackButton = makeButton(R.string.new_rack);
        TextView cameraButton = makeButton(R.string.camera);
        TextView aboutButton = makeButton(R.string.about);
        controls.addView(rackButton);
        addSpacedButton(controls, cameraButton);
        addSpacedButton(controls, aboutButton);
        rackButton.setOnClickListener(view -> renderer.resetRack());
        cameraButton.setOnClickListener(view -> renderer.toggleCamera());
        aboutButton.setOnClickListener(view -> showAboutDialog());

        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        controlsParams.bottomMargin = dp(10);
        screen.addView(controls, controlsParams);
        return screen;
    }

    private LinearLayout createScreen() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER);
        screen.setPadding(dp(24), dp(16), dp(24), dp(16));
        screen.setBackgroundColor(COLOR_BACKGROUND);
        return screen;
    }

    private void startGame(boolean twoPlayers) {
        renderer.setTwoPlayerMode(twoPlayers);
        renderer.resetRack();
        turnLabel.setText(getString(R.string.player_status, 1, 0));
        showScreen(gameScreen);
    }

    private void showScreen(View visibleScreen) {
        surface.setVisibility(visibleScreen == gameScreen ? View.VISIBLE : View.GONE);
        introScreen.setVisibility(visibleScreen == introScreen ? View.VISIBLE : View.GONE);
        menuScreen.setVisibility(visibleScreen == menuScreen ? View.VISIBLE : View.GONE);
        gameScreen.setVisibility(visibleScreen == gameScreen ? View.VISIBLE : View.GONE);
    }

    private void addSpacedButton(LinearLayout row, TextView button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(6);
        row.addView(button, params);
    }

    private TextView makeText(int text, int size, int color) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(color);
        label.setTextSize(size);
        label.setGravity(Gravity.CENTER);
        return label;
    }

    private TextView makeButton(int label) {
        TextView button = makeText(label, 15, Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(18), dp(12), dp(18), dp(12));
        button.setBackgroundColor(COLOR_BUTTON);
        button.setClickable(true);
        return button;
    }

    private LinearLayout.LayoutParams centeredParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void playSound(int soundType) {
        runOnUiThread(() -> {
            if (sound == null) {
                return;
            }
            switch (soundType) {
                case PoolRenderer.SOUND_SHOT:
                    sound.startTone(ToneGenerator.TONE_PROP_BEEP2, 90);
                    break;
                case PoolRenderer.SOUND_BALL:
                    sound.startTone(ToneGenerator.TONE_PROP_ACK, 45);
                    break;
                case PoolRenderer.SOUND_POCKET:
                    sound.startTone(ToneGenerator.TONE_PROP_NACK, 110);
                    break;
                default:
                    break;
            }
        });
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
        if (sound != null) {
            sound.release();
            sound = null;
        }
        super.onDestroy();
    }

    private void showHowToPlay() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.how_to_play)
                .setMessage(R.string.instructions)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showAboutDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
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
