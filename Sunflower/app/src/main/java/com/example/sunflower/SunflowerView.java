package com.example.sunflower;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class SunflowerView extends View {

    private Bitmap centralCircle;
    private Bitmap frontPetal;   // e.g. orange
    private Bitmap backPetal;    // e.g. yellow
    private Bitmap stem;
    private Bitmap leftLeaf;
    private Bitmap rightLeaf;
    private Bitmap photoImage;   // thumbup.png

    // -- Durations (in ms) --
    private static final long STEM_ANIMATION_DURATION = 2000;
    private static final long LEAF_ANIMATION_DURATION = 2000;
    private static final long PETAL_APPEAR_DURATION = 500; // each petal fades in one by one
    private static final int  TOTAL_PETALS = 16;
    private static final long PETAL_TOTAL_DURATION = PETAL_APPEAR_DURATION * TOTAL_PETALS;
    private static final long PHOTO_ANIMATION_DURATION = 2000; // scale photo over 2 seconds

    // -- Animation start times --
    private long animationStartTime = 0;      // for stem
    private long leafAnimationStartTime = 0;    // for leaves
    private long petalAnimationStartTime = 0;   // for petals
    private long photoAnimationStartTime = 0;   // for the final photo scale

    // -- Start Again Button --
    private boolean showStartButton = false;
    private Rect startButtonRect;

    public SunflowerView(Context context) {
        super(context);
        init();
    }

    public SunflowerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SunflowerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        centralCircle = BitmapFactory.decodeResource(getResources(), R.drawable.centralsmall);
        frontPetal    = BitmapFactory.decodeResource(getResources(), R.drawable.frontpetalsmall);
        backPetal     = BitmapFactory.decodeResource(getResources(), R.drawable.backpetalsmall);
        stem          = BitmapFactory.decodeResource(getResources(), R.drawable.stemsmall);
        leftLeaf      = BitmapFactory.decodeResource(getResources(), R.drawable.leftleafsmall);
        rightLeaf     = BitmapFactory.decodeResource(getResources(), R.drawable.rightleafsmall);
        photoImage    = BitmapFactory.decodeResource(getResources(), R.drawable.thumbup);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.WHITE);
        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();
        long now = System.currentTimeMillis();

        // --------------------------
        // Draw the growing stem.
        //
        // In the original code the stem is drawn with:
        //    destTop = canvasHeight + centralCircle.getHeight()/2 - visibleStemHeight;
        //
        // That makes the central circle’s center at
        //    destTop - centralCircle.getHeight()/2 = canvasHeight - visibleStemHeight
        //
        // so if stem.getHeight() equals canvasHeight/2 then the center lands at canvasHeight/2.
        // On a 720×1280 screen this no longer holds.
        //
        // To fix this we define the desired final (scaled) stem height to be:
        //    finalStemHeight = canvasHeight/2 - centralCircle.getHeight()/2;
        // Then when the stem is fully drawn (progress==1):
        //    destTop = canvasHeight - finalStemHeight = canvasHeight/2 + centralCircle.getHeight()/2
        // and the central circle (drawn immediately above) will have its center at canvasHeight/2.
        // --------------------------
        if (animationStartTime == 0) {
            animationStartTime = now;
        }
        long elapsed = now - animationStartTime;
        float progress = Math.min(1f, (float) elapsed / STEM_ANIMATION_DURATION);

        // Compute the desired (final) stem height so that when full-grown, the central circle's center is at canvasHeight/2.
        int finalStemHeight = canvasHeight / 2 - centralCircle.getHeight() / 2;
        // Determine the scale factor to map the intrinsic stem bitmap height to finalStemHeight.
        float stemScale = finalStemHeight / (float) stem.getHeight();

        // How many pixels of the stem bitmap should be visible (based on animation progress)
        int visibleStemBitmapHeight = (int) (stem.getHeight() * progress);
        // And its drawn (scaled) height:
        int visibleStemHeight = (int) (visibleStemBitmapHeight * stemScale);

        int destBottom = canvasHeight;
        int destTop = canvasHeight - visibleStemHeight;
        int scaledStemWidth = (int) (stem.getWidth() * stemScale);
        int destLeft = (canvasWidth - scaledStemWidth) / 2;
        int destRight = destLeft + scaledStemWidth;

        // Use the bottom part of the stem bitmap.
        Rect srcRect = new Rect(0, stem.getHeight() - visibleStemBitmapHeight, stem.getWidth(), stem.getHeight());
        Rect destRect = new Rect(destLeft, destTop, destRight, destBottom);
        canvas.drawBitmap(stem, srcRect, destRect, null);

        // --------------------------
        // Once the stem is fully grown, draw the rest of the sunflower.
        // --------------------------
        if (progress >= 1f) {
            // Draw the central circle immediately above the stem.
            int circleLeft = (canvasWidth - centralCircle.getWidth()) / 2;
            int circleTop = destTop - centralCircle.getHeight();
            canvas.drawBitmap(centralCircle, circleLeft, circleTop, null);
            // (Its center is at circleTop + centralCircle.getHeight()/2.
            // With destTop = canvasHeight/2 + centralCircle.getHeight()/2,
            // the center becomes canvasHeight/2.)

            // Draw leaves.
            if (leafAnimationStartTime == 0) {
                leafAnimationStartTime = now;
            }
            drawLeftLeaf(canvas, destTop, destRight);
            drawRightLeaf(canvas, destTop, destLeft);

            // Draw petals.
            if (petalAnimationStartTime == 0) {
                petalAnimationStartTime = now;
            }
            int centerX = circleLeft + centralCircle.getWidth() / 2;
            int centerY = circleTop + centralCircle.getHeight() / 2;
            drawPetalsSequentially(canvas, centerX, centerY);

            // Draw the photo after the petals animation is done.
            long petalElapsed = now - petalAnimationStartTime;
            if (petalElapsed >= PETAL_TOTAL_DURATION) {
                if (photoAnimationStartTime == 0) {
                    photoAnimationStartTime = now;
                }
                drawPhoto(canvas, centerX, centerY, canvasWidth);
            }
        }

        // Check if any animations are still running.
        boolean stillAnimating =
                (progress < 1f) ||
                        ((now - leafAnimationStartTime) < LEAF_ANIMATION_DURATION) ||
                        ((now - petalAnimationStartTime) < PETAL_TOTAL_DURATION) ||
                        ((photoAnimationStartTime == 0) || (now - photoAnimationStartTime < PHOTO_ANIMATION_DURATION));

        if (!stillAnimating) {
            showStartButton = true;
            drawStartButton(canvas, canvasWidth, canvasHeight);
            drawTextLine(canvas, canvasWidth, canvasHeight);
        } else {
            showStartButton = false;
        }

        if (stillAnimating) {
            postInvalidateOnAnimation();
        }
    }

    // ----------------------------------------------------------
    // Leaves
    // ----------------------------------------------------------
    private void drawRightLeaf(Canvas canvas, int stemTop, int stemLeft) {
        long leafElapsed = System.currentTimeMillis() - leafAnimationStartTime;
        float leafProgress = Math.min(1f, (float) leafElapsed / LEAF_ANIMATION_DURATION);
        float scaleFactor = 0.5f + 0.5f * leafProgress; // Scale from 0.5x to 1.0x

        int leafWidth = rightLeaf.getWidth();
        int leafHeight = rightLeaf.getHeight();

        int leafRight = stemLeft; // left edge fixed at stem
        int screenWidth = getWidth();
        float maxAllowedScale = (float) (screenWidth - leafRight) / leafWidth;
        scaleFactor = Math.min(scaleFactor, maxAllowedScale);

        int leafTop = stemTop + stem.getHeight() / 16;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleFactor, scaleFactor, 0, leafHeight / 2f); // Pivot at left edge
        matrix.postTranslate(leafRight, leafTop - (leafHeight * (scaleFactor - 1) / 2));
        canvas.drawBitmap(rightLeaf, matrix, null);
    }

    private void drawLeftLeaf(Canvas canvas, int stemTop, int stemRight) {
        long leafElapsed = System.currentTimeMillis() - leafAnimationStartTime;
        float leafProgress = Math.min(1f, (float) leafElapsed / LEAF_ANIMATION_DURATION);
        float scaleFactor = 0.5f + 0.5f * leafProgress; // Scale from 0.5x to 1.0x

        int leafWidth = leftLeaf.getWidth();
        int leafHeight = leftLeaf.getHeight();
        int leafRight = stemRight; // right edge fixed at stem
        int screenWidth = getWidth();
        float maxAllowedScale = (float) (leafRight) / leafWidth;
        scaleFactor = Math.min(scaleFactor, maxAllowedScale);

        int leafTop = stemTop + stem.getHeight() / 16; // vertical adjustment
        Matrix matrix = new Matrix();
        matrix.postScale(scaleFactor, scaleFactor, leafWidth, leafHeight / 2f); // Pivot at right edge
        matrix.postTranslate(leafRight - leafWidth, leafTop - (leafHeight * (scaleFactor - 1) / 2));
        canvas.drawBitmap(leftLeaf, matrix, null);
    }

    // ----------------------------------------------------------
    // Petals (Sequential fade-in for 16 petals)
    // ----------------------------------------------------------
    private static class PetalInfo {
        Bitmap petalBitmap;
        double angleDeg;
        PetalInfo(Bitmap bmp, double angle) {
            petalBitmap = bmp;
            angleDeg = angle;
        }
    }

    private PetalInfo[] buildPetalSequence() {
        PetalInfo[] petals = new PetalInfo[TOTAL_PETALS];
        for (int i = 0; i < 8; i++) {
            double angleFront = i * 45.0;         // front petals at multiples of 45°
            double angleBack  = angleFront + 22.5;  // back petals offset by 22.5°
            petals[2 * i]     = new PetalInfo(backPetal, angleBack);
            petals[2 * i + 1] = new PetalInfo(frontPetal, angleFront);
        }
        return petals;
    }

    /**
     * Draws 16 petals sequentially with each petal fading in over 500ms.
     */
    private void drawPetalsSequentially(Canvas canvas, int centerX, int centerY) {
        long petalElapsed = System.currentTimeMillis() - petalAnimationStartTime;
        PetalInfo[] petals = buildPetalSequence();
        for (int i = 0; i < petals.length; i++) {
            long startFade = i * PETAL_APPEAR_DURATION;
            long endFade   = startFade + PETAL_APPEAR_DURATION;
            int alpha;
            if (petalElapsed < startFade) {
                alpha = 0;
            } else if (petalElapsed >= endFade) {
                alpha = 255;
            } else {
                float fraction = (float) (petalElapsed - startFade) / PETAL_APPEAR_DURATION;
                alpha = (int) (fraction * 255);
            }
            if (alpha > 0) {
                Paint paint = new Paint();
                paint.setAlpha(alpha);
                drawSinglePetal(canvas, centerX, centerY,
                        petals[i].petalBitmap, petals[i].angleDeg, paint);
            }
        }
    }

    private void drawSinglePetal(Canvas canvas,
                                 float centerX,
                                 float centerY,
                                 Bitmap petal,
                                 double angleDeg,
                                 Paint paint) {
        int petalWidth = petal.getWidth();
        int petalHeight = petal.getHeight();
        float circleRadius = centralCircle.getWidth() / 2f;
        // Adjust final radius so the petal's base is near the circle edge.
        float finalRadius = circleRadius - 10 + (petalHeight / 2f);
        double angleRad = Math.toRadians(angleDeg);
        float x = centerX + (float) (finalRadius * Math.cos(angleRad));
        float y = centerY + (float) (finalRadius * Math.sin(angleRad));
        Matrix matrix = new Matrix();
        // Center the petal at (0,0)
        matrix.postTranslate(-petalWidth / 2f, -petalHeight / 2f);
        // Rotate so the petal points outward (adjust by 90°)
        matrix.postRotate((float) angleDeg + 90);
        // Translate to its final position
        matrix.postTranslate(x, y);
        canvas.drawBitmap(petal, matrix, paint);
    }

    // ----------------------------------------------------------
    // Final Photo Scaling Animation
    // ----------------------------------------------------------
    private void drawPhoto(Canvas canvas, int centerX, int centerY, int canvasWidth) {
        long now = System.currentTimeMillis();
        long photoElapsed = now - photoAnimationStartTime;
        float photoProgress = Math.min(1f, (float) photoElapsed / PHOTO_ANIMATION_DURATION);
        int photoW = photoImage.getWidth();
        int photoH = photoImage.getHeight();
        float finalScale = (float) canvasWidth / photoW;
        float currentScale = finalScale * photoProgress;
        Matrix matrix = new Matrix();
        matrix.postTranslate(-photoW / 2f, -photoH / 2f);
        matrix.postScale(currentScale, currentScale);
        matrix.postTranslate(centerX, centerY);
        canvas.drawBitmap(photoImage, matrix, null);
    }

    // ----------------------------------------------------------
    // Draw Start Again Button (Top Left)
    // ----------------------------------------------------------
    private void drawStartButton(Canvas canvas, int canvasWidth, int canvasHeight) {
        int buttonWidth = 350;
        int buttonHeight = 100;
        int margin = 20; // margin from the top and left edges

        int left = margin;
        int top = margin;
        startButtonRect = new Rect(left, top, left + buttonWidth, top + buttonHeight);

        Paint buttonPaint = new Paint();
        buttonPaint.setColor(Color.LTGRAY);
        canvas.drawRect(startButtonRect, buttonPaint);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(40);
        textPaint.setTextAlign(Paint.Align.CENTER);

        String buttonText = getContext().getString(R.string.start_again);
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        float textX = startButtonRect.centerX();
        float textY = startButtonRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2;
        canvas.drawText(buttonText, textX, textY, textPaint);
    }

    // ----------------------------------------------------------
    // Draw Greeting Text Line Under the Start Again Button
    // ----------------------------------------------------------
    private void drawTextLine(Canvas canvas, int canvasWidth, int canvasHeight) {
        int margin = 20;
        String greeting = getContext().getString(R.string.textgreeting);
        int textWidth = canvasWidth - 2 * margin;

        TextPaint textPaint = new TextPaint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(40);
        textPaint.setTextAlign(Paint.Align.LEFT);

        StaticLayout staticLayout = new StaticLayout(
                greeting,
                textPaint,
                textWidth,
                Layout.Alignment.ALIGN_CENTER,
                1.0f,
                0.0f,
                false);

        canvas.save();
        int textTop = startButtonRect.bottom + margin;
        canvas.translate((canvasWidth - textWidth) / 2, textTop);
        staticLayout.draw(canvas);
        canvas.restore();
    }

    // ----------------------------------------------------------
    // Handle touch events for the Start Again button
    // ----------------------------------------------------------
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (showStartButton && event.getAction() == MotionEvent.ACTION_DOWN) {
            if (startButtonRect != null && startButtonRect.contains((int) event.getX(), (int) event.getY())) {
                // Reset all animation timers to restart the animation sequence.
                animationStartTime = 0;
                leafAnimationStartTime = 0;
                petalAnimationStartTime = 0;
                photoAnimationStartTime = 0;
                showStartButton = false;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
