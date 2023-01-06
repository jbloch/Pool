package us.bloch.pool;

import java.util.Arrays;
import java.util.Map;

import us.bloch.pool.hardware.PentairBus;

import static java.util.stream.Collectors.toMap;

/**
 * The various heat sources for the pool or spa.
 */
public enum HeatSource {
    UNHEATED(PentairBus.HeatSource.UNHEATED),
    HEATER(PentairBus.HeatSource.HEATER),
    SOLAR_PREF(PentairBus.HeatSource.SOLAR_PREF),
    SOLAR(PentairBus.HeatSource.SOLAR);

    /** The Pentair heat source underlying this heat source. */
    final PentairBus.HeatSource heatSource;

    private static final Map<PentairBus.HeatSource, HeatSource> forUnderlyingHeatSource
            = Arrays.stream(values()).collect(toMap(hs -> hs.heatSource, hs -> hs));

    HeatSource(PentairBus.HeatSource heatSource) {
        this.heatSource = heatSource;
    }

    static HeatSource from(us.bloch.pool.hardware.PentairBus.HeatSource uhs) {
        return forUnderlyingHeatSource.get(uhs);
    }
}