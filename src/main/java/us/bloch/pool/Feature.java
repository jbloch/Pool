package us.bloch.pool;

import us.bloch.pool.hardware.PentairBus.Circuit;

/**
 * An attribute of this pool that can be turned on or off. All features except for
 * {@link #HEATER} can be turned on or off directly by
 * {@link PoolController#setFeatureState(Feature, PowerState)}. The heater is controlled
 * automatically by the pool controller hardware, based on configuration parameters such as the
 * pool and spa seek temperatures.
 *
 * <p>NOTE: This type is a bit of a hack, in that it consists of the features that our pool
 * system has (rather than all of the features that any pool system might provide). The
 * (internal) mapping from feature to circuit is also specific to our pool system.
 */
public enum Feature {
    /** The pool light */
    LIGHT(Circuit.AUX1),

    /** The jets. */
    JETS(Circuit.AUX3),

    /** A heater (gas or solar). */
    HEATER(null),

    /** Heat boost mode (which temporarily raises the temperature of the active body). */
    HEAT_BOOST(Circuit.HEAT_BOOST);

    final Circuit circuit;

    Feature(Circuit circuit) {
        this.circuit = circuit;
    }
}