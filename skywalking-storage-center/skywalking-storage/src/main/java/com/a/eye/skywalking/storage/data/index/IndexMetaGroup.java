package com.a.eye.skywalking.storage.data.index;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 2016/11/4.
 */
public class IndexMetaGroup {

    private long timestamp;

    private List<IndexMetaInfo> metaInfo;

    public IndexMetaGroup(long timestamp) {
        this.timestamp = timestamp;
        metaInfo = new ArrayList<IndexMetaInfo>();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<IndexMetaInfo> getMetaInfo() {
        return metaInfo;
    }

    public void addIndexMetaInfo(IndexMetaInfo info) {
        this.metaInfo.add(info);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        IndexMetaGroup that = (IndexMetaGroup) o;

        return timestamp == that.timestamp;

    }

    @Override
    public int hashCode() {
        return (int) (timestamp ^ (timestamp >>> 32));
    }

    public int size() {
        return metaInfo.size();
    }
}
