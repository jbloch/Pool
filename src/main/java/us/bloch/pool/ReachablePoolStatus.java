package us.bloch.pool;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;

/**
 * The status of a pool system whose controller is reachable. This abstract class contains the
 * members shared between {@link ActivePoolStatus} and {@link InactivePoolStatus}.
 */
abstract class ReachablePoolStatus extends PoolStatus {
    final LocalTime time;
    final int airTemp;
    final Set<Feature> activeFeatures;
    final int poolSeekTemp;
    final int spaSeekTemp;
    final HeatSource poolHeatSource;
    final HeatSource spaHeatSource;

    // Used by our subclasses to format the time field, which is accurate only to the minute
    final static DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    ReachablePoolStatus(LocalTime time, Set<Feature> activeFeatures, int airTemp, int poolSeekTemp,
            int spaSeekTemp, HeatSource poolHeatSource, HeatSource spaHeatSource) {
        this.time = time;
        this.airTemp = airTemp;
        this.activeFeatures = Objects.requireNonNull(activeFeatures);
        this.poolSeekTemp = poolSeekTemp;
        this.spaSeekTemp = spaSeekTemp;
        this.poolHeatSource = poolHeatSource;
        this.spaHeatSource = spaHeatSource;
    }

    /** Returns the temperature of the air surrounding the pool. */
    public LocalTime time() {
        return this.time;
    }

    /** Returns the temperature of the air surrounding the pool. */
    public int airTemp() {
        return this.airTemp;
    }

    /** Returns the features currently turned on for the pool (e.g., light, jets, heater). */
    public Set<Feature> activeFeatures() {
        return this.activeFeatures;
    }

    /** Returns the pool seek temperature (below which the pool heat source will be turned on). */
    public int poolSeekTemperature() {
        return this.poolSeekTemp;
    }

    /** Returns the spa seek temperature (below which the spa heat source will be turned on). */
    public int spaSeekTemperature() {
        return this.spaSeekTemp;
    }

    /** Returns the pool heat source. */
    public HeatSource poolHeatSource() {
        return this.poolHeatSource;
    }

    /** Returns the spa heat source. */
    public HeatSource spaHeatSource() {
        return this.spaHeatSource;
    }
}
