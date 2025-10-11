package com.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class InstancesController {

  // put your NLB DNS here (or override with OPCUA_ENDPOINT env var)
  private static final String DEFAULT_ENDPOINT =
      "opc.tcp://opcua-nlb-103856889438b10c.elb.us-east-1.amazonaws.com:4840";

  @CrossOrigin(origins = "http://localhost:3000")
  @PostMapping("/instances")
  public ResponseEntity<?> createInstance() {
    // If you want to swap endpoints without code changes:
    String endpoint = System.getenv().getOrDefault("OPCUA_ENDPOINT", DEFAULT_ENDPOINT);
    return ResponseEntity.ok(Map.of("endpoint", endpoint));
  }
}
