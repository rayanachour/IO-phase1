package com.example;

public record InstanceResponse(
        String instanceId,
        String endpoint,
        int externalPort,
        String targetGroupArn,
        String taskArn,
        String privateIp
) {}
