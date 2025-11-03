package org.cloudsim.adaptive.zone.core;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Vm;

/**
 * ZoneMetrics tracks performance metrics for each zone
 */
public class ZoneMetrics {

    private int zoneId;
    private List<Vm> vms;
    private double totalCPU;
    private double totalRAM;
    private double totalStorage;
    private int migrationCount;
    private double averageResponseTime;

    public ZoneMetrics(int zoneId) {
        this.zoneId = zoneId;
        this.vms = new ArrayList<>();
        this.totalCPU = 0.0;
        this.totalRAM = 0.0;
        this.totalStorage = 0.0;
        this.migrationCount = 0;
        this.averageResponseTime = 0.0;
    }

    /**
     * Add VM to metrics tracking
     */
    public void addVM(Vm vm) {
        vms.add(vm);
        totalCPU += vm.getMips();
        totalRAM += vm.getRam();
        totalStorage += vm.getSize();
    }

    /**
     * Remove VM from metrics tracking
     */
    public void removeVM(Vm vm) {
        if (vms.remove(vm)) {
            totalCPU -= vm.getMips();
            totalRAM -= vm.getRam();
            totalStorage -= vm.getSize();
        }
    }

    /**
     * Record a VM migration
     */
    public void recordMigration() {
        migrationCount++;
    }

    /**
     * Update response time metrics
     */
    public void updateResponseTime(double responseTime) {
        if (vms.size() > 0) {
            averageResponseTime = ((averageResponseTime * (vms.size() - 1)) + responseTime) / vms.size();
        }
    }

    // Getter methods for metrics
    public int getZoneId() { return zoneId; }
    public int getVMCount() { return vms.size(); }
    public double getAverageCPU() { return vms.size() > 0 ? totalCPU / vms.size() : 0.0; }
    public double getAverageRAM() { return vms.size() > 0 ? totalRAM / vms.size() : 0.0; }
    public double getAverageStorage() { return vms.size() > 0 ? totalStorage / vms.size() : 0.0; }
    public double getTotalLoad() { return (totalCPU * 0.4) + (totalRAM * 0.3) + (totalStorage * 0.3); }
    public int getMigrationCount() { return migrationCount; }
    public double getAverageResponseTime() { return averageResponseTime; }
}