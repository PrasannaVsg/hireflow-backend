package com.hireflow.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "obs")
public class IMannerProperties {

    private boolean enabled = false;
    private String apiEndpoint = "";
    private String apiKey = "";
    private String orgId = "sageit";
    private String projectId = "";
    private String applicationId = "";
    private String applicationName = "";
    private String environment = "development";
    private double samplingRate = 1.0;
    private boolean piiScrubbing = true;

    public boolean isEnabled()            { return enabled; }
    public String getApiEndpoint()        { return apiEndpoint; }
    public String getApiKey()             { return apiKey; }
    public String getOrgId()              { return orgId; }
    public String getProjectId()          { return projectId; }
    public String getApplicationId()      { return applicationId; }
    public String getApplicationName()    { return applicationName; }
    public String getEnvironment()        { return environment; }
    public double getSamplingRate()       { return samplingRate; }
    public boolean isPiiScrubbing()       { return piiScrubbing; }

    public void setEnabled(boolean v)           { this.enabled = v; }
    public void setApiEndpoint(String v)        { this.apiEndpoint = v; }
    public void setApiKey(String v)             { this.apiKey = v; }
    public void setOrgId(String v)              { this.orgId = v; }
    public void setProjectId(String v)          { this.projectId = v; }
    public void setApplicationId(String v)      { this.applicationId = v; }
    public void setApplicationName(String v)    { this.applicationName = v; }
    public void setEnvironment(String v)        { this.environment = v; }
    public void setSamplingRate(double v)       { this.samplingRate = v; }
    public void setPiiScrubbing(boolean v)      { this.piiScrubbing = v; }
}
