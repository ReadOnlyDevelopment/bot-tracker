package net.readonly.dev.tracker;

import java.util.Comparator;
import java.util.function.ToLongFunction;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

/**
 * Default Instance implementations
 */
public enum DefaultInstance implements Instance {
    LAST_SECOND(Tracker::secondUsages),
    LAST_MINUTE(Tracker::minuteUsages),
    LAST_5_MINUTES(tracker -> tracker.hourBuffer().sumLast(4) + tracker.minuteUsages()),
    LAST_15_MINUTES(tracker -> tracker.hourBuffer().sumLast(14) + tracker.minuteUsages()),
    LAST_30_MINUTES(tracker -> tracker.hourBuffer().sumLast(29) + tracker.minuteUsages()),
    LAST_HOUR(Tracker::hourlyUsages),
    LAST_2_HOURS(tracker -> tracker.dayBuffer().sumLast(1) + tracker.hourlyUsages()),
    LAST_6_HOURS(tracker -> tracker.dayBuffer().sumLast(5) + tracker.hourlyUsages()),
    LAST_12_HOURS(tracker -> tracker.dayBuffer().sumLast(11) + tracker.hourlyUsages()),
    LAST_DAY(Tracker::dailyUsages),
    TOTAL(Tracker::totalUsages);

    private final ToLongFunction<Tracker<?>> amountFunction;
    private final Comparator<Tracker<?>> comparator;

    DefaultInstance(ToLongFunction<Tracker<?>> amountFunction) {
        this.amountFunction = amountFunction;
        this.comparator = Comparator.comparingLong(amountFunction);
    }

    @Override
    @Nonnull
    public Comparator<Tracker<?>> comparator() {
        return comparator;
    }

    @Override
    @Nonnegative
    public long amount(Tracker<?> tracker) {
        return amountFunction.applyAsLong(tracker);
    }
}
