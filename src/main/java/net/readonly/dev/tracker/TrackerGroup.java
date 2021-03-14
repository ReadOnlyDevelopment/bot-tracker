package net.readonly.dev.tracker;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.readonly.dev.tracker.ringbuffer.IntRingBuffer;
import net.readonly.dev.tracker.ringbuffer.RingBuffer;

/**
 * Represents a group of {@link Tracker}'s, and handles updating their usage Instances.
 *
 * @param <K> The type of the key used to identify each tracker.
 */
public class TrackerGroup<K> {
    protected final ConcurrentHashMap<K, Tracker<K>> map = new ConcurrentHashMap<>();
    protected final ScheduledExecutorService executor;
    protected final boolean recursiveIncrements;

    /**
     * Creates a new {@link TrackerGroup} with a given executor.
     *
     * @param executor Executor used to schedule updates. Cannot be null.
     * @param recursiveIncrements Whether or not to recursively increment a tracker's parents.
     */
    public TrackerGroup(@Nonnull ScheduledExecutorService executor, boolean recursiveIncrements) {
        this.executor = Objects.requireNonNull(executor, "Executor may not be null");
        this.recursiveIncrements = recursiveIncrements;
        executor.scheduleAtFixedRate(()->map.values().forEach(Tracker::rollSecond), 1, 1, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(()->map.values().forEach(Tracker::rollMinute), 1, 1, TimeUnit.MINUTES);
        executor.scheduleAtFixedRate(()->map.values().forEach(Tracker::rollHour), 1, 1, TimeUnit.HOURS);
    }

    /**
     * Creates a new {@link TrackerGroup} with a given executor.
     *
     * @param executor Executor used to schedule updates. Cannot be null.
     */
    public TrackerGroup(@Nonnull ScheduledExecutorService executor) {
        this(executor, false);
    }

    /**
     * Creates a new {@link TrackerGroup}, with a single threaded executor and a given thread factory.
     *
     * @param factory Factory used to create the executor thread.
     * @param recursiveIncrements Whether or not to recursively increment a tracker's parents.
     */
    public TrackerGroup(@Nonnull ThreadFactory factory, boolean recursiveIncrements) {
        this(Executors.newSingleThreadScheduledExecutor(factory), recursiveIncrements);
    }

    /**
     * Creates a new {@link TrackerGroup}, with a single threaded executor and a given thread factory.
     *
     * @param factory Factory used to create the executor thread.
     */
    public TrackerGroup(@Nonnull ThreadFactory factory) {
        this(Executors.newSingleThreadScheduledExecutor(factory), false);
    }

    /**
     * Creates a new {@link TrackerGroup}, with a single threaded daemon executor.
     *
     * @param recursiveIncrements Whether or not to recursively increment a tracker's parents.
     */
    public TrackerGroup(boolean recursiveIncrements) {
        this(r->{
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("TrackerGroup-Updater");
            return t;
        }, recursiveIncrements);
    }

    /**
     * Creates a new {@link TrackerGroup}, with a single threaded daemon executor.
     */
    public TrackerGroup() {
        this(r->{
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("TrackerGroup-Updater");
            return t;
        }, false);
    }

    /**
     * Returns the executor used to schedule updates to trackers.
     *
     * @return The executor used.
     */
    @Nonnull
    public ScheduledExecutorService getExecutor() {
        return executor;
    }

    /**
     * Returns the {@link Tracker} for the given key, creating a new one if needed.
     *
     * @param key The identifier of the wanted {@link Tracker}. Cannot be null.
     *
     * @return The tracker for this key. Never null.
     */
    @Nonnull
    public Tracker<K> tracker(@Nonnull K key) {
        return map.computeIfAbsent(key, k -> createTracker(null, k));
    }

    /**
     * Removes a {@link Tracker} from this group. This does the same as {@code trackers().remove(key)}.
     *
     * @param key The identifier of the {@link Tracker} to be removed.
     *
     * @return The removed {@link Tracker}, or null if there wasn't one for this key.
     *
     * @see #trackers()
     */
    @Nullable
    public Tracker<K> remove(@Nonnull K key) {
        return map.remove(key);
    }

    /**
     * Returns the map of the existing {@link Tracker}'s. Modifications made to this map will have effect on this group.
     *
     * @return The map used to store {@link Tracker}'s. Never null.
     */
    @Nonnull
    public Map<K, Tracker<K>> trackers() {
        return map;
    }

    /**
     * Returns the trackers with the highest uses in the given Instance.
     * <br>This is equivalent to {@code Instance.highest(trackers().values().stream(), amount)}
     *
     * @param Instance The Instance to sort trackers.
     * @param amount The maximum amount of results.
     *
     * @return The highest trackers in the Instance.
     */
    public Stream<Tracker<K>> highest(Instance Instance, int amount) {
        return Instance.highest(trackers().values().stream(), amount);
    }

    /**
     * Returns the trackers with the lowest uses in the given Instance.
     * <br>This is equivalent to {@code Instance.lowest(trackers().values().stream(), amount)}
     *
     * @param Instance The Instance to sort trackers.
     * @param amount The maximum amount of results.
     *
     * @return The lowest trackers in the Instance.
     */
    public Stream<Tracker<K>> lowest(Instance Instance, int amount) {
        return Instance.lowest(trackers().values().stream(), amount);
    }

    /**
     * Returns the sum of all usages in the given Instance.
     * <br>This is equivalent to {@code trackers().values().stream().mapToLong(Instance::amount).sum()}
     *
     * @param Instance The Instance of the wanted total.
     *
     * @return The sum of the usages in all trackers for this Instance.
     */
    public long total(Instance Instance) {
        return trackers().values().stream().mapToLong(Instance::amount).sum();
    }

    /**
     * Creates a new tracker for the given key.
     *
     * @param parent Parent for the new tracker.
     * @param key Key for the new tracker.
     *
     * @return A new tracker.
     *
     * @implNote This method does not register the tracker, so it should not be used
     * directly. Use {@link #tracker(Object) tracker(K)} instead. This method is
     * available so subclasses can provide a different tracker implementation.
     */
    public Tracker<K> createTracker(Tracker<K> parent, K key) {
        return new Tracker<>(this, parent, key, recursiveIncrements);
    }

    /**
     * Creates a new ring buffer with a given size.
     *
     * @param size Size for the new buffer.
     *
     * @return A new ring buffer.
     *
     * @implNote  This method should not be used directly, it's available so
     * subclasses can provide a different buffer implementation.
     */
    public RingBuffer createRingBuffer(int size) {
        return new IntRingBuffer(size);
    }
}
