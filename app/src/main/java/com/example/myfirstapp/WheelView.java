package com.example.myfirstapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.OverScroller;

public class WheelView extends View {

    private static final int DIGITS = 10;

    private float itemHeight;
    private float totalOffset;

    private int currentValue;

    private OverScroller scroller;
    private VelocityTracker velocityTracker;

    private float lastY;

    private Paint textPaint;
    private Paint framePaint;
    private Paint borderPaint;
    private Paint separatorPaint;
    private Paint shadowPaint;
    private Paint glossPaint;
    private Paint windowPaint;
    private Paint highlightPaint;
    private Paint blackFramePaint;

    private RectF frameRect = new RectF();
    private RectF windowRect = new RectF();

    private int touchSlop;
    private boolean dragging;

    public WheelView(Context context) {
        super(context);
        init(context);
    }

    public WheelView(
            Context context,
            AttributeSet attrs
    ) {
        super(context, attrs);
        init(context);
    }

    public WheelView(
            Context context,
            AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {

        scroller =
                new OverScroller(context);

        touchSlop =
                ViewConfiguration
                        .get(context)
                        .getScaledTouchSlop();

        textPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        textPaint.setColor(
                Color.parseColor("#202020")
        );

        textPaint.setTextAlign(
                Paint.Align.CENTER
        );

        textPaint.setTypeface(
                Typeface.MONOSPACE
        );

        textPaint.setFakeBoldText(
                true
        );


        framePaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        borderPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        borderPaint.setStyle(
                Paint.Style.STROKE
        );

        borderPaint.setStrokeWidth(
                dp(2)
        );

        borderPaint.setColor(
                Color.parseColor("#909090")
        );


        separatorPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        separatorPaint.setColor(
                Color.parseColor("#5A5A5A")
        );

        separatorPaint.setStrokeWidth(
                dp(2)
        );


        shadowPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        glossPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        windowPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        highlightPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        blackFramePaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        blackFramePaint.setStyle(
                Paint.Style.STROKE
        );

        blackFramePaint.setStrokeWidth(
                dp(2)
        );

        blackFramePaint.setColor(
                Color.parseColor("#444444")
        );


        setValue(
                0,
                false
        );
    }


    private float dp(float value) {

        return value *
                getResources()
                        .getDisplayMetrics()
                        .density;
    }


    @Override
    protected void onSizeChanged(
            int w,
            int h,
            int oldw,
            int oldh
    ) {

        super.onSizeChanged(
                w,
                h,
                oldw,
                oldh
        );


        itemHeight =
                h / 3f;


        if (itemHeight < 1f) {
            itemHeight = 1f;
        }


        textPaint.setTextSize(
                itemHeight * 0.72f
        );


        frameRect.set(
                dp(2),
                dp(2),
                w - dp(2),
                h - dp(2)
        );


        windowRect.set(
                dp(4),
                h / 2f -
                        itemHeight / 2f,
                w - dp(4),
                h / 2f +
                        itemHeight / 2f
        );


        updateOffsetFromValue();
    }


    private void updateOffsetFromValue() {

        totalOffset =
                currentValue *
                        itemHeight;
    }


    public int getValue() {

        return currentValue;
    }


    public void setValue(int value) {

        setValue(
                value,
                true
        );
    }


    public void setValue(
            int value,
            boolean animated
    ) {

        value =
                wrap(value);


        if (itemHeight <= 0) {

            currentValue =
                    value;

            return;
        }


        if (!animated) {

            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }

            currentValue =
                    value;

            updateOffsetFromValue();

            invalidate();

            return;
        }


        float target =
                value *
                        itemHeight;


        if (!scroller.isFinished()) {

            scroller.abortAnimation();
        }


        scroller.startScroll(
                0,
                (int) totalOffset,
                0,
                (int) (
                        target -
                                totalOffset
                ),
                250
        );


        currentValue =
                value;


        invalidate();
    }


    private int wrap(int value) {

        value %= DIGITS;

        if (value < 0) {
            value += DIGITS;
        }

        return value;
    }


    private void updateCurrentValue() {

        if (itemHeight <= 0) {
            return;
        }


        int index =
                Math.round(
                        totalOffset /
                                itemHeight
                );


        currentValue =
                wrap(index);
    }


    private void snap() {

        if (itemHeight <= 0) {
            return;
        }


        float target =
                Math.round(
                        totalOffset /
                                itemHeight
                ) *
                        itemHeight;


        scroller.startScroll(
                0,
                (int) totalOffset,
                0,
                (int) (
                        target -
                                totalOffset
                ),
                180
        );


        invalidate();
    }


    private void allowParentIntercept(
            boolean allow
    ) {

        ViewParent parent =
                getParent();


        while (parent != null) {

            parent.requestDisallowInterceptTouchEvent(
                    !allow
            );

            parent =
                    parent.getParent();
        }
    }


    @Override
    public boolean onTouchEvent(
            MotionEvent event
    ) {

        if (velocityTracker == null) {

            velocityTracker =
                    VelocityTracker.obtain();
        }


        velocityTracker.addMovement(
                event
        );


        switch (
                event.getActionMasked()
        ) {

            case MotionEvent.ACTION_DOWN:

                /*
                 * Ключевое исправление:
                 * ScrollView больше не перехватывает
                 * вертикальный жест барабана.
                 */
                allowParentIntercept(
                        false
                );


                if (!scroller.isFinished()) {

                    scroller.abortAnimation();
                }


                dragging =
                        false;


                lastY =
                        event.getY();


                return true;


            case MotionEvent.ACTION_MOVE:

                float dy =
                        event.getY() -
                                lastY;


                if (!dragging) {

                    if (
                            Math.abs(dy) >
                                    touchSlop
                    ) {

                        dragging =
                                true;

                        allowParentIntercept(
                                false
                        );

                    } else {

                        return true;
                    }
                }


                lastY =
                        event.getY();


                /*
                 * Палец вниз → цифры вниз.
                 * Палец вверх → цифры вверх.
                 */
                totalOffset -=
                        dy;


                updateCurrentValue();


                invalidate();


                return true;


            case MotionEvent.ACTION_UP:

                velocityTracker.computeCurrentVelocity(
                        1000
                );


                float velocityY =
                        velocityTracker
                                .getYVelocity();


                if (
                        Math.abs(velocityY) >
                                300
                ) {

                    scroller.fling(
                            0,
                            (int) totalOffset,
                            0,
                            (int) -velocityY,
                            0,
                            0,
                            Integer.MIN_VALUE,
                            Integer.MAX_VALUE
                    );

                    invalidate();

                } else {

                    snap();
                }


                allowParentIntercept(
                        true
                );


                velocityTracker.recycle();

                velocityTracker =
                        null;


                dragging =
                        false;


                return true;


            case MotionEvent.ACTION_CANCEL:

                if (velocityTracker != null) {

                    velocityTracker.recycle();

                    velocityTracker =
                            null;
                }


                dragging =
                        false;


                snap();


                allowParentIntercept(
                        true
                );


                return true;
        }


        return super.onTouchEvent(
                event
        );
    }


    @Override
    public void computeScroll() {

        if (
                scroller.computeScrollOffset()
        ) {

            totalOffset =
                    scroller.getCurrY();


            updateCurrentValue();


            invalidate();

        } else {

            if (!dragging) {

                float target =
                        Math.round(
                                totalOffset /
                                        itemHeight
                        ) *
                                itemHeight;


                if (
                        Math.abs(
                                target -
                                        totalOffset
                        ) > 1f
                ) {

                    snap();

                } else {

                    totalOffset =
                            target;

                    updateCurrentValue();
                }
            }
        }
    }


    private int getDigit(
            int offset
    ) {

        int center =
                Math.round(
                        totalOffset /
                                itemHeight
                );


        return wrap(
                center +
                        offset
        );
    }


    private float getCenterY(
            int offset
    ) {

        float nearest =
                Math.round(
                        totalOffset /
                                itemHeight
                ) *
                        itemHeight;


        float remain =
                totalOffset -
                        nearest;


        return getHeight() /
                2f
                +
                offset *
                        itemHeight
                -
                remain;
    }


    private float getScale(
            float y
    ) {

        float distance =
                Math.abs(
                        y -
                                getHeight() /
                                        2f
                );


        float k =
                1f -
                        distance /
                                (
                                        getHeight() /
                                                2f
                                );


        if (k < 0) {
            k = 0;
        }


        return 0.45f +
                k *
                        0.55f;
    }


    private int getAlpha(
            float y
    ) {

        float distance =
                Math.abs(
                        y -
                                getHeight() /
                                        2f
                );


        float k =
                1f -
                        distance /
                                (
                                        getHeight() /
                                                2f
                                );


        if (k < 0) {
            k = 0;
        }


        return (int)
                (
                        60 +
                                k *
                                        195
                );
    }


    private void drawDigit(
            Canvas canvas,
            int digit,
            float centerY
    ) {

        float scale =
                getScale(
                        centerY
                );


        textPaint.setAlpha(
                getAlpha(
                        centerY
                )
        );


        textPaint.setTextSize(
                itemHeight *
                        0.72f *
                        scale
        );


        float x =
                getWidth() /
                        2f;


        canvas.drawText(
                String.valueOf(
                        digit
                ),
                x,
                centerY -
                        (
                                textPaint.ascent() +
                                        textPaint.descent()
                        ) /
                                2,
                textPaint
        );
    }


    @Override
    protected void onDraw(
            Canvas canvas
    ) {

        super.onDraw(
                canvas
        );


        int w =
                getWidth();


        int h =
                getHeight();


        float radius =
                dp(12);


        framePaint.setShader(
                new LinearGradient(
                        0,
                        0,
                        0,
                        h,
                        new int[]{
                                Color.parseColor(
                                        "#D5D5D5"
                                ),
                                Color.parseColor(
                                        "#F7F7F7"
                                ),
                                Color.parseColor(
                                        "#CFCFCF"
                                )
                        },
                        new float[]{
                                0f,
                                0.5f,
                                1f
                        },
                        Shader.TileMode.CLAMP
                )
        );


        canvas.drawRoundRect(
                frameRect,
                radius,
                radius,
                framePaint
        );


        canvas.drawRoundRect(
                frameRect,
                radius,
                radius,
                borderPaint
        );


        windowPaint.setShader(
                new LinearGradient(
                        0,
                        windowRect.top,
                        0,
                        windowRect.bottom,
                        new int[]{
                                Color.parseColor(
                                        "#ECECEC"
                                ),
                                Color.WHITE,
                                Color.parseColor(
                                        "#E6E6E6"
                                )
                        },
                        null,
                        Shader.TileMode.CLAMP
                )
        );


        canvas.drawRoundRect(
                windowRect,
                dp(6),
                dp(6),
                windowPaint
        );


        canvas.drawRoundRect(
                windowRect,
                dp(6),
                dp(6),
                blackFramePaint
        );


        shadowPaint.setShader(
                new LinearGradient(
                        0,
                        0,
                        0,
                        h * 0.22f,
                        new int[]{
                                Color.argb(
                                        120,
                                        0,
                                        0,
                                        0
                                ),
                                Color.argb(
                                        0,
                                        0,
                                        0,
                                        0
                                )
                        },
                        null,
                        Shader.TileMode.CLAMP
                )
        );


        canvas.drawRect(
                0,
                0,
                w,
                h * 0.22f,
                shadowPaint
        );


        shadowPaint.setShader(
                new LinearGradient(
                        0,
                        h,
                        0,
                        h * 0.78f,
                        new int[]{
                                Color.argb(
                                        120,
                                        0,
                                        0,
                                        0
                                ),
                                Color.argb(
                                        0,
                                        0,
                                        0,
                                        0
                                )
                        },
                        null,
                        Shader.TileMode.CLAMP
                )
        );


        canvas.drawRect(
                0,
                h * 0.78f,
                w,
                h,
                shadowPaint
        );


        glossPaint.setShader(
                new LinearGradient(
                        0,
                        0,
                        w,
                        0,
                        new int[]{
                                Color.argb(
                                        80,
                                        255,
                                        255,
                                        255
                                ),
                                Color.argb(
                                        10,
                                        255,
                                        255,
                                        255
                                ),
                                Color.argb(
                                        80,
                                        255,
                                        255,
                                        255
                                )
                        },
                        new float[]{
                                0f,
                                0.5f,
                                1f
                        },
                        Shader.TileMode.CLAMP
                )
        );


        canvas.drawRoundRect(
                frameRect,
                radius,
                radius,
                glossPaint
        );


        canvas.drawLine(
                0,
                windowRect.top,
                w,
                windowRect.top,
                separatorPaint
        );


        canvas.drawLine(
                0,
                windowRect.bottom,
                w,
                windowRect.bottom,
                separatorPaint
        );


        for (
                int i = -4;
                i <= 4;
                i++
        ) {

            float y =
                    getCenterY(
                            i
                    );


            if (
                    y < -itemHeight ||
                            y >
                                    h +
                                            itemHeight
            ) {

                continue;
            }


            canvas.save();


            float scale =
                    getScale(
                            y
                    );


            canvas.scale(
                    1f,
                    scale,
                    w / 2f,
                    y
            );


            drawDigit(
                    canvas,
                    getDigit(i),
                    y
            );


            canvas.restore();
        }


        highlightPaint.setShader(
                new LinearGradient(
                        0,
                        windowRect.top,
                        0,
                        windowRect.bottom,
                        new int[]{
                                Color.argb(
                                        20,
                                        255,
                                        255,
                                        255
                                ),
                                Color.argb(
                                        0,
                                        255,
                                        255,
                                        255
                                ),
                                Color.argb(
                                        40,
                                        0,
                                        0,
                                        0
                                )
                        },
                        null,
                        Shader.TileMode.CLAMP
                )
        );


        canvas.drawRoundRect(
                windowRect,
                dp(6),
                dp(6),
                highlightPaint
        );
    }
}