package io.github.delirius325.jmeter.backendlistener.dynatrace;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

public class TestDynatraceMetricLineBuilder {
    @Test
    public void buildsP95GaugeWithEscapedDimensions() {
        Map<String, String> dimensions = DynatraceMetricLineBuilder.parseStaticDimensions(
                "environment=staging;team=checkout\\\"ops");

        String metricLine = DynatraceMetricLineBuilder.buildPercentileGauge(
                95.0,
                "GET /api/orders\\active\"",
                "loadgen-01",
                dimensions,
                218.5,
                1710000000000L);

        assertEquals(
                "jmeter.mint.p95,SampleLabel=\"GET /api/orders\\\\active\\\"\","
                        + "injector_hostname=\"loadgen-01\",environment=\"staging\",team=\"checkout\\\\\\\"ops\" "
                        + "gauge,218.5 1710000000000",
                metricLine);
    }

    @Test
    public void parsesPercentilesOnceAndPreservesOrder() {
        assertEquals(
                Arrays.asList(50.0, 90.0, 95.0, 99.0),
                DynatracePercentileMetricsExporter.parsePercentiles("50;90;95;99;95"));
    }


    @Test(expected = IllegalArgumentException.class)
    public void rejectsStaticTransactionOverride() {
        DynatraceMetricLineBuilder.parseStaticDimensions("SampleLabel=override");
    }
}
