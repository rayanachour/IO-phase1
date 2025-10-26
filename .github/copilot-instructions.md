# Copilot Instructions for IO-phase1

## Project Overview
This repository contains a multi-component demo for OPC UA cloud integration, including:
- **hello-world-k8s/**: Kubernetes demo with backend (Spring Boot), frontend (React), and OPC UA server (Node.js)
- **java-loadgen/**: Java-based load generator for simulating OPC UA traffic, configurable via YAML
- **config/**: Centralized configuration (YAML) for loadgen and other services
- **scripts/**: Utility scripts for building and running components

## Architecture & Data Flow
- **Frontend** (`hello-frontend`): React app to trigger instance creation and display OPC UA endpoints.
- **Backend** (`hello-backend`): Spring Boot REST API, `/instances` endpoint returns OPC UA endpoint (from env or default). Used by frontend.
- **OPCUA Server** (`opcua-server`): Node.js server exposing a demo OPC UA endpoint on port 4840. Dockerized for local or ECS/Fargate deployment.
- **Load Generator** (`java-loadgen`): Reads `config/config.yml` and simulates load profiles (see `App.java`).

## Developer Workflows
- **Build & Run Loadgen**: Use `scripts/run-loadgen.sh [profile]` (default: baseline). This cleans, builds, and runs the shaded JAR with the specified profile from YAML.
- **Build Backend**: `cd hello-world-k8s/hello-backend && mvn package`
- **Run Backend**: `java -jar target/hello-backend-1.0.0.jar`
- **Run Frontend**: `cd hello-world-k8s/hello-frontend && npm install && npm start`
- **Run OPCUA Server**: `cd hello-world-k8s/opcua-server && npm install && node server.js` or build Docker image per `Dockerfile`.

## Conventions & Patterns
- **Config**: All loadgen parameters and profiles are in `config/config.yml`. Use env vars for secrets (see `${OPCUA_USER}`/`${OPCUA_PASS}` in YAML).
- **Endpoints**: Backend endpoint is set via `OPCUA_ENDPOINT` env var or hardcoded fallback.
- **Frontend/Backend CORS**: Backend allows CORS from `localhost:3000` for local dev.
- **Java**: Java 17+ for loadgen, Java 21+ for backend. Use Maven Shade for fat JARs.
- **Node.js**: Use `node-opcua` for server, port 4840.
- **Docker/ECS**: See `opcua-task.json` and `trust-ecs-tasks.json` for AWS Fargate deployment.

## Key Files & Directories
- `config/config.yml`: Main config for loadgen
- `scripts/run-loadgen.sh`: Build/run script for loadgen
- `hello-world-k8s/hello-backend/src/main/java/com/example/InstancesController.java`: REST endpoint logic
- `hello-world-k8s/opcua-server/server.js`: Demo OPC UA server
- `hello-world-k8s/opcua-server/Dockerfile`: Containerization for OPC UA server

## Tips for AI Agents
- Always check `config/config.yml` for runtime parameters and profiles
- Use provided scripts for builds/runs to ensure correct environment
- When adding new load profiles, update both YAML and Java POJOs if schema changes
- For cloud deployment, update ECS task files and Docker images accordingly

---
_Last updated: 2025-10-14_
