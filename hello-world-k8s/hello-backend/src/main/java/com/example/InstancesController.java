package com.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class InstancesController {
    private final AwsOrchestrator orchestrator;

    public InstancesController(AwsOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/instances")
    public ResponseEntity<InstanceResponse> create() {
        return ResponseEntity.ok(orchestrator.createInstance());
    }

    @DeleteMapping("/{port}")
    public ResponseEntity<String> deleteInstance(@PathVariable int port) {
        orchestrator.deleteInstance(port);
        return ResponseEntity.ok("Deleted instance on port " + port);
    }
}
