package com.example.loadgen;
public class PerformanceTargets {
    private int tags_per_second;
    private int alarms_per_second;
    private int p99_latency_ms;
    public int getTags_per_second() { return tags_per_second; }
    public void setTags_per_second(int tags_per_second) { this.tags_per_second = tags_per_second; }
    public int getAlarms_per_second() { return alarms_per_second; }
    public void setAlarms_per_second(int alarms_per_second) { this.alarms_per_second = alarms_per_second; }
    public int getP99_latency_ms() { return p99_latency_ms; }
    public void setP99_latency_ms(int p99_latency_ms) { this.p99_latency_ms = p99_latency_ms; }
}
