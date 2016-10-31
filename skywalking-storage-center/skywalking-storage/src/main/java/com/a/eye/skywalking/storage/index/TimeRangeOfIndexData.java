package com.a.eye.skywalking.storage.index;

/**
 * Created by xin on 2016/10/31.
 */
public class TimeRangeOfIndexData {
    private String indexDataFileName;

    private long startTime;

    private long endTime;


    public TimeRangeOfIndexData() {
    }

    public TimeRangeOfIndexData(String fileName) {
        this.indexDataFileName = fileName;
    }

    public TimeRangeOfIndexData(String fileName, long startTime, long endTime) {
        this.indexDataFileName = fileName;
        this.startTime = startTime;
        this.endTime = endTime;
    }


    public String getIndexDataFileName() {
        return indexDataFileName;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public String buildConnectionURL() {
        // TODO: 2016/10/31 通过参数构建连接URL
        return null;
    }

    @Override
    public String toString() {
        return "TimeRangeOfIndexData{" + "indexDataFileName='" + indexDataFileName + '\'' + ", startTime=" + startTime
                + ", endTime=" + endTime + '}';
    }
}
