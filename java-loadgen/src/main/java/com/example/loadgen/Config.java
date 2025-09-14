package com.example.loadgen;

import java.util.List;

public class Config {
    private Service service;
    private Connectivity connectivity;
    private PerformanceTargets performance_targets;
    private List<LoadProfile> load_profiles;
    private Providers providers;

    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }
    public Connectivity getConnectivity() { return connectivity; }
    public void setConnectivity(Connectivity connectivity) { this.connectivity = connectivity; }
    public PerformanceTargets getPerformance_targets() { return performance_targets; }
    public void setPerformance_targets(PerformanceTargets performance_targets) { this.performance_targets = performance_targets; }
    public List<LoadProfile> getLoad_profiles() { return load_profiles; }
    public void setLoad_profiles(List<LoadProfile> load_profiles) { this.load_profiles = load_profiles; }
    public Providers getProviders() { return providers; }
    public void setProviders(Providers providers) { this.providers = providers; }
}
