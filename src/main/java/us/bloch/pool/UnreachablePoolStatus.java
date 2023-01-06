package us.bloch.pool;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * The status of an unreachable pool, i.e., one with which we are unable to communicate.
 * Typically you will get this status if a power failure has shut down the pool system.
 */
public class UnreachablePoolStatus extends PoolStatus {
    /** The time of last contact, or no value if we've never contacted the controller hardware. */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    final Optional<LocalDateTime> lastContact;

    /**
     * Creates an {@code UnreachablePoolStatus} instance with the given time of last contact,
     * which must be non-null. The parameterless constructor should be used if we have never been
     * able to contact the pool controller hardware.
     *
     * @throws NullPointerException if lastContact is null.
     */
    public UnreachablePoolStatus(LocalDateTime lastContact) {
        this.lastContact = Optional.of(Objects.requireNonNull(lastContact));
    }

    /**
     * Creates an {@code UnreachablePoolStatus} instance indicating that we have never been able
     * to contact the pool controller hardware.
     */
    public UnreachablePoolStatus() {
        this.lastContact = Optional.empty();
    }

    /** Returns The time of last contact, or empty if we've never contacted the controller. */
    public Optional<LocalDateTime> lastContact() {
        return lastContact;
    }

    /** Returns the string form of this pool status. */
    public String toString() {
        String result = "Unable to contact pool controller";
        if (lastContact.isPresent())
            result += " since " + lastContact.get();
        return result;
    }
}
