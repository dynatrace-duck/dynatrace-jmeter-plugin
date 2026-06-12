package io.github.delirius325.jmeter.backendlistener.dynatrace;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends line-protocol payloads to the Dynatrace Metrics API.
 *
 * <p>This class is intentionally separate from {@link DynatraceMetricSender},
 * which sends JSON log batches to the Log Ingest API.</p>
 */
public final class DynatraceMetricsApiSender implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynatraceMetricsApiSender.class);

    /**
     * Dynatrace documents a 1 MiB request maximum. Leave headroom for proxies
     * and future headers instead of filling the limit exactly.
     */
    static final int MAX_PAYLOAD_BYTES = 900 * 1024;

    private final String dtUrl;
    private final String apiToken;
    private final CloseableHttpClient httpClient;

    public DynatraceMetricsApiSender(String dtUrl, String apiToken, int timeoutMs) {
        if (dtUrl == null || dtUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Dynatrace Metrics API URL must not be blank");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Metrics API timeout must be greater than zero");
        }

        this.dtUrl = dtUrl.trim();
        this.apiToken = apiToken == null ? "" : apiToken.trim();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setConnectionRequestTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .build();

        this.httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    public void send(List<String> metricLines) throws IOException {
        if (metricLines == null || metricLines.isEmpty()) {
            return;
        }

        for (String payload : splitIntoPayloads(metricLines)) {
            sendPayload(payload);
        }
    }

    private List<String> splitIntoPayloads(List<String> metricLines) throws IOException {
        List<String> payloads = new ArrayList<>();
        StringBuilder currentPayload = new StringBuilder();
        int currentBytes = 0;

        for (String metricLine : metricLines) {
            if (metricLine == null || metricLine.trim().isEmpty()) {
                continue;
            }

            int lineBytes = metricLine.getBytes(StandardCharsets.UTF_8).length;
            if (lineBytes > MAX_PAYLOAD_BYTES) {
                throw new IOException("A single metric line exceeds the Metrics API payload limit");
            }

            int separatorBytes = currentPayload.length() == 0 ? 0 : 1;
            if (currentBytes + separatorBytes + lineBytes > MAX_PAYLOAD_BYTES
                    && currentPayload.length() > 0) {
                payloads.add(currentPayload.toString());
                currentPayload.setLength(0);
                currentBytes = 0;
                separatorBytes = 0;
            }

            if (currentPayload.length() > 0) {
                currentPayload.append('\n');
            }
            currentPayload.append(metricLine);
            currentBytes += separatorBytes + lineBytes;
        }

        if (currentPayload.length() > 0) {
            payloads.add(currentPayload.toString());
        }

        return payloads;
    }

    private void sendPayload(String payload) throws IOException {
        HttpPost request = new HttpPost(this.dtUrl);
        request.setHeader("Authorization", "Api-Token " + this.apiToken);
        request.setHeader("Content-Type", "text/plain; charset=utf-8");
        request.setEntity(new StringEntity(payload, StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = this.httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = response.getEntity() == null
                    ? ""
                    : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode != HttpStatus.SC_ACCEPTED) {
                throw new IOException("Dynatrace Metrics API returned HTTP " + statusCode
                        + (responseBody.isEmpty() ? "" : ": " + responseBody));
            }

            LOGGER.debug("Successfully ingested {} metric lines", countLines(payload));
        }
    }

    private static int countLines(String payload) {
        int count = 1;
        for (int index = 0; index < payload.length(); index++) {
            if (payload.charAt(index) == '\n') {
                count++;
            }
        }
        return count;
    }

    @Override
    public void close() throws IOException {
        this.httpClient.close();
    }
}
