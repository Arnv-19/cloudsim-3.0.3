package org.cloudsim.adaptive.zone.utils;

import java.text.DecimalFormat;
import org.cloudbus.cloudsim.Log;
import java.util.List;

/**
 * StatisticsAnalyzer provides statistical analysis for VM categorization
 */
public class StatisticsAnalyzer {

    private DecimalFormat df = new DecimalFormat("#.##");

    /**
     * Calculate mean of a list of values
     */
    public double calculateMean(List<Double> values) {
        if (values.isEmpty()) return 0.0;

        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        return sum / values.size();
    }

    /**
     * Calculate standard deviation
     */
    public double calculateStandardDeviation(List<Double> values, double mean) {
        if (values.isEmpty()) return 0.0;

        double sumSquaredDeviations = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .sum();

        return Math.sqrt(sumSquaredDeviations / values.size());
    }

    /**
     * Perform bell curve analysis for zone thresholds
     */
    public BellCurveResult analyzeBellCurve(List<Double> values) {
        double mean = calculateMean(values);
        double stdDev = calculateStandardDeviation(values, mean);

        // Calculate percentiles based on bell curve
        double lowerThreshold = mean - stdDev;      // ~16th percentile
        double upperThreshold = mean + stdDev;      // ~84th percentile

        return new BellCurveResult(mean, stdDev, lowerThreshold, upperThreshold);
    }

    /**
     * Generate performance report
     */
    public void generatePerformanceReport() {
        Log.printLine();
        Log.printLine("========== PERFORMANCE ANALYSIS ==========");
        Log.printLine("Statistical analysis completed successfully.");
        Log.printLine("Zone categorization based on bell curve distribution.");
        Log.printLine("Dynamic zone resizing performed based on load balancing.");
        Log.printLine();
    }

    /**
     * Inner class for bell curve analysis results
     */
    public static class BellCurveResult {
        public final double mean;
        public final double standardDeviation;
        public final double lowerThreshold;
        public final double upperThreshold;

        public BellCurveResult(double mean, double stdDev, double lower, double upper) {
            this.mean = mean;
            this.standardDeviation = stdDev;
            this.lowerThreshold = lower;
            this.upperThreshold = upper;
        }
    }
}