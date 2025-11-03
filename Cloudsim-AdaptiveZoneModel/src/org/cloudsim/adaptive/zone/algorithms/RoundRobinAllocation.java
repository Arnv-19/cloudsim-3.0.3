package org.cloudsim.adaptive.zone.algorithms;

import java.util.HashMap;
import org.cloudbus.cloudsim.Log;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;

/**
 * Round Robin VM Allocation Algorithm for comparison
 */
public class RoundRobinAllocation extends VmAllocationPolicy {

    private Map<String, Vm> vmTable;
    private int currentHostIndex;

    public RoundRobinAllocation(List<? extends Host> hostList) {
        super(hostList);
        this.vmTable = new HashMap<>();
        this.currentHostIndex = 0;
    }

    @Override
    public boolean allocateHostForVm(Vm vm) {
        int attempts = 0;
        int hostCount = getHostList().size();

        // Try each host in round-robin fashion
        while (attempts < hostCount) {
            Host host = getHostList().get(currentHostIndex);

            if (host.isSuitableForVm(vm)) {
                if (host.vmCreate(vm)) {
                    vmTable.put(vm.getUid(), vm);
                    Log.printLine("RoundRobin: VM " + vm.getId() + " allocated to Host " + host.getId());
                    currentHostIndex = (currentHostIndex + 1) % hostCount;
                    return true;
                }
            }

            currentHostIndex = (currentHostIndex + 1) % hostCount;
            attempts++;
        }

        return false;
    }

    @Override
    public boolean allocateHostForVm(Vm vm, Host host) {
        if (host.vmCreate(vm)) {
            vmTable.put(vm.getUid(), vm);
            return true;
        }
        return false;
    }

    @Override
    public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
        return null;
    }

    @Override
    public void deallocateHostForVm(Vm vm) {
        Host host = vm.getHost();
        if (host != null) {
            host.vmDestroy(vm);
            vmTable.remove(vm.getUid());
        }
    }

    @Override
    public Host getHost(Vm vm) {
        return vm.getHost();
    }

    @Override
    public Host getHost(int vmId, int userId) {
        return vmTable.get(Vm.getUid(userId, vmId)).getHost();
    }
}