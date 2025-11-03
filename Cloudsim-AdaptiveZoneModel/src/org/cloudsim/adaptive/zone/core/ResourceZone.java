package org.cloudsim.adaptive.zone.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.cloudbus.cloudsim.Vm;

/**
 * ResourceZone represents a zone in the three-tier adaptive model
 */
public class ResourceZone {

    private int zoneId;
    private String zoneName;
    private List<Vm> assignedVMs;
    private double totalCPU;
    private double totalRAM;
    private double totalStorage;
    private double currentLoad;

    public ResourceZone(int zoneId, String zoneName) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.assignedVMs = new ArrayList<>();
        this.totalCPU = 0.0;
        this.totalRAM = 0.0;
        this.totalStorage = 0.0;
        this.currentLoad = 0.0;
    }

    /**
     * Add VM to this zone
     */
    public void addVM(Vm vm) {
        assignedVMs.add(vm);
        totalCPU += vm.getMips();
        totalRAM += vm.getRam();
        totalStorage += vm.getSize();
        updateCurrentLoad();
    }

    /**
     * Remove VM from this zone
     */
    public void removeVM(Vm vm) {
        if (assignedVMs.remove(vm)) {
            totalCPU -= vm.getMips();
            totalRAM -= vm.getRam();
            totalStorage -= vm.getSize();
            updateCurrentLoad();
        }
    }

    /**
     * Get VMs suitable for migration (least loaded first)
     */
    public List<Vm> getVMsForMigration(int count) {
        List<Vm> sortedVMs = new ArrayList<>(assignedVMs);
        // Sort by load (assuming lower MIPS = lower load for simplicity)
        sortedVMs.sort(Comparator.comparingDouble(Vm::getMips));

        int migrateCount = Math.min(count, sortedVMs.size());
        return sortedVMs.subList(0, migrateCount);
    }

    /**
     * Calculate how well a VM fits in this zone
     */
    public double calculateVMFitScore(Vm vm) {
        if (assignedVMs.isEmpty()) return 0.0;

        double avgCPU = totalCPU / assignedVMs.size();
        double avgRAM = totalRAM / assignedVMs.size();
        double avgStorage = totalStorage / assignedVMs.size();

        // Calculate fit score based on how close VM is to zone averages
        double cpuDiff = Math.abs(vm.getMips() - avgCPU) / avgCPU;
        double ramDiff = Math.abs(vm.getRam() - avgRAM) / avgRAM;
        double storageDiff = Math.abs(vm.getSize() - avgStorage) / avgStorage;

        return (cpuDiff + ramDiff + storageDiff) / 3.0;
    }

    /**
     * Update current load of the zone
     */
    private void updateCurrentLoad() {
        currentLoad = (totalCPU * 0.4) + (totalRAM * 0.3) + (totalStorage * 0.3);
    }

    // Getter methods
    public int getZoneId() { return zoneId; }
    public String getZoneName() { return zoneName; }
    public List<Vm> getVMs() { return new ArrayList<>(assignedVMs); }
    public int getVMCount() { return assignedVMs.size(); }
    public double getTotalCPU() { return totalCPU; }
    public double getTotalRAM() { return totalRAM; }
    public double getTotalStorage() { return totalStorage; }
    public double getCurrentLoad() { return currentLoad; }
}