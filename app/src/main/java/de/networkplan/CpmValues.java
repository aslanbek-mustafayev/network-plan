package de.networkplan;

/**
 * Schedule times and floats computed for a single activity.
 *
 * <p>All day values are one-based and inclusive: an activity with
 * {@code earliestStart == 1} and duration 10 has {@code earliestFinish == 10}.
 */
public record CpmValues(
        int earliestStart,
        int earliestFinish,
        int latestStart,
        int latestFinish,
        int totalFloat,
        int freeFloat,
        int conditionalFloat,
        int independentFloat) {
}
