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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds metrics for all response codes within a specific transaction.
 * Maps (responseCode, errorDescription) to ResponseCodeMetricsHolder.
 */
public class TransactionResponseCodeMetrics {
    private Map<String, ResponseCodeMetricsHolder> metricsMap = new ConcurrentHashMap<>();

    /**
     * Get or create a holder for the given response code and error description.
     */
    public ResponseCodeMetricsHolder getOrCreate(String responseCode, String errorDescription) {
        String key = generateKey(responseCode, errorDescription);
        return metricsMap.computeIfAbsent(key, k -> new ResponseCodeMetricsHolder(responseCode, errorDescription));
    }

    /**
     * Get all metrics holders for this transaction.
     */
    public Collection<ResponseCodeMetricsHolder> getAllMetrics() {
        return metricsMap.values();
    }

    /**
     * Clear all metrics for the next interval.
     */
    public void reset() {
        metricsMap.clear();
    }

    /**
     * Generate a unique key for (responseCode, errorDescription) combination.
     */
    private String generateKey(String responseCode, String errorDescription) {
        return responseCode + "|" + (errorDescription != null ? errorDescription : "");
    }
}