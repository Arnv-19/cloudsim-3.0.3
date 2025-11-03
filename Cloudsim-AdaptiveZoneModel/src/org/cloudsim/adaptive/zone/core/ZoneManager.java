package org.cloudsim.adaptive.zone.core;

import java.util.ArrayList;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.cloudbus.cloudsim.Vm;
import org.cloudsim.adaptive.zone.utils.StatisticsAnalyzer;

/**
 * ZoneManager manages the three-tier adaptive zone model
 * Zones are: High-resource & slow, Medium, Fast & small VM zones
 */
public class ZoneManager {

    // Zone types
    public static final int HIGH_RESOURCE_ZONE = 0;  // High RAM, storage, steady processing
    public static final int MEDIUM_ZONE = 1;         // Average resource usage
    public static final int FAST_SMALL_ZONE = 2;     // Lightweight, high-speed demand

    private List<ResourceZone> zones;
    private Map<Integer, ZoneMetrics> zoneMetrics;
    private Map<Integer, Integer> vmToZoneMapping;
    private StatisticsAnalyzer statsAnalyzer;

    // Zone thresholds based on standard deviation analysis
    private double cpuThresholdLow;
    private double cpuThresholdHigh;
    private double ramThresholdLow;
    private double ramThresholdHigh;
    private double storageThresholdLow;
    private double storageThresholdHigh;

    public ZoneManager(int numberOfZones) {
        this.zones = new ArrayList<>();
        this.zoneMetrics = new HashMap<>();
        this.vmToZoneMapping = new HashMap<>();
        this.statsAnalyzer = new StatisticsAnalyzer();

        // Initialize zones
        for (int i = 0; i < numberOfZones; i++) {
            ResourceZone zone = new ResourceZone(i, getZoneName(i));
            zones.add(zone);
            zoneMetrics.put(i, new ZoneMetrics(i));
        }

        Log.printLine("ZoneManager initialized with " + numberOfZones + " zones");
    }

    /**
     * Analyze VM traffic and categorize VMs into zones using statistical analysis
     */
    public void categorizeVMs(List<Vm> vmList) {
        if (vmList == null || vmList.isEmpty()) return;

        // Calculate statistics for VM resources
        calculateResourceThresholds(vmList);

        // Categorize each VM based on resource demand patterns
        for (Vm vm : vmList) {
            int zoneId = determineVMZone(vm);
            assignVMToZone(vm, zoneId);
        }

        Log.printLine("VM categorization completed. Zone distribution:");
        printZoneDistribution();
    }

    /**
     * Calculate resource thresholds using standard deviation and bell curve analysis
     */
    private void calculateResourceThresholds(List<Vm> vmList) {
        List<Double> cpuValues = vmList.stream().mapToDouble(vm -> (double)vm.getMips()).boxed().collect(Collectors.toList());
        List<Double> ramValues = vmList.stream().mapToDouble(vm -> (double)vm.getRam()).boxed().collect(Collectors.toList());
        List<Double> storageValues = vmList.stream().mapToDouble(vm -> (double)vm.getSize()).boxed().collect(Collectors.toList());

        // Calculate mean and standard deviation for each resource
        double cpuMean = statsAnalyzer.calculateMean(cpuValues);
        double cpuStdDev = statsAnalyzer.calculateStandardDeviation(cpuValues, cpuMean);

        double ramMean = statsAnalyzer.calculateMean(ramValues);
        double ramStdDev = statsAnalyzer.calculateStandardDeviation(ramValues, ramMean);

        double storageMean = statsAnalyzer.calculateMean(storageValues);
        double storageStdDev = statsAnalyzer.calculateStandardDeviation(storageValues, storageMean);

        // Set thresholds based on one standard deviation from mean (bell curve analysis)
        cpuThresholdLow = cpuMean - cpuStdDev;
        cpuThresholdHigh = cpuMean + cpuStdDev;

        ramThresholdLow = ramMean - ramStdDev;
        ramThresholdHigh = ramMean + ramStdDev;

        storageThresholdLow = storageMean - storageStdDev;
        storageThresholdHigh = storageMean + storageStdDev;

        Log.printLine("Resource thresholds calculated:");
        Log.printLine("CPU: Low=" + String.format("%.2f", cpuThresholdLow) + ", High=" + String.format("%.2f", cpuThresholdHigh));
        Log.printLine("RAM: Low=" + String.format("%.2f", ramThresholdLow) + ", High=" + String.format("%.2f", ramThresholdHigh));
        Log.printLine("Storage: Low=" + String.format("%.2f", storageThresholdLow) + ", High=" + String.format("%.2f", storageThresholdHigh));
    }

    /**
     * Determine which zone a VM belongs to based on its resource characteristics
     */
    private int determineVMZone(Vm vm) {
        double cpu = vm.getMips();
        double ram = vm.getRam();
        double storage = vm.getSize();

        // High-resource zone: High RAM and storage, moderate to low processing speed
        if (ram > ramThresholdHigh && storage > storageThresholdHigh) {
            return HIGH_RESOURCE_ZONE;
        }

        // Fast & small zone: High processing speed, low RAM and storage
        if (cpu > cpuThresholdHigh && ram < ramThresholdLow && storage < storageThresholdLow) {
            return FAST_SMALL_ZONE;
        }

        // Medium zone: Average resource usage
        return MEDIUM_ZONE;
    }

    /**
     * Assign VM to a specific zone
     */
    public void assignVMToZone(Vm vm, int zoneId) {
        if (zoneId < 0 || zoneId >= zones.size()) {
            Log.printLine("Invalid zone ID: " + zoneId);
            return;
        }

        ResourceZone zone = zones.get(zoneId);
        zone.addVM(vm);
        vmToZoneMapping.put(vm.getId(), zoneId);

        // Update zone metrics
        ZoneMetrics metrics = zoneMetrics.get(zoneId);
        metrics.addVM(vm);
    }

    /**
     * Migrate VM from one zone to another based on changing resource demands
     */
    public boolean migrateVMBetweenZones(Vm vm, int targetZoneId) {
        Integer currentZoneId = vmToZoneMapping.get(vm.getId());
        if (currentZoneId == null) {
            Log.printLine("VM " + vm.getId() + " is not assigned to any zone");
            return false;
        }

        if (currentZoneId == targetZoneId) {
            return false; // No migration needed
        }

        // Remove from current zone
        ResourceZone currentZone = zones.get(currentZoneId);
        currentZone.removeVM(vm);
        zoneMetrics.get(currentZoneId).removeVM(vm);

        // Add to target zone
        ResourceZone targetZone = zones.get(targetZoneId);
        targetZone.addVM(vm);
        zoneMetrics.get(targetZoneId).addVM(vm);
        vmToZoneMapping.put(vm.getId(), targetZoneId);

        Log.printLine("VM " + vm.getId() + " migrated from Zone " + currentZoneId + " to Zone " + targetZoneId);
        return true;
    }

    /**
     * Migrate VM to a new host in the target zone (real CloudSim migration)
     */
    public boolean migrateVMToZoneAndHost(Vm vm, int targetZoneId, List<? extends org.cloudbus.cloudsim.Host> hostList) {
        Integer currentZoneId = vmToZoneMapping.get(vm.getId());
        if (currentZoneId == null || currentZoneId == targetZoneId) {
            return false; // No migration needed
        }

        // Remove from current zone
        ResourceZone currentZone = zones.get(currentZoneId);
        currentZone.removeVM(vm);
        zoneMetrics.get(currentZoneId).removeVM(vm);

        // Find best host in target zone
        Host bestHost = null;
        double bestFitScore = Double.MAX_VALUE;
        for (Host host : hostList) {
            if (host.isSuitableForVm(vm)) {
                double fitScore = zones.get(targetZoneId).calculateVMFitScore(vm);
                if (fitScore < bestFitScore) {
                    bestFitScore = fitScore;
                    bestHost = host;
                }
            }
        }
        if (bestHost == null) {
            Log.printLine("No suitable host found for VM " + vm.getId() + " in zone " + targetZoneId);
            // Re-add to current zone
            currentZone.addVM(vm);
            zoneMetrics.get(currentZoneId).addVM(vm);
            return false;
        }

        // Perform CloudSim migration: destroy on old host, create on new host
        Host oldHost = vm.getHost();
        if (oldHost != null) {
            oldHost.vmDestroy(vm);
        }
        boolean created = bestHost.vmCreate(vm);
        if (!created) {
            Log.printLine("Failed to migrate VM " + vm.getId() + " to new host in zone " + targetZoneId);
            // Re-add to current zone
            currentZone.addVM(vm);
            zoneMetrics.get(currentZoneId).addVM(vm);
            return false;
        }

        // Add to target zone
        ResourceZone targetZone = zones.get(targetZoneId);
        targetZone.addVM(vm);
        zoneMetrics.get(targetZoneId).addVM(vm);
        vmToZoneMapping.put(vm.getId(), targetZoneId);

        // Update migration count
        zoneMetrics.get(targetZoneId).getMigrationCount();
        Log.printLine("VM " + vm.getId() + " migrated from Zone " + currentZoneId + " to Zone " + targetZoneId + " and Host " + bestHost.getId());
        return true;
    }

    /**
     * Dynamic zone resizing based on current workload distribution
     */
    public void performDynamicZoneResizing() {
        // Calculate load imbalance
        Map<Integer, Double> zoneLoads = calculateZoneLoads();

        // Find overloaded and underutilized zones
        List<Integer> overloadedZones = new ArrayList<>();
        List<Integer> underutilizedZones = new ArrayList<>();

        double averageLoad = zoneLoads.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double loadThreshold = 0.3; // 30% threshold for load imbalance

        for (Map.Entry<Integer, Double> entry : zoneLoads.entrySet()) {
            double loadDifference = Math.abs(entry.getValue() - averageLoad) / averageLoad;

            if (entry.getValue() > averageLoad && loadDifference > loadThreshold) {
                overloadedZones.add(entry.getKey());
            } else if (entry.getValue() < averageLoad && loadDifference > loadThreshold) {
                underutilizedZones.add(entry.getKey());
            }
        }

        // Perform zone resizing by migrating VMs
        if (!overloadedZones.isEmpty() && !underutilizedZones.isEmpty()) {
            redistributeVMsBetweenZones(overloadedZones, underutilizedZones);
        }
    }

    /**
     * Calculate current load for each zone
     */
    private Map<Integer, Double> calculateZoneLoads() {
        Map<Integer, Double> loads = new HashMap<>();

        for (ResourceZone zone : zones) {
            double totalLoad = 0.0;
            List<Vm> vms = zone.getVMs();

            for (Vm vm : vms) {
                // Calculate load based on CPU utilization, RAM usage, etc.
                double vmLoad = (vm.getMips() * 0.4) + (vm.getRam() * 0.3) + (vm.getSize() * 0.3);
                totalLoad += vmLoad;
            }

            loads.put(zone.getZoneId(), totalLoad);
        }

        return loads;
    }

    /**
     * Redistribute VMs between overloaded and underutilized zones
     */
    private void redistributeVMsBetweenZones(List<Integer> overloadedZones, List<Integer> underutilizedZones) {
        for (Integer overloadedZoneId : overloadedZones) {
            ResourceZone overloadedZone = zones.get(overloadedZoneId);
            List<Vm> vmsToMigrate = overloadedZone.getVMsForMigration(2); // Migrate 2 VMs at a time

            for (Vm vm : vmsToMigrate) {
                // Find best target zone among underutilized zones
                Integer targetZoneId = findBestTargetZone(vm, underutilizedZones);
                if (targetZoneId != null) {
                    migrateVMBetweenZones(vm, targetZoneId);
                }
            }
        }
    }

    /**
     * Find the best target zone for VM migration
     */
    private Integer findBestTargetZone(Vm vm, List<Integer> candidateZones) {
        double bestFitScore = Double.MAX_VALUE;
        Integer bestZone = null;

        for (Integer zoneId : candidateZones) {
            ResourceZone zone = zones.get(zoneId);
            double fitScore = zone.calculateVMFitScore(vm);

            if (fitScore < bestFitScore) {
                bestFitScore = fitScore;
                bestZone = zoneId;
            }
        }

        return bestZone;
    }

    /**
     * Get zone name based on zone ID
     */
    private String getZoneName(int zoneId) {
        switch (zoneId) {
            case HIGH_RESOURCE_ZONE: return "High-Resource & Slow Zone";
            case MEDIUM_ZONE: return "Medium Zone";
            case FAST_SMALL_ZONE: return "Fast & Small VM Zone";
            default: return "Zone " + zoneId;
        }
    }

    /**
     * Print current zone distribution
     */
    public void printZoneDistribution() {
        for (ResourceZone zone : zones) {
            Log.printLine(zone.getZoneName() + ": " + zone.getVMCount() + " VMs");
        }
    }

    /**
     * Print detailed zone statistics
     */
    public void printZoneStatistics() {
        Log.printLine();
        Log.printLine("========== ZONE STATISTICS ==========");

        for (ResourceZone zone : zones) {
            ZoneMetrics metrics = zoneMetrics.get(zone.getZoneId());
            Log.printLine();
            Log.printLine("Zone: " + zone.getZoneName());
            Log.printLine("VM Count: " + zone.getVMCount());
            Log.printLine("Average CPU: " + String.format("%.2f", metrics.getAverageCPU()));
            Log.printLine("Average RAM: " + String.format("%.2f", metrics.getAverageRAM()));
            Log.printLine("Average Storage: " + String.format("%.2f", metrics.getAverageStorage()));
            Log.printLine("Zone Load: " + String.format("%.2f", metrics.getTotalLoad()));
        }
    }

    // Getter methods
    public List<ResourceZone> getZones() { return zones; }
    public ResourceZone getZone(int zoneId) { return zones.get(zoneId); }
    public ZoneMetrics getZoneMetrics(int zoneId) { return zoneMetrics.get(zoneId); }
    public Integer getVMZone(int vmId) { return vmToZoneMapping.get(vmId); }
    public Map<Integer, Integer> getVMToZoneMapping() { return vmToZoneMapping; }
}