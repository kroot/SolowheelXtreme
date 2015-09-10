/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.inventist.solowheel.xtreme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class XtremeWatchFace extends CanvasWatchFaceService {

    private final static String TAG = "solowheel"; //.class.getSimpleName();

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    private GoogleApiClient mGoogleApiClient;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        Paint mBackgroundPaint;
        Paint mHourHandPaint;
        Paint mMinuteHandPaint;
        Paint mTextBatteryPaint;
        Paint mTextSpeedPaint;
        private Paint arcPaintBatteryFill;
        private Paint arcPaintBatteryForeStroke;
        private Paint arcPaintBatteryBackStroke;
        private Bitmap mBackgroundBitmap;

        boolean mAmbient;
        Time mTime;

        String mFormattedSpeed = "";
        Double mBatteryPercent = 0.0;
       // String mSpeedUnits = "";

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            Log.i(TAG, "onCreate");

            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(XtremeWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = XtremeWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.analog_background));

            mMinuteHandPaint = new Paint();
            mMinuteHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mMinuteHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_minute_hand_stroke));
            mMinuteHandPaint.setAntiAlias(true);
            mMinuteHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mHourHandPaint = new Paint();
            mHourHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHourHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hour_hand_stroke));
            mHourHandPaint.setAntiAlias(true);
            mHourHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mTextSpeedPaint = new Paint();
            mTextSpeedPaint.setColor(resources.getColor(R.color.analog_hands));
            mTextSpeedPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_minute_hand_stroke));
            mTextSpeedPaint.setAntiAlias(true);
            mTextSpeedPaint.setStrokeCap(Paint.Cap.ROUND);
            mTextSpeedPaint.setTextSize(20);

            mTextBatteryPaint = new Paint();
            mTextBatteryPaint.setColor(resources.getColor(R.color.analog_hands));
            mTextBatteryPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_minute_hand_stroke));
            mTextBatteryPaint.setAntiAlias(true);
            mTextBatteryPaint.setStrokeCap(Paint.Cap.ROUND);
            mTextBatteryPaint.setTextSize(40);

            arcPaintBatteryFill = new Paint();
            arcPaintBatteryFill.setAntiAlias(true);
            arcPaintBatteryFill.setStyle(Paint.Style.FILL);

            arcPaintBatteryForeStroke = new Paint();
            arcPaintBatteryForeStroke.setAntiAlias(true);
            arcPaintBatteryForeStroke.setStrokeWidth(1);
            arcPaintBatteryForeStroke.setStyle(Paint.Style.STROKE);
            arcPaintBatteryForeStroke.setColor(Color.argb(255, 0, 0, 40));

            arcPaintBatteryBackStroke = new Paint();
            arcPaintBatteryBackStroke.setAntiAlias(true);
            arcPaintBatteryBackStroke.setStrokeWidth(1);
            arcPaintBatteryBackStroke.setStyle(Paint.Style.STROKE);
            arcPaintBatteryBackStroke.setColor(resources.getColor(R.color.analog_background));

            mBackgroundPaint.setColor(resources.getColor(R.color.analog_background));

            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);

            mTime = new Time();

            initGoogleApiClient();
        }

        @Override
        public void onDestroy() {
            Log.i(TAG, "onDestroy");
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
            closeGoogleApiClient();
        }

        private void initGoogleApiClient()
        {
            if (mGoogleApiClient == null)
            {
                mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                        .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "onConnected: " + bundle);

                                if (mGoogleApiClient != null)
                                    Wearable.MessageApi.addListener(mGoogleApiClient, messageListener);
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                Log.i(TAG, "onConnectionSuspended: " + i);

                            }
                        })
                        .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                            @Override
                            public void onConnectionFailed(ConnectionResult connectionResult) {
                                Log.i(TAG, "onConnectionFailed: " + connectionResult);

                            }
                        })
                        .addApi(Wearable.API)
                        .build();
            }
            if (!mGoogleApiClient.isConnected())
                mGoogleApiClient.connect();
        }

        private void closeGoogleApiClient()
        {
            if (mGoogleApiClient != null)
            {
                if (mGoogleApiClient.isConnected())
                    mGoogleApiClient.disconnect();

                mGoogleApiClient = null;
                mBatteryPercent = 0.0;
                invalidate();
            }
        }

        MessageApi.MessageListener messageListener = new MessageApi.MessageListener() {
            @Override
            public void onMessageReceived(MessageEvent messageEvent) {
               // Log.i(TAG, "onMessageReceived path: " + messageEvent.getPath());

                if (messageEvent.getPath().equals("/solowheelxtreme")) {
                    String msg = new String(messageEvent.getData());

                    String[] parts = msg.split(",");
                    if (parts != null && parts.length == 3) {
                        Log.i(TAG, "onMessageReceived: charge:" + parts[0] + " speed:" + parts[1]);

                        mBatteryPercent = Double.parseDouble(parts[0]);
                        mFormattedSpeed = parts[1] + " " + parts[2];
                    }
                   // Log.i(TAG, "onMessageReceived: " + msg);
                }
            }
        };

        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            Log.i(TAG, "onAmbient: " + inAmbientMode);

            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;

                if (mLowBitAmbient) {
                    mMinuteHandPaint.setAntiAlias(!inAmbientMode);
                    mHourHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            int width = bounds.width();
            int height = bounds.height();

            // Draw the background.
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);

            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            float centerX = width / 2f;
            float centerY = height / 2f;

            if (mBatteryPercent > 0) {
                // battery text
                int percent = mBatteryPercent.intValue();
                String percentText = String.format("%d", percent) + "%";
                //canvas.drawText(String.format("%d", percent) + "%", centerX, centerY + (height / 8), mTextPaint);

                android.graphics.Rect TextBounds = new android.graphics.Rect();
                mTextBatteryPaint.getTextBounds(percentText, 0, percentText.length(), TextBounds);
                canvas.drawText
                        (
                                percentText,
                                centerX - (TextBounds.right + TextBounds.left) / 2.0f,
                                centerY - (TextBounds.bottom + TextBounds.top) / 2.0f + 40,
                                mTextBatteryPaint
                        );

                // speed
                mTextSpeedPaint.getTextBounds(mFormattedSpeed, 0, mFormattedSpeed.length(), TextBounds);
                canvas.drawText
                        (
                                mFormattedSpeed,
                                centerX - (TextBounds.right + TextBounds.left) / 2.0f,
                                centerY - (TextBounds.bottom + TextBounds.top) / 2.0f - 40,
                                mTextSpeedPaint
                        );

                // maintain fixed aspect ratio
                float gaugeWidth = width;
                float gaugeHeight = height;
                if (gaugeHeight > gaugeWidth)
                    gaugeHeight = gaugeWidth;
                else if (gaugeWidth > gaugeHeight)
                    gaugeWidth = gaugeHeight;

                int gaugeWidth2 = (int) gaugeWidth / 2;
                RectF gaugeRect = new RectF(
                        centerX - gaugeWidth2, centerY - gaugeWidth2,
                        centerX + gaugeWidth2, centerY + gaugeWidth2);

                float pad = dpToPixels(20);
                RectF arcRect = new RectF(gaugeRect.left + pad, gaugeRect.top + pad, gaugeRect.right - pad, gaugeRect.bottom - pad);

                LedSegmentData ledSegmentData = new LedSegmentData(canvas, (int) centerX, (int) centerY, arcRect).invoke();
                float segmentAngle = ledSegmentData.getSegmentArcSpan();
                Path ptsSegments = ledSegmentData.getPtsSegments();
                //float innerRadius = ledSegmentData.getInnerRadius();

                DrawLedSegments(canvas, (int) centerX, (int) centerY, ptsSegments, segmentAngle);
            }
            else if (!isInAmbientMode())
            {
                /* Scale loaded background image (more efficient) if surface dimensions change. */
                float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();

                mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                        (int) (mBackgroundBitmap.getWidth() * scale),
                        (int) (mBackgroundBitmap.getHeight() * scale), true);

                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            }

                        /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            float innerTickRadius = centerX - 10;
            float outerTickRadius = centerX;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(centerX + innerX, centerY + innerY,
                        centerX + outerX, centerY + outerY, mMinuteHandPaint);
            }

            // hands
            float secRot = mTime.second / 30f * (float) Math.PI;
            int minutes = mTime.minute;
            float minRot = minutes / 30f * (float) Math.PI;
            float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

            float secLength = centerX - 20;
            float minLength = centerX - 40;
            float hrLength = centerX - 80;

/*            if (!mAmbient) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mHandPaint);
            }*/

            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mMinuteHandPaint);

            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHourHandPaint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();


            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            Log.i(TAG, "register");

            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            XtremeWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);

            initGoogleApiClient();
        }

        private void unregisterReceiver() {
            Log.i(TAG, "unRegister");

            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            XtremeWatchFace.this.unregisterReceiver(mTimeZoneReceiver);

            closeGoogleApiClient();
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible();
            //return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                if (isInAmbientMode())
                    delayMs = 10 * INTERACTIVE_UPDATE_RATE_MS;
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        private void DrawLedSegments(Canvas canvas, int centerX, int centerY,
                                     Path ptsSegments, float segmentAngle) {

            canvas.save();

            final int numSegments = 100;
            int red, green, blue;
            int alpha;
            float scale = (255.0f/numSegments);

           // Log.d(TAG, "");

            for(int i=numSegments; i >= 0; i--)
            {

                alpha = 0xFF;

                red = 255 - (int)(i * scale);
                green = (int)(i * scale);
                blue = 0;

                if(i > mBatteryPercent)
                {
                    red = 40;
                    green = 40;
                    blue = 40;
                }

                Log.d(TAG, String.format("i=%d rga=[%2x][%2x]%2x", i, red, green, alpha));

                arcPaintBatteryFill.setColor(Color.argb(alpha, red, green, blue));
                canvas.drawPath(ptsSegments, arcPaintBatteryFill);

                canvas.rotate(-segmentAngle, centerX, centerY);
            }
            canvas.restore();
        }

        public int Lighten(int red, int green, int blue, double inAmount)
        {
            return Color.argb(

                    255,
                    (int) Math.min(255, red + 255 * inAmount),
                    (int) Math.min(255, green + 255 * inAmount),
                    (int) Math.min(255, blue + 255 * inAmount) );
        }
    }



    private static class EngineHandler extends Handler {
        private final WeakReference<XtremeWatchFace.Engine> mWeakReference;

        public EngineHandler(XtremeWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            XtremeWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private float dpToPixels(float dp) {

        return getResources().getDisplayMetrics().density * dp;
//
//		return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
//				dp, getResources().getDisplayMetrics());
    }

    private class LedSegmentData {
        private Canvas canvas;
        private int centerY;
        private int centerX;
        private RectF arcRect;
        private float innerRadius;
        private Path ptsSegments;
        private float segmentArcSpan;

        public LedSegmentData(Canvas canvas, int centerX, int centerY, RectF arcRect) {
            this.canvas = canvas;
            this.centerY = centerY;
            this.centerX = centerX;
            this.arcRect = arcRect;
        }

        public float getInnerRadius() {
            return innerRadius;
        }

        public Path getPtsSegments() {
            return ptsSegments;
        }

        public float getSegmentArcSpan() {
            return segmentArcSpan;
        }

        public LedSegmentData invoke() {
            ptsSegments = new Path();
            float arcSpan = 270.f;
            float segmentWidth = dpToPixels(20);
            float gap = dpToPixels(10);

            RectF arcRectChargeOuter = new RectF(arcRect.left + gap, arcRect.top + gap, arcRect.right - gap, arcRect.bottom - gap);

            RectF arcRectChargeInner = new RectF(arcRect.left + gap + segmentWidth, arcRect.top + gap + segmentWidth, arcRect.right - gap - segmentWidth, arcRect.bottom - gap - segmentWidth);

            float outerRadius = arcRectChargeOuter.right - centerX;
            innerRadius = arcRectChargeInner.right - centerX;

            //float segmentArcSpanFull = arcSpan / 100.0f;
            //segmentArcSpan = (arcSpan - (segmentArcSpanFull / 2)) / 100;
            segmentArcSpan = arcSpan / 100.0f;

            float segmentArcRad2 = (float)Math.toRadians(segmentArcSpan / 2);

            int innerOffsetX = (int)(Math.cos(segmentArcRad2) * innerRadius);
            int innerOffsetY = (int)(Math.sin(segmentArcRad2) * innerRadius);

            int outerOffsetX = (int)(Math.cos(segmentArcRad2) * outerRadius);
            int outerOffsetY = (int)(Math.sin(segmentArcRad2) * outerRadius);

            int X1 = centerX + innerOffsetX;
            int Y1 = centerY + innerOffsetY;
//            int X2 = centerX + innerOffsetX;
//            int Y2 = centerY - innerOffsetY;
            int X3 = centerX + outerOffsetX;
            int Y3 = centerY - outerOffsetY;
//            int X4 = centerX + outerOffsetX;
//            int Y4 = centerY + outerOffsetY;

            ptsSegments.moveTo(X1, Y1);
            ptsSegments.arcTo(arcRectChargeInner, (segmentArcSpan / 2), -segmentArcSpan);
            ptsSegments.lineTo(X3, Y3);
            ptsSegments.arcTo(arcRectChargeOuter, -(segmentArcSpan / 2), segmentArcSpan);
            ptsSegments.lineTo(X1, Y1);

            ptsSegments.close();
            return this;
        }
    }
}
