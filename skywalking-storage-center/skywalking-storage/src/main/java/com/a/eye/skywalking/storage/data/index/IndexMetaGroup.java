package com.a.eye.skywalking.storage.data.index;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by xin on 2016/11/4.
 */
public class IndexMetaGroup<V>{

    private V key;

    private List<IndexMetaInfo> metaInfo;

    public IndexMetaGroup(V key) {
        this.key = key;
        metaInfo = new ArrayList<IndexMetaInfo>();
    }

    public V getKey() {
        return key;
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

        IndexMetaGroup<?> that = (IndexMetaGroup<?>) o;

        return key.equals(that.key);

    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    public int size() {
        return metaInfo.size();
    }

}
