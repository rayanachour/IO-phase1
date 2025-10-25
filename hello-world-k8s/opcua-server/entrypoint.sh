#!/usr/bin/env bash
set -euo pipefail

# Paths for XML template and destination
XML_PATH="${OPCUA_XML_PATH:-/app/config/server-config.xml}"
XML_TEMPLATE="${OPCUA_XML_TEMPLATE:-/app/config/server-config.xml.template}"

# Fargate task will inject these environment variables
PORT="${OPCUA_PORT:-4840}"
ENDPOINT="${OPCUA_ENDPOINT:-opc.tcp://0.0.0.0:${PORT}}"

# Patch the template to bind 4840 internally but advertise the NLB DNS + dynamic port
cp "$XML_TEMPLATE" "$XML_PATH"
sed -i "s|__BIND_PORT__|${PORT}|g" "$XML_PATH"
sed -i "s|__ENDPOINT_URL__|${ENDPOINT}|g" "$XML_PATH"

echo "✅ Patched OPC UA XML: bind=${PORT}, advertised=${ENDPOINT}"
echo "🚀 Starting OPC UA server..."

exec "$@"
