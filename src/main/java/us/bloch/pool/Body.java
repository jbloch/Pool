package us.bloch.pool;

import us.bloch.pool.hardware.PentairBus.Circuit;

/**
 * A body of water controlled by this controller. If water is circulating through a body, it is
 * said to be active.
 */
public enum Body {
    /** The pool. */
    POOL(Circuit.POOL),

    /** The spa (hot tub). */
    SPA(Circuit.SPA);

    final Circuit circuit;

    Body(Circuit circuit) {
        this.circuit = circuit;
    }
}
