package com.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
      // Create nginx on :80 and expose NodePort 80->80
      runOrThrow(new String[]{"kubectl","create","deployment",name,"--image=nginx:1.25-alpine","--port=80","-n",ns}, 10);
      runOrThrow(new String[]{"kubectl","expose","deployment",name,"--type=NodePort","--port=80","--target-port=80","-n",ns}, 10);

      // Best-effort rollout wait (bounded)
      try { runOrThrow(new String[]{"kubectl","rollout","status","deploy/"+name,"-n",ns,"--timeout=30s"}, 35); } catch (Exception ignored) {}

      // Always compute URL via minikube IP + NodePort (no 'minikube service' hang)
      String nodePort = runAndCapture(new String[]{"kubectl","get","svc",name,"-n",ns,"-o","jsonpath={.spec.ports[0].nodePort}"}, 5).trim();
      String ip = runAndCapture(new String[]{"minikube","ip"}, 3).trim();
      String url = "http://" + ip + ":" + nodePort;

      return ResponseEntity.ok(Map.of("endpoint", url));

    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.status(500).body(Map.of("error", e.getMessage() == null ? "internal error" : e.getMessage()));
    }
  }

  // ---- helpers with hard timeouts ----

  private static void runOrThrow(String[] cmd, int timeoutSeconds) throws Exception {
    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
      while (r.readLine() != null) { /* consume */ }
    }
    boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
    if (!finished) {
      p.destroyForcibly();
      throw new RuntimeException(String.join(" ", cmd) + " timed out");
    }
    if (p.exitValue() != 0) throw new RuntimeException(String.join(" ", cmd) + " failed");
  }

  private static String runAndCapture(String[] cmd, int timeoutSeconds) throws Exception {
    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    StringBuilder sb = new StringBuilder();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
      String line; while ((line = r.readLine()) != null) sb.append(line).append('\n');
    }
    boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
    if (!finished) {
      p.destroyForcibly();
      throw new RuntimeException(String.join(" ", cmd) + " timed out");
    }
    if (p.exitValue() != 0) throw new RuntimeException(String.join(" ", cmd) + " failed");
    return sb.toString();
  }
}
