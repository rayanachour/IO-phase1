const { OPCUAServer, Variant, DataType, nodesets } = require("node-opcua");

(async () => {
  const server = new OPCUAServer({
    port: 4840,
    buildInfo: { productName: "DemoOPCUA", buildNumber: "1", buildDate: new Date() },
    nodeset_filename: [ nodesets.standard ]
  });
  await server.initialize();

  const addressSpace = server.engine.addressSpace;
  const namespace = addressSpace.getOwnNamespace();

  const device = namespace.addObject({ organizedBy: addressSpace.rootFolder.objects, browseName: "DemoDevice" });
  namespace.addVariable({
    componentOf: device,
    browseName: "Temperature",
    nodeId: "ns=1;s=Temperature",
    dataType: "Double",
    value: { get: () => new Variant({ dataType: DataType.Double, value: 20 + Math.random() * 5 }) }
  });

  await server.start();
  console.log("OPC UA server listening on opc.tcp://0.0.0.0:4840");
})();
