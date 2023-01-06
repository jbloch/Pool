package us.bloch.pool;

import java.time.LocalTime;
import java.util.Objects;
import java.util.Set;

/**
 * The status of an "active" pool, i.e., one whose water is currently circulating.
 */
public class ActivePoolStatus extends ReachablePoolStatus {
    private final Body activeBody;
    private final int waterTemp;
    private final int pumpSpeed;
    private final int pumpPower;

    public ActivePoolStatus(LocalTime time, Set<Feature> activeFeatures, Body activeBody,
            int airTemp, int waterTemp, int pumpSpeed, int pumpPower, int poolSeekTemp,
            int spaSeekTemp, HeatSource poolHeatSrc, HeatSource spaHeatSrc) {
        super(time, activeFeatures, airTemp, poolSeekTemp, spaSeekTemp, poolHeatSrc, spaHeatSrc);
        this.activeBody = Objects.requireNonNull(activeBody);
        this.waterTemp = waterTemp;
        this.pumpSpeed = pumpSpeed;
        this.pumpPower = pumpPower;
    }

    /** Returns the active body (either pool or spa). Will never be null, as system is active. */
    public Body activeBody() {
        return this.activeBody;
    }

    /** Returns the water temperature in degrees. */
    public int waterTemp() {
        return this.waterTemp;
    }

    /** Returns the pump speed in RPM. */
    public int pumpSpeed() {
        return this.pumpSpeed;
    }

    /** Returns the pump's power consumptions in watts. */
    public int pumpPower() {
        return this.pumpPower;
    }

    /** Returns a string representation of this pool status. */
    public String toString() {
        return String.format("%s: %s on. Air: %d°, Water: %d°, Pump: %d RPM, %d watts, %s"
                + "Pool seek: %d°, Spa seek: %d°, Pool heat src: %s, Spa heat src: %s",
                time.format(TIME_FORMATTER), activeBody, airTemp, waterTemp, pumpSpeed, pumpPower,
                activeFeatures.isEmpty() ? "" : activeFeatures + ", ",
                poolSeekTemp, spaSeekTemp, poolHeatSource, spaHeatSource);
    }
}
