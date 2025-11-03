#!/bin/bash
# Startup script for Cloud Simulation API

echo "Starting Adaptive Cloud Simulation..."

# Install requirements if needed
pip install -r requirements.txt

# Start the FastAPI server
python cloud_simulation_api.py
