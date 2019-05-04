package amirz.aidlbridge;

public class FeedState {

    public static final FeedState CLOSED = new FeedState(0f);
    public static final FeedState OPEN = new FeedState(1f);

    private float mProgress;

    private FeedState(float progress) {
        mProgress = progress;
    }

    public float getProgress() {
        return mProgress;
    }
}
