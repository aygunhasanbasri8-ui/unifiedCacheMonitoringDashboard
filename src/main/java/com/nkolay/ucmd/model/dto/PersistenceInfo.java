package com.nkolay.ucmd.model.dto;

/** Rehber §4.6 — INFO persistence. Kullanılmıyorsa NOT_APPLICABLE. */
public class PersistenceInfo {

    // RDB
    private boolean rdbEnabled;
    private boolean rdbBgsaveInProgress;
    private String rdbLastBgsaveStatus;     // ok | err
    private Long rdbLastSaveTime;           // epoch seconds
    private long rdbChangesSinceLastSave;

    // AOF
    private boolean aofEnabled;
    private boolean aofRewriteInProgress;
    private String aofLastBgrewriteStatus;
    private String aofLastWriteStatus;

    private boolean loading;

    private MetricStatus overallStatus;
    private MetricStatus rdbStatus;
    private MetricStatus aofStatus;
    private MetricMeta meta = new MetricMeta();

    public PersistenceInfo() {}

    public static PersistenceInfo notApplicable() {
        PersistenceInfo p = new PersistenceInfo();
        p.setOverallStatus(MetricStatus.NOT_APPLICABLE);
        return p;
    }

    public static PersistenceInfo unavailable() {
        PersistenceInfo p = new PersistenceInfo();
        p.setOverallStatus(MetricStatus.UNAVAILABLE);
        return p;
    }

    public boolean isRdbEnabled() { return rdbEnabled; }
    public void setRdbEnabled(boolean v) { this.rdbEnabled = v; }

    public boolean isRdbBgsaveInProgress() { return rdbBgsaveInProgress; }
    public void setRdbBgsaveInProgress(boolean v) { this.rdbBgsaveInProgress = v; }

    public String getRdbLastBgsaveStatus() { return rdbLastBgsaveStatus; }
    public void setRdbLastBgsaveStatus(String v) { this.rdbLastBgsaveStatus = v; }

    public Long getRdbLastSaveTime() { return rdbLastSaveTime; }
    public void setRdbLastSaveTime(Long v) { this.rdbLastSaveTime = v; }

    public long getRdbChangesSinceLastSave() { return rdbChangesSinceLastSave; }
    public void setRdbChangesSinceLastSave(long v) { this.rdbChangesSinceLastSave = v; }

    public boolean isAofEnabled() { return aofEnabled; }
    public void setAofEnabled(boolean v) { this.aofEnabled = v; }

    public boolean isAofRewriteInProgress() { return aofRewriteInProgress; }
    public void setAofRewriteInProgress(boolean v) { this.aofRewriteInProgress = v; }

    public String getAofLastBgrewriteStatus() { return aofLastBgrewriteStatus; }
    public void setAofLastBgrewriteStatus(String v) { this.aofLastBgrewriteStatus = v; }

    public String getAofLastWriteStatus() { return aofLastWriteStatus; }
    public void setAofLastWriteStatus(String v) { this.aofLastWriteStatus = v; }

    public boolean isLoading() { return loading; }
    public void setLoading(boolean loading) { this.loading = loading; }

    public MetricStatus getOverallStatus() { return overallStatus; }
    public void setOverallStatus(MetricStatus v) { this.overallStatus = v; }

    public MetricStatus getRdbStatus() { return rdbStatus; }
    public void setRdbStatus(MetricStatus v) { this.rdbStatus = v; }

    public MetricStatus getAofStatus() { return aofStatus; }
    public void setAofStatus(MetricStatus v) { this.aofStatus = v; }

    public MetricMeta getMeta() { return meta; }
    public void setMeta(MetricMeta meta) { this.meta = meta; }
}