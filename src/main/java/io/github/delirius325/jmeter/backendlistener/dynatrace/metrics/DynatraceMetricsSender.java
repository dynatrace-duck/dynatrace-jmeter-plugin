package io.github.delirius325.jmeter.backendlistener.dynatrace.metrics;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Sends MINT percentile metrics to Dynatrace via the Metrics v2 Ingest API.
 * Each percentile (p50, p90, p95, p99) for each transaction/request is sent as
 * a separate metric data point.
 */
public class DynatraceMetricsSender implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(DynatraceMetricsSender.class);

    private static final String METRIC_PREFIX = "jmeter.mint.";
    private static final long TIMEOUT_MS = 10000L;

    private final String dtMetricsUrl;
    private final String apiToken;
    private final CloseableHttpClient httpClient;

    public DynatraceMetricsSender(String dtEnvironmentUrl, String apiToken, int timeoutMs) {
        this.dtMetricsUrl = buildMetricsUrl(dtEnvironmentUrl);
        this.apiToken = apiToken;

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setSocketTimeout(timeoutMs > 0 ? timeoutMs : (int) TIMEOUT_MS)
                .build();
        this.httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .build();

        logger.info("Dynatrace Metrics Sender initialized with URL: {}", this.dtMetricsUrl);
    }

    /**
     * Constructs the Metrics v2 Ingest API URL from the environment URL.
     * Converts log ingest URL to metrics ingest URL.
     */
    private static String buildMetricsUrl(String environmentUrl) {
        if (environmentUrl == null || environmentUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Environment URL cannot be null or empty");
        }
        
        // Replace log ingest path with metrics ingest path
        String url = environmentUrl.trim();
        if (url.contains("/api/v2/logs/ingest")) {
            return url.replace("/api/v2/logs/ingest", "/api/v2/metrics/ingest");
        }
        
        // If not a log URL, assume it's a base URL and append metrics path
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url + "api/v2/metrics/ingest";
    }

    /**
     * Sends MINT percentile metrics for a transaction.
     * Sends 4 separate metric data points (p50, p90, p95, p99).
     *
     * @param transactionName name of the transaction
     * @param percentiles the MINT percentiles object
     * @throws IOException if sending fails
     */
    public void sendTransactionMetrics(String transactionName, PercentileCalculator.MintPercentiles percentiles)
            throws IOException {
        if (percentiles == null || percentiles.p50 == -1) {
            logger.debug("No valid percentile data to send for transaction: {}", transactionName);
            return;
        }

        List<MetricDataPoint> dataPoints = new ArrayList<>();
        long timestamp = System.currentTimeMillis();

        dataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p50", percentiles.p50, transactionName, timestamp));
        dataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p90", percentiles.p90, transactionName, timestamp));
        dataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p95", percentiles.p95, transactionName, timestamp));
        dataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p99", percentiles.p99, transactionName, timestamp));

        sendMetricsPayload(dataPoints);
    }

    /**
     * Sends MINT percentile metrics for a request.
     * Sends 4 separate metric data points (p50, p90, p95, p99).
     *
     * @param requestName name of the request
     * @param percentiles the MINT percentiles object
     * @throws IOException if sending fails
     */
    public void sendRequestMetrics(String requestName, PercentileCalculator.MintPercentiles percentiles)
            throws IOException {
        if (percentiles == null || percentiles.p50 == -1) {
            logger.debug("No valid percentile data to send for request: {}", requestName);
            return;
        }

        List<MetricDataPoint> dataPoints = new ArrayList<>();
        long timestamp = System.currentTimeMillis();

        dataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p50", percentiles.p50, requestName, timestamp));
        dataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p90", percentiles.p90, requestName, timestamp));
        dataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p95", percentiles.p95, requestName, timestamp));
        dataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p99", percentiles.p99, requestName, timestamp));

        sendMetricsPayload(dataPoints);
    }

    /**
     * Sends all transaction and request metrics from the collector.
     * Batch sends all metrics at once.
     *
     * @param collector the MintMetricsCollector containing all metrics
     * @throws IOException if sending fails
     */
    public void sendAllMetrics(MintMetricsCollector collector) throws IOException {
        List<MetricDataPoint> allDataPoints = new ArrayList<>();
        long timestamp = System.currentTimeMillis();

        // Add transaction metrics
        Map<String, PercentileCalculator.MintPercentiles> transactionMetrics = 
                collector.getAllTransactionPercentiles();
        for (Map.Entry<String, PercentileCalculator.MintPercentiles> entry : transactionMetrics.entrySet()) {
            PercentileCalculator.MintPercentiles p = entry.getValue();
            if (p.p50 != -1) {
                allDataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p50", p.p50, entry.getKey(), timestamp));
                allDataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p90", p.p90, entry.getKey(), timestamp));
                allDataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p95", p.p95, entry.getKey(), timestamp));
                allDataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p99", p.p99, entry.getKey(), timestamp));
            }
        }

        // Add request metrics
        Map<String, PercentileCalculator.MintPercentiles> requestMetrics = 
                collector.getAllRequestPercentiles();
        for (Map.Entry<String, PercentileCalculator.MintPercentiles> entry : requestMetrics.entrySet()) {
            PercentileCalculator.MintPercentiles p = entry.getValue();
            if (p.p50 != -1) {
                allDataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p50", p.p50, entry.getKey(), timestamp));
                allDataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p90", p.p90, entry.getKey(), timestamp));
                allDataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p95", p.p95, entry.getKey(), timestamp));
                allDataPoints.add(new MetricDataPoint(METRIC_PREFIX + "p99", p.p99, entry.getKey(), timestamp));
            }
        }

        if (!allDataPoints.isEmpty()) {
            sendMetricsPayload(allDataPoints);
        }
    }

    /**
     * Sends the metrics payload to Dynatrace Metrics v2 Ingest API.
     */
    private void sendMetricsPayload(List<MetricDataPoint> dataPoints) throws IOException {
        if (dataPoints.isEmpty()) {
            return;
        }

        // Build metrics ingest payload format
        Map<String, Object> payload = new HashMap<>();
        payload.put("series", dataPoints);

        String jsonPayload = new Gson().toJson(payload);
        HttpPost post = new HttpPost(this.dtMetricsUrl);
        post.setHeader("Authorization", "Api-Token " + this.apiToken);
        post.setHeader("Content-Type", "application/json; charset=utf-8");
        post.setEntity(new StringEntity(jsonPayload, StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = this.httpClient.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_ACCEPTED) {
                logger.debug("Dynatrace Metrics API successfully ingested {} metric data points.", dataPoints.size());
            } else {
                logger.error("Dynatrace Metrics API failed to ingest metrics. Response status: {}",
                        response.getStatusLine().toString());
            }
        } catch (Exception e) {
            logger.error("Error sending metrics to Dynatrace", e);
            throw new IOException("Failed to send metrics to Dynatrace", e);
        }
    }

    @Override
    public void close() throws IOException {
        this.httpClient.close();
    }

    /**
     * Inner class representing a single metric data point for the Dynatrace Metrics v2 API.
     */
    public static class MetricDataPoint {
        public String metric;
        public List<Object> points;
        public Map<String, String> dimensions;

        public MetricDataPoint(String metricName, long value, String label, long timestamp) {
            this.metric = metricName;
            this.points = new ArrayList<>();
            this.points.add(new Object[]{timestamp, value});
            this.dimensions = new HashMap<>();
            this.dimensions.put("jmeter.sample", label);
        }
    }
}
