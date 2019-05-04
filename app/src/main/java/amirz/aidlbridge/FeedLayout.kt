package amirz.aidlbridge

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

class FeedLayout(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs),
        SwipeDetector.Listener {

    private var progress = 0f

    private val detector = SwipeDetector(context, this, SwipeDetector.HORIZONTAL).apply {
        setDetectableScrollConditions(SwipeDetector.DIRECTION_BOTH, true)
    }
    private val time get() = System.currentTimeMillis()
    private var downTime = 0L

    private val feedContent by lazy { findViewById<View>(R.id.feed_content) }
    private var currentAnimation: AnimatorPlaybackController? = null
    private var currentState = STATE_CLOSED
    private var startState: Int? = null
    private var fromState: Int? = null
    private var toState: Int? = null
    private var displacementShift = 0f
    private var startProgress = 0f

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        translationX = (-width).toFloat()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return detector.isDraggingOrSettling
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return detector.onTouchEvent(event)
    }

    fun startScroll() {
        downTime = time
        detector.onTouchEvent(MotionEvent.obtain(downTime, time, MotionEvent.ACTION_DOWN, 0f, 0f, 0))
    }

    fun onScroll(progress: Float) {
        this.progress = progress
        detector.onTouchEvent(MotionEvent.obtain(downTime, time, MotionEvent.ACTION_MOVE, progress * width, 0f, 0))
    }

    fun endScroll() {
        detector.onTouchEvent(MotionEvent.obtain(downTime, time, MotionEvent.ACTION_UP, progress * width, 0f, 0))
    }

    private fun reinitCurrentAnimation(reachedToState: Boolean, isDragTowardPositive: Boolean): Boolean {
        val newFromState = when {
            fromState == null -> currentState
            reachedToState -> toState
            else -> fromState
        }
        val newToState = if (isDragTowardPositive) STATE_OPEN else STATE_CLOSED

        fromState = newFromState
        toState = newToState

        startProgress = 0f
        currentAnimation?.onCancelRunnable = null
        initCurrentAnimation()
        currentAnimation?.dispatchOnStart()
        return true
    }

    private fun initCurrentAnimation() {

    }

    override fun onDragStart(start: Boolean) {
        Log.d(TAG, "onDragStart($start)")
        startState = currentState
        if (currentAnimation == null) {
            fromState = startState
            toState = null
            cancelAnimationControllers()
            reinitCurrentAnimation(false, detector.wasInitialTouchPositive())
            displacementShift = 0f
        } else {
            currentAnimation!!.pause()
            startProgress = currentAnimation!!.progressFraction
        }
    }

    override fun onDrag(displacement: Float, velocity: Float): Boolean {
        Log.d(TAG, "onDrag($displacement, $velocity)")

        return true
    }

    override fun onDragEnd(velocity: Float, fling: Boolean) {
        Log.d(TAG, "onDragEnd($velocity, $fling)")
        val targetState: Int
        if (fling) {
            targetState = (if (Math.signum(velocity).compareTo(Math.signum(1f)) == 0) toState else fromState)!!
        }
    }

    private fun cancelAnimationControllers() {
        currentAnimation = null
        detector.finishedScrolling()
        detector.setDetectableScrollConditions(0, false)
    }



    companion object {

        private const val TAG = "FeedLayout"

        private const val STATE_CLOSED = 0
        private const val STATE_OPEN = 1
    }
}
