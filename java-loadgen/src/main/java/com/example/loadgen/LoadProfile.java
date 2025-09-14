package com.example.loadgen;
public class LoadProfile {
    private String name;
    private int tags_per_second;
    private int alarms_per_second;
    private int duration_seconds;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTags_per_second() { return tags_per_second; }
    public void setTags_per_second(int tags_per_second) { this.tags_per_second = tags_per_second; }
    public int getAlarms_per_second() { return alarms_per_second; }
    public void setAlarms_per_second(int alarms_per_second) { this.alarms_per_second = alarms_per_second; }
    public int getDuration_seconds() { return duration_seconds; }
    public void setDuration_seconds(int duration_seconds) { this.duration_seconds = duration_seconds; }
}
