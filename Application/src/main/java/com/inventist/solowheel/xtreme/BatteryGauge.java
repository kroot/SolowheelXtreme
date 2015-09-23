/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class BatteryGauge extends View {
	private static final String TAG = "solowheel";

	private Paint titlePaint;
	private float titleFontSize = 10.0f;
	private int titleColor = Color.BLACK;

	private Paint valuePaint;
	private float valueFontSize = 40f;
	private Integer valueDigits = 2;
	private int valueColor = Color.BLACK;

    private Paint arcPaintBatteryFill;
    private Paint arcPaintBatteryStroke;

	private Paint needlePaint;

	private int fullValue = 0;

	private String title = "Battery Level";
	private RectF titleRect;

	private SharedPreferences prefs;
	private OnSharedPreferenceChangeListener listener;

	public BatteryGauge(Context context) {
		super(context);
		init(context, null);
	}

	public BatteryGauge(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	public BatteryGauge(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, attrs);
	}

	private void init(Context context, AttributeSet attrs) {
		// Get the properties from the resource file.
		if (context != null && attrs != null){
			TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.BatteryGauge, 0, 0);

			try {
				// fetch defaults from attr.xml
				valueDigits = a.getInteger(R.styleable.BatteryGauge_valueDigits_charge, 0);
				valueFontSize = a.getInteger(R.styleable.BatteryGauge_valueFontSize_charge, 40);
				fullValue = a.getInteger(R.styleable.BatteryGauge_value_charge_percent, 0);
				valueColor = a.getInteger(R.styleable.BatteryGauge_valueColor_charge, 0xffffff);

				titleFontSize = a.getInteger(R.styleable.BatteryGauge_titleFontSize_charge, 18);
				titleColor = a.getInteger(R.styleable.BatteryGauge_titleColor_charge, 0xffffff);

				if (a.getString(R.styleable.BatteryGauge_title_charge) != null)
					title = a.getString(R.styleable.BatteryGauge_title_charge);

			} finally {
				a.recycle();
			}
		}
		initDrawingTools();

		prefs = PreferenceManager.getDefaultSharedPreferences(context);

		// note that if prefs isn't in a field, it can be GC'd
		listener = new SharedPreferences.OnSharedPreferenceChangeListener() {

			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
					String key) {

				if ("charge_level_percent" == key) {
					Integer charge_level = prefs.getInt("charge_level_percent", 0);
					setFullPercent(charge_level);
				}
			}
		};
		prefs.registerOnSharedPreferenceChangeListener(listener);
	}


	private void initDrawingTools() {
		RectF controlRect = new RectF(0.0f, 0.0f, getWidth(), getHeight());
		float padding = 5.0f;
		float titlePosition = .3f;

		RectF valueRect = new RectF();
		valueRect.set(controlRect.left  + padding, controlRect.top + padding,
				controlRect.right - padding, controlRect.bottom - padding);

		titleRect = new RectF();
		titleRect.set(controlRect.left + titlePosition, controlRect.top + titlePosition,
				controlRect.right - titlePosition, controlRect.bottom - titlePosition);

		titlePaint = new Paint();
		titlePaint.setColor(titleColor);
		titlePaint.setAntiAlias(true);
		titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
		titlePaint.setTextAlign(Paint.Align.CENTER);
		titlePaint.setTextSize(dpToPixels(titleFontSize));

		valuePaint = new Paint();
		valuePaint.setColor(valueColor);
		valuePaint.setAntiAlias(true);
		valuePaint.setTypeface(Typeface.DEFAULT_BOLD);
		valuePaint.setTextAlign(Paint.Align.CENTER);
		valuePaint.setTextSize(dpToPixels(valueFontSize));

        arcPaintBatteryFill = new Paint();
        arcPaintBatteryFill.setAntiAlias(true);
        arcPaintBatteryFill.setStyle(Paint.Style.FILL);

        arcPaintBatteryStroke = new Paint();
        arcPaintBatteryStroke.setAntiAlias(true);
        arcPaintBatteryStroke.setStrokeWidth(1);
        arcPaintBatteryStroke.setStyle(Style.STROKE);
        arcPaintBatteryStroke.setColor(Color.BLACK);

		needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		needlePaint.setColor(Color.BLACK);
	}


	private float dpToPixels(float dp) {

		return getResources().getDisplayMetrics().density * dp;
//
//		return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
//				dp, getResources().getDisplayMetrics());
	}

	@SuppressLint("DrawAllocation")
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		RectF controlRect = new RectF(0.0f, 0.0f, getWidth(), getHeight());

		// vertically center the gauge in the control
		int centerY = (int) ((controlRect.bottom - controlRect.top) / 2);

		float rightWidth = 0; // for the -100 text

		float gaugeWidth = controlRect.width() - rightWidth;
		float gaugeHeight = gaugeWidth;

        // maintain aspect
        if (gaugeHeight > gaugeWidth)
            gaugeHeight = gaugeWidth;
        else if (gaugeWidth > gaugeHeight)
            gaugeWidth = gaugeHeight;

		int centerX = (int) (gaugeWidth / 2);

		int gaugeWidth2 = (int) gaugeWidth / 2;
		RectF gaugeRect = new RectF(
				centerX - gaugeWidth2, centerY - gaugeWidth2,
				centerX + gaugeWidth2, centerY + gaugeWidth2);

		String textVal = fullValue + "%";

		// get the value height
		final android.graphics.Rect TextBounds = new android.graphics.Rect();
		valuePaint.getTextBounds(textVal, 0, textVal.length(), TextBounds);

		DrawCenteredText(canvas, textVal, centerX, centerY, valuePaint);

        float pad = dpToPixels(1);

		RectF arcRect = new RectF(gaugeRect.left + pad, gaugeRect.top + pad, gaugeRect.right - pad, gaugeRect.bottom - pad);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap batteryImage = BitmapFactory.decodeResource(getResources(),
                R.drawable.battery, options);

        float battImageScale = (gaugeWidth / 6) / gaugeWidth;
        float battWidth = gaugeWidth * battImageScale;
        float battHeight = gaugeHeight * battImageScale;
        float battLeft = centerX - (battWidth / 2);
        float battTop = centerY + (gaugeHeight / 7) - (battHeight / 2);

        Rect src = new Rect(0,0,batteryImage.getWidth()-1, batteryImage.getHeight()-1);
        Rect dest = new Rect((int)battLeft, (int)battTop, (int)(battLeft + battWidth),
                (int)(battTop + battHeight));
        canvas.drawBitmap(batteryImage, src, dest, valuePaint);

        LedSegmentData ledSegmentData = new LedSegmentData(canvas, centerX, centerY, arcRect).invoke();
        float segmentAngle = ledSegmentData.getSegmentArcSpan();
        Path ptsSegments = ledSegmentData.getPtsSegments();
        float innerRadius = ledSegmentData.getInnerRadius();

        DrawLedSegments(canvas, centerX, centerY, ptsSegments, segmentAngle);
        DrawTickMark(canvas, centerY, centerX, segmentAngle, innerRadius);
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
            float segmentWidth = dpToPixels(40);
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

    private void DrawLedSegments(Canvas canvas, int centerX, int centerY,
                                 Path ptsSegments, float segmentAngle) {

        canvas.save();

        final int numSegments = 100;
        int red, green, blue;
        int alpha;
        float scale = (255.0f/numSegments);

        Log.d(TAG, "");

        for(int i=numSegments; i >= 0; i--)
        {
//            canvas.rotate(-segmentAngle, centerX, centerY);

            if(i > fullValue)
                alpha = 0x10;
            else
                alpha = 0xFF;

            red = 255 - (int)(i * scale);
            green = (int)(i * scale);
            blue = 0;

//            Log.d(TAG, String.format("i=%d rga=[%2x][%2x]%2x", i, red, green, alpha));

            arcPaintBatteryFill.setColor(Color.argb(alpha, red, green, blue));
            canvas.drawPath(ptsSegments, arcPaintBatteryFill);

            canvas.drawPath(ptsSegments, arcPaintBatteryStroke);

			canvas.rotate(-segmentAngle, centerX, centerY);
		}
        canvas.restore();
    }

    private void DrawTickMark(Canvas canvas, int centerY, int centerX, float segmentAngle, float innerRadius) {
        // put a carat at the full level so it makes sense visually

        // get a point on the arc based on an angle
        Integer triangleHeight = (int)dpToPixels(20);
        Integer triangleHeight2 = (int)(triangleHeight / 2);

//		float arrowAng = 360 - ((segmentAngle * fullValue) + (segmentAngle/2));
		float arrowAng = 360 - (segmentAngle * fullValue);

        // draw triangle marker
        Path pts = new Path(); // draw a triangle
        pts.moveTo(centerX - triangleHeight2, centerY - triangleHeight);
        pts.lineTo(centerX + triangleHeight2, centerY - triangleHeight);
        pts.lineTo(centerX, centerY);
        pts.lineTo(centerX - triangleHeight2, centerY - triangleHeight);

        canvas.save(Canvas.MATRIX_SAVE_FLAG);

        canvas.translate(0, innerRadius);
        canvas.rotate(-arrowAng, centerX, centerY - innerRadius);

        canvas.drawPath(pts, needlePaint);
        canvas.restore();
    }

    public static void DrawCenteredText
	(
       android.graphics.Canvas Draw,
       String TheText,
       float x,
       float y,
       android.graphics.Paint UsePaint
     )
     /* draws text at position x, vertically centered around y. */
     {
       final android.graphics.Rect TextBounds = new android.graphics.Rect();
       UsePaint.getTextBounds(TheText, 0, TheText.length(), TextBounds);
       Draw.drawText
         (
           TheText,
           x, /* depend on UsePaint to align horizontally */
           y - (TextBounds.bottom + TextBounds.top) / 2.0f,
           UsePaint
         );
     }

	public void setFullPercent(int value) {

		if (value < 0)
			value = 0;
		else if (value > 100)
			value = 100;
		
		fullValue = value;
		
		invalidate(); // forces onDraw() to be called.
		requestLayout();
	}

	public int getFullPercent() {
		return fullValue;
	}
	
	public void setTitle(String value) {
		title = value;
		
		invalidate(); // forces onDraw() to be called.
		requestLayout();
	}

	public String getTitle() {
		return title;
	}

	public void setTitleFontSize(float dpValue) {
		titleFontSize = dpValue;
		
		titlePaint.setTextSize(dpToPixels(dpValue));
		
		invalidate(); // forces onDraw() to be called.
		requestLayout();
	}

	public float getTitleFontSize() {
		return titleFontSize;
	}

	public void setValueFontSize(float dpValue) {
		valueFontSize = dpValue;
		
		valuePaint.setTextSize(dpToPixels(dpValue));
		
		invalidate(); // forces onDraw() to be called.
		requestLayout();
	}

	public float getValueFontSize() {
		return valueFontSize;
	}
		
	public void setValueDigits(Integer numDigits) {
		valueDigits = numDigits;
				
		invalidate(); // forces onDraw() to be called.
		requestLayout();
	}

	public float getValueDigits() {
		return valueDigits;
	}
}
