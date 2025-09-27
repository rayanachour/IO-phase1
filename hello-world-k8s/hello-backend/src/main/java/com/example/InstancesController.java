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
      // 1) Create a super-simple app (nginx) and expose it
      runOrThrow(new String[]{"kubectl","create","deployment",name,
          "--image=nginx:1.25-alpine","--port=80","-n",ns}, 20);

      runOrThrow(new String[]{"kubectl","expose","deployment",name,
          "--type=NodePort","--port=80","--target-port=80","-n",ns}, 20);

      // 2) Wait briefly for the pod to become Ready (bounded)
      try {
        runOrThrow(new String[]{"kubectl","rollout","status","deploy/"+name,"-n",ns,"--timeout=30s"}, 35);
      } catch (Exception ignored) {}

      // 3) Pick a free local port and start a port-forward to the Service
      int localPort = findFreePort();
      new ProcessBuilder("kubectl","port-forward","svc/"+name, localPort + ":80","-n",ns,"--address","127.0.0.1")
          .redirectErrorStream(true)
          .start(); // let it run in background for demo

      // 4) Give the forward a moment to bind
      Thread.sleep(900);

      String url = "http://127.0.0.1:" + localPort;
      return ResponseEntity.ok(Map.of("endpoint", url));

    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.status(500).body(Map.of("error", e.getMessage() == null ? "internal error" : e.getMessage()));
    }
  }

  // --- helpers ---
  private static void runOrThrow(String[] cmd, int timeoutSec) throws Exception {
    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
      while (r.readLine() != null) { /* drain */ }
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
