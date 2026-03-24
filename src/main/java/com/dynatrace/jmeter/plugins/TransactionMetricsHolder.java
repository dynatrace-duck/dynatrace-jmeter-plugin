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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds response times and other data for a transaction to calculate percentiles and throughput.
 */
public class TransactionMetricsHolder {
    private List<Long> responseTimes = new CopyOnWriteArrayList<>();
    private long requestCount = 0;

    /**
     * Add a response time to the list.
     */
    public void addResponseTime(long responseTime) {
        responseTimes.add(responseTime);
    }

    /**
     * Increment the request count.
     */
    public void incrementRequestCount() {
        requestCount++;
    }

    /**
     * Get all response times collected in this interval.
     */
    public List<Long> getResponseTimes() {
        return new ArrayList<>(responseTimes);
    }

    /**
     * Get the total request count for this interval.
     */
    public long getRequestCount() {
        return requestCount;
    }

    /**
     * Clear collected data for the next interval.
     */
    public void reset() {
        responseTimes.clear();
        requestCount = 0;
    }
}