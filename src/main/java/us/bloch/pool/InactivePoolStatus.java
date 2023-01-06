package us.bloch.pool;

import java.time.LocalTime;
import java.util.Set;

/**
 * The status of an "inactive" pool, i.e., one whose water is not currently circulating.
 */
public class InactivePoolStatus extends ReachablePoolStatus {
    public InactivePoolStatus(LocalTime time, Set<Feature> activeFeatures, int airTemp,
            int poolSeekTemp, int spaSeekTemp, HeatSource poolHeatSrc, HeatSource spaHeatSrc) {
        super(time, activeFeatures, airTemp, poolSeekTemp, spaSeekTemp, poolHeatSrc, spaHeatSrc);
    }

    /** Returns the string form of this pool status. */
    public String toString() {
        return String.format(
                "%s: Pump off. Air: %d°,%s Pool seek: %d°, Spa seek: %d°, Pool heat src: %s, Spa heat src: %s",
                time.format(TIME_FORMATTER), airTemp, activeFeatures.contains(Feature.LIGHT) ? " Light on," : "",
                poolSeekTemp, spaSeekTemp, poolHeatSource, spaHeatSource);
    }
}
