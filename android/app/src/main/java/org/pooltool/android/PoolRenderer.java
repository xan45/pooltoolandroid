package org.pooltool.android;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class PoolRenderer implements GLSurfaceView.Renderer {
    static final int SOUND_SHOT = 1;
    static final int SOUND_BALL = 2;
    static final int SOUND_POCKET = 3;

    // Table & Physics Dimensions
    private static final float TABLE_HALF_X = 1.30f;
    private static final float TABLE_HALF_Z = 0.65f;
    private static final float BALL_RADIUS = 0.047f;
    private static final float CLOTH_Y = 0.060f;
    private static final float BALL_Y = CLOTH_Y + BALL_RADIUS;
    private static final float MAX_SHOT_SPEED = 3.8f;
    private static final float FIXED_STEP = 1f / 120f;
    private static final float POCKET_RADIUS = 0.098f;

    // Cue Style Materials (Color ARGB)
    static final int[] CUE_COLORS = {
            0xFFC68A4C, // 0: Classic Wood (Maple)
            0xFF1A1A1A, // 1: Ebony (Midnight Obsidian)
            0xFF2D3748, // 2: Carbon Fiber (Graphite)
            0xFFD4AF37, // 3: Gold (Polished Brass)
            0xFF00F0FF  // 4: Neon Blue (Cyberpunk Glow)
    };

    static final String[] CUE_NAMES = {
            "Classic Wood",
            "Ebony",
            "Carbon Fiber",
            "Gold Edition",
            "Neon Blue"
    };

    static final String[] CUE_DESCRIPTIONS = {
            "Traditional polished maple shaft with smooth glide",
            "Premium dark ebony wood with silver accent ferrule",
            "Lightweight carbon fiber composite for precision control",
            "Luxury 24K gold polished cue stick",
            "Futuristic neon blue glowing cue shaft"
    };

    // Pocket World Positions
    private static final float[][] POCKET_POSITIONS = {
            {-1.25f, -0.62f}, {0.00f, -0.64f}, {1.25f, -0.62f},
            {-1.25f, 0.62f}, {0.00f, 0.64f}, {1.25f, 0.62f}
    };

    private final List<Ball> balls = new ArrayList<>();
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] viewProjection = new float[16];
    private final float[] model = new float[16];
    private final float[] mvp = new float[16];

    private int program;
    private int positionAttribute;
    private int normalAttribute;
    private int mvpUniform;
    private int colorUniform;

    // Pre-allocated Buffers for Zero-GC Rendering
    private FloatBuffer sphereBuffer;
    private int sphereVertexCount;

    private FloatBuffer tableBuffer;
    private int tableVertexCount;

    private FloatBuffer pocketBuffer;
    private int pocketVertexCount;

    private FloatBuffer dynamicBuffer; // Scratch buffer for lines & cue

    private int surfaceWidth = 1;
    private int surfaceHeight = 1;

    // Aiming & Cue Stick State
    private float targetAimAngle = (float) -Math.PI / 2;
    private float currentAimAngle = (float) -Math.PI / 2;
    private float aimX = 0f;
    private float aimZ = -1f;

    private float aimSensitivity = 0.45f; // 50% reduced sensitivity for fine aiming
    private float cuePullback = 0f;       // Shot power pullback distance (0.0 to 1.0)
    private int selectedCueIndex = 0;     // Currently equipped cue

    private boolean overheadCamera;
    private long lastFrameNanos;
    private float accumulator;
    private int pocketedCount;
    private int currentPlayer = 1;
    private boolean twoPlayerMode;
    private boolean shotInProgress;
    private long lastBallSoundMillis;
    private volatile SoundListener soundListener;
    private volatile GameStateListener gameStateListener;

    interface SoundListener {
        void onSound(int soundType);
    }

    interface GameStateListener {
        void onGameStateChanged(int player, int pocketed);
    }

    void setSoundListener(SoundListener listener) {
        soundListener = listener;
    }

    void setGameStateListener(GameStateListener listener) {
        gameStateListener = listener;
    }

    void setCueStyle(int index) {
        if (index >= 0 && index < CUE_COLORS.length) {
            selectedCueIndex = index;
        }
    }

    void setAimSensitivity(float sensitivity) {
        aimSensitivity = Math.max(0.1f, Math.min(1.0f, sensitivity));
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.025f, 0.05f, 0.04f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        positionAttribute = GLES20.glGetAttribLocation(program, "aPosition");
        normalAttribute = GLES20.glGetAttribLocation(program, "aNormal");
        mvpUniform = GLES20.glGetUniformLocation(program, "uMvp");
        colorUniform = GLES20.glGetUniformLocation(program, "uColor");

        createSphere();
        buildTableGeometry();
        buildPocketGeometry();

        ByteBuffer bb = ByteBuffer.allocateDirect(2048 * 4);
        bb.order(ByteOrder.nativeOrder());
        dynamicBuffer = bb.asFloatBuffer();

        resetRack();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
        float aspect = (float) surfaceWidth / surfaceHeight;
        float fov = aspect < 1.0f ? 44f : 36f;
        Matrix.perspectiveM(projection, 0, fov, aspect, 0.1f, 25f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        if (lastFrameNanos == 0) {
            lastFrameNanos = now;
        }
        float elapsed = Math.min((now - lastFrameNanos) / 1_000_000_000f, 0.05f);
        lastFrameNanos = now;
        accumulator += elapsed;

        while (accumulator >= FIXED_STEP) {
            stepPhysics(FIXED_STEP);
            accumulator -= FIXED_STEP;
        }

        // Smooth angle Exponential Moving Average (EMA/LERP) interpolation
        float angleDiff = targetAimAngle - currentAimAngle;
        while (angleDiff > Math.PI) angleDiff -= (float) (Math.PI * 2);
        while (angleDiff < -Math.PI) angleDiff += (float) (Math.PI * 2);
        currentAimAngle += angleDiff * Math.min(1.0f, elapsed * 14.0f);
        aimX = (float) Math.cos(currentAimAngle);
        aimZ = (float) Math.sin(currentAimAngle);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program);
        configureCamera();

        drawTable();
        drawPockets();
        drawAimAndCue();
        drawBalls();
    }

    synchronized void shoot(float screenDx, float screenDy, float distance, int width, int height) {
        Ball cue = cueBall();
        if (cue == null || cue.pocketed || isMoving()) {
            return;
        }

        updateAim(screenDx, screenDy, width, height, true);

        float power = Math.min(1f, Math.max(cuePullback, distance / Math.max(1f, Math.min(width, height) * 0.45f)));
        float speed = 0.40f + (float) Math.pow(power, 1.3) * MAX_SHOT_SPEED;
        cue.vx = aimX * speed;
        cue.vz = aimZ * speed;
        shotInProgress = true;
        cuePullback = 0f;
        emitSound(SOUND_SHOT);
    }

    synchronized void previewAim(float screenDx, float screenDy, int width, int height) {
        if (!isMoving()) {
            updateAim(screenDx, screenDy, width, height, false);
        }
    }

    private void updateAim(float screenDx, float screenDy, int width, int height, boolean isRelease) {
        float scaledDx = (screenDx / Math.max(1, width)) * aimSensitivity;
        float scaledDz = (screenDy / Math.max(1, height)) * aimSensitivity;
        float magnitude = (float) Math.hypot(scaledDx, scaledDz);
        if (magnitude < 0.0001f) {
            return;
        }

        targetAimAngle = (float) Math.atan2(scaledDz, scaledDx);

        float dotPull = -(scaledDx * aimX + scaledDz * aimZ);
        if (dotPull > 0.05f) {
            cuePullback = Math.min(1.0f, dotPull * 3.5f);
        } else {
            cuePullback = 0f;
        }

        if (isRelease) {
            currentAimAngle = targetAimAngle;
        }
    }

    synchronized void resetRack() {
        balls.clear();
        pocketedCount = 0;
        currentPlayer = 1;
        shotInProgress = false;
        cuePullback = 0f;

        balls.add(new Ball(0, BALL_Y, 0.42f, 0xFFF6F1E7, true));

        int[] colors = {
                0xFFF4D03F, 0xFF3585D5, 0xFFD74343, 0xFF8746B5, 0xFFF18B35,
                0xFF2A9C60, 0xFF8B3232, 0xFF2B2B32, 0xFFF4D03F, 0xFF3585D5,
                0xFFD74343, 0xFF8746B5, 0xFFF18B35, 0xFF2A9C60, 0xFF8B3232
        };

        int ballIndex = 0;
        float rackZ = -0.22f;
        float spacing = BALL_RADIUS * 2.06f;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col <= row; col++) {
                float x = (col - row * 0.5f) * spacing;
                float z = rackZ - row * spacing * 0.88f;
                balls.add(new Ball(x, BALL_Y, z, colors[ballIndex++], false));
            }
        }

        targetAimAngle = (float) -Math.PI / 2;
        currentAimAngle = targetAimAngle;
        aimX = 0f;
        aimZ = -1f;
        notifyGameStateChanged();
    }

    synchronized void setTwoPlayerMode(boolean enabled) {
        twoPlayerMode = enabled;
    }

    synchronized void toggleCamera() {
        overheadCamera = !overheadCamera;
    }

    private void configureCamera() {
        float eyeY = overheadCamera ? 3.4f : 2.1f;
        float eyeZ = overheadCamera ? 0.01f : 2.1f;
        Matrix.setLookAtM(view, 0, 0f, eyeY, eyeZ, 0f, 0f, -0.05f, 0f, 1f, 0f);
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0);
    }

    private void setMvp(float[] transform) {
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, transform, 0);
        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, mvp, 0);
    }

    private void drawTable() {
        Matrix.setIdentityM(model, 0);
        setMvp(model);

        tableBuffer.position(0);
        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 24, tableBuffer);
        tableBuffer.position(3);
        GLES20.glEnableVertexAttribArray(normalAttribute);
        GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 24, tableBuffer);

        // 1. Table Cloth Bed (#0B5D3B - Dark Green Cloth)
        GLES20.glUniform4fv(colorUniform, 1, color(0xFF0B5D3B), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);

        // 2. Outer Base Frame (Dark Mahogany)
        GLES20.glUniform4fv(colorUniform, 1, color(0xFF24130A), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 36, 36);

        // 3. Wooden Rails (Dark Mahogany)
        GLES20.glUniform4fv(colorUniform, 1, color(0xFF3A2012), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 72, 144);

        // 4. Six Tapered Cloth Cushions (#094B30)
        GLES20.glUniform4fv(colorUniform, 1, color(0xFF094B30), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 216, 216);

        // 5. Diamond Sight Markers
        GLES20.glUniform4fv(colorUniform, 1, color(0xFFF0E8D0), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 432, tableVertexCount - 432);
    }

    private void drawPockets() {
        Matrix.setIdentityM(model, 0);
        setMvp(model);

        pocketBuffer.position(0);
        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 24, pocketBuffer);
        pocketBuffer.position(3);
        GLES20.glEnableVertexAttribArray(normalAttribute);
        GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 24, pocketBuffer);

        // Pocket Rim Ring
        GLES20.glUniform4fv(colorUniform, 1, color(0xFF202323), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, pocketVertexCount / 2);

        // Recessed Dark Pocket Well Interior
        GLES20.glUniform4fv(colorUniform, 1, color(0xFF080A0A), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, pocketVertexCount / 2, pocketVertexCount / 2);
    }

    private synchronized void drawBalls() {
        for (Ball ball : balls) {
            if (ball.pocketed) {
                continue;
            }
            Matrix.setIdentityM(model, 0);
            Matrix.translateM(model, 0, ball.x, BALL_Y, ball.z);
            Matrix.scaleM(model, 0, BALL_RADIUS, BALL_RADIUS, BALL_RADIUS);
            setMvp(model);

            GLES20.glUniform4fv(colorUniform, 1, color(ball.color), 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            sphereBuffer.position(0);
            GLES20.glEnableVertexAttribArray(positionAttribute);
            GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 24, sphereBuffer);
            sphereBuffer.position(3);
            GLES20.glEnableVertexAttribArray(normalAttribute);
            GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 24, sphereBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, sphereVertexCount);
        }
    }

    private synchronized void drawAimAndCue() {
        Ball cue = cueBall();
        if (cue == null || cue.pocketed || isMoving()) {
            return;
        }

        float lineLength = 1.60f;
        float hitDistance = lineLength;
        Ball targetBall = null;

        for (Ball ball : balls) {
            if (ball.cue || ball.pocketed) continue;
            float dx = ball.x - cue.x;
            float dz = ball.z - cue.z;
            float proj = dx * aimX + dz * aimZ;
            if (proj > 0) {
                float perpSq = (dx * dx + dz * dz) - (proj * proj);
                float radiusSum = BALL_RADIUS * 2.0f;
                if (perpSq < radiusSum * radiusSum) {
                    float dist = proj - (float) Math.sqrt(Math.max(0, radiusSum * radiusSum - perpSq));
                    if (dist > 0 && dist < hitDistance) {
                        hitDistance = dist;
                        targetBall = ball;
                    }
                }
            }
        }

        float endX = cue.x + aimX * hitDistance;
        float endZ = cue.z + aimZ * hitDistance;

        // Draw Aim Trajectory Line
        dynamicBuffer.clear();
        int offset = 0;
        float lineStartX = cue.x + aimX * (BALL_RADIUS * 1.2f);
        float lineStartZ = cue.z + aimZ * (BALL_RADIUS * 1.2f);

        offset = putVertex(dynamicBuffer, offset, lineStartX, BALL_Y, lineStartZ, 0f, 1f, 0f);
        putVertex(dynamicBuffer, offset, endX, BALL_Y, endZ, 0f, 1f, 0f);

        Matrix.setIdentityM(model, 0);
        setMvp(model);

        dynamicBuffer.position(0);
        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 24, dynamicBuffer);
        dynamicBuffer.position(3);
        GLES20.glEnableVertexAttribArray(normalAttribute);
        GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 24, dynamicBuffer);

        GLES20.glLineWidth(3.5f);
        GLES20.glUniform4fv(colorUniform, 1, color(0xCCE0F2DC), 0);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);

        // REPLACEMENT FOR GHOST WHITE BALL:
        // Draw a subtle, professional glowing target impact crosshair ring (NOT a solid duplicate ball)
        if (targetBall != null) {
            dynamicBuffer.clear();
            offset = 0;
            int ringSegments = 16;
            float ringR = BALL_RADIUS * 1.05f;
            for (int i = 0; i < ringSegments; i++) {
                float a = (float) (i * Math.PI * 2 / ringSegments);
                float b = (float) ((i + 1) * Math.PI * 2 / ringSegments);
                offset = putVertex(dynamicBuffer, offset, endX + (float) Math.cos(a) * ringR, BALL_Y + 0.002f, endZ + (float) Math.sin(a) * ringR, 0f, 1f, 0f);
                offset = putVertex(dynamicBuffer, offset, endX + (float) Math.cos(b) * ringR, BALL_Y + 0.002f, endZ + (float) Math.sin(b) * ringR, 0f, 1f, 0f);
            }

            Matrix.setIdentityM(model, 0);
            setMvp(model);

            dynamicBuffer.position(0);
            GLES20.glEnableVertexAttribArray(positionAttribute);
            GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 24, dynamicBuffer);
            dynamicBuffer.position(3);
            GLES20.glEnableVertexAttribArray(normalAttribute);
            GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 24, dynamicBuffer);

            GLES20.glLineWidth(2.5f);
            GLES20.glUniform4fv(colorUniform, 1, color(0xFF00E6A1), 0); // Vibrant emerald target ring
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, ringSegments * 2);
        }

        // 3D Cue Stick Model (Uses Custom Selected Cue Style Material)
        float cueBackOffset = BALL_RADIUS + 0.08f + cuePullback * 0.28f;
        float cueLength = 1.15f;
        float cueStartX = cue.x - aimX * cueBackOffset;
        float cueStartZ = cue.z - aimZ * cueBackOffset;
        float cueEndX = cue.x - aimX * (cueBackOffset + cueLength);
        float cueEndZ = cue.z - aimZ * (cueBackOffset + cueLength);

        dynamicBuffer.clear();
        offset = 0;

        offset = putVertex(dynamicBuffer, offset, cueStartX, BALL_Y + 0.015f, cueStartZ, 0f, 1f, 0f);
        putVertex(dynamicBuffer, offset, cueEndX, BALL_Y + 0.030f, cueEndZ, 0f, 1f, 0f);

        dynamicBuffer.position(0);
        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 24, dynamicBuffer);
        dynamicBuffer.position(3);
        GLES20.glEnableVertexAttribArray(normalAttribute);
        GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 24, dynamicBuffer);

        GLES20.glLineWidth(9.5f);
        GLES20.glUniform4fv(colorUniform, 1, color(CUE_COLORS[selectedCueIndex]), 0);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);
    }

    private void buildTableGeometry() {
        List<Float> verts = new ArrayList<>();

        // 1. Table Cloth Bed (#0B5D3B - Dark Green Cloth)
        addBox(verts, 0f, 0.040f, 0f, TABLE_HALF_X * 2.0f, 0.040f, TABLE_HALF_Z * 2.0f, 0f, 1f, 0f);

        // 2. Outer Base Frame (Dark Mahogany)
        addBox(verts, 0f, -0.040f, 0f, 2.84f, 0.12f, 1.54f, 0f, 1f, 0f);

        // 3. Wooden Rails (Dark Mahogany)
        float railY = 0.085f;
        float railH = 0.070f;
        float railW = 0.12f;
        addBox(verts, 0f, railY, -TABLE_HALF_Z - 0.06f, 2.84f, railH, railW, 0f, 1f, 0f);
        addBox(verts, 0f, railY, TABLE_HALF_Z + 0.06f, 2.84f, railH, railW, 0f, 1f, 0f);
        addBox(verts, -TABLE_HALF_X - 0.06f, railY, 0f, railW, railH, TABLE_HALF_Z * 2.0f + 0.24f, 0f, 1f, 0f);
        addBox(verts, TABLE_HALF_X + 0.06f, railY, 0f, railW, railH, TABLE_HALF_Z * 2.0f + 0.24f, 0f, 1f, 0f);

        // 4. Six Tapered Cloth Cushions (#094B30)
        float cY = 0.070f;
        float cH = 0.024f;
        float cW = 0.038f;

        addBox(verts, -0.63f, cY, -TABLE_HALF_Z + 0.019f, 1.10f, cH, cW, 0f, 1f, 0f);
        addBox(verts, 0.63f, cY, -TABLE_HALF_Z + 0.019f, 1.10f, cH, cW, 0f, 1f, 0f);
        addBox(verts, -0.63f, cY, TABLE_HALF_Z - 0.019f, 1.10f, cH, cW, 0f, 1f, 0f);
        addBox(verts, 0.63f, cY, TABLE_HALF_Z - 0.019f, 1.10f, cH, cW, 0f, 1f, 0f);
        addBox(verts, -TABLE_HALF_X + 0.019f, cY, 0f, cW, cH, 1.06f, 0f, 1f, 0f);
        addBox(verts, TABLE_HALF_X - 0.019f, cY, 0f, cW, cH, 1.06f, 0f, 1f, 0f);

        // 5. Diamond Sight Markers
        float markerSize = 0.012f;
        float mY = railY + 0.036f;
        float[] xLocs = {-0.80f, -0.40f, 0f, 0.40f, 0.80f};
        for (float x : xLocs) {
            addBox(verts, x, mY, -TABLE_HALF_Z - 0.06f, markerSize, markerSize, markerSize, 0f, 1f, 0f);
            addBox(verts, x, mY, TABLE_HALF_Z + 0.06f, markerSize, markerSize, markerSize, 0f, 1f, 0f);
        }

        tableVertexCount = verts.size() / 6;
        float[] array = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) array[i] = verts.get(i);
        tableBuffer = floatBuffer(array);
    }

    private void buildPocketGeometry() {
        List<Float> verts = new ArrayList<>();
        int segments = 24;

        for (float[] pos : POCKET_POSITIONS) {
            for (int i = 0; i < segments; i++) {
                float a = (float) (i * Math.PI * 2 / segments);
                float b = (float) ((i + 1) * Math.PI * 2 / segments);
                addTriangle(verts, pos[0], 0.061f, pos[1],
                        pos[0] + (float) Math.cos(a) * POCKET_RADIUS, 0.061f, pos[1] + (float) Math.sin(a) * POCKET_RADIUS,
                        pos[0] + (float) Math.cos(b) * POCKET_RADIUS, 0.061f, pos[1] + (float) Math.sin(b) * POCKET_RADIUS,
                        0f, 1f, 0f);
            }
        }

        for (float[] pos : POCKET_POSITIONS) {
            float innerR = POCKET_RADIUS * 0.88f;
            for (int i = 0; i < segments; i++) {
                float a = (float) (i * Math.PI * 2 / segments);
                float b = (float) ((i + 1) * Math.PI * 2 / segments);
                addTriangle(verts, pos[0], 0.010f, pos[1],
                        pos[0] + (float) Math.cos(a) * innerR, 0.060f, pos[1] + (float) Math.sin(a) * innerR,
                        pos[0] + (float) Math.cos(b) * innerR, 0.060f, pos[1] + (float) Math.sin(b) * innerR,
                        0f, 1f, 0f);
            }
        }

        pocketVertexCount = verts.size() / 6;
        float[] array = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) array[i] = verts.get(i);
        pocketBuffer = floatBuffer(array);
    }

    private static void addBox(List<Float> verts, float x, float y, float z,
                               float w, float h, float d, float nx, float ny, float nz) {
        float[] v = boxVertices(w, h, d);
        for (int i = 0; i < v.length; i += 6) {
            verts.add(v[i] + x);
            verts.add(v[i + 1] + y);
            verts.add(v[i + 2] + z);
            verts.add(v[i + 3]);
            verts.add(v[i + 4]);
            verts.add(v[i + 5]);
        }
    }

    private static void addTriangle(List<Float> verts,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3,
                                    float nx, float ny, float nz) {
        verts.add(x1); verts.add(y1); verts.add(z1); verts.add(nx); verts.add(ny); verts.add(nz);
        verts.add(x2); verts.add(y2); verts.add(z2); verts.add(nx); verts.add(ny); verts.add(nz);
        verts.add(x3); verts.add(y3); verts.add(z3); verts.add(nx); verts.add(ny); verts.add(nz);
    }

    private void stepPhysics(float dt) {
        synchronized (this) {
            for (Ball ball : balls) {
                if (ball.pocketed) {
                    continue;
                }
                ball.x += ball.vx * dt;
                ball.z += ball.vz * dt;
                float speed = (float) Math.hypot(ball.vx, ball.vz);
                if (speed > 0f) {
                    float newSpeed = Math.max(0f, speed - 0.40f * dt);
                    float scale = newSpeed / speed;
                    ball.vx *= scale;
                    ball.vz *= scale;
                    if (newSpeed < 0.012f) {
                        ball.vx = 0f;
                        ball.vz = 0f;
                    }
                }
                pocketIfReached(ball);
                if (!ball.pocketed) {
                    collideWithCushions(ball);
                }
            }

            for (int i = 0; i < balls.size(); i++) {
                Ball first = balls.get(i);
                if (first.pocketed) {
                    continue;
                }
                for (int j = i + 1; j < balls.size(); j++) {
                    Ball second = balls.get(j);
                    if (!second.pocketed) {
                        collide(first, second);
                    }
                }
            }

            if (shotInProgress && !isMoving()) {
                shotInProgress = false;
                if (twoPlayerMode) {
                    currentPlayer = currentPlayer == 1 ? 2 : 1;
                }
                notifyGameStateChanged();
            }
        }
    }

    private void pocketIfReached(Ball ball) {
        for (float[] pocket : POCKET_POSITIONS) {
            if (Math.hypot(ball.x - pocket[0], ball.z - pocket[1]) < POCKET_RADIUS) {
                ball.pocketed = true;
                ball.vx = 0f;
                ball.vz = 0f;
                if (ball.cue) {
                    ball.pocketed = false;
                    ball.x = 0f;
                    ball.z = 0.42f;
                } else {
                    pocketedCount++;
                }
                emitSound(SOUND_POCKET);
                return;
            }
        }
    }

    private void collideWithCushions(Ball ball) {
        float limitX = TABLE_HALF_X - BALL_RADIUS * 1.2f;
        float limitZ = TABLE_HALF_Z - BALL_RADIUS * 1.2f;

        if (Math.abs(ball.x) > limitX && Math.abs(ball.z) > 0.15f) {
            ball.x = Math.copySign(limitX, ball.x);
            ball.vx *= -0.85f;
        }
        if (Math.abs(ball.z) > limitZ && Math.abs(ball.x) > 0.15f) {
            ball.z = Math.copySign(limitZ, ball.z);
            ball.vz *= -0.85f;
        }
    }

    private void collide(Ball first, Ball second) {
        float dx = second.x - first.x;
        float dz = second.z - first.z;
        float distanceSquared = dx * dx + dz * dz;
        float diameter = BALL_RADIUS * 2f;
        if (distanceSquared >= diameter * diameter || distanceSquared < 0.000001f) {
            return;
        }

        float distance = (float) Math.sqrt(distanceSquared);
        float nx = dx / distance;
        float nz = dz / distance;
        float overlap = (diameter - distance) * 0.5f;
        first.x -= nx * overlap;
        first.z -= nz * overlap;
        second.x += nx * overlap;
        second.z += nz * overlap;

        float relativeVelocity = (second.vx - first.vx) * nx + (second.vz - first.vz) * nz;
        if (relativeVelocity >= 0f) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBallSoundMillis >= 60L) {
            lastBallSoundMillis = now;
            emitSound(SOUND_BALL);
        }
        float impulse = -(1f + 0.96f) * relativeVelocity * 0.5f;
        first.vx -= impulse * nx;
        first.vz -= impulse * nz;
        second.vx += impulse * nx;
        second.vz += impulse * nz;
    }

    private boolean isMoving() {
        for (Ball ball : balls) {
            if (!ball.pocketed && Math.hypot(ball.vx, ball.vz) > 0.012f) {
                return true;
            }
        }
        return false;
    }

    private Ball cueBall() {
        for (Ball ball : balls) {
            if (ball.cue) {
                return ball;
            }
        }
        return null;
    }

    private void emitSound(int soundType) {
        SoundListener listener = soundListener;
        if (listener != null) {
            listener.onSound(soundType);
        }
    }

    private void notifyGameStateChanged() {
        GameStateListener listener = gameStateListener;
        if (listener != null) {
            listener.onGameStateChanged(currentPlayer, pocketedCount);
        }
    }

    private void createSphere() {
        int latitudeBands = 14;
        int longitudeBands = 20;
        float[] vertices = new float[latitudeBands * longitudeBands * 6 * 6];
        int offset = 0;
        for (int lat = 0; lat < latitudeBands; lat++) {
            float lat0 = (float) (Math.PI * (-0.5 + (double) lat / latitudeBands));
            float lat1 = (float) (Math.PI * (-0.5 + (double) (lat + 1) / latitudeBands));
            for (int lon = 0; lon < longitudeBands; lon++) {
                float lon0 = (float) (Math.PI * 2 * lon / longitudeBands);
                float lon1 = (float) (Math.PI * 2 * (lon + 1) / longitudeBands);
                float[] a = spherePoint(lat0, lon0);
                float[] b = spherePoint(lat1, lon0);
                float[] c = spherePoint(lat1, lon1);
                float[] d = spherePoint(lat0, lon1);
                offset = putVertex(vertices, offset, a);
                offset = putVertex(vertices, offset, b);
                offset = putVertex(vertices, offset, c);
                offset = putVertex(vertices, offset, a);
                offset = putVertex(vertices, offset, c);
                offset = putVertex(vertices, offset, d);
            }
        }
        sphereBuffer = floatBuffer(vertices);
        sphereVertexCount = vertices.length / 6;
    }

    private static float[] spherePoint(float latitude, float longitude) {
        float y = (float) Math.sin(latitude);
        float ring = (float) Math.cos(latitude);
        float x = ring * (float) Math.cos(longitude);
        float z = ring * (float) Math.sin(longitude);
        return new float[] {x, y, z, x, y, z};
    }

    private static int putVertex(FloatBuffer buffer, int offset, float x, float y, float z, float nx, float ny, float nz) {
        buffer.put(offset++, x);
        buffer.put(offset++, y);
        buffer.put(offset++, z);
        buffer.put(offset++, nx);
        buffer.put(offset++, ny);
        buffer.put(offset++, nz);
        return offset;
    }

    private static int putVertex(float[] buffer, int offset, float[] vertex) {
        System.arraycopy(vertex, 0, buffer, offset, vertex.length);
        return offset + vertex.length;
    }

    private static float[] boxVertices(float width, float height, float depth) {
        float x = width * 0.5f;
        float y = height * 0.5f;
        float z = depth * 0.5f;
        float[] vertices = new float[36 * 6];
        int offset = 0;
        offset = face(vertices, offset, new float[] {-x, -y, z}, new float[] {x, -y, z},
                new float[] {x, y, z}, new float[] {-x, y, z}, 0f, 0f, 1f);
        offset = face(vertices, offset, new float[] {x, -y, -z}, new float[] {-x, -y, -z},
                new float[] {-x, y, -z}, new float[] {x, y, -z}, 0f, 0f, -1f);
        offset = face(vertices, offset, new float[] {-x, y, z}, new float[] {x, y, z},
                new float[] {x, y, -z}, new float[] {-x, y, -z}, 0f, 1f, 0f);
        offset = face(vertices, offset, new float[] {-x, -y, -z}, new float[] {x, -y, -z},
                new float[] {x, -y, z}, new float[] {-x, -y, z}, 0f, -1f, 0f);
        offset = face(vertices, offset, new float[] {x, -y, z}, new float[] {x, -y, -z},
                new float[] {x, y, -z}, new float[] {x, y, z}, 1f, 0f, 0f);
        face(vertices, offset, new float[] {-x, -y, -z}, new float[] {-x, -y, z},
                new float[] {-x, y, z}, new float[] {-x, y, -z}, -1f, 0f, 0f);
        return vertices;
    }

    private static int face(float[] vertices, int offset, float[] a, float[] b, float[] c,
            float[] d, float nx, float ny, float nz) {
        offset = appendFaceVertex(vertices, offset, a, nx, ny, nz);
        offset = appendFaceVertex(vertices, offset, b, nx, ny, nz);
        offset = appendFaceVertex(vertices, offset, c, nx, ny, nz);
        offset = appendFaceVertex(vertices, offset, a, nx, ny, nz);
        offset = appendFaceVertex(vertices, offset, c, nx, ny, nz);
        return appendFaceVertex(vertices, offset, d, nx, ny, nz);
    }

    private static int appendFaceVertex(float[] vertices, int offset, float[] p,
            float nx, float ny, float nz) {
        vertices[offset++] = p[0];
        vertices[offset++] = p[1];
        vertices[offset++] = p[2];
        vertices[offset++] = nx;
        vertices[offset++] = ny;
        vertices[offset++] = nz;
        return offset;
    }

    private static FloatBuffer floatBuffer(float[] values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer.put(values);
        buffer.position(0);
        return buffer;
    }

    private static float[] color(int argb) {
        return new float[] {
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                ((argb >> 24) & 0xFF) / 255f
        };
    }

    private static int createProgram(String vertexShader, String fragmentShader) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);
        int shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vertex);
        GLES20.glAttachShader(shaderProgram, fragment);
        GLES20.glLinkProgram(shaderProgram);
        int[] status = new int[1];
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(shaderProgram);
            GLES20.glDeleteProgram(shaderProgram);
            throw new IllegalStateException("Could not link pool renderer: " + log);
        }
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        return shaderProgram;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Could not compile pool renderer shader: " + log);
        }
        return shader;
    }

    private static final String VERTEX_SHADER =
            "attribute vec3 aPosition;\n" +
            "attribute vec3 aNormal;\n" +
            "uniform mat4 uMvp;\n" +
            "varying float vLight;\n" +
            "void main() {\n" +
            "  vec3 light = normalize(vec3(-0.3, 0.9, 0.4));\n" +
            "  vLight = 0.52 + 0.48 * max(dot(normalize(aNormal), light), 0.0);\n" +
            "  gl_Position = uMvp * vec4(aPosition, 1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "varying float vLight;\n" +
            "void main() {\n" +
            "  gl_FragColor = vec4(uColor.rgb * vLight, uColor.a);\n" +
            "}\n";

    private static final class Ball {
        final int color;
        final boolean cue;
        float x;
        float y;
        float z;
        float vx;
        float vz;
        boolean pocketed;

        Ball(float x, float y, float z, int color, boolean cue) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.cue = cue;
        }
    }
}
