package org.cloudsim.adaptive.zone.algorithms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.Log;


/**
 * First Fit VM Allocation Algorithm for comparison
 */
public class FirstFitAllocation extends VmAllocationPolicy {

    private Map<String, Vm> vmTable;

    public FirstFitAllocation(List<? extends Host> hostList) {
        super(hostList);
        this.vmTable = new HashMap<>();
    }

    @Override
    public boolean allocateHostForVm(Vm vm) {
        // Find first suitable host
        for (Host host : getHostList()) {
            if (host.isSuitableForVm(vm)) {
                if (host.vmCreate(vm)) {
                    vmTable.put(vm.getUid(), vm);
                    Log.printLine("FirstFit: VM " + vm.getId() + " allocated to Host " + host.getId());
                    return true;
                }
            }
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