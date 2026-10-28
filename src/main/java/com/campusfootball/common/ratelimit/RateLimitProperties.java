package com.campusfootball.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private int loginPerMinute = 8;
    private int ratingPerMinute = 20;
    private int reportPerTenMinutes = 5;
    private int disciplinePerMinute = 10;
    private int matchWritePerMinute = 120;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getLoginPerMinute() { return loginPerMinute; }
    public void setLoginPerMinute(int loginPerMinute) { this.loginPerMinute = loginPerMinute; }
    public int getRatingPerMinute() { return ratingPerMinute; }
    public void setRatingPerMinute(int ratingPerMinute) { this.ratingPerMinute = ratingPerMinute; }
    public int getReportPerTenMinutes() { return reportPerTenMinutes; }
    public void setReportPerTenMinutes(int reportPerTenMinutes) { this.reportPerTenMinutes = reportPerTenMinutes; }
    public int getDisciplinePerMinute() { return disciplinePerMinute; }
    public void setDisciplinePerMinute(int disciplinePerMinute) { this.disciplinePerMinute = disciplinePerMinute; }
    public int getMatchWritePerMinute() { return matchWritePerMinute; }
    public void setMatchWritePerMinute(int matchWritePerMinute) { this.matchWritePerMinute = matchWritePerMinute; }
}