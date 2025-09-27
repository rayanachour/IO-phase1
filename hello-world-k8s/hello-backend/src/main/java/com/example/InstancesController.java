package com.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
public class InstancesController {

  @CrossOrigin(origins = "http://localhost:3000")
  @PostMapping("/instances")
  public ResponseEntity<?> createInstance() {
    final String ns = "default";
    final String name = "hello-" + UUID.randomUUID().toString().substring(0, 6);

    try {
      // 1) Create an OPC UA deployment (our local image) exposing 4840 in the pod
      runOrThrow(new String[]{
          "kubectl","create","deployment",name,
          "--image=opcua-demo:v1","--port=4840","-n",ns
      }, 30);
    
      // 2) Expose as a ClusterIP service on 4840 (no NodePort needed for the demo)
      runOrThrow(new String[]{
          "kubectl","expose","deployment",name,
          "--type=ClusterIP","--port=4840","--target-port=4840","-n",ns
      }, 30);
    
      // 3) Wait briefly for the pod to become Ready (bounded)
      try {
        runOrThrow(new String[]{
            "kubectl","rollout","status","deploy/"+name,"-n",ns,"--timeout=40s"
        }, 45);
      } catch (Exception ignored) {}
    
      // 4) Pick a free localhost port and port-forward the Service to it
      int localPort = findFreePort();
      new ProcessBuilder(
          "kubectl","port-forward","svc/"+name, localPort + ":4840", "-n", ns, "--address","127.0.0.1"
      ).redirectErrorStream(true).start();
    
      // Tiny delay to let the forward bind before we return
      Thread.sleep(1000);
    
      String url = "opc.tcp://127.0.0.1:" + localPort;  
      return ResponseEntity.ok(Map.of("endpoint", url));
    

    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.status(500).body(Map.of(
          "error", e.getMessage() == null ? "internal error" : e.getMessage()
      ));
    }
  }

  // ---------- helpers ----------
  private static void runOrThrow(String[] cmd, int timeoutSec) throws Exception {
    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
      while (r.readLine() != null) { /* drain output so the process doesn't block */ }
    }
    boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
    if (!finished) { p.destroyForcibly(); throw new RuntimeException(String.join(" ", cmd) + " timed out"); }
    if (p.exitValue() != 0) throw new RuntimeException(String.join(" ", cmd) + " failed");
  }

  private static int findFreePort() throws Exception {
    try (ServerSocket s = new ServerSocket(0)) {
      s.setReuseAddress(true);
      return s.getLocalPort();
    }
  }
}
