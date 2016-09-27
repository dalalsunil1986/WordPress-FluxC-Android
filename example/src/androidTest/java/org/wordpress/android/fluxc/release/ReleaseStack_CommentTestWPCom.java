package org.wordpress.android.fluxc.release;

import org.greenrobot.eventbus.Subscribe;
import org.wordpress.android.fluxc.TestUtils;
import org.wordpress.android.fluxc.generated.CommentActionBuilder;
import org.wordpress.android.fluxc.model.CommentModel;
import org.wordpress.android.fluxc.model.CommentStatus;
import org.wordpress.android.fluxc.store.CommentStore;
import org.wordpress.android.fluxc.store.CommentStore.FetchCommentsPayload;
import org.wordpress.android.fluxc.store.CommentStore.PushCommentPayload;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class ReleaseStack_CommentTestWPCom extends ReleaseStack_WPComBase {
    @Inject CommentStore mCommentStore;
    private List<CommentModel> mComments;

    private enum TEST_EVENTS {
        NONE,
        COMMENT_CHANGED,
        COMMENT_CHANGED_ERROR,
    }
    private TEST_EVENTS mNextEvent;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReleaseStackAppComponent.inject(this);

        // Authenticate, fetch sites and initialize mSite.
        init();
        mNextEvent = TEST_EVENTS.NONE;
    }

    public void testFetchComments() throws InterruptedException {
        FetchCommentsPayload payload = new FetchCommentsPayload(mSite);
        mNextEvent = TEST_EVENTS.COMMENT_CHANGED;
        mCountDownLatch = new CountDownLatch(1);
        mDispatcher.dispatch(CommentActionBuilder.newFetchCommentsAction(payload));
        // Wait for a network response / onChanged event
        assertEquals(true, mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    public void testEditValidComment() throws InterruptedException {
        fetchFirstComments();

        // Get first comment
        CommentModel firstComment = mComments.get(0);

        // Edit the comment
        firstComment.setContent("If we could somehow harness this lightning... "
                                + "channel it into the flux capacitor... it just might work.");
        firstComment.setStatus(CommentStatus.APPROVED.toString());

        // Push the edited comment
        PushCommentPayload pushCommentPayload = new PushCommentPayload(firstComment, mSite);
        mCountDownLatch = new CountDownLatch(1);
        mDispatcher.dispatch(CommentActionBuilder.newPushCommentAction(pushCommentPayload));
        assertEquals(true, mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    public void testEditInvalidComment() throws InterruptedException {
        CommentModel comment = new CommentModel();
        comment.setContent("");
        comment.setDatePublished("");
        comment.setStatus("approved");
        // Try to push the invalid comment
        mNextEvent = TEST_EVENTS.COMMENT_CHANGED_ERROR;
        PushCommentPayload pushCommentPayload = new PushCommentPayload(comment, mSite);
        mCountDownLatch = new CountDownLatch(1);
        mDispatcher.dispatch(CommentActionBuilder.newPushCommentAction(pushCommentPayload));
        assertEquals(true, mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Subscribe
    public void onCommentChanged(CommentStore.OnCommentChanged event) {
        List<CommentModel> comments = mCommentStore.getCommentsForSite(mSite);
        if (event.isError()) {
            AppLog.i(T.TESTS, "event error type: " + event.error.type);
            if (mNextEvent != TEST_EVENTS.COMMENT_CHANGED_ERROR) {
                assertTrue("onCommentChanged Error", false);
            }
            mCountDownLatch.countDown();
            return;
        }
        AppLog.i(T.TESTS, "comments count " + comments.size());
        assertTrue(comments.size() != 0);
        assertEquals(TEST_EVENTS.COMMENT_CHANGED, mNextEvent);
        mCountDownLatch.countDown();
    }

    // Private methods

    private void fetchFirstComments() throws InterruptedException {
        if (mComments != null) {
            return;
        }
        FetchCommentsPayload payload = new FetchCommentsPayload(mSite);
        mNextEvent = TEST_EVENTS.COMMENT_CHANGED;
        mCountDownLatch = new CountDownLatch(1);
        mDispatcher.dispatch(CommentActionBuilder.newFetchCommentsAction(payload));
        assertEquals(true, mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        mComments = mCommentStore.getCommentsForSite(mSite);
    }
}
