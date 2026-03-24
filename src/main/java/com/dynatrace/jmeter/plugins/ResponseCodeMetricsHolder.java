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
import java.util.Objects;

/**
 * Holds metrics grouped by (responseCode, errorDescription) for a specific transaction.
 * Allows splitting metrics by response code and error description.
 */
public class ResponseCodeMetricsHolder {
    private String responseCode;
    private String errorDescription;
    private List<Long> responseTimes = new CopyOnWriteArrayList<>();
    private long requestCount = 0;
    private long errorCount = 0;
    private long sentBytes = 0;
    private long receivedBytes = 0;

    public ResponseCodeMetricsHolder(String responseCode, String errorDescription) {
        this.responseCode = responseCode != null ? responseCode : "unknown";
        this.errorDescription = errorDescription != null ? errorDescription : "";
    }

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
     * Increment the error count (for failed samples).
     */
    public void incrementErrorCount() {
        errorCount++;
    }

    /**
     * Add sent bytes.
     */
    public void addSentBytes(long bytes) {
        sentBytes += bytes;
    }

    /**
     * Add received bytes.
     */
    public void addReceivedBytes(long bytes) {
        receivedBytes += bytes;
    }

    /**
     * Get all response times collected for this response code/error combination.
     */
    public List<Long> getResponseTimes() {
        return new ArrayList<>(responseTimes);
    }

    /**
     * Get the total request count.
     */
    public long getRequestCount() {
        return requestCount;
    }

    /**
     * Get the total error count.
     */
    public long getErrorCount() {
        return errorCount;
    }

    /**
     * Get the response code.
     */
    public String getResponseCode() {
        return responseCode;
    }

    /**
     * Get the error description.
     */
    public String getErrorDescription() {
        return errorDescription;
    }

    /**
     * Get total sent bytes.
     */
    public long getSentBytes() {
        return sentBytes;
    }

    /**
     * Get total received bytes.
     */
    public long getReceivedBytes() {
        return receivedBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResponseCodeMetricsHolder that = (ResponseCodeMetricsHolder) o;
        return Objects.equals(responseCode, that.responseCode) &&
               Objects.equals(errorDescription, that.errorDescription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(responseCode, errorDescription);
    }

    @Override
    public String toString() {
        return "ResponseCodeMetricsHolder{" +
                "responseCode='" + responseCode + '\'' +
                ", errorDescription='" + errorDescription + '\'' +
                ", requestCount=" + requestCount +
                ", errorCount=" + errorCount +
                ", sentBytes=" + sentBytes +
                ", receivedBytes=" + receivedBytes +
                '}';
    }
}