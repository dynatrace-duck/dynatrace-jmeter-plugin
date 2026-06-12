# MINT Metrics Integration Guide

## Overview

This document describes the implementation of MINT (Median, p90, p95, p99) percentile metrics export to Dynatrace. The feature calculates response time percentiles for both individual transactions and requests (samples) and sends them to Dynatrace as custom metrics.

## Architecture

### Core Components

#### 1. **PercentileCalculator** (`src/main/java/io/github/delirius325/jmeter/backendlistener/dynatrace/metrics/PercentileCalculator.java`)

Utility class for calculating percentiles using the nearest-rank method.

**Key Methods:**
- `addResponseTime(long responseTime)` - Records a single response time
- `calculatePercentile(int percentile)` - Calculates any percentile value (0-100)
- `calculateP50()`, `calculateP90()`, `calculateP95()`, `calculateP99()` - Convenience methods for MINT percentiles
- `calculateAllPercentiles()` - Returns all four percentiles at once

**Inner Class:**
- `MintPercentiles` - Data class containing p50, p90, p95, p99 values

---

#### 2. **MintMetricsCollector** (`src/main/java/io/github/delirius325/jmeter/backendlistener/dynatrace/metrics/MintMetricsCollector.java`)

Aggregates response times by transaction and request, maintaining separate `PercentileCalculator` instances for each.

**Key Methods:**
- `recordTransactionResponseTime(String transactionName, long responseTime)` - Records transaction response times
- `recordRequestResponseTime(String requestName, long responseTime)` - Records request response times
- `getTransactionPercentiles(String transactionName)` - Retrieves percentiles for a transaction
- `getRequestPercentiles(String requestName)` - Retrieves percentiles for a request
- `getAllTransactionPercentiles()` / `getAllRequestPercentiles()` - Batch retrieve all percentiles

---

#### 3. **MintMetricEnricher** (`src/main/java/io/github/delirius325/jmeter/backendlistener/dynatrace/metrics/MintMetricEnricher.java`)

Enriches JMeter sample metrics with MINT percentile values before sending to Dynatrace.

**Key Methods:**
- `enrichWithTransactionPercentiles(Map<String, Object> metric, String transactionName)` - Adds transaction percentiles
- `enrichWithRequestPercentiles(Map<String, Object> metric, String requestName)` - Adds request percentiles
- `enrichWithBothPercentiles(...)` - Convenience method to enrich with both transaction and request percentiles

**Metric Field Naming Convention:**
- `mint.transaction.p50`, `mint.transaction.p90`, `mint.transaction.p95`, `mint.transaction.p99`
- `mint.request.p50`, `mint.request.p90`, `mint.request.p95`, `mint.request.p99`

---

## Integration Points

### 1. **DynatraceBackendClient** Integration

The `DynatraceBackendClient` needs to be updated to:

1. **Initialize the Collector:**
   ```java
   private MintMetricsCollector mintCollector = new MintMetricsCollector();
   ```

2. **Collect Response Times:**
   In the `handleSampleResults()` method, when processing `SampleResult` objects:
   ```java
   mintCollector.recordTransactionResponseTime(
       sampleResult.getSampleLabel(), 
       sampleResult.getTime()
   );
   mintCollector.recordRequestResponseTime(
       sampleResult.getSampleLabel(), 
       sampleResult.getTime()
   );
   ```

3. **Create Enricher:**
   ```java
   MintMetricEnricher enricher = new MintMetricEnricher(mintCollector);
   ```

### 2. **DynatraceMetric** Enhancement

The `DynatraceMetric.getMetric()` method should:

1. Receive the `MintMetricEnricher` as a parameter
2. Call enrichment method after building the base metric:
   ```java
   enricher.enrichWithBothPercentiles(
       this.json, 
       this.sampleResult.getSampleLabel(),
       this.sampleResult.getSampleLabel()
   );
   ```

---

## Configuration Parameters

Consider adding these optional parameters to `DynatraceBackendClient`:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `dt.mint.enabled` | `true` | Enable/disable MINT metrics collection |
| `dt.mint.export.transaction` | `true` | Export transaction-level percentiles |
| `dt.mint.export.request` | `true` | Export request-level percentiles |

---

## Data Flow

```
JMeter Sample Result
    ↓
MintMetricsCollector.record*ResponseTime()
    ↓ (Stores in PercentileCalculator)
    ↓
DynatraceMetric.getMetric()
    ↓
MintMetricEnricher.enrichWith*Percentiles()
    ↓ (Adds mint.* fields to metric)
    ↓
DynatraceMetricSender.sendRequest()
    ↓
Dynatrace Log Ingest v2 API
```

---

## Usage Example

```java
// Initialize components
MintMetricsCollector collector = new MintMetricsCollector();
MintMetricEnricher enricher = new MintMetricEnricher(collector);

// During test execution
for (SampleResult result : results) {
    // Record response times
    collector.recordTransactionResponseTime("Login", result.getTime());
    collector.recordRequestResponseTime("GET /api/auth", result.getTime());
}

// When creating metrics for export
Map<String, Object> metric = new HashMap<>();
metric.put("timestamp", System.currentTimeMillis());
metric.put("sample", result.getSampleLabel());

// Enrich with percentiles
enricher.enrichWithBothPercentiles(
    metric, 
    "Login",           // transaction name
    "GET /api/auth"    // request name
);

// Now metric contains:
// - mint.transaction.p50, p90, p95, p99
// - mint.request.p50, p90, p95, p99
```

---

## Testing

Unit tests are provided in:
- `src/test/java/io/github/delirius325/jmeter/backendlistener/dynatrace/metrics/PercentileCalculatorTest.java`
- `src/test/java/io/github/delirius325/jmeter/backendlistener/dynatrace/metrics/MintMetricsCollectorTest.java`

Run tests with:
```bash
mvn test
```

---

## Performance Considerations

1. **Memory Usage:** Each transaction and request maintains its own list of response times. For long-running tests with many unique transactions/requests, consider:
   - Periodic metric export and collector reset
   - Sampling strategies for high-volume scenarios

2. **Calculation Cost:** Percentile calculation requires sorting. This is performed on-demand when percentiles are requested, minimizing CPU overhead during data collection.

3. **JMeter Integration:** Response time recording is a O(1) operation, so the overhead during test execution is minimal.

---

## Future Enhancements

1. **Configurable Percentiles:** Allow users to specify custom percentiles beyond p50/p90/p95/p99
2. **Time-windowed Aggregation:** Export percentiles at regular intervals instead of waiting until end of test
3. **Additional Metrics:** Min, max, mean, standard deviation
4. **Filtering:** Option to exclude specific transactions/requests from percentile calculation
5. **Histogram Export:** Export full response time histograms to Dynatrace for detailed analysis

---

## References

- **Percentile Calculation:** Uses nearest-rank method (also called inverse of empirical CDF)
- **Dynatrace Log Ingest v2 API:** https://docs.dynatrace.com/docs/dynatrace-api/environment-api/log-events/post-log-events
