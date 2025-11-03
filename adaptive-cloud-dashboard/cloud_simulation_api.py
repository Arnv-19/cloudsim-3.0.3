
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException
from fastapi.staticfiles import StaticFiles
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import asyncio
import json
from datetime import datetime
import numpy as np
import pandas as pd
from typing import List, Dict, Any
import uvicorn
import logging

# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Adaptive Cloud Simulation API", version="1.0.0")

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global state
simulation_state = {
    "running": False,
    "scenario": "idle",
    "metrics": {
        "cpu_utilization": 0.0,
        "ram_usage": 0.0,
        "storage_usage": 0.0,
        "bandwidth_usage": 0.0,
        "vm_migration_count": 0
    },
    "zone_status": {
        "high_resource_slow": {"vm_count": 0, "utilization": 0.0},
        "medium": {"vm_count": 0, "utilization": 0.0},
        "fast_small": {"vm_count": 0, "utilization": 0.0}
    },
    "vms": [],
    "hosts": []
}

# WebSocket connections
connected_clients: List[WebSocket] = []

class WebSocketManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)
        logger.info(f"New WebSocket connection. Total: {len(self.active_connections)}")

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)
        logger.info(f"WebSocket disconnected. Total: {len(self.active_connections)}")

    async def broadcast(self, message: str):
        if self.active_connections:
            disconnected = []
            for connection in self.active_connections:
                try:
                    await connection.send_text(message)
                except Exception as e:
                    logger.error(f"Error sending message: {e}")
                    disconnected.append(connection)

            # Remove disconnected clients
            for conn in disconnected:
                self.disconnect(conn)

manager = WebSocketManager()

# VM and Host classes (simplified for API)
class VirtualMachine:
    def __init__(self, vm_id, cpu_req, ram_req, storage_req, bandwidth_req):
        self.vm_id = vm_id
        self.cpu_req = cpu_req
        self.ram_req = ram_req
        self.storage_req = storage_req
        self.bandwidth_req = bandwidth_req
        self.current_zone = None
        self.host_id = None
        self.migration_count = 0

    def to_dict(self):
        return {
            "vm_id": self.vm_id,
            "cpu_req": self.cpu_req,
            "ram_req": self.ram_req,
            "storage_req": self.storage_req,
            "bandwidth_req": self.bandwidth_req,
            "current_zone": self.current_zone,
            "host_id": self.host_id,
            "migration_count": self.migration_count
        }

class Host:
    def __init__(self, host_id, total_cpu, total_ram, total_storage, total_bandwidth):
        self.host_id = host_id
        self.total_cpu = total_cpu
        self.total_ram = total_ram
        self.total_storage = total_storage
        self.total_bandwidth = total_bandwidth
        self.available_cpu = total_cpu
        self.available_ram = total_ram
        self.available_storage = total_storage
        self.available_bandwidth = total_bandwidth
        self.zones = {
            "high_resource_slow": {"vms": [], "cpu_allocation": 0, "ram_allocation": 0},
            "medium": {"vms": [], "cpu_allocation": 0, "ram_allocation": 0},
            "fast_small": {"vms": [], "cpu_allocation": 0, "ram_allocation": 0}
        }

    def to_dict(self):
        return {
            "host_id": self.host_id,
            "total_cpu": self.total_cpu,
            "total_ram": self.total_ram,
            "total_storage": self.total_storage,
            "total_bandwidth": self.total_bandwidth,
            "available_cpu": self.available_cpu,
            "available_ram": self.available_ram,
            "available_storage": self.available_storage,
            "available_bandwidth": self.available_bandwidth,
            "zones": self.zones
        }

# Initialize simulation data
def initialize_simulation():
    """Initialize hosts and VMs for simulation"""
    global simulation_state

    # Create hosts
    hosts = []
    for i in range(5):
        host = Host(f"Host_{i}", 200, 16000, 500000, 1000)
        hosts.append(host)

    # Create VMs with varied requirements
    vms = []
    for i in range(15):
        cpu_req = max(10, np.random.normal(50, 20))
        ram_req = max(1000, np.random.normal(4000, 1500))
        storage_req = max(10000, np.random.normal(50000, 20000))
        bandwidth_req = max(10, np.random.normal(100, 30))

        vm = VirtualMachine(f"VM_{i}", cpu_req, ram_req, storage_req, bandwidth_req)
        vms.append(vm)

    simulation_state["hosts"] = hosts
    simulation_state["vms"] = vms
    logger.info(f"Initialized simulation with {len(hosts)} hosts and {len(vms)} VMs")

# Simulation scenarios
class SimulationScenario:
    @staticmethod
    async def scenario_1_high_load():
        """Scenario 1: High CPU/RAM load simulation"""
        logger.info("Starting Scenario 1: High Load")
        simulation_state["scenario"] = "high_load"

        for step in range(30):  # 30 simulation steps
            # Simulate high load
            cpu_util = min(95, 60 + step * 1.2 + np.random.normal(0, 5))
            ram_usage = min(90, 50 + step * 1.0 + np.random.normal(0, 3))
            storage_usage = min(85, 40 + step * 0.8 + np.random.normal(0, 2))
            bandwidth_usage = min(80, 35 + step * 0.6 + np.random.normal(0, 4))

            migration_count = simulation_state["metrics"]["vm_migration_count"]
            if cpu_util > 80 and np.random.random() < 0.3:
                migration_count += 1

            simulation_state["metrics"] = {
                "cpu_utilization": max(0, cpu_util),
                "ram_usage": max(0, ram_usage),
                "storage_usage": max(0, storage_usage),
                "bandwidth_usage": max(0, bandwidth_usage),
                "vm_migration_count": migration_count
            }

            # Update zone status
            simulation_state["zone_status"] = {
                "high_resource_slow": {
                    "vm_count": max(0, 8 + np.random.randint(-2, 3)),
                    "utilization": min(100, cpu_util + np.random.normal(0, 10))
                },
                "medium": {
                    "vm_count": max(0, 5 + np.random.randint(-1, 2)),
                    "utilization": min(100, cpu_util * 0.7 + np.random.normal(0, 5))
                },
                "fast_small": {
                    "vm_count": max(0, 2 + np.random.randint(-1, 2)),
                    "utilization": min(100, cpu_util * 0.4 + np.random.normal(0, 3))
                }
            }

            # Broadcast update
            await manager.broadcast(json.dumps({
                "type": "metrics_update",
                "data": simulation_state["metrics"],
                "zone_status": simulation_state["zone_status"],
                "timestamp": datetime.now().isoformat()
            }))

            await asyncio.sleep(1)  # 1 second per simulation step

    @staticmethod
    async def scenario_2_zone_migration():
        """Scenario 2: Dynamic zone migration simulation"""
        logger.info("Starting Scenario 2: Zone Migration")
        simulation_state["scenario"] = "zone_migration"

        for step in range(25):
            # Simulate zone imbalance and migration
            if step < 10:
                # Initial imbalance
                high_zone_load = 90 - step * 2
                medium_zone_load = 40 + step * 3
                fast_zone_load = 20 + step * 1
            else:
                # Migration balancing
                high_zone_load = max(40, 70 - (step - 10) * 2)
                medium_zone_load = min(80, 60 + (step - 10) * 1)
                fast_zone_load = min(60, 30 + (step - 10) * 1.5)

            migration_count = simulation_state["metrics"]["vm_migration_count"]
            if step >= 8 and step <= 15:
                migration_count += np.random.randint(1, 4)

            avg_utilization = (high_zone_load + medium_zone_load + fast_zone_load) / 3

            simulation_state["metrics"] = {
                "cpu_utilization": avg_utilization,
                "ram_usage": avg_utilization * 0.9,
                "storage_usage": avg_utilization * 0.7,
                "bandwidth_usage": avg_utilization * 0.6,
                "vm_migration_count": migration_count
            }

            simulation_state["zone_status"] = {
                "high_resource_slow": {
                    "vm_count": max(2, int(8 - step * 0.2)),
                    "utilization": max(0, high_zone_load)
                },
                "medium": {
                    "vm_count": max(3, int(5 + step * 0.1)),
                    "utilization": max(0, medium_zone_load)
                },
                "fast_small": {
                    "vm_count": max(1, int(2 + step * 0.1)),
                    "utilization": max(0, fast_zone_load)
                }
            }

            await manager.broadcast(json.dumps({
                "type": "metrics_update",
                "data": simulation_state["metrics"],
                "zone_status": simulation_state["zone_status"],
                "timestamp": datetime.now().isoformat()
            }))

            await asyncio.sleep(1.2)

    @staticmethod
    async def scenario_3_resource_scaling():
        """Scenario 3: Resource scaling and optimization"""
        logger.info("Starting Scenario 3: Resource Scaling")
        simulation_state["scenario"] = "resource_scaling"

        for step in range(20):
            # Simulate resource scaling patterns
            scale_factor = 1 + 0.5 * np.sin(step * 0.3)  # Sinusoidal scaling

            base_cpu = 45 * scale_factor + np.random.normal(0, 5)
            base_ram = 50 * scale_factor + np.random.normal(0, 3)
            base_storage = 35 * scale_factor + np.random.normal(0, 4)
            base_bandwidth = 40 * scale_factor + np.random.normal(0, 6)

            simulation_state["metrics"] = {
                "cpu_utilization": min(100, max(0, base_cpu)),
                "ram_usage": min(100, max(0, base_ram)),
                "storage_usage": min(100, max(0, base_storage)),
                "bandwidth_usage": min(100, max(0, base_bandwidth)),
                "vm_migration_count": simulation_state["metrics"]["vm_migration_count"] + (1 if step % 4 == 0 else 0)
            }

            # Simulate zone balancing
            total_utilization = (base_cpu + base_ram + base_storage + base_bandwidth) / 4
            simulation_state["zone_status"] = {
                "high_resource_slow": {
                    "vm_count": max(2, int(6 + 2 * np.sin(step * 0.2))),
                    "utilization": min(100, max(0, total_utilization * 1.2))
                },
                "medium": {
                    "vm_count": max(3, int(7 + np.sin(step * 0.15))),
                    "utilization": min(100, max(0, total_utilization))
                },
                "fast_small": {
                    "vm_count": max(1, int(3 + np.sin(step * 0.25))),
                    "utilization": min(100, max(0, total_utilization * 0.8))
                }
            }

            await manager.broadcast(json.dumps({
                "type": "metrics_update",
                "data": simulation_state["metrics"],
                "zone_status": simulation_state["zone_status"],
                "timestamp": datetime.now().isoformat()
            }))

            await asyncio.sleep(1.5)

# API Routes
@app.get("/")
async def root():
    return {"message": "Adaptive Cloud Simulation API", "status": "running"}

@app.get("/api/status")
async def get_status():
    return {
        "simulation_running": simulation_state["running"],
        "current_scenario": simulation_state["scenario"],
        "metrics": simulation_state["metrics"],
        "zone_status": simulation_state["zone_status"]
    }

@app.post("/api/scenario/{scenario_id}")
async def start_scenario(scenario_id: int):
    if simulation_state["running"]:
        raise HTTPException(status_code=400, detail="Simulation already running")

    simulation_state["running"] = True

    try:
        if scenario_id == 1:
            asyncio.create_task(SimulationScenario.scenario_1_high_load())
        elif scenario_id == 2:
            asyncio.create_task(SimulationScenario.scenario_2_zone_migration())
        elif scenario_id == 3:
            asyncio.create_task(SimulationScenario.scenario_3_resource_scaling())
        else:
            simulation_state["running"] = False
            raise HTTPException(status_code=400, detail="Invalid scenario ID")

        return {"message": f"Started scenario {scenario_id}", "status": "success"}
    except Exception as e:
        simulation_state["running"] = False
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/stop")
async def stop_simulation():
    simulation_state["running"] = False
    simulation_state["scenario"] = "idle"
    return {"message": "Simulation stopped", "status": "success"}

@app.post("/api/reset")
async def reset_simulation():
    global simulation_state
    simulation_state["running"] = False
    simulation_state["scenario"] = "idle"
    simulation_state["metrics"] = {
        "cpu_utilization": 0.0,
        "ram_usage": 0.0,
        "storage_usage": 0.0,
        "bandwidth_usage": 0.0,
        "vm_migration_count": 0
    }
    simulation_state["zone_status"] = {
        "high_resource_slow": {"vm_count": 0, "utilization": 0.0},
        "medium": {"vm_count": 0, "utilization": 0.0},
        "fast_small": {"vm_count": 0, "utilization": 0.0}
    }
    initialize_simulation()
    return {"message": "Simulation reset", "status": "success"}

@app.get("/api/hosts")
async def get_hosts():
    return [host.to_dict() for host in simulation_state["hosts"]]

@app.get("/api/vms")
async def get_vms():
    return [vm.to_dict() for vm in simulation_state["vms"]]

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        while True:
            # Keep connection alive
            await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect(websocket)

# Initialize on startup
@app.on_event("startup")
async def startup_event():
    initialize_simulation()
    logger.info("FastAPI application started")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000, log_level="info")
