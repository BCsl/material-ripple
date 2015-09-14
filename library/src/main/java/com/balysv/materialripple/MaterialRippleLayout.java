/*
 * Copyright (C) 2014 Balys Valentukevicius
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

package com.balysv.materialripple;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Property;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.FrameLayout;

import static android.view.GestureDetector.SimpleOnGestureListener;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class MaterialRippleLayout extends FrameLayout {

    private static final int DEFAULT_DURATION = 350;
    private static final int DEFAULT_FADE_DURATION = 75;
    private static final float DEFAULT_DIAMETER_DP = 35;
    private static final float DEFAULT_ALPHA = 0.2f;
    private static final int DEFAULT_COLOR = Color.BLACK;
    private static final int DEFAULT_BACKGROUND = Color.TRANSPARENT;
    private static final boolean DEFAULT_HOVER = true;
    private static final boolean DEFAULT_DELAY_CLICK = true;
    private static final boolean DEFAULT_PERSISTENT = false;
    private static final boolean DEFAULT_SEARCH_ADAPTER = false;
    private static final boolean DEFAULT_RIPPLE_OVERLAY = false;
    private static final int DEFAULT_ROUNDED_CORNERS = 0;

    private static final int FADE_EXTRA_DELAY = 50;
    private static final long HOVER_DURATION = 2500;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bounds = new Rect();

    private int rippleColor;
    private boolean rippleOverlay;
    private boolean rippleHover;
    private int rippleDiameter;
    private int rippleDuration;
    private int rippleAlpha;
    private boolean rippleDelayClick;
    private int rippleFadeDuration;//alpha animation duration,rippleAlpha to 0
    private boolean ripplePersistent;
    private Drawable rippleBackground;
    private boolean rippleInAdapter;
    private float rippleRoundedCorners;

    private float radius;

    private AdapterView mAdapterView;
    private View mChildView;

    private AnimatorSet mRippleAnimator;
    private ObjectAnimator mObjectAnimator;

    private Point mCurrentCoords = new Point();
    private Point mPreviousCoords = new Point();

    private int mLayerType;

    private boolean mEventCancelled;
    private boolean mPrepressed;
    private int mPositionInAdapter;

    private GestureDetector mGestureDetector;
    private PerformClickEvent mPerformClickEvent;
    private PressedEvent mPendingPressEvent;

    public static RippleBuilder on(View view) {
        return new RippleBuilder(view);
    }

    public MaterialRippleLayout(Context context) {
        this(context, null, 0);
    }

    public MaterialRippleLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MaterialRippleLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setWillNotDraw(false);
        mGestureDetector = new GestureDetector(context, mLongClickListener);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MaterialRippleLayout);
        rippleColor = a.getColor(R.styleable.MaterialRippleLayout_mrl_rippleColor, DEFAULT_COLOR);
        rippleDiameter = a.getDimensionPixelSize(
                R.styleable.MaterialRippleLayout_mrl_rippleDimension,
                (int) dpToPx(getResources(), DEFAULT_DIAMETER_DP)
        );
        rippleOverlay = a.getBoolean(R.styleable.MaterialRippleLayout_mrl_rippleOverlay, DEFAULT_RIPPLE_OVERLAY);
        rippleHover = a.getBoolean(R.styleable.MaterialRippleLayout_mrl_rippleHover, DEFAULT_HOVER);
        rippleDuration = a.getInt(R.styleable.MaterialRippleLayout_mrl_rippleDuration, DEFAULT_DURATION);
        rippleAlpha = (int) (255 * a.getFloat(R.styleable.MaterialRippleLayout_mrl_rippleAlpha, DEFAULT_ALPHA));
        rippleDelayClick = a.getBoolean(R.styleable.MaterialRippleLayout_mrl_rippleDelayClick, DEFAULT_DELAY_CLICK);
        rippleFadeDuration = a.getInteger(R.styleable.MaterialRippleLayout_mrl_rippleFadeDuration, DEFAULT_FADE_DURATION);
        rippleBackground = new ColorDrawable(a.getColor(R.styleable.MaterialRippleLayout_mrl_rippleBackground, DEFAULT_BACKGROUND));
        ripplePersistent = a.getBoolean(R.styleable.MaterialRippleLayout_mrl_ripplePersistent, DEFAULT_PERSISTENT);
        rippleInAdapter = a.getBoolean(R.styleable.MaterialRippleLayout_mrl_rippleInAdapter, DEFAULT_SEARCH_ADAPTER);
        rippleRoundedCorners = a.getDimensionPixelSize(R.styleable.MaterialRippleLayout_mrl_rippleRoundedCorners, DEFAULT_ROUNDED_CORNERS);

        a.recycle();

        paint.setColor(rippleColor);
        paint.setAlpha(rippleAlpha);

        enableClipPathSupportIfNecessary();
    }


    @SuppressWarnings("unchecked")
    public <T extends View> T getChildView() {
        return (T) mChildView;
    }

    @Override
    public final void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("MaterialRippleLayout can host only one child");
        }
        //noinspection unchecked
        mChildView = child;
        super.addView(child, index, params);
    }

    @Override
    public void setOnClickListener(OnClickListener onClickListener) {
        if (mChildView == null) {
            throw new IllegalStateException("MaterialRippleLayout must have a child view to handle clicks");
        }
        mChildView.setOnClickListener(onClickListener);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener onClickListener) {
        if (mChildView == null) {
            throw new IllegalStateException("MaterialRippleLayout must have a child view to handle clicks");
        }
        mChildView.setOnLongClickListener(onClickListener);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return !findClickableViewInChild(mChildView, (int) event.getX(), (int) event.getY());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean superOnTouchEvent = super.onTouchEvent(event);

        if (!isEnabled() || !mChildView.isEnabled()) return superOnTouchEvent;

        boolean isEventInBounds = bounds.contains((int) event.getX(), (int) event.getY());

        if (isEventInBounds) {
            mPreviousCoords.set(mCurrentCoords.x, mCurrentCoords.y);
            mCurrentCoords.set((int) event.getX(), (int) event.getY());
        }

        boolean gestureResult = mGestureDetector.onTouchEvent(event);//chilview handle longclick if nessary
        if (gestureResult || hasPerformedLongPress) {
            return true;
        } else {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_UP:
                    mPerformClickEvent = new PerformClickEvent();

                    if (mPrepressed) {//posiable true when isInScrollingContainer return true
                        mChildView.setPressed(true);
                        postDelayed(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        mChildView.setPressed(false);
                                    }
                                }, ViewConfiguration.getPressedStateDuration());
                    }
                    //if the last ripper Animation is running,just ignore this click event
                    if (isEventInBounds && mRippleAnimator == null || !mRippleAnimator.isRunning()) {
                        startRipple(mPerformClickEvent);//ripple
                    } else if (!rippleHover) {
                        setRadius(0);
                    }
                    if (!rippleDelayClick && isEventInBounds) {
                        mPerformClickEvent.run();
                    }
                    cancelPressedEvent();
                    break;
                case MotionEvent.ACTION_DOWN:
                    if (mRippleAnimator != null && mRippleAnimator.isRunning() || isPerformingClickEvent()) {
                        return true;
                    }
                    setPositionInAdapter();
                    mEventCancelled = false;
                    mPendingPressEvent = new PressedEvent(event);
                    if (isInScrollingContainer()) {
                        cancelPressedEvent();
                        mPrepressed = true;
                        postDelayed(mPendingPressEvent, ViewConfiguration.getTapTimeout());
                    } else {
                        mPendingPressEvent.run();
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    if (rippleInAdapter) {
                        // dont use current coords in adapter since they tend to jump drastically on scroll
                        mCurrentCoords.set(mPreviousCoords.x, mPreviousCoords.y);
                        mPreviousCoords = new Point();
                    }
                    mChildView.onTouchEvent(event);
                    if (rippleHover) {
                        if (!mPrepressed) {
                            startRipple(null);
                        }
                    } else {
                        mChildView.setPressed(false);
                    }
                    cancelPressedEvent();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (rippleHover) {
                        if (isEventInBounds && !mEventCancelled) {
                            invalidate();
                        } else if (!isEventInBounds) {
                            startRipple(null);
                        }
                    }

                    if (!isEventInBounds) {
                        cancelPressedEvent();
                        if (mObjectAnimator != null) {
                            mObjectAnimator.cancel();
                        }
                        mChildView.onTouchEvent(event);
                        mEventCancelled = true;
                    }
                    break;
            }
            return true;
        }
    }

    private void cancelPressedEvent() {
        if (mPendingPressEvent != null) {
            removeCallbacks(mPendingPressEvent);
            mPrepressed = false;
        }
    }

    private boolean hasPerformedLongPress;

    //handle childview longclick event
    private SimpleOnGestureListener mLongClickListener = new GestureDetector.SimpleOnGestureListener() {
        public void onLongPress(MotionEvent e) {
            hasPerformedLongPress = mChildView.performLongClick();//Call this view's OnLongClickListener
            if (hasPerformedLongPress) {
                //childview consume the longclick event
                if (rippleHover) {
                    startRipple(null);//show ripper with out perfrom click event
                }
                cancelPressedEvent();
            }
        }

        @Override
        public boolean onDown(MotionEvent e) {
            hasPerformedLongPress = false;
            return super.onDown(e);
        }
    };

    /**
     * show ripper when under child comsume longPress or perform on pressed if need
     */
    private void startHover() {
        if (mEventCancelled) return;

        if (mObjectAnimator != null) {
            mObjectAnimator.cancel();
        }
        final float radius = (float) (Math.sqrt(Math.pow(getWidth(), 2) + Math.pow(getHeight(), 2)) * 1.2f);
        mObjectAnimator = ObjectAnimator.ofFloat(this, radiusProperty, rippleDiameter, radius)
                .setDuration(HOVER_DURATION);
        mObjectAnimator.setInterpolator(new LinearInterpolator());
        mObjectAnimator.start();
    }

    /**
     * start ripple animation
     *
     * @param animationEndRunnable runnable to be excuted after ripple animation(some time is the click event)
     */
    private void startRipple(final Runnable animationEndRunnable) {
        if (mEventCancelled) return;

        float endRadius = getEndRadius();

        cancelAnimations();

        mRippleAnimator = new AnimatorSet();
        mRippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!ripplePersistent) {
                    setRadius(0);
                    setRippleAlpha(rippleAlpha);
                }
                if (animationEndRunnable != null && rippleDelayClick) {
                    animationEndRunnable.run();
                }
                mChildView.setPressed(false);
            }
        });

        ObjectAnimator ripple = ObjectAnimator.ofFloat(this, radiusProperty, radius, endRadius);
        ripple.setDuration(rippleDuration);
        ripple.setInterpolator(new DecelerateInterpolator());
        ObjectAnimator fade = ObjectAnimator.ofInt(this, circleAlphaProperty, rippleAlpha, 0);
        fade.setDuration(rippleFadeDuration);
        fade.setInterpolator(new AccelerateInterpolator());
        fade.setStartDelay(rippleDuration - rippleFadeDuration - FADE_EXTRA_DELAY);

        if (ripplePersistent) {
            mRippleAnimator.play(ripple);
        } else if (getRadius() > endRadius) {
            fade.setStartDelay(0);
            mRippleAnimator.play(fade);
        } else {
            mRippleAnimator.playTogether(ripple, fade);
        }
        mRippleAnimator.start();
    }

    private void cancelAnimations() {
        if (mRippleAnimator != null) {
            mRippleAnimator.cancel();
            mRippleAnimator.removeAllListeners();
        }

        if (mObjectAnimator != null) {
            mObjectAnimator.cancel();
        }
    }

    private float getEndRadius() {
        final int width = getWidth();
        final int height = getHeight();

        final int halfWidth = width / 2;
        final int halfHeight = height / 2;

        final float radiusX = halfWidth > mCurrentCoords.x ? width - mCurrentCoords.x : mCurrentCoords.x;
        final float radiusY = halfHeight > mCurrentCoords.y ? height - mCurrentCoords.y : mCurrentCoords.y;

        return (float) Math.sqrt(Math.pow(radiusX, 2) + Math.pow(radiusY, 2)) * 1.2f;
    }

    private boolean isInScrollingContainer() {
        ViewParent p = getParent();
        while (p != null && p instanceof ViewGroup) {
            if (((ViewGroup) p).shouldDelayChildPressedState()) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }

    private AdapterView findParentAdapterView() {
        if (mAdapterView != null) {
            return mAdapterView;
        }
        ViewParent current = getParent();
        while (true) {
            if (current instanceof AdapterView) {
                mAdapterView = (AdapterView) current;
                return mAdapterView;
            } else {
                try {
                    current = current.getParent();
                } catch (NullPointerException npe) {
                    throw new RuntimeException("Could not find a parent AdapterView");
                }
            }
        }
    }

    private void setPositionInAdapter() {
        if (rippleInAdapter) {
            mPositionInAdapter = findParentAdapterView().getPositionForView(MaterialRippleLayout.this);
        }
    }

    private boolean adapterPositionChanged() {
        if (rippleInAdapter) {
            int newPosition = findParentAdapterView().getPositionForView(MaterialRippleLayout.this);
            final boolean changed = newPosition != mPositionInAdapter;
            mPositionInAdapter = newPosition;
            if (changed) {
                cancelPressedEvent();
                cancelAnimations();
                mChildView.setPressed(false);
                setRadius(0);
            }
            return changed;
        }
        return false;
    }

    private boolean findClickableViewInChild(View view, int x, int y) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                final Rect rect = new Rect();
                child.getHitRect(rect);

                final boolean contains = rect.contains(x, y);
                if (contains) {
                    return findClickableViewInChild(child, x - rect.left, y - rect.top);
                }
            }
        } else if (view != mChildView) {
            return (view.isEnabled() && (view.isClickable() || view.isLongClickable() || view.isFocusableInTouchMode()));
        }

        return view.isFocusableInTouchMode();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bounds.set(0, 0, w, h);
        rippleBackground.setBounds(bounds);
    }

    @Override
    public boolean isInEditMode() {
        return true;
    }

    /*
     * Drawing
     */
    @Override
    public void draw(Canvas canvas) {
        final boolean positionChanged = adapterPositionChanged();
        if (rippleOverlay) {
            if (!positionChanged) {
                rippleBackground.draw(canvas);
            }
            super.draw(canvas);
            if (!positionChanged) {
                if (rippleRoundedCorners != 0) {
                    Path clipPath = new Path();
                    RectF rect = new RectF(0, 0, canvas.getWidth(), canvas.getHeight());
                    clipPath.addRoundRect(rect, rippleRoundedCorners, rippleRoundedCorners, Path.Direction.CW);
                    canvas.clipPath(clipPath);
                }
                canvas.drawCircle(mCurrentCoords.x, mCurrentCoords.y, radius, paint);
            }
        } else {
            if (!positionChanged) {
                rippleBackground.draw(canvas);
                canvas.drawCircle(mCurrentCoords.x, mCurrentCoords.y, radius, paint);
            }
            super.draw(canvas);
        }
    }

    /*
     * Animations
     */
    private Property<MaterialRippleLayout, Float> radiusProperty
            = new Property<MaterialRippleLayout, Float>(Float.class, "radius") {
        @Override
        public Float get(MaterialRippleLayout object) {
            return object.getRadius();
        }

        @Override
        public void set(MaterialRippleLayout object, Float value) {
            object.setRadius(value);
        }
    };

    private float getRadius() {
        return radius;
    }


    public void setRadius(float radius) {
        this.radius = radius;
        invalidate();
    }

    private Property<MaterialRippleLayout, Integer> circleAlphaProperty
            = new Property<MaterialRippleLayout, Integer>(Integer.class, "rippleAlpha") {
        @Override
        public Integer get(MaterialRippleLayout object) {
            return object.getRippleAlpha();
        }

        @Override
        public void set(MaterialRippleLayout object, Integer value) {
            object.setRippleAlpha(value);
        }
    };

    public int getRippleAlpha() {
        return paint.getAlpha();
    }

    public void setRippleAlpha(Integer rippleAlpha) {
        paint.setAlpha(rippleAlpha);
        invalidate();
    }

    /*
    * Accessor
     */
    public void setRippleColor(int rippleColor) {
        this.rippleColor = rippleColor;
        paint.setColor(rippleColor);
        paint.setAlpha(rippleAlpha);
        invalidate();
    }

    public void setRippleOverlay(boolean rippleOverlay) {
        this.rippleOverlay = rippleOverlay;
    }

    public void setRippleDiameter(int rippleDiameter) {
        this.rippleDiameter = rippleDiameter;
    }

    public void setRippleDuration(int rippleDuration) {
        this.rippleDuration = rippleDuration;
    }

    public void setRippleBackground(int color) {
        rippleBackground = new ColorDrawable(color);
        rippleBackground.setBounds(bounds);
        invalidate();
    }

    public void setRippleHover(boolean rippleHover) {
        this.rippleHover = rippleHover;
    }

    public void setRippleDelayClick(boolean rippleDelayClick) {
        this.rippleDelayClick = rippleDelayClick;
    }

    public void setRippleFadeDuration(int rippleFadeDuration) {
        this.rippleFadeDuration = rippleFadeDuration;
    }

    public void setRipplePersistent(boolean ripplePersistent) {
        this.ripplePersistent = ripplePersistent;
    }

    public void setRippleInAdapter(boolean rippleInAdapter) {
        this.rippleInAdapter = rippleInAdapter;
    }

    public void setRippleRoundedCorners(int rippleRoundedCorner) {
        this.rippleRoundedCorners = rippleRoundedCorner;
        enableClipPathSupportIfNecessary();
    }

    public void setDefaultRippleAlpha(int alpha) {
        this.rippleAlpha = alpha;
        paint.setAlpha(alpha);
        invalidate();
    }

    public void performRipple() {
        mCurrentCoords = new Point(getWidth() / 2, getHeight() / 2);
        startRipple(null);
    }

    public void performRipple(Point anchor) {
        mCurrentCoords = new Point(anchor.x, anchor.y);
        startRipple(null);
    }

    /**
     * {@link Canvas#clipPath(Path)} is not supported in hardware accelerated layers
     * before API 18. Use software layer instead
     * <p/>
     * https://developer.android.com/guide/topics/graphics/hardware-accel.html#unsupported
     */
    private void enableClipPathSupportIfNecessary() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (rippleRoundedCorners != 0) {
                mLayerType = getLayerType();
                setLayerType(LAYER_TYPE_SOFTWARE, null);
            } else {
                setLayerType(mLayerType, null);
            }
        }
    }

    private boolean isPerformingClickEvent() {
        return mPerformClickEvent != null && mPerformClickEvent.isRunning();
    }

    /*
     * Helper,Call this view's OnClickListener if need.
     */
    private class PerformClickEvent implements Runnable {
        private boolean isRunning;

        @Override
        public void run() {
            if (hasPerformedLongPress) return;
            isRunning = true;
            // if parent is an AdapterView, try to call its ItemClickListener
            if (getParent() instanceof AdapterView) {
                clickAdapterView((AdapterView) getParent());
            } else if (rippleInAdapter) {
                // find adapter view
                clickAdapterView(findParentAdapterView());
            } else {
                // otherwise, just perform click on child
                mChildView.performClick();
            }
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    isRunning = false;
                }
            }, (long) (ViewConfiguration.getDoubleTapTimeout()*1.5));

        }

        public boolean isRunning() {
            return isRunning;
        }

        private void clickAdapterView(AdapterView parent) {
            final int position = parent.getPositionForView(MaterialRippleLayout.this);
            final long itemId = parent.getAdapter() != null
                    ? parent.getAdapter().getItemId(position)
                    : 0;
            if (position != AdapterView.INVALID_POSITION) {
                parent.performItemClick(MaterialRippleLayout.this, position, itemId);
            }
        }
    }

    private final class PressedEvent implements Runnable {

        private final MotionEvent event;

        public PressedEvent(MotionEvent event) {
            this.event = event;
        }

        @Override
        public void run() {
            mPrepressed = false;
            mChildView.setLongClickable(false);//prevent the child's long click,let's the ripple layout call it's performLongClick
            mChildView.onTouchEvent(event);
            mChildView.setPressed(true);
            if (rippleHover) {
                startHover();
            }
        }
    }

    static float dpToPx(Resources resources, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.getDisplayMetrics());
    }

    /*
     * Builder
     */

    public static class RippleBuilder {

        private final Context context;
        private final View child;

        private int rippleColor = DEFAULT_COLOR;
        private boolean rippleOverlay = DEFAULT_RIPPLE_OVERLAY;
        private boolean rippleHover = DEFAULT_HOVER;
        private float rippleDiameter = DEFAULT_DIAMETER_DP;
        private int rippleDuration = DEFAULT_DURATION;
        private float rippleAlpha = DEFAULT_ALPHA;
        private boolean rippleDelayClick = DEFAULT_DELAY_CLICK;
        private int rippleFadeDuration = DEFAULT_FADE_DURATION;
        private boolean ripplePersistent = DEFAULT_PERSISTENT;
        private int rippleBackground = DEFAULT_BACKGROUND;
        private boolean rippleSearchAdapter = DEFAULT_SEARCH_ADAPTER;
        private float rippleRoundedCorner = DEFAULT_ROUNDED_CORNERS;

        public RippleBuilder(View child) {
            this.child = child;
            this.context = child.getContext();
        }

        public RippleBuilder rippleColor(int color) {
            this.rippleColor = color;
            return this;
        }

        public RippleBuilder rippleOverlay(boolean overlay) {
            this.rippleOverlay = overlay;
            return this;
        }

        public RippleBuilder rippleHover(boolean hover) {
            this.rippleHover = hover;
            return this;
        }

        public RippleBuilder rippleDiameterDp(int diameterDp) {
            this.rippleDiameter = diameterDp;
            return this;
        }

        public RippleBuilder rippleDuration(int duration) {
            this.rippleDuration = duration;
            return this;
        }

        public RippleBuilder rippleAlpha(float alpha) {
            this.rippleAlpha = 255 * alpha;
            return this;
        }

        public RippleBuilder rippleDelayClick(boolean delayClick) {
            this.rippleDelayClick = delayClick;
            return this;
        }

        public RippleBuilder rippleFadeDuration(int fadeDuration) {
            this.rippleFadeDuration = fadeDuration;
            return this;
        }

        public RippleBuilder ripplePersistent(boolean persistent) {
            this.ripplePersistent = persistent;
            return this;
        }

        public RippleBuilder rippleBackground(int color) {
            this.rippleBackground = color;
            return this;
        }

        public RippleBuilder rippleInAdapter(boolean inAdapter) {
            this.rippleSearchAdapter = inAdapter;
            return this;
        }

        public RippleBuilder rippleRoundedCorners(int radiusDp) {
            this.rippleRoundedCorner = radiusDp;
            return this;
        }

        public MaterialRippleLayout create() {
            MaterialRippleLayout layout = new MaterialRippleLayout(context);
            layout.setRippleColor(rippleColor);
            layout.setDefaultRippleAlpha((int) rippleAlpha);
            layout.setRippleDelayClick(rippleDelayClick);
            layout.setRippleDiameter((int) dpToPx(context.getResources(), rippleDiameter));
            layout.setRippleDuration(rippleDuration);
            layout.setRippleFadeDuration(rippleFadeDuration);
            layout.setRippleHover(rippleHover);
            layout.setRipplePersistent(ripplePersistent);
            layout.setRippleOverlay(rippleOverlay);
            layout.setRippleBackground(rippleBackground);
            layout.setRippleInAdapter(rippleSearchAdapter);
            layout.setRippleRoundedCorners((int) dpToPx(context.getResources(), rippleRoundedCorner));

            ViewGroup.LayoutParams params = child.getLayoutParams();
            ViewGroup parent = (ViewGroup) child.getParent();
            int index = 0;

            if (parent != null && parent instanceof MaterialRippleLayout) {
                throw new IllegalStateException("MaterialRippleLayout could not be created: parent of the view already is a MaterialRippleLayout");
            }

            if (parent != null) {
                index = parent.indexOfChild(child);
                parent.removeView(child);
            }

            layout.addView(child, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            if (parent != null) {
                parent.addView(layout, index, params);
            }

            return layout;
        }
    }
}
