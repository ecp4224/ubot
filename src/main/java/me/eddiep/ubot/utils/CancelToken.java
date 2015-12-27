package me.eddiep.ubot.utils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A synchronized token to notifier another Thread when to stop
 * a operation
 */
public class CancelToken {
    private AtomicBoolean canceled = new AtomicBoolean(false);

    /**
     * Atomically check if this token has been canceled
     * @return True if this token has been canceled, otherwise false
     */
    public boolean isCanceled() {
        return canceled.get();
    }

    /**
     * Cancel this token atomically
     */
    public void cancel() {
        canceled.set(true);
    }

    /**
     * Set whether this token is canceled or not atomically
     * @param value True if this token should be canceled, otherwise false
     */
    public void setCanceled(boolean value) {
        canceled.set(value);
    }
}
