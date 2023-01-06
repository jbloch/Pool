package us.bloch.pool;

import us.bloch.pool.hardware.PentairBus;

/**
 * The power state of a body or feature, either on or off.
 */
public enum PowerState {
    /** The body or feature is switched on. */
    ON(PentairBus.CircuitPowerState.ON),

    /** The body or feature is switched off. */
    OFF(PentairBus.CircuitPowerState.OFF);

    final PentairBus.CircuitPowerState circuitState;

    /** Returns the pentair circuit state corresponding to this power state. */
    PowerState(PentairBus.CircuitPowerState circuitState) {
        this.circuitState = circuitState;
    }
}
