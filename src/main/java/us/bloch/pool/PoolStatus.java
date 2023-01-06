package us.bloch.pool;

/**
 * The status of a pool. You can subscribe to status updates with {@link PoolController#subscribe()}
 * or {@link PoolController#addStatusListener(PoolController.StatusListener)}.
 */
public abstract class PoolStatus {
    PoolStatus() { }
}

