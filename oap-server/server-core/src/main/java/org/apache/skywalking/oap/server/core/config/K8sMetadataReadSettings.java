/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.oap.server.core.config;

import java.time.Duration;

/**
 * The core settings of the Kubernetes metadata reads, which the readers, in the receivers and the meter analyzer,
 * get from here: the core module that loads them cannot reach the readers. The defaults are those of
 * {@code application.yml}, for a file written before a setting was added.
 */
public final class K8sMetadataReadSettings {
    private static final int NO_LIMIT = -1;
    private static final int DEFAULT_CACHE_TIME_MINUTES = 21;

    private static volatile long MAL_READ_TIMEOUT_MILLIS = 2000;
    private static volatile int ALS_READ_QUEUE_SIZE = NO_LIMIT;
    private static volatile int MAL_READ_QUEUE_SIZE = NO_LIMIT;
    private static volatile Duration ALS_CACHE_TIME = Duration.ofMinutes(DEFAULT_CACHE_TIME_MINUTES);
    private static volatile Duration MAL_CACHE_TIME = Duration.ofMinutes(DEFAULT_CACHE_TIME_MINUTES);
    private static volatile Duration REFRESH_PERIOD = Duration.ofMinutes(3);
    private static volatile Duration NOT_FOUND_RETRY_PERIOD = Duration.ofMinutes(1);

    private K8sMetadataReadSettings() {
    }

    /**
     * @return how long one MAL lookup step, such as one {@code retagByK8sMeta} over the samples of a metric, waits in
     * all for metadata not read yet, from {@code core.default.malMetadataReadTimeout}
     */
    public static long getMalReadTimeoutMillis() {
        return MAL_READ_TIMEOUT_MILLIS;
    }

    /**
     * @return the most reads waiting at once for the lookups that do not wait (Envoy ALS, the eBPF access log) and
     * for every read again, or a negative number for no limit: {@code als} of
     * {@code core.default.k8sMetadataReadQueueSize}
     */
    public static int getAlsReadQueueSize() {
        return ALS_READ_QUEUE_SIZE;
    }

    /**
     * @return the most reads waiting at once for the MAL lookups, which wait, or a negative number for no limit:
     * {@code mal} of {@code core.default.k8sMetadataReadQueueSize}
     */
    public static int getMalReadQueueSize() {
        return MAL_READ_QUEUE_SIZE;
    }

    /**
     * @return how long after it was last written an entry of the Envoy ALS caches expires: {@code als} of
     * {@code core.default.k8sMetadataCacheTime}
     */
    public static Duration getAlsCacheTime() {
        return ALS_CACHE_TIME;
    }

    /**
     * @return how long after it was last written an entry of the caches of MAL and the eBPF access log expires:
     * {@code mal} of {@code core.default.k8sMetadataCacheTime}
     */
    public static Duration getMalCacheTime() {
        return MAL_CACHE_TIME;
    }

    /**
     * @return how long metadata is used before it is read again, and a failed read waits before it is retried, from
     * {@code core.default.k8sMetadataRefreshPeriod}
     */
    public static Duration getRefreshPeriod() {
        return REFRESH_PERIOD;
    }

    /**
     * @return how long finding nothing is used before the key is read again, from
     * {@code core.default.k8sMetadataNotFoundRetryPeriod}
     */
    public static Duration getNotFoundRetryPeriod() {
        return NOT_FOUND_RETRY_PERIOD;
    }

    /**
     * Checks the settings, then publishes them all.
     *
     * @param readQueueSizes             {@code als=<size>,mal=<size>}; either may be left out, for no limit
     * @param cacheTimes                 {@code als=<minutes>,mal=<minutes>}; either may be left out, for 21 minutes;
     *                                   each longer than the refresh period
     * @param refreshPeriodMinutes       positive
     * @param notFoundRetryPeriodMinutes positive
     * @throws IllegalArgumentException when a setting is not in its form
     */
    public static void set(final long malReadTimeoutMillis, final Object readQueueSizes, final Object cacheTimes,
                           final int refreshPeriodMinutes, final int notFoundRetryPeriodMinutes) {
        if (malReadTimeoutMillis < 0) {
            throw new IllegalArgumentException(
                "core.default.malMetadataReadTimeout must not be negative, but is: " + malReadTimeoutMillis);
        }
        if (refreshPeriodMinutes <= 0) {
            throw new IllegalArgumentException(
                "core.default.k8sMetadataRefreshPeriod must be positive, but is: " + refreshPeriodMinutes);
        }
        if (notFoundRetryPeriodMinutes <= 0) {
            throw new IllegalArgumentException(
                "core.default.k8sMetadataNotFoundRetryPeriod must be positive, but is: " + notFoundRetryPeriodMinutes);
        }
        final int[] sizes = parseReadQueueSizes(readQueueSizes);
        final int[] cacheMinutes = parseCacheTimes(cacheTimes, refreshPeriodMinutes);
        MAL_READ_TIMEOUT_MILLIS = malReadTimeoutMillis;
        ALS_READ_QUEUE_SIZE = sizes[0];
        MAL_READ_QUEUE_SIZE = sizes[1];
        ALS_CACHE_TIME = Duration.ofMinutes(cacheMinutes[0]);
        MAL_CACHE_TIME = Duration.ofMinutes(cacheMinutes[1]);
        REFRESH_PERIOD = Duration.ofMinutes(refreshPeriodMinutes);
        NOT_FOUND_RETRY_PERIOD = Duration.ofMinutes(notFoundRetryPeriodMinutes);
    }

    /**
     * @return the {@code als} and {@code mal} sizes, {@link #NO_LIMIT} for one left out
     */
    static int[] parseReadQueueSizes(final Object readQueueSizes) {
        return parseLanes(
            readQueueSizes, NO_LIMIT,
            "core.default.k8sMetadataReadQueueSize must be als=<size>,mal=<size>, either may be left out, -1 for no "
                + "limit, but is: "
        );
    }

    /**
     * @return the {@code als} and {@code mal} minutes, 21 for one left out
     */
    static int[] parseCacheTimes(final Object cacheTimes, final int refreshPeriodMinutes) {
        final int[] minutes = parseLanes(
            cacheTimes, DEFAULT_CACHE_TIME_MINUTES,
            "core.default.k8sMetadataCacheTime must be als=<minutes>,mal=<minutes>, either may be left out for "
                + DEFAULT_CACHE_TIME_MINUTES + ", but is: "
        );
        for (final int lane : minutes) {
            if (lane <= refreshPeriodMinutes) {
                throw new IllegalArgumentException(
                    "core.default.k8sMetadataCacheTime must be longer than core.default.k8sMetadataRefreshPeriod ("
                        + refreshPeriodMinutes + " minutes) for each lane, but is: " + cacheTimes);
            }
        }
        return minutes;
    }

    /**
     * @param value          {@code als=<int>,mal=<int>}. Not a String when the config loader read a bare number, such
     *                       as {@code 1000}, which is refused as any value without lanes is.
     * @param invalidMessage the message refusing {@code value}, which is appended to it
     */
    private static int[] parseLanes(final Object value, final int leftOut, final String invalidMessage) {
        final int[] lanes = {leftOut, leftOut};
        final String text = value == null ? "" : String.valueOf(value);
        if (text.trim().isEmpty()) {
            return lanes;
        }
        final boolean[] given = new boolean[2];
        for (final String part : text.split(",", -1)) {
            final int equals = part.indexOf('=');
            if (equals < 0) {
                throw new IllegalArgumentException(invalidMessage + text);
            }
            final int lane;
            switch (part.substring(0, equals).trim()) {
                case "als":
                    lane = 0;
                    break;
                case "mal":
                    lane = 1;
                    break;
                default:
                    throw new IllegalArgumentException(invalidMessage + text);
            }
            if (given[lane]) {
                throw new IllegalArgumentException(invalidMessage + text);
            }
            given[lane] = true;
            try {
                lanes[lane] = Integer.parseInt(part.substring(equals + 1).trim());
            } catch (final NumberFormatException e) {
                throw new IllegalArgumentException(invalidMessage + text);
            }
        }
        return lanes;
    }
}
