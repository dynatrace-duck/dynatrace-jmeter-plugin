package io.github.delirius325.jmeter.backendlistener.dynatrace;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.SamplerMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregates JMeter samples and periodically emits response-time percentile
 * gauges to the Dynatrace Metrics API.
 */
public final class DynatracePercentileMetricsExporter implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynatracePercentileMetricsExporter.class);

    private final Object metricLock = new Object();
    private final Map<String, SamplerMetric> samplers = new ConcurrentHashMap<>();
    private final DynatraceMetricsApiSender sender;
    private final ScheduledExecutorService scheduler;
    private final ScheduledFuture<?> scheduledFlush;
    private final List<Double> percentiles;
    private final Map<String, String> staticDimensions;
    private final String injectorHostname;

    public DynatracePercentileMetricsExporter(
            String metricsApiUrl,
            String metricsApiToken,
            int timeoutMs,
            long flushIntervalMs,
            String rawPercentiles,
            String rawStaticDimensions) {

        if (flushIntervalMs <= 0) {
            throw new IllegalArgumentException("Metric flush interval must be greater than zero");
        }

        this.percentiles = parsePercentiles(rawPercentiles);
        this.staticDimensions = DynatraceMetricLineBuilder.parseStaticDimensions(rawStaticDimensions);
        this.injectorHostname = solveHostname();
        this.sender = new DynatraceMetricsApiSender(metricsApiUrl, metricsApiToken, timeoutMs);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dynatrace-percentile-metrics-exporter");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduledFlush = this.scheduler.scheduleAtFixedRate(
                this::flushSafely,
                flushIntervalMs,
                flushIntervalMs,
                TimeUnit.MILLISECONDS);
    }

    public void record(SampleResult sampleResult) {
        if (sampleResult == null) {
            return;
        }

        String transaction = sampleResult.getSampleLabel();
        if (transaction == null || transaction.trim().isEmpty()) {
            transaction = "(unnamed)";
        }

        synchronized (this.metricLock) {
            this.samplers.computeIfAbsent(transaction, ignored -> new SamplerMetric()).add(sampleResult);
        }
    }

    /** Visible for tests and manual flushing at JMeter teardown. */
    synchronized void flush() throws IOException {
        List<String> lines = new ArrayList<>();
        long timestamp = System.currentTimeMillis();

        synchronized (this.metricLock) {
            for (Map.Entry<String, SamplerMetric> sampler : this.samplers.entrySet()) {
                SamplerMetric metric = sampler.getValue();
                if (metric.getTotal() <= 0) {
                    continue;
                }

                for (double percentile : this.percentiles) {
                    double value = metric.getAllPercentile(percentile);
                    if (Double.isFinite(value)) {
                        lines.add(DynatraceMetricLineBuilder.buildPercentileGauge(
                                percentile,
                                sampler.getKey(),
                                this.injectorHostname,
                                this.staticDimensions,
                                value,
                                timestamp));
                    }
                }

                /*
                 * This intentionally follows JMeter's native backend listener
                 * behavior. SamplerMetric retains its percentile window when
                 * interval counters are reset.
                 */
                metric.resetForTimeInterval();
            }
        }

        this.sender.send(lines);
    }

    private void flushSafely() {
        try {
            flush();
        } catch (Exception exception) {
            /* Metrics are an independent best-effort path. Log ingestion continues. */
            LOGGER.error("Unable to send percentile metrics to Dynatrace", exception);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        this.scheduledFlush.cancel(false);
        this.scheduler.shutdown();
        try {
            flush();
        } finally {
            try {
                this.sender.close();
            } finally {
                this.scheduler.shutdownNow();
            }
        }
    }

    static List<Double> parsePercentiles(String rawPercentiles) {
        if (rawPercentiles == null || rawPercentiles.trim().isEmpty()) {
            throw new IllegalArgumentException("At least one percentile must be configured");
        }

        Set<Double> parsed = new LinkedHashSet<>();
        for (String rawPercentile : rawPercentiles.split(";")) {
            String normalized = rawPercentile.trim();
            if (normalized.isEmpty()) {
                continue;
            }

            double percentile;
            try {
                percentile = Double.parseDouble(normalized);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid percentile: " + normalized, exception);
            }

            if (!Double.isFinite(percentile) || percentile <= 0.0 || percentile > 100.0) {
                throw new IllegalArgumentException(
                        "Percentiles must be finite values greater than 0 and at most 100: " + normalized);
            }
            parsed.add(percentile);
        }

        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("At least one percentile must be configured");
        }
        return new ArrayList<>(parsed);
    }

    private static String solveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            LOGGER.warn("Unable to determine injector hostname", exception);
            return "unknown";
        }
    }
}
