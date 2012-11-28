package com.inteleme.arabicwheel;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.ImageView;

import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.ViewById;

@EActivity(R.layout.activity_main)
public class MainActivity extends Activity {

	@ViewById
	ImageView wheel;
	@ViewById
	View container;
	
	private static Bitmap imageOriginal, imageScaled;
	private static Matrix finalMatrix = new Matrix();
	private int wheelHeight, wheelWidth;
	private ScaleGestureDetector detector;
	private boolean allowRotating;
	private static float currentScale = 1f, maxScale = 2.5f;
	private static double currentDegrees = 360.0 / 26 / 2;
	private int wheelSize = 100, scaledSize = 100;
	private static double degreesPerSlice = 360.0 / 26;
	
	/** Wheel can zoom to the size of maximum 2.5 times the screen height */ 
	private static final float MAX_ZOOM = 2.5f;
	private static final String TAG = "Main";
	
	@AfterViews
	protected void setup() {
	    if (imageOriginal == null)
	        imageOriginal = BitmapFactory.decodeResource(getResources(), R.drawable.wheel1);
	    
	    detector = new ScaleGestureDetector(this, new MyScaleGestureDetector());
	    wheel.setOnTouchListener(new MyOnTouchListener());
	}
    
    @Override
    public void onResume() {
    	super.onResume();
		initImage();
    }

    /** Initialize the wheel image as soon as the interface has loaded enough to know all measurements.
     * - Determine wheelsize and maxScale
     * - Make a version of the image that fits the screen at max zoom.
     */
    private void initImage() {
	    wheel.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
            	if (wheelHeight == 0) { 
	                wheelHeight = wheel.getHeight();
	                wheelWidth = wheel.getWidth();
	            	wheelSize = Math.min(wheelWidth, wheelHeight);
	            	maxScale = MAX_ZOOM * wheelHeight / wheelSize;
	
	                // resize the image to 
	                Matrix resize = new Matrix();
	                float scale = maxScale * wheelSize / imageOriginal.getWidth();
	                resize.postScale(scale, scale);
	                imageScaled = Bitmap.createBitmap(imageOriginal, 0, 0, imageOriginal.getWidth(), imageOriginal.getHeight(), resize, false);
	                wheel.setImageBitmap(imageScaled);
	                scaleWheel(1f);
	//                    wheel.post(new FlingRunnable(System.currentTimeMillis(), 500f));
            	}
            }
	    });
    }

    /** Detects touch events, handles rotating wheel and forwards to MyScaleGestureDetector 
     * - touch down: stop ongoing movement, save initial angle.
     * - touch move: rotate the wheel
     * - touch up  : Give the wheel the current velocity and let it move.  
     */
    private class MyOnTouchListener implements OnTouchListener {
        private double previousAngle=0.0, currentAngle=0.0;
        private long previousEventTime=0, currentEventTime=0;
        private int lastPointerCount = 0;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
        	detector.onTouchEvent(event);
        	if (event.getAction() == MotionEvent.ACTION_MOVE && event.getPointerCount() != lastPointerCount) {
        		event.setAction(MotionEvent.ACTION_DOWN);
        		lastPointerCount = event.getPointerCount();
        	}

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                	previousEventTime = 0;
                	currentAngle = getAngle(event);
                    allowRotating = false; // stop wheel from spinning.
                    break;
                case MotionEvent.ACTION_MOVE:
                	previousEventTime = currentEventTime;
                	currentEventTime = event.getEventTime();
                	previousAngle = currentAngle;
                    currentAngle = getAngle(event);

                    rotateWheel(currentAngle - previousAngle);
                    break;
                case MotionEvent.ACTION_UP:
                	if (previousEventTime != 0)
                		wheel.post(new FlingRunnable(event.getEventTime(), 1000.0 * (currentAngle - previousAngle) / (currentEventTime - previousEventTime)));
                	allowRotating = true;
                    break;
            }
            return true;
        }
    }
 
    /** Detects pinching and scales the image accordingly */
    private class MyScaleGestureDetector extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
        	return true;
        }
        
    	@Override
        public boolean onScale (ScaleGestureDetector detector) {
    		scaleWheel(detector.getScaleFactor());
        	return true;
        }
    }
    
    /**
     * A {@link Runnable} for animating the the wheels movement when let go.
     * - Every 14ms rotate wheel and decrease velocity.
     * - When velocity is low, move to center of segment.
     */
    private class FlingRunnable implements Runnable {
        private double velocity;
        private long lastRunTime; 
        public FlingRunnable(long lastEventTime, double velocity) {
        	this.lastRunTime = lastEventTime;
            this.velocity = (float) velocity;
            Log.d(TAG, "inital velocity: " + velocity);
//            if (Math.abs(velocity) < 90)
//            	this.velocity = 0f;
        }
        @Override
        public void run() {
        	if ((Math.abs(sliceCenterSpeed()) > 0.5) && allowRotating) {
            	long currentTime = SystemClock.uptimeMillis();
            	double deltaS = (double)(currentTime - lastRunTime) / 1000.0;
                rotateWheel(velocity * deltaS);
                // decrease velocity based on time delta and some constant
                velocity *= (1 - (Math.min(5.0 * deltaS, 1)));
                // slide to center when velocity is low.
                if (velocity < 25) 
                	velocity = sliceCenterSpeed();
                lastRunTime = currentTime;
                wheel.postDelayed(this, 14);
            } else {
            	Log.d(TAG, "current degrees: " + currentDegrees + " - " + Math.abs(sliceCenterSpeed()) + " - " + allowRotating);
            }
        }
    }
    
    /** velocity to move to slice-center.
     * Currently just a constant factor of the angle still to go.
     */
    private double sliceCenterSpeed() {
    	return (targetDegrees() - currentDegrees) * 5;
    }
    
    /** Returns Angle of closest slice-center to current angle.
     */
    private double targetDegrees() {
    	if (currentDegrees % degreesPerSlice < degreesPerSlice/2.0)
    		return currentDegrees - (currentDegrees % degreesPerSlice) + degreesPerSlice/2.0;
    	else
    		return currentDegrees + (degreesPerSlice - currentDegrees % degreesPerSlice) - degreesPerSlice/2.0;
    }
    
    /**
     * @return The angle of the unit circle with the image view's center
     */
    private double getAngle(MotionEvent event) {
    	double x,y;
    	if (event.getPointerCount() > 2) {
    		return 0.0;
    	} else if (event.getPointerCount() == 2) {
    		x = event.getX(1) - event.getX(0);
    		y = event.getY(0) - event.getY(1);
    	} else {
    		x = event.getX() - (wheelWidth / 2d);
    		y = currentCenterY() - event.getY();
    	}
        return Math.toDegrees(Math.atan2(x, y));
    }
    
    private int currentCenterY() {
    	return (scaledSize < wheelHeight) ?
    	  wheelHeight/2 :
    	  scaledSize/2;
    }
    
    /**
     * Rotate the wheel.
     *
     * @param degrees Delta angle to current angle.
     */
    private void rotateWheel(double degrees) {
    	currentDegrees += degrees;

    	// keep angle within bounds.
    	if (currentDegrees < 0.0)
    		currentDegrees += 360.0;
    	if (currentDegrees >= 360.0)
    		currentDegrees -= 360.0;

    	showWheel();
    }
    
    /** Scale the wheel. size *= factor */
    private void scaleWheel(float factor) {
		currentScale *= factor;

		// keep scale within bounds
    	if (currentScale > maxScale)
    		currentScale = maxScale;
    	if (currentScale < 1f)
    		currentScale = 1f;
    	// save the scaled size
    	scaledSize = (int) (wheelSize * currentScale);

        showWheel();
    }
    
    /** First scales the image, then rotates then translates it into place.
     * wheel.center_horizontal == screen.center always
     * wheel.center_vertical == screen.center if wheel.diameter <= screen.height.
     * wheel.top == screen.top if wheel.diameter > screen.height.
     */
    private void showWheel() {
    	finalMatrix.reset();

    	// undo max quality scale.
    	finalMatrix.setScale(1f/maxScale, 1f/maxScale);
    	// scale the image
    	finalMatrix.postScale(currentScale, currentScale, 0, 0);
    	// rotate the image
    	finalMatrix.postRotate((float)currentDegrees, scaledSize / 2, scaledSize / 2);
    	// move image to correct place.
        float translateX = (wheelWidth  - scaledSize) / 2;
        float translateY = (wheelHeight - scaledSize) / 2;
        if (translateY < 0) translateY = 0;
        finalMatrix.postTranslate(translateX, translateY);
    	
        wheel.setImageMatrix(finalMatrix);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
}
