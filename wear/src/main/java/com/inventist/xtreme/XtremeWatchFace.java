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

package com.inventist.xtreme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
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
        Paint mHandPaint;
        Paint mTextPaint;
        boolean mAmbient;
        Time mTime;

        Double mSpeed = 0.0;
        Double mBatteryPercent = 0.0;

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        private Paint arcPaintBatteryFill;
        private Paint arcPaintBatteryStroke;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            Log.i("XTREME", "onCreate");

            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(XtremeWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = XtremeWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.analog_background));

            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mTextPaint = new Paint();
            mTextPaint.setColor(resources.getColor(R.color.analog_hands));
            mTextPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mTextPaint.setAntiAlias(true);
            mTextPaint.setStrokeCap(Paint.Cap.ROUND);
            mTextPaint.setTextSize(30);

            arcPaintBatteryFill = new Paint();
            arcPaintBatteryFill.setAntiAlias(true);
            arcPaintBatteryFill.setStyle(Paint.Style.FILL);

            arcPaintBatteryStroke = new Paint();
            arcPaintBatteryStroke.setAntiAlias(true);
            arcPaintBatteryStroke.setStrokeWidth(1);
            arcPaintBatteryStroke.setStyle(Paint.Style.STROKE);
            mBackgroundPaint.setColor(resources.getColor(R.color.analog_background));

            mTime = new Time();

            initGoogleApiClient();
        }

        @Override
        public void onDestroy() {
            Log.i("XTREME", "onDestroy");
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
                                Log.i("XTREME", "onConnected: " + bundle);

                                Wearable.MessageApi.addListener(mGoogleApiClient, messageListener);
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                Log.i("XTREME", "onConnectionSuspended: " + i);

                            }
                        })
                        .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                            @Override
                            public void onConnectionFailed(ConnectionResult connectionResult) {
                                Log.i("XTREME", "onConnectionFailed: " + connectionResult);

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
            }
        }

        MessageApi.MessageListener messageListener = new MessageApi.MessageListener() {
            @Override
            public void onMessageReceived(MessageEvent messageEvent) {
                String msg = new String(messageEvent.getData());

                String[] parts = msg.split(",");
                if (parts != null && parts.length == 2)
                {
                    Log.i("XTREME", "onMessageReceived: charge:" + parts[0] + " speed:" + parts[1]);
                    mBatteryPercent = Double.parseDouble(parts[0]);
                    mSpeed = Double.parseDouble(parts[1]);
                }
                Log.i("XTREME", "onMessageReceived: " + msg);
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
            Log.i("XTREME", "onAmbient");

            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
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

            // battery text
            int percent = mBatteryPercent.intValue();
            String percentText = String.format("%d", percent) + "%";
            //canvas.drawText(String.format("%d", percent) + "%", centerX, centerY + (height / 8), mTextPaint);

            android.graphics.Rect TextBounds = new android.graphics.Rect();
            mTextPaint.getTextBounds(percentText, 0, percentText.length(), TextBounds);
            canvas.drawText
                    (
                            percentText,
                            centerX - (TextBounds.right + TextBounds.left) / 2.0f,
                            centerY - (TextBounds.bottom + TextBounds.top) / 2.0f + 40,
                            mTextPaint
                    );

            // only draw the gauge if non-ambient mode
            if (!mAmbient) {

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

                LedSegmentData ledSegmentData = new LedSegmentData(canvas, (int)centerX, (int)centerY, arcRect).invoke();
                float segmentAngle = ledSegmentData.getSegmentArcSpan();
                Path ptsSegments = ledSegmentData.getPtsSegments();
                //float innerRadius = ledSegmentData.getInnerRadius();

                DrawLedSegments(canvas, (int)centerX, (int)centerY, ptsSegments, segmentAngle);
            }

            // hands
            float secRot = mTime.second / 30f * (float) Math.PI;
            int minutes = mTime.minute;
            float minRot = minutes / 30f * (float) Math.PI;
            float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

            float secLength = centerX - 20;
            float minLength = centerX - 40;
            float hrLength = centerX - 80;

            if (!mAmbient) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mHandPaint);
            }

            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mHandPaint);

            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHandPaint);
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
            Log.i("XTREME", "register");

            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            XtremeWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);

//            initGoogleApiClient();
        }

        private void unregisterReceiver() {
            Log.i("XTREME", "unRegister");

            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            XtremeWatchFace.this.unregisterReceiver(mTimeZoneReceiver);

//            closeGoogleApiClient();
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
            return isVisible() && !isInAmbientMode();
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
//            canvas.rotate(-segmentAngle, centerX, centerY);

                if(i > mBatteryPercent)
                    alpha = 0x10;
                else
                    alpha = 0xFF;

                red = 255 - (int)(i * scale);
                green = (int)(i * scale);
                blue = 0;

                Log.d(TAG, String.format("i=%d rga=[%2x][%2x]%2x", i, red, green, alpha));

                arcPaintBatteryFill.setColor(Color.argb(alpha, red, green, blue));
                canvas.drawPath(ptsSegments, arcPaintBatteryFill);

                canvas.drawPath(ptsSegments, arcPaintBatteryStroke);

                canvas.rotate(-segmentAngle, centerX, centerY);
            }
            canvas.restore();
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
