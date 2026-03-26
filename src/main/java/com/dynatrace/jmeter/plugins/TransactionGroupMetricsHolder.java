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
 * Holds aggregated metrics for a Transaction Group (Transaction Controller).
 * Collects data from all child samples within the group.
 */
public class TransactionGroupMetricsHolder {
    private String groupName;
    private List<Long> elapsedTimes = new CopyOnWriteArrayList<>();
    private long totalCount = 0;
    private long totalErrors = 0;
    private long totalSentBytes = 0;
    private long totalReceivedBytes = 0;

    public TransactionGroupMetricsHolder(String groupName) {
        this.groupName = groupName;
    }

    /**
     * Add the elapsed time of the transaction group execution.
     */
    public void addElapsedTime(long time) {
        elapsedTimes.add(time);
    }

    /**
     * Add count of requests in this group.
     */
    public void addCount(long count) {
        totalCount += count;
    }

    /**
     * Add count of errors in this group.
     */
    public void addErrors(long errors) {
        totalErrors += errors;
    }

    /**
     * Add sent bytes from requests in this group.
     */
    public void addSentBytes(long bytes) {
        totalSentBytes += bytes;
    }

    /**
     * Add received bytes from requests in this group.
     */
    public void addReceivedBytes(long bytes) {
        totalReceivedBytes += bytes;
    }

    /**
     * Get all elapsed times collected for this transaction group.
     */
    public List<Long> getElapsedTimes() {
        return new ArrayList<>(elapsedTimes);
    }

    /**
     * Get the group name.
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * Get total request count.
     */
    public long getTotalCount() {
        return totalCount;
    }

    /**
     * Get total error count.
     */
    public long getTotalErrors() {
        return totalErrors;
    }

    /**
     * Get total sent bytes.
     */
    public long getTotalSentBytes() {
        return totalSentBytes;
    }

    /**
     * Get total received bytes.
     */
    public long getTotalReceivedBytes() {
        return totalReceivedBytes;
    }

    @Override
    public String toString() {
        return "TransactionGroupMetricsHolder{" +
                "groupName='" + groupName + '\'' +
                ", totalCount=" + totalCount +
                ", totalErrors=" + totalErrors +
                ", totalSentBytes=" + totalSentBytes +
                ", totalReceivedBytes=" + totalReceivedBytes +
                '}';
    }
}