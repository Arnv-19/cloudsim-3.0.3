import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.*;

import org.cloudbus.cloudsim.Vm;

public class ScenarioLoader {

    private static final String SCENARIO_PATH = "src/main/java/org/cloudsim/adaptive/zone/scenerios/";

    public static Map<String, Object> loadScenario(String scenarioName) {
        try {
            String filePath = SCENARIO_PATH + scenarioName + ".json";
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            return gson.fromJson(new FileReader(filePath), type);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    private static List<Vm> createVirtualMachinesFromScenario(int brokerId, Map<String, Object> scenarioConfig) {
        List<Vm> vms = new ArrayList<>();
        String vmm = "Xen";
        
        List<Map<String, Object>> list2 = (List<Map<String, Object>>) scenarioConfig.get("vm_configurations");
		List<Map<String, Object>> list = list2;
		List<Map<String, Object>> vmConfigs = list;
        int vmId = 0;
        
        for (Map<String, Object> vmConf : vmConfigs) {
            int count = ((Double) vmConf.get("count")).intValue();
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

    public static void main(String[] args) {
        int brokerId = 1;

        // Choose which scenario to load
        Map<String, Object> scenario = loadScenario("scenerio1_config");
        
        List<Vm> vms = createVirtualMachinesFromScenario(brokerId, scenario);
        System.out.println("VMs created from scenario: " + vms.size());
    }
}
