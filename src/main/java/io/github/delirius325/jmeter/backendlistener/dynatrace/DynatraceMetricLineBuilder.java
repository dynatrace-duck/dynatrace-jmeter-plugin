package io.github.delirius325.jmeter.backendlistener.dynatrace;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Builds Dynatrace Metrics API line-protocol records for percentile gauges. */
final class DynatraceMetricLineBuilder {
    private static final Pattern DIMENSION_KEY_PATTERN = Pattern.compile("[a-z0-9_.:-]+");

    private static final String METRIC_PREFIX = "jmeter.mint";
    private static final String TRANSACTION_DIMENSION = "samplelabel";
    private static final String INJECTOR_HOSTNAME_DIMENSION = "injector_hostname";

    private DynatraceMetricLineBuilder() {
    }

    static Map<String, String> parseStaticDimensions(String rawDimensions) {
        if (rawDimensions == null || rawDimensions.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> dimensions = new LinkedHashMap<>();
        for (String pair : rawDimensions.split(";")) {
            String trimmedPair = pair.trim();
            if (trimmedPair.isEmpty()) {
                continue;
            }

            int separatorIndex = trimmedPair.indexOf('=');
            if (separatorIndex <= 0) {
                throw new IllegalArgumentException(
                        "Invalid static metric dimension. Expected key=value: " + trimmedPair);
            }

            String key = trimmedPair.substring(0, separatorIndex).trim();
            String value = trimmedPair.substring(separatorIndex + 1).trim();
            validateDimensionKey(key);

            if (TRANSACTION_DIMENSION.equals(key) || INJECTOR_HOSTNAME_DIMENSION.equals(key)) {
                throw new IllegalArgumentException("Static metric dimensions must not override " + key);
            }
            dimensions.put(key, value);
        }

        if (dimensions.size() > 48) {
            throw new IllegalArgumentException(
                    "At most 48 static dimensions are allowed because two dimensions are added by the plugin");
        }

        return Collections.unmodifiableMap(dimensions);
    }

    static String buildPercentileGauge(
            double percentile,
            String transaction,
            String injectorHostname,
            Map<String, String> staticDimensions,
            double value,
            long timestamp) {

        if (percentile <= 0.0 || percentile > 100.0) {
            throw new IllegalArgumentException("Percentile must be greater than 0 and at most 100");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Metric value must be finite");
        }

        String metricKey = METRIC_PREFIX + ".p" + normalizePercentile(percentile);
        if (metricKey.length() > 255) {
            throw new IllegalArgumentException("Metric key exceeds 255 characters: " + metricKey);
        }

        StringBuilder line = new StringBuilder(metricKey);
        appendDimension(line, TRANSACTION_DIMENSION, normalizeValue(transaction, "(unnamed)"));
        appendDimension(line, INJECTOR_HOSTNAME_DIMENSION, normalizeValue(injectorHostname, "unknown"));

        if (staticDimensions != null) {
            for (Map.Entry<String, String> dimension : staticDimensions.entrySet()) {
                appendDimension(line, dimension.getKey(), dimension.getValue());
            }
        }

        line.append(" gauge,")
                .append(formatNumber(value))
                .append(' ')
                .append(timestamp);
        return line.toString();
    }

    private static void appendDimension(StringBuilder line, String key, String value) {
        validateDimensionKey(key);
        line.append(',')
                .append(key)
                .append("=\"")
                .append(escapeDimensionValue(value))
                .append('"');
    }

    private static void validateDimensionKey(String key) {
      if (key == null || !DIMENSION_KEY_PATTERN.matcher(key).matches()) {
        throw new IllegalArgumentException(
          "Invalid Dynatrace dimension key: " + key + " (allowed: [a-z0-9_.:-]+)"
        );
      }
    }

    private static String normalizeValue(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String escapeDimensionValue(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String normalizePercentile(double percentile) {
        return BigDecimal.valueOf(percentile)
                .stripTrailingZeros()
                .toPlainString()
                .replace('.', '_');
    }

    private static String formatNumber(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
