/*
 * Copyright (C) 2019 The Android Open Source Project
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
package amirz.aidlbridge;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import static amirz.aidlbridge.Interpolators.LINEAR;
import static amirz.aidlbridge.Interpolators.scrollInterpolatorForVelocity;
import static amirz.aidlbridge.Utilities.SINGLE_FRAME_MS;

public class FeedController extends FrameLayout implements SwipeDetector.Listener {

    private static final String TAG = "FeedController";

    // Progress after which the transition is assumed to be a success in case user does not fling
    public static final float SUCCESS_TRANSITION_PROGRESS = 0.5f;

    protected final SwipeDetector mDetector;

    private boolean mNoIntercept;

    protected FeedState mStartState;
    protected FeedState mFromState;
    protected FeedState mToState;
    protected AnimatorPlaybackController mCurrentAnimation;
    protected PendingAnimation mPendingAnimation;

    private float mStartProgress;
    // Ratio of transition process [0, 1] to drag displacement (px)
    private float mProgressMultiplier;
    private float mDisplacementShift;
    private boolean mCanBlockFling;
    private FlingBlockCheck mFlingBlockCheck = new FlingBlockCheck();

    private View mFeedBackground;
    private View mFeedContent;
    private FeedState mCurrentState = FeedState.CLOSED;
    private long mDownTime = 0;
    private float mLastScroll = 0f;
    private float mProgress;
    private LauncherFeed mLauncherFeed;

    public FeedController(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDetector = new SwipeDetector(context, this, SwipeDetector.HORIZONTAL);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mFeedContent = findViewById(R.id.feed_content);
        mFeedBackground = findViewById(R.id.feed_background);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            closeOverlay(true, 0);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    public void closeOverlay(boolean animated, int duration) {
        if (!animated) {
            setProgress(0, true);
        } else {
            if (duration == 0) {
                duration = 350;
            }
            Animator animator = ObjectAnimator.ofFloat(this, PROGRESS, 1f, 0f);
            animator.setDuration(duration);
            animator.setInterpolator(Interpolators.DEACCEL_1_5);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mCurrentState = FeedState.CLOSED;
                }
            });
            animator.start();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        setProgress(mCurrentState.getProgress(), false);
    }

    public void setLauncherFeed(LauncherFeed launcherFeed) {
        mLauncherFeed = launcherFeed;
    }

    private void setProgress(float progress, boolean notify) {
        Log.d(TAG, "setProgress: " + progress);
        mProgress = progress;
        if (notify) {
            mLauncherFeed.onProgress(mProgress, mDetector.isDraggingOrSettling());
        }
        mFeedBackground.setAlpha(mProgress);
        mFeedContent.setTranslationX((-1 + mProgress) * getShiftRange());
    }

    private long time() {
        return System.currentTimeMillis();
    }

    public void startScroll() {
        mDownTime = time();
        onInterceptTouchEvent(MotionEvent.obtain(mDownTime, mDownTime, MotionEvent.ACTION_DOWN, 0f, 0f, 0));
    }

    public void onScroll(float progress) {
        mLastScroll = progress;
        onTouchEvent(MotionEvent.obtain(mDownTime, time(), MotionEvent.ACTION_MOVE, mLastScroll * getWidth(), 0f, 0));
    }

    public void endScroll() {
        onTouchEvent(MotionEvent.obtain(mDownTime, time(), MotionEvent.ACTION_UP, mLastScroll * getWidth(), 0f, 0));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mCurrentAnimation != null && mFromState == FeedState.OPEN) {
            return false;
        }
        mDetector.setDetectableScrollConditions(getSwipeDirection(), mCurrentAnimation != null);
        mDetector.onTouchEvent(ev);
        return mDetector.isDraggingOrSettling();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mDetector.onTouchEvent(event);
    }

    private int getSwipeDirection() {
        FeedState fromState = mCurrentState;
        int swipeDirection = 0;
        if (getTargetState(fromState, true /* isDragTowardPositive */) != fromState) {
            swipeDirection |= SwipeDetector.DIRECTION_POSITIVE;
        }
        if (getTargetState(fromState, false /* isDragTowardPositive */) != fromState) {
            swipeDirection |= SwipeDetector.DIRECTION_NEGATIVE;
        }
        return swipeDirection;
    }

    protected float getShiftRange() {
        return getWidth();
    }

    /**
     * Returns the state to go to from fromState given the drag direction. If there is no state in
     * that direction, returns fromState.
     */
    protected FeedState getTargetState(FeedState fromState, boolean isDragTowardPositive) {
        return fromState == FeedState.CLOSED ? FeedState.OPEN : FeedState.CLOSED;
    }

    protected float initCurrentAnimation() {
        float range = getShiftRange();
        long maxAccuracy = (long) (2 * range);

        float startShift = mFromState.getProgress() * range;
        float endShift = mToState.getProgress() * range;

        float totalShift = endShift - startShift;

        cancelPendingAnim();
        mCurrentAnimation = AnimatorPlaybackController.wrap(createAnim(mToState, maxAccuracy), maxAccuracy, this::clearState);

        if (totalShift == 0) {
            totalShift = getShiftRange();
        }

        return 1 / totalShift;
    }

    private AnimatorSet createAnim(FeedState toState, long duration) {
        Animator translationX = ObjectAnimator.ofFloat(this, PROGRESS, toState.getProgress());
        translationX.setDuration(duration);
        translationX.setInterpolator(LINEAR);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(translationX);
        return animatorSet;
    }

    private void cancelPendingAnim() {
        if (mPendingAnimation != null) {
            mPendingAnimation.finish(false);
            mPendingAnimation = null;
        }
    }

    private boolean reinitCurrentAnimation(boolean reachedToState, boolean isDragTowardPositive) {
        FeedState newFromState = mFromState == null ? mCurrentState
                : reachedToState ? mToState : mFromState;
        FeedState newToState = getTargetState(newFromState, isDragTowardPositive);

        if (newFromState == mFromState && newToState == mToState || (newFromState == newToState)) {
            return false;
        }

        mFromState = newFromState;
        mToState = newToState;

        mStartProgress = 0;
        if (mCurrentAnimation != null) {
            mCurrentAnimation.setOnCancelRunnable(null);
        }

        mProgressMultiplier = initCurrentAnimation();
        mCurrentAnimation.dispatchOnStart();
        return true;
    }

    @Override
    public void onDragStart(boolean start) {
        mStartState = mCurrentState;
        if (mCurrentAnimation == null) {
            mFromState = mStartState;
            mToState = null;
            cancelAnimationControllers();
            reinitCurrentAnimation(false, mDetector.wasInitialTouchPositive());
            mDisplacementShift = 0;
        } else {
            mCurrentAnimation.pause();
            mStartProgress = mCurrentAnimation.getProgressFraction();
        }
        mCanBlockFling = false;
        mFlingBlockCheck.unblockFling();
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        float deltaProgress = mProgressMultiplier * (displacement - mDisplacementShift);
        float progress = deltaProgress + mStartProgress;
        updateProgress(progress);
        boolean isDragTowardPositive = (displacement - mDisplacementShift) < 0;
        if (progress <= 0) {
            if (reinitCurrentAnimation(false, isDragTowardPositive)) {
                mDisplacementShift = displacement;
                if (mCanBlockFling) {
                    mFlingBlockCheck.blockFling();
                }
            }
        } else if (progress >= 1) {
            if (reinitCurrentAnimation(true, isDragTowardPositive)) {
                mDisplacementShift = displacement;
                if (mCanBlockFling) {
                    mFlingBlockCheck.blockFling();
                }
            }
        } else {
            mFlingBlockCheck.onEvent();
        }

        return true;
    }

    protected void updateProgress(float fraction) {
        mCurrentAnimation.setPlayFraction(fraction);
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        boolean blockedFling = fling && mFlingBlockCheck.isBlocked();
        if (blockedFling) {
            fling = false;
        }

        final FeedState targetState;
        final float progress = mCurrentAnimation.getProgressFraction();
        final float interpolatedProgress = mCurrentAnimation.getInterpolator()
                .getInterpolation(progress);
        if (fling) {
            targetState =
                    Float.compare(Math.signum(velocity), Math.signum(mProgressMultiplier)) == 0
                            ? mToState : mFromState;
            // snap to top or bottom using the release velocity
        } else {
            targetState = (interpolatedProgress > SUCCESS_TRANSITION_PROGRESS) ? mToState : mFromState;
        }

        final float endProgress;
        final float startProgress;
        final long duration;
        // Increase the duration if we prevented the fling, as we are going against a high velocity.
        final int durationMultiplier = blockedFling && targetState == mFromState
                ? blockedFlingDurationFactor(velocity) : 1;

        if (targetState == mToState) {
            endProgress = 1;
            if (progress >= 1) {
                duration = 0;
                startProgress = 1;
            } else {
                startProgress = Utilities.boundToRange(
                        progress + velocity * SINGLE_FRAME_MS * mProgressMultiplier, 0f, 1f);
                duration = SwipeDetector.calculateDuration(velocity,
                        endProgress - Math.max(progress, 0)) * durationMultiplier;
            }
        } else {
            // Let the state manager know that the animation didn't go to the target state,
            // but don't cancel ourselves (we already clean up when the animation completes).
            Runnable onCancel = mCurrentAnimation.getOnCancelRunnable();
            mCurrentAnimation.setOnCancelRunnable(null);
            mCurrentAnimation.dispatchOnCancel();
            mCurrentAnimation.setOnCancelRunnable(onCancel);

            endProgress = 0;
            if (progress <= 0) {
                duration = 0;
                startProgress = 0;
            } else {
                startProgress = Utilities.boundToRange(
                        progress + velocity * SINGLE_FRAME_MS * mProgressMultiplier, 0f, 1f);
                duration = SwipeDetector.calculateDuration(velocity,
                        Math.min(progress, 1) - endProgress) * durationMultiplier;
            }
        }

        mCurrentAnimation.setEndAction(() -> onSwipeInteractionCompleted(targetState));
        ValueAnimator anim = mCurrentAnimation.getAnimationPlayer();
        anim.setFloatValues(startProgress, endProgress);
        updateSwipeCompleteAnimation(anim, duration, targetState, velocity, fling);
        mCurrentAnimation.dispatchOnStart();
        anim.start();
    }

    protected void updateSwipeCompleteAnimation(ValueAnimator animator, long expectedDuration,
                                                FeedState targetState, float velocity, boolean isFling) {
        animator.setDuration(expectedDuration)
                .setInterpolator(scrollInterpolatorForVelocity(velocity));
    }

    protected void onSwipeInteractionCompleted(FeedState targetState) {
        cancelAnimationControllers();
        boolean shouldGoToTargetState = true;
        if (mPendingAnimation != null) {
            boolean reachedTarget = mToState == targetState;
            mPendingAnimation.finish(reachedTarget);
            mPendingAnimation = null;
            shouldGoToTargetState = !reachedTarget;
        }
        if (shouldGoToTargetState) {
            mCurrentState = targetState;
        }
        mLauncherFeed.onProgress(mProgress, false);
    }

    protected void clearState() {
        cancelAnimationControllers();
    }

    private void cancelAnimationControllers() {
        mCurrentAnimation = null;
        mDetector.finishedScrolling();
        mDetector.setDetectableScrollConditions(0, false);
    }

    public static int blockedFlingDurationFactor(float velocity) {
        return (int) Utilities.boundToRange(Math.abs(velocity) / 2, 2f, 6f);
    }

    public static final Property<FeedController, Float> PROGRESS = new Property<FeedController, Float>(Float.class, "progress") {
        @Override
        public Float get(FeedController object) {
            return object.mProgress;
        }

        @Override
        public void set(FeedController object, Float value) {
            object.setProgress(value, true);
        }
    };
}
