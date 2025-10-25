package com.example;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSubnetsRequest;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.ContainerOverride;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.TaskOverride;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ActionTypeEnum;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.CreateListenerRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.CreateTargetGroupRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ProtocolEnum;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.RegisterTargetsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetDescription;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupNotFoundException;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetTypeEnum;

@Configuration
@ConfigurationProperties(prefix = "aws.ecs")
class AwsProps {
    private String clusterArn;
    private String taskDefinitionArn;
    private List<String> subnets;
    private List<String> securityGroups;
    private String assignPublicIp;

    public String getClusterArn() { return clusterArn; }
    public void setClusterArn(String v) { clusterArn = v; }
    public String getTaskDefinitionArn() { return taskDefinitionArn; }
    public void setTaskDefinitionArn(String v) { taskDefinitionArn = v; }
    public List<String> getSubnets() { return subnets; }
    public void setSubnets(List<String> v) { subnets = v; }
    public List<String> getSecurityGroups() { return securityGroups; }
    public void setSecurityGroups(List<String> v) { securityGroups = v; }
    public String getAssignPublicIp() { return assignPublicIp; }
    public void setAssignPublicIp(String v) { assignPublicIp = v; }
}

@Configuration
@ConfigurationProperties(prefix = "aws.elbv2")
class ElbProps {
    private String nlbDns;
    public String getNlbDns() { return nlbDns; }
    public void setNlbDns(String v) { nlbDns = v; }
}

@Configuration
@ConfigurationProperties(prefix = "orchestrator")
class OrchProps {
    private String containerName;
    private int containerPort;
    private int waitForRunningSeconds;
    private int healthWaitSeconds;
    private int baseExternalPort;

    public String getContainerName() { return containerName; }
    public void setContainerName(String v) { containerName = v; }
    public int getContainerPort() { return containerPort; }
    public void setContainerPort(int v) { containerPort = v; }
    public int getWaitForRunningSeconds() { return waitForRunningSeconds; }
    public void setWaitForRunningSeconds(int v) { waitForRunningSeconds = v; }
    public int getHealthWaitSeconds() { return healthWaitSeconds; }
    public void setHealthWaitSeconds(int v) { healthWaitSeconds = v; }
    public int getBaseExternalPort() { return baseExternalPort; }
    public void setBaseExternalPort(int v) { baseExternalPort = v; }
}

@Service
public class AwsOrchestrator {

    private final EcsClient ecs;
    private final Ec2Client ec2;
    private final ElasticLoadBalancingV2Client elbv2;
    private final AwsProps aws;
    private final ElbProps elb;
    private final OrchProps orch;

    public AwsOrchestrator(
            AwsProps aws, ElbProps elb, OrchProps orch,
            @Value("${aws.region}") String regionStr
    ) {
        this.aws = aws;
        this.elb = elb;
        this.orch = orch;
        var creds = DefaultCredentialsProvider.create();
        var region = Region.of(regionStr);
        this.ecs   = EcsClient.builder().region(region).credentialsProvider(creds).build();
        this.ec2   = Ec2Client.builder().region(region).credentialsProvider(creds).build();
        this.elbv2 = ElasticLoadBalancingV2Client.builder().region(region).credentialsProvider(creds).build();
    }

    public InstanceResponse createInstance() {
        PortSlot slot = ensureNextFreePortSlot();
        String endpoint = "opc.tcp://" + elb.getNlbDns() + ":" + slot.port;
        String lbArn = loadBalancerArnByDns(elb.getNlbDns());
        List<String> placementSubnets = filterSubnetsToNlb(lbArn, aws.getSubnets());
        String taskArn = runTaskWithEnv(endpoint, placementSubnets);
        waitForTaskRunning(taskArn, Math.max(orch.getWaitForRunningSeconds(), 60));
        String ip = waitForPrivateIp(taskArn, orch.getWaitForRunningSeconds());
        registerIp(slot.targetGroupArn, ip, orch.getContainerPort());
        waitUntilHealthy(slot.targetGroupArn, ip, orch.getHealthWaitSeconds());

        return new InstanceResponse(
                "ua-" + UUID.randomUUID(),
                endpoint,
                slot.port,
                slot.targetGroupArn,
                taskArn,
                ip
        );
    }

    private static class PortSlot { int port; String targetGroupArn; PortSlot(int p,String a){port=p;targetGroupArn=a;} }

    private PortSlot ensureNextFreePortSlot() {
        String lbArn = loadBalancerArnByDns(elb.getNlbDns());
        int port = chooseNextExternalPort(lbArn, orch.getBaseExternalPort());
        String vpcId = vpcIdFromSubnet(aws.getSubnets().get(0));

        String tgName = tgNameFor(port);
        String tgArn = getOrCreateTargetGroup(tgName, vpcId, orch.getContainerPort());
        createListenerIfMissing(lbArn, port, tgArn);

        return new PortSlot(port, tgArn);
    }

    private String tgNameFor(int port) {
        String name = "opcua-tg-" + port;
        return name.length() > 32 ? name.substring(0, 32) : name;
    }

    private String loadBalancerArnByDns(String dns) {
        var lbs = elbv2.describeLoadBalancers(DescribeLoadBalancersRequest.builder().build()).loadBalancers();
        return lbs.stream()
                .filter(lb -> dns.equalsIgnoreCase(lb.dnsName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("NLB not found for DNS: " + dns))
                .loadBalancerArn();
    }

    private String vpcIdFromSubnet(String subnetId) {
        var resp = ec2.describeSubnets(DescribeSubnetsRequest.builder().subnetIds(subnetId).build());
        if (resp.subnets().isEmpty()) throw new RuntimeException("Subnet not found: " + subnetId);
        return resp.subnets().get(0).vpcId();
    }

    private int chooseNextExternalPort(String lbArn, int base) {
        var listeners = elbv2.describeListeners(DescribeListenersRequest.builder()
                .loadBalancerArn(lbArn).build()).listeners();
        var used = new HashSet<Integer>();
        for (var l : listeners) used.add(l.port());
        int p = base;
        while (used.contains(p)) p++;
        return p;
    }

    private String getOrCreateTargetGroup(String name, String vpcId, int targetPort) {
    try {
        var found = elbv2.describeTargetGroups(DescribeTargetGroupsRequest.builder().names(name).build())
                .targetGroups();
        if (!found.isEmpty()) return found.get(0).targetGroupArn();
    } catch (TargetGroupNotFoundException ignored) {}

    var resp = elbv2.createTargetGroup(CreateTargetGroupRequest.builder()
            .name(name)
            .protocol(ProtocolEnum.TCP)
            .port(targetPort)
            .vpcId(vpcId)
            .targetType(TargetTypeEnum.IP)
            .healthCheckProtocol(ProtocolEnum.TCP)
            .healthCheckPort("traffic-port")
            .healthCheckIntervalSeconds(10)
            .healthCheckTimeoutSeconds(6)
            .healthyThresholdCount(2)
            .unhealthyThresholdCount(2)
            .build());
    return resp.targetGroups().get(0).targetGroupArn();
}


    private void createListenerIfMissing(String lbArn, int externalPort, String tgArn) {
        var listeners = elbv2.describeListeners(DescribeListenersRequest.builder()
                .loadBalancerArn(lbArn).build()).listeners();
        boolean exists = listeners.stream().anyMatch(l -> l.port() == externalPort);
        if (exists) return;

        elbv2.createListener(CreateListenerRequest.builder()
                .loadBalancerArn(lbArn)
                .protocol(ProtocolEnum.TCP)
                .port(externalPort)
                .defaultActions(Action.builder()
                        .type(ActionTypeEnum.FORWARD)
                        .targetGroupArn(tgArn)
                        .build())
                .build());
    }


    private Set<String> getNlbSubnetIdsByArn(String lbArn) {
        var lb = elbv2.describeLoadBalancers(r -> r.loadBalancerArns(lbArn))
                      .loadBalancers().get(0);
        return lb.availabilityZones().stream()
                 .map(software.amazon.awssdk.services.elasticloadbalancingv2.model.AvailabilityZone::subnetId)
                 .collect(Collectors.toSet());
    }

    private List<String> filterSubnetsToNlb(String lbArn, List<String> ecsSubnets) {
        var nlbSubnets = getNlbSubnetIdsByArn(lbArn);
        var filtered = ecsSubnets.stream().filter(nlbSubnets::contains).toList();
        if (filtered.isEmpty()) {
            throw new IllegalStateException("No overlap between ECS subnets and NLB-enabled subnets.");
        }
        return filtered;
    }

    // ---- ECS task + networking ----

    private String runTaskWithEnv(String endpoint, List<String> placementSubnets) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("OPCUA_ENDPOINT", endpoint);
        env.put("OPCUA_PORT", String.valueOf(orch.getContainerPort()));
        env.put("OPCUA_XML_PATCH", "true");
        env.put("OPCUA_XML_PATH", "/app/config/server-config.xml");

        var envList = new ArrayList<KeyValuePair>();
        env.forEach((k, v) -> envList.add(KeyValuePair.builder().name(k).value(v).build()));

        var containerOverride = ContainerOverride.builder()
                .name(orch.getContainerName())
                .environment(envList)
                .build();

        var vpc = AwsVpcConfiguration.builder()
                .assignPublicIp(aws.getAssignPublicIp())
                .subnets(placementSubnets)               // filtered subnets here
                .securityGroups(aws.getSecurityGroups())
                .build();

        var req = RunTaskRequest.builder()
                .cluster(aws.getClusterArn())
                .taskDefinition(aws.getTaskDefinitionArn())
                .launchType(LaunchType.FARGATE)
                .networkConfiguration(NetworkConfiguration.builder()
                        .awsvpcConfiguration(vpc).build())
                .overrides(TaskOverride.builder().containerOverrides(containerOverride).build())
                .build();

        var resp = ecs.runTask(req);
        if (resp.failures() != null && !resp.failures().isEmpty()) {
            throw new RuntimeException("ECS runTask failed: " + resp.failures());
        }
        return resp.tasks().get(0).taskArn();
    }

    private void waitForTaskRunning(String taskArn, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis();
        while (System.currentTimeMillis() < deadline) {
            var task = ecs.describeTasks(r -> r.cluster(aws.getClusterArn()).tasks(taskArn))
                          .tasks().get(0);
            String last = task.lastStatus();
            if ("RUNNING".equalsIgnoreCase(last)) return;
            if ("STOPPED".equalsIgnoreCase(last)) {
                throw new RuntimeException("Task stopped early: " + task.stoppedReason());
            }
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
        throw new RuntimeException("Task did not reach RUNNING in time.");
    }

    private String waitForPrivateIp(String taskArn, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis();
        while (System.currentTimeMillis() < deadline) {
            var dtr = ecs.describeTasks(DescribeTasksRequest.builder()
                    .cluster(aws.getClusterArn())
                    .tasks(taskArn)
                    .build());

            if (dtr.tasks().isEmpty()) throw new RuntimeException("Task not found: " + taskArn);
            var t = dtr.tasks().get(0);

            if ("STOPPED".equals(t.lastStatus()) && t.stoppedReason() != null) {
                throw new RuntimeException("Task stopped before getting IP: " + t.stoppedReason());
            }

            for (var att : t.attachments()) {
                if ("ElasticNetworkInterface".equals(att.type())) {
                    for (var kv : att.details()) {
                        if ("privateIPv4Address".equals(kv.name())) {
                            return kv.value();
                        }
                    }
                }
            }
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
        throw new RuntimeException("Timed out waiting for private IP for task: " + taskArn);
    }

    private void registerIp(String targetGroupArn, String ip, int port) {
        elbv2.registerTargets(RegisterTargetsRequest.builder()
                .targetGroupArn(targetGroupArn)
                .targets(TargetDescription.builder().id(ip).port(port).build())
                .build());
    }

    private void waitUntilHealthy(String tgArn, String targetIp, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis();
        String lastState = null, lastReason = null, lastInfo = null;

        while (System.currentTimeMillis() < deadline) {
            var th = elbv2.describeTargetHealth(r -> r.targetGroupArn(tgArn));
            var desc = th.targetHealthDescriptions().stream()
                    .filter(d -> targetIp.equals(d.target().id()))
                    .findFirst().orElse(null);

            String state = (desc == null) ? "initial" : desc.targetHealth().stateAsString();
            String reason = (desc == null || desc.targetHealth().reasonAsString() == null) ? "" : desc.targetHealth().reasonAsString();
            String info = (desc == null || desc.targetHealth().description() == null) ? "" : desc.targetHealth().description();

            if (!state.equals(lastState) || !reason.equals(lastReason)) {
                System.out.printf("TG health: state=%s reason=%s info=%s%n", state, reason, info);
                lastState = state; lastReason = reason; lastInfo = info;
            }

            if ("healthy".equalsIgnoreCase(state)) return;
            if ("unused".equalsIgnoreCase(state)) {
                throw new RuntimeException("Target is UNUSED (task AZ not enabled on NLB).");
            }
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
        throw new RuntimeException("Target did not become healthy within timeout. Last=" +
                lastState + " reason=" + lastReason + " info=" + lastInfo);
    }

    public void deleteInstance(int port) {
        String lbArn = loadBalancerArnByDns(elb.getNlbDns());

        var listeners = elbv2.describeListeners(r -> r.loadBalancerArn(lbArn)).listeners();
        var listenerOpt = listeners.stream().filter(l -> l.port() == port).findFirst();
        if (listenerOpt.isEmpty()) return;

        var listener = listenerOpt.get();
        String tgArn = listener.defaultActions().get(0).targetGroupArn();

        elbv2.deleteListener(r -> r.listenerArn(listener.listenerArn()));
        elbv2.deleteTargetGroup(r -> r.targetGroupArn(tgArn));

        var tasks = ecs.listTasks(r -> r.cluster(aws.getClusterArn())).taskArns();
        if (!tasks.isEmpty()) {
            ecs.describeTasks(r -> r.cluster(aws.getClusterArn()).tasks(tasks))
               .tasks()
               .forEach(t -> ecs.stopTask(r -> r.cluster(aws.getClusterArn()).task(t.taskArn())));
        }
    }
}
