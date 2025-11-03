
import subprocess
import sys
import os

def main():
    print("Starting Adaptive Cloud Simulation API...")

    # Install requirements
    print("Installing requirements...")
    subprocess.run([sys.executable, "-m", "pip", "install", "-r", "requirements.txt"])

    # Start the API
    print("Starting FastAPI server...")
    subprocess.run([sys.executable, "cloud_simulation_api.py"])

if __name__ == "__main__":
    main()
