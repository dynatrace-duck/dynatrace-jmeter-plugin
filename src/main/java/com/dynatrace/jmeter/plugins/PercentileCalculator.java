/**
 * Copyright 2018-2020 Dynatrace LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dynatrace.jmeter.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class to calculate percentiles from a list of response times.
 * Uses the nearest-rank method for percentile calculation.
 */
public class PercentileCalculator {

    /**
     * Calculate a specific percentile from a list of values.
     * Uses the "nearest rank" method.
     *
     * @param values list of values (must not be empty)
     * @param percentile the percentile to calculate (0-100)
     * @return the percentile value, or 0 if list is empty
     */
    public static long calculatePercentile(List<Long> values, double percentile) {
        if (values == null || values.isEmpty()) {
            return 0;
        }

        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("Percentile must be between 0 and 100");
        }

        // Sort the values
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        // Calculate the rank (position in sorted list)
        // Using nearest-rank method: rank = ceil(percentile/100 * n)
        int rank = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;

        // Ensure rank is within bounds
        rank = Math.max(0, Math.min(rank, sorted.size() - 1));

        return sorted.get(rank);
    }

    /**
     * Calculate p50 (median).
     */
    public static long calculateP50(List<Long> values) {
        return calculatePercentile(values, 50);
    }

    /**
     * Calculate p90.
     */
    public static long calculateP90(List<Long> values) {
        return calculatePercentile(values, 90);
    }

    /**
     * Calculate p95.
     */
    public static long calculateP95(List<Long> values) {
        return calculatePercentile(values, 95);
    }

    /**
     * Calculate p99.
     */
    public static long calculateP99(List<Long> values) {
        return calculatePercentile(values, 99);
    }

    /**
     * Calculate throughput (requests per second).
     *
     * @param requestCount total number of requests
     * @param intervalSeconds duration of the interval in seconds
     * @return requests per second
     */
    public static double calculateThroughput(long requestCount, long intervalSeconds) {
        if (intervalSeconds <= 0) {
            return 0;
        }
        return (double) requestCount / intervalSeconds;
    }
}