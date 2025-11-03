package org.cloudsim.adaptive.zone;

import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudsim.adaptive.zone.core.AdaptiveVmAllocationPolicy;
import org.cloudsim.adaptive.zone.core.ZoneManager;
import org.cloudsim.adaptive.zone.gui.SimulationDashboard;
import org.cloudsim.adaptive.zone.utils.StatisticsAnalyzer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Main simulation class for the CloudSim Adaptive Zone Model
 * Implements a three-tier adaptive zone system for VM placement and migration
 */
public class AdaptiveZoneSimulation {

    private static List<Cloudlet> cloudletList;
    private static List<Vm> vmList;
    private static ZoneManager zoneManager;
    private static SimulationDashboard dashboard;
    private static StatisticsAnalyzer statsAnalyzer;

    /**
     * Main method to start the adaptive zone simulation
     */
    public static void main(String[] args) {
        Log.printLine("Starting CloudSim Adaptive Zone Model Simulation...");

        try {
            // List of scenario config files
            String[] scenarioFiles = {
                "src/org/cloudsim/adaptive/zone/scenerios/scenerio1_config.json",
                "src/org/cloudsim/adaptive/zone/scenerios/scenerio2_zone_resizing.json",
                "src/org/cloudsim/adaptive/zone/scenerios/scenerio3_vm_migration.json"
            };
            for (String scenarioFile : scenarioFiles) {
                runScenario(scenarioFile);
            }
            Log.printLine("All scenarios finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("The simulation has been terminated due to an unexpected error");
        }
    }

    /**
     * Run a single scenario from a JSON config file
     */
    private static void runScenario(String scenarioConfigPath) throws IOException {
        Log.printLine("\n--- Running scenario: " + scenarioConfigPath + " ---");
        // Parse scenario config
        Gson gson = new Gson();
        Map<String, Object> scenarioConfig = gson.fromJson(new FileReader(scenarioConfigPath), new TypeToken<Map<String, Object>>(){}.getType());
        Map<String, Object> map = (Map<String, Object>) scenarioConfig.get("simulation_parameters");
		Map<String, Object> simParams = map;
        int numHosts = ((Double)simParams.get("number_of_hosts")).intValue();
        int numCloudlets = ((Double)simParams.get("number_of_cloudlets")).intValue();
        int simTime = ((Double)simParams.get("simulation_time")).intValue();

        // Initialize CloudSim
        int numUsers = 1;
        Calendar calendar = Calendar.getInstance();
        boolean traceFlag = false;
        CloudSim.init(numUsers, calendar, traceFlag);

        // Initialize components
        initializeComponents();

        // Create datacenter with adaptive zone allocation policy
        Datacenter datacenter = createDatacenter("AdaptiveZoneDatacenter", numHosts);

        // Create broker
        DatacenterBroker broker = createBroker();
        int brokerId = broker.getId();

        // Create VMs and cloudlets from scenario config
        vmList = createVirtualMachinesFromScenario(brokerId, scenarioConfig);
        broker.submitVmList(vmList);
        // Categorize VMs into zones using statistics
        zoneManager.categorizeVMs(vmList);

        cloudletList = createCloudletsFromScenario(brokerId, numCloudlets);
        broker.submitCloudletList(cloudletList);

        // Start GUI dashboard
        if (dashboard != null) {
            dashboard.startRealTimeMonitoring();
        }

        // --- Periodic optimization/migration loop ---
        double currentTime = 0.0;
        double step = 1.0; // 1 second per step
        AdaptiveVmAllocationPolicy allocationPolicy = null;
        if (datacenter.getVmAllocationPolicy() instanceof AdaptiveVmAllocationPolicy) {
            allocationPolicy = (AdaptiveVmAllocationPolicy) datacenter.getVmAllocationPolicy();
        }

        CloudSim.startSimulation();
        while (currentTime < simTime) {
            CloudSim.runClockTick(); // Advance simulation by one tick
            currentTime += step;
            if (allocationPolicy != null) {
                allocationPolicy.optimizeAllocation(vmList);
            }
            if (dashboard != null) {
                dashboard.updateDashboard();
            }
        }
        CloudSim.stopSimulation();

        // Print results and comparisons
        printSimulationResults(broker);
    }

    /**
     * Initialize all components for the adaptive zone simulation
     */
    private static void initializeComponents() {
        // Initialize zone manager with three zones
        zoneManager = new ZoneManager(3);

        // Initialize statistics analyzer
        statsAnalyzer = new StatisticsAnalyzer();

        // Initialize GUI dashboard
        dashboard = new SimulationDashboard(zoneManager, statsAnalyzer);

        Log.printLine("Components initialized successfully");
    }

    /**
     * Create datacenter with adaptive zone allocation policy
     */
    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<Host>();

        // Create hosts with different capabilities
        for (int i = 0; i < 10; i++) {
            List<Pe> peList = new ArrayList<Pe>();
            int mips = 1000 + (i * 200); // Varying processing power

            // Create processing elements
            peList.add(new Pe(0, new PeProvisionerSimple(mips)));
            peList.add(new Pe(1, new PeProvisionerSimple(mips)));

            int hostId = i;
            int ram = 2048 + (i * 512); // Varying RAM
            long storage = 10000 + (i * 5000); // Varying storage
            int bw = 10000 + (i * 1000); // Varying bandwidth

            Host host = new Host(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                storage,
                peList,
                new VmSchedulerTimeShared(peList)
            );

            hostList.add(host);
        }

        // Create datacenter characteristics
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double timeZone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
            arch, os, vmm, hostList, timeZone, cost, costPerMem, costPerStorage, costPerBw
        );

        // Create adaptive VM allocation policy
        AdaptiveVmAllocationPolicy adaptivePolicy = new AdaptiveVmAllocationPolicy(hostList, zoneManager);

        LinkedList<Storage> storageList = new LinkedList<Storage>();

        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics, adaptivePolicy, storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    // Overload createDatacenter to accept host count
    private static Datacenter createDatacenter(String name, int numHosts) {
        List<Host> hostList = new ArrayList<Host>();
        for (int i = 0; i < numHosts; i++) {
            List<Pe> peList = new ArrayList<Pe>();
            int mips = 1000 + (i * 200);
            peList.add(new Pe(0, new PeProvisionerSimple(mips)));
            peList.add(new Pe(1, new PeProvisionerSimple(mips)));
            int hostId = i;
            int ram = 2048 + (i * 512);
            long storage = 10000 + (i * 5000);
            int bw = 10000 + (i * 1000);
            Host host = new Host(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                storage,
                peList,
                new VmSchedulerTimeShared(peList)
            );
            hostList.add(host);
        }
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double timeZone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
            arch, os, vmm, hostList, timeZone, cost, costPerMem, costPerStorage, costPerBw
        );
        AdaptiveVmAllocationPolicy adaptivePolicy = new AdaptiveVmAllocationPolicy(hostList, zoneManager);
        LinkedList<Storage> storageList = new LinkedList<Storage>();
        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics, adaptivePolicy, storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return datacenter;
    }

    /**
     * Create broker for managing VMs and cloudlets
     */
    private static DatacenterBroker createBroker() {
        DatacenterBroker broker = null;
        try {
            broker = new DatacenterBroker("AdaptiveZoneBroker");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return broker;
    }

    /**
     * Create virtual machines with different resource profiles for zone classification
     */
    private static List<Vm> createVirtualMachines(int brokerId) {
        List<Vm> vms = new ArrayList<Vm>();
        String vmm = "Xen";

        // High-resource VMs (slow zone)
        for (int i = 0; i < 10; i++) {
            Vm vm = new Vm(i, brokerId, 500 + (i * 50), 1, 2048 + (i * 256), 1000, 10000 + (i * 2000), vmm, new CloudletSchedulerTimeShared());
            vms.add(vm);
        }

        // Medium-resource VMs (medium zone)
        for (int i = 10; i < 25; i++) {
            Vm vm = new Vm(i, brokerId, 1000 + (i * 25), 1, 1024 + (i * 128), 1000, 5000 + (i * 1000), vmm, new CloudletSchedulerTimeShared());
            vms.add(vm);
        }

        // Fast & small VMs (fast zone)
        for (int i = 25; i < 40; i++) {
            Vm vm = new Vm(i, brokerId, 2000 + (i * 100), 1, 512 + (i * 64), 1000, 2000 + (i * 500), vmm, new CloudletSchedulerTimeShared());
            vms.add(vm);
        }

        return vms;
    }

    // Create VMs from scenario config
    private static List<Vm> createVirtualMachinesFromScenario(int brokerId, Map<String, Object> scenarioConfig) {
        List<Vm> vms = new ArrayList<>();
        String vmm = "Xen";
        List<Map<String, Object>> vmConfigs = (List<Map<String, Object>>) scenarioConfig.get("vm_configurations");
        int vmId = 0;
        for (Map<String, Object> vmConf : vmConfigs) {
            int count = ((Double)vmConf.get("count")).intValue();
            List<Double> mipsRange = (List<Double>) vmConf.get("mips_range");
            List<Double> ramRange = (List<Double>) vmConf.get("ram_range");
            List<Double> storageRange = (List<Double>) vmConf.get("storage_range");
            for (int i = 0; i < count; i++) {
                int mips = mipsRange.get(0).intValue() + (int)(Math.random() * (mipsRange.get(1) - mipsRange.get(0)));
                int ram = ramRange.get(0).intValue() + (int)(Math.random() * (ramRange.get(1) - ramRange.get(0)));
                int storage = storageRange.get(0).intValue() + (int)(Math.random() * (storageRange.get(1) - storageRange.get(0)));
                Vm vm = new Vm(vmId++, brokerId, mips, 1, ram, 1000, storage, vmm, new CloudletSchedulerTimeShared());
                vms.add(vm);
            }
        }
        return vms;
    }

    /**
     * Create cloudlets with varying workload patterns
     */
    private static List<Cloudlet> createCloudlets(int brokerId) {
        List<Cloudlet> cloudlets = new ArrayList<Cloudlet>();
        UtilizationModel utilizationModel = new UtilizationModelFull();

        // Create cloudlets with different resource requirements
        for (int i = 0; i < 50; i++) {
            long length = 10000 + (long)(Math.random() * 40000); // Varying computational length
            long fileSize = 300 + (long)(Math.random() * 1000);
            long outputSize = 300 + (long)(Math.random() * 500);

            Cloudlet cloudlet = new Cloudlet(i, length, 1, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
            cloudlet.setUserId(brokerId);
            cloudlets.add(cloudlet);
        }

        return cloudlets;
    }

    // Create cloudlets from scenario config
    private static List<Cloudlet> createCloudletsFromScenario(int brokerId, int numCloudlets) {
        List<Cloudlet> cloudlets = new ArrayList<>();
        UtilizationModel utilizationModel = new UtilizationModelFull();
        for (int i = 0; i < numCloudlets; i++) {
            long length = 10000 + (long)(Math.random() * 40000);
            long fileSize = 300 + (long)(Math.random() * 1000);
            long outputSize = 300 + (long)(Math.random() * 500);
            Cloudlet cloudlet = new Cloudlet(i, length, 1, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
            cloudlet.setUserId(brokerId);
            cloudlets.add(cloudlet);
        }
        return cloudlets;
    }

    /**
     * Print simulation results and performance comparisons
     */
    private static void printSimulationResults(DatacenterBroker broker) {
        List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();

        Log.printLine();
        Log.printLine("========== ADAPTIVE ZONE MODEL RESULTS ==========");
        Log.printLine();

        // Print zone statistics
        if (zoneManager != null) {
            zoneManager.printZoneStatistics();
        }

        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent + "Data center ID" + indent + "VM ID" + indent + "Time" + indent + "Start Time" + indent + "Finish Time");

        double totalResponseTime = 0;
        double totalUtilization = 0;
        int migrationCount = 0;
        int finishedCount = 0;
        DecimalFormat dft = new DecimalFormat("###.##");
        for (Cloudlet cloudlet : finishedCloudlets) {
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);
            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                Log.print("SUCCESS");
                Log.printLine(indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId() +
                        indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent + dft.format(cloudlet.getExecStartTime()) +
                        indent + indent + dft.format(cloudlet.getFinishTime()));
                totalResponseTime += (cloudlet.getFinishTime() - cloudlet.getExecStartTime());
                finishedCount++;
            }
        }

        // Calculate average response time
        double avgResponseTime = finishedCount > 0 ? totalResponseTime / finishedCount : 0;

        // Calculate resource utilization and migration count
        int totalVMs = vmList != null ? vmList.size() : 0;
        int zoneCount = zoneManager != null ? zoneManager.getZones().size() : 0;
        for (int i = 0; i < zoneCount; i++) {
            ZoneMetrics metrics = zoneManager.getZoneMetrics(i);
            if (metrics != null) {
                totalUtilization += metrics.getAverageCPU();
                migrationCount += metrics.getMigrationCount();
            }
        }
        double avgUtilization = zoneCount > 0 ? totalUtilization / zoneCount : 0;
        String energyEfficiency = avgUtilization > 2000 ? "High" : (avgUtilization > 1000 ? "Medium" : "Low");

        // Update GUI comparison panel with real data
        if (dashboard != null && dashboard.getComparisonPanel() != null) {
            java.util.List<Object[]> rows = new java.util.ArrayList<>();
            rows.add(new Object[] {
                "Adaptive Zone Model",
                String.valueOf(totalVMs),
                dft.format(avgResponseTime),
                dft.format(avgUtilization),
                String.valueOf(migrationCount),
                energyEfficiency
            });
            dashboard.getComparisonPanel().setComparisonData(rows);
        }

        // Print performance metrics
        if (statsAnalyzer != null) {
            statsAnalyzer.generatePerformanceReport();
        }
    }
}