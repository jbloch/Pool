package us.bloch.pool;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import us.bloch.pool.hardware.*;
import us.bloch.pool.hardware.PentairBus.Circuit;
import us.bloch.pool.hardware.PentairBus.CircuitPowerState;

/**
 * A programmatic interface to a swimming pool system. This class was designed to work atop the
 * {@link PentairBus} class, but could theoretically be ported to another systems. The pool
 * system includes a spa, a pump, a gas heater, a solar heater, and a light. This class is not
 * currently extensible: adding equipment to the system would require that the class be modified.
 *
 * <p>This class is thread-safe.
 *
 * @author Josh Bloch
 */
public class PoolController {
    /** The Pentair bus encapsulated by this pool controller. */
    private final PentairBus bus;

    /** Number of seconds we attempt to get a message from bus before we declare pool unreachable */
    private static final long REACHABILITY_TIMEOUT = 10;

    /*
     * These variables represent the most recent values of their respective attributes. They are
     * maintained by our bus monitor thread on the basis of messages received on the Pentair bus,
     * and used to generate pool status updates. Other threads may not read these variables:
     * they are not synchronized in any way.
     */
    private LocalTime time;
    private int airTemp;
    private int waterTemp;
    private Body activeBody;
    private Set<Feature> activeFeatures;
    private int pumpSpeedInRpm;
    private int pumpPowerInWatts;
    private int poolSeekTemp;
    private int spaSeekTemp;
    private HeatSource poolHeatSource;
    private HeatSource spaHeatSource;

    /**
     * A list of the subscriptions to this pool controller. Each change in the status of the pool
     * will be forwarded to each subscriber. The subscriptions are implemented atop unbounded
     * blocking queues, so that each subscriber can consume the notifications at any rate without
     * interfering with other subscribers, or with our message bus monitor thread (which
     * translates bus messages into pool status change events).
     */
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    /**
     * Creates a {@code PoolController} for the pool system whose bus is on the named serial port.
     *
     * <p>Creating an instance of this class encapsulates an instance of {@link PentairBus},
     * which starts a daemon thread to monitor the bus for incoming traffic (and possibly a few
     * other daemon threads). Attempting to use multiple concurrent instances of this class atop
     * the same serial port, whether in a single process or multiple processes, will yield
     * undefined results.
     *
     * @throws IOException if attempting to create a {@code PentairBus} on the named port fails
     */
    public PoolController(String portName) throws IOException {
        bus = new PentairBus(portName);
        Thread busMonitor = new Thread(() -> {
            PentairBus.Subscription messages = bus.subscribe();

            while(true) {
                try {
                    Message message = messages.queue().poll(REACHABILITY_TIMEOUT, TimeUnit.SECONDS);
                    if (message == null)
                        processUnreachability();
                    else
                        processMessage(message);
                } catch (InterruptedException e) {
                    System.err.println(
                            "PoolController bus monitor ignoring unexpected thread interrupt");
                }
            }
        }, "us.bloch.pool.PoolController message bus monitor");
        busMonitor.setDaemon(true); // TODO: stpe isn't daemon, so this doesn't work anymore (???)
        busMonitor.start();
    }

    /**
     * Takes appropriate action when the bus monitor discovers that the pool controller is
     * unreachable. This consists of generating an appropriate UnreachablePoolStatus event unless
     * one has already been issued (we never issue two consecutive UnreachablePoolStatus events).
     */
    private void processUnreachability() {
        if (latestStatus instanceof UnreachablePoolStatus) // Last event was an unreachability event
            return;

        if (latestStatus == null) // We've never contacted the pool monitor
            generateEvent(new UnreachablePoolStatus());
        else // We were running along just fine, and the pool controller vanished
            generateEvent(new UnreachablePoolStatus(LocalDateTime.now().minusSeconds(REACHABILITY_TIMEOUT)));
    }

    /** Executor used to schedule clock synchronization events and such. */
    final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    /**
     * Processes the given message which was received on the Pentair bus. If the message
     * represents a nontrivial state change, generates an appropriate pool status update event.
     */
    private void processMessage(Message msg) {
        boolean statusChanged = false;
        if (msg instanceof SystemStatus) {
            statusChanged = processSystemStatusUpdate((SystemStatus) msg);
            attemptToSendMessage(HeatStatusQuery.INSTANCE);  // Daisy-chain reqs to avoid collisions
        } else if (msg instanceof HeatStatus) {
            statusChanged = processHeatStatusUpdate((HeatStatus) msg);
            attemptToSendMessage(PumpStatusRequest.INSTANCE);// Daisy-chain reqs to avoid collisions
        } else if (msg instanceof PumpStatus) {
            statusChanged = processPumpStatusUpdate((PumpStatus) msg);
        }

        // If msg indicated a status change and we've received initial system and heat status...
        if (statusChanged && (activeFeatures != null) && (poolHeatSource != null)) {
            // ...generate an appropriate status change event
            if (activeBody == null) {
                generateEvent(new InactivePoolStatus(time, activeFeatures, airTemp,
                        poolSeekTemp, spaSeekTemp, poolHeatSource, spaHeatSource));
            } else {
                generateEvent(new ActivePoolStatus(time, activeFeatures, activeBody,
                        airTemp, waterTemp, pumpSpeedInRpm, pumpPowerInWatts,
                        poolSeekTemp, spaSeekTemp, poolHeatSource, spaHeatSource));
            }
        }
    }

    /**
     * Processes the given system status update and returns true if the update represents a state
     * change.
     * */
    private boolean processSystemStatusUpdate(SystemStatus statusUpdate) {
        int oldAirTemp = airTemp;
        int oldWaterTemp = waterTemp;
        Body oldActiveBody = activeBody;
        Set<Feature> oldActiveFeatures = activeFeatures;

        time = statusUpdate.time;
        airTemp = statusUpdate.airTemp;
        waterTemp = statusUpdate.waterTemp;
        activeBody = statusUpdate.enabledCircuits.contains(Circuit.SPA) ? Body.SPA
                : (statusUpdate.enabledCircuits.contains(Circuit.POOL) ? Body.POOL : null);
        activeFeatures = features(statusUpdate.enabledCircuits, statusUpdate.heaterOn);

        // todo This looks awfully complicate. Do we need the activeBody check and final disjunct?
        return airTemp != oldAirTemp || activeBody != oldActiveBody  || waterTemp != oldWaterTemp
                || activeBody == null && activeFeatures.contains(Feature.LIGHT) != oldActiveFeatures.contains(Feature.LIGHT)
                || activeBody != null && (!activeFeatures.equals(oldActiveFeatures));
    }

    //** Processes the given pump status update. Returns true if it represents a state change. */
    private boolean processPumpStatusUpdate(PumpStatus statusUpdate) {
        int oldPumpSpeedInRpm = pumpSpeedInRpm;
        int oldPumpPowerInWatts = pumpPowerInWatts;

        pumpSpeedInRpm = statusUpdate.speedInRpm;
        pumpPowerInWatts = statusUpdate.powerInWatts;

        return pumpSpeedInRpm != oldPumpSpeedInRpm || pumpPowerInWatts != oldPumpPowerInWatts;
    }

    //** Processes the given heat status update. Returns true if it represents a state change. */
    private boolean processHeatStatusUpdate(HeatStatus statusUpdate) {
        int oldPoolSeekTemp = poolSeekTemp;
        int oldSpaSeekTemp = spaSeekTemp;
        HeatSource oldPoolHeatSource = poolHeatSource;
        HeatSource oldSpaHeatSource = spaHeatSource;

        poolSeekTemp = statusUpdate.poolSeekTemp;
        spaSeekTemp = statusUpdate.spaSeekTemp;
        poolHeatSource = HeatSource.from(statusUpdate.poolHeatSource);
        spaHeatSource = HeatSource.from(statusUpdate.spaHeatSource);

        return poolSeekTemp != oldPoolSeekTemp || spaSeekTemp != oldSpaSeekTemp
                || poolHeatSource != oldPoolHeatSource || spaHeatSource != oldSpaHeatSource;
    }

    /** The most recent pool status event generated by this PoolController. Protected by latestStatusLock. */
    private volatile PoolStatus latestStatus;
    private final Object latestStatusLock = new Object();

    /** Generate a pool status update event for the given pool status. */
    private void generateEvent(PoolStatus poolStatus) {
        // Record the new status and wake any waiters (it's very rare that there are any)
        synchronized (latestStatusLock) {
            latestStatus = poolStatus;
            latestStatusLock.notifyAll();
        }

        for (Subscription subscription : subscriptions)
            subscription.updateQueue.add(poolStatus);
    }

    /**
     * Returns most recent status event generated by this {@link PoolController}.  If this method
     * is called before a status has come in, it waits until one has. Typically this will be less
     * than three seconds. If the pool controller hardware is broken or missing, it could take up
     * to ten seconds.
     */
    public PoolStatus status() {
        PoolStatus result = latestStatus;
        if (result == null) {
            synchronized (latestStatusLock) {
                while ((result = latestStatus) == null) {
                    try {
                        latestStatusLock.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns a subscription, which encapsulates a blocking queue to which all pool status
     * updates will be delivered as they become available. If you prefer to provide a listener
     * and receive callbacks, you can use {@link #addStatusListener(StatusListener)} instead.
     *
     * <p>Note that {@code Subscription} implements {@code AutoCloseable}. It is your
     * responsibility to close the subscription when you're done with it. Failure to do so will
     * consume CPU time and memory.
     */
    public Subscription subscribe() {
        return new Subscription();
    }

    /**
     * A subscription to receive updates to this pool system's status, via a blocking queue. To
     * subscribe, use the {@link #subscribe()} method. Note that you must close the subscription
     * when you no longer need it to avoid wasting CPU and memory resources.
     */
    public class Subscription implements AutoCloseable {
        final BlockingQueue<PoolStatus> updateQueue = new LinkedBlockingQueue<>();

        private Subscription() {
            subscriptions.add(this);
        }

        /**
         * Returns the blocking queue associated with this subscription. All pool status updates
         * will be posted to this queue until the subscription is closed.
         */
        public BlockingQueue<PoolStatus> queue() {
            return updateQueue;
        }

        /**
         * Terminates the subscription. Once terminated, no further status updates will be
         * delivered to the queue.
         */
        public void close() {
            subscriptions.remove(this);
        }
    }

    /**
     * Convenience method to deliver pool status updates via a listener. A call to this method
     * starts a dedicated thread to deliver updates to the given listener.
     *
     * <p>todo Harden this (i.e., provide a method to remove the listener and let the thread die) or eliminate it.
     */
    public void addStatusListener(StatusListener listener) {
        Subscription updates = subscribe();
        new Thread(() -> {
            while(true) {
                try {
                    listener.statusChange(updates.updateQueue.take());
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
        }).start();
    }

    /** Status update listener for use with {@link #addStatusListener(StatusListener)} method. */
    @FunctionalInterface public interface StatusListener {
        void statusChange(PoolStatus status);
    }

    /** Attempt to send the given message. If an IOException results, generate an error message. */
    private void attemptToSendMessage(Message msg) {
        try {
            bus.putMessage(msg);
        } catch (Exception e) {
            System.err.printf("Error %s occurred when attempting to send message: %s%n", e, msg);
        }
    }

    /*
     * Methods for manipulating the pool state.
     */

    /**
     * Sets the specified feature to the specified power state. Attempting to turn on the jets
     * when pool and spa are off has no effect. The same is true for attempting to turn on heat
     * boost when the heat is off.
     *
     * @throws IllegalArgumentException if feature is HEATER (whose state can't be directly set)
     * @throws NullPointerException if feature or state is null
     * @throws IOException if we can't communicate with the pool controller
     */
    public void setFeatureState(Feature feature, PowerState state) throws IOException {
        if (feature == Feature.HEATER)
            throw new IllegalArgumentException("HEATER power state cannot be set directly.");

        if (feature == Feature.JETS && state == PowerState.ON && activeBody == null)
            return;

        setCircuitState(feature.circuit, state.circuitState);
    }

    /**
     * Sets the specified body to the specified power state. The controller maintains independent
     * power states for the pool and the spa, and prioritizes the spa state over the pool state:
     * if both are ON, the spa runs and the pool does not. This class tries to hide this: if the
     * caller turns on the pool, this method will also turn off the spa, ensuring that the pool
     * will actually be turned on.
     *
     * @throws NullPointerException if feature or state is null
     * @throws IOException if IO exception encountered when trying to write to the Pentair bus
     */
    public void setBodyState(Body body, PowerState state) throws IOException {
        // Leaving jets (virtual circuit with high pump speed) on leaves pump on even if pool & spa are off
        if (state == PowerState.OFF && activeFeatures.contains(Feature.JETS))
            setFeatureState(Feature.JETS, PowerState.OFF);

        setCircuitState(body.circuit, state.circuitState);

        // Turning pool on when spa is on will have no effect unless spa is turned off (as spa takes precedence)
        if (body == Body.POOL && state == PowerState.ON)
            setCircuitState(Circuit.SPA, CircuitPowerState.OFF);
    }

    /**
     * Sets the specified Pentair bus circuit to the specified state.
     */
    private void setCircuitState(Circuit circuit, CircuitPowerState state) throws IOException {
       rpc(new CircuitStateChangeRequest(circuit, state), StateChangeResponse.class);
    }

    /**
     * Sets the specified body's seek temperature to the specified temperature.
     *
     * @throws NullPointerException if body is null
     * @throws IOException  if we can't communicate with the pool controller
     */
    public void setSeekTemp(Body body, int seekTemp) throws NullPointerException, IOException {
        HeatStatus heatStatus = refreshHeatStatus();
        int poolSeekTemp = heatStatus.poolSeekTemp;
        int spaSeekTemp = heatStatus.spaSeekTemp;

        switch (body) {
            case POOL -> poolSeekTemp = seekTemp;
            case SPA  -> spaSeekTemp  = seekTemp;
        }

        setHeatConfiguration(poolSeekTemp, spaSeekTemp,
                heatStatus.poolHeatSource, heatStatus.spaHeatSource);
    }

    /**
     * Sets the specified body's heat source to the specified source.
     *
     * @throws NullPointerException if body or heat source is null
     * @throws IOException if we can't communicate with the pool controller
     */
    public void setHeatSource(Body body, HeatSource source) throws IOException {
        HeatStatus heatStatus = refreshHeatStatus();
        PentairBus.HeatSource poolHeatSource = heatStatus.poolHeatSource;
        PentairBus.HeatSource spaHeatSource = heatStatus.spaHeatSource;

        switch (body) {
            case POOL -> poolHeatSource = source.heatSource;
            case SPA ->  spaHeatSource  = source.heatSource;
        }

        setHeatConfiguration(heatStatus.poolSeekTemp, heatStatus.spaSeekTemp,
                poolHeatSource, spaHeatSource);
    }

    /**
     * Sets the specified Pentair circuit to the specified state. This method will send a request
     * to the pool controller as many times as necessary to achieve the desired result.
     * Typically a single request will suffice, but repeated requests will be necessary if a
     * collision occurs on the RS-485 bus.
     *
     * @throws NullPointerException if feature or state is null
     * @throws IOException if we can't communicate with the pool controller
     */
    private void setHeatConfiguration(int poolSeekTemp, int spaSeekTemp,
            PentairBus.HeatSource poolSource, PentairBus.HeatSource spaSource) throws IOException {
        rpc(new HeatConfigurationChangeRequest(poolSeekTemp, spaSeekTemp, poolSource, spaSource),
                StateChangeResponse.class);

        // Attempt to propagate change to message processing thread to generate prompt status updates
        attemptToSendMessage(HeatStatusQuery.INSTANCE);
    }

    /**
     * Requests a heat status message from the controller and returns the resulting heat status.
     *
     * @throws IOException if we can't communicate with the pool controller
     */
    private HeatStatus refreshHeatStatus() throws IOException {
        return rpc(HeatStatusQuery.INSTANCE, HeatStatus.class);
    }

    /**
     * Puts the given request message on the bus, and waits for a response of the given type.
     * Retries as necessary. todo add timeout version, perhaps backoff, etc. (Whatever it takes
     * to make this work well.)
     *
     * @param request the request message
     * @param responseType the class object for the response type
     * @param <T> the response type
     * @return the response
     */
    private <T extends Message> T rpc(Message request, Class<T> responseType) throws IOException {
        Message response;
        do {
            bus.putMessage(request);
            response = bus.getMessage();

            if (!responseType.isInstance(response))
                System.err.printf("Sent RPC %s; Got invalid response %s%n", request, response);
        } while(!responseType.isInstance(response));

        return responseType.cast(response);
    }

    /**
     * Sets the pool controller's real-time clock to the current time (as returned by
     * {@link LocalDateTime#now()}), and resynchronizes periodically to maintain synchronization
     * (and adapt to daylight savings time).
     *
     * @throws IOException if we can't communicate with the pool controller
     */
    public void synchronizeClock() throws IOException {
        rpc(new ClockChangeRequest(LocalDateTime.now()), StateChangeResponse.class);

        executor.scheduleAtFixedRate(() -> {
            try {
                rpc(new ClockChangeRequest(LocalDateTime.now()), StateChangeResponse.class);
            } catch (Exception e) {
                System.err.println("Could not synchronize clocks: " + e);
            }
        }, 1, 1, TimeUnit.HOURS);
    }

    // Utility methods

    /** A de facto constant representing all of the "features" controlled by the pool controller. */
    private static final Set<Feature> ALL_FEATURES = EnumSet.allOf(Feature.class);

    /**
     * Returns a set consisting of the features corresponding to the circuits in the given circuit
     * set, in conjunction with the given heater status.
     *
     * @param circuits the circuits to be translated into features
     * @param heaterOn the heater status to be translated into a feature (true is on, false is off)
     * @return the features represented by the input parameters.
     */
    private static Set<Feature> features(Set<Circuit> circuits, boolean heaterOn) {
        Set<Feature> result = EnumSet.noneOf(Feature.class);

        for (Feature f : ALL_FEATURES)
            if (circuits.contains(f.circuit))
                result.add(f);

        if (heaterOn)
            result.add(Feature.HEATER);

        return result;
    }
}
