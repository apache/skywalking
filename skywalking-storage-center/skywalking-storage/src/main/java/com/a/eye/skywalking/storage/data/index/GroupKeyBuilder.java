package com.a.eye.skywalking.storage.data.index;

import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;

/**
 * Created by xin on 2016/11/6.
 */
public interface GroupKeyBuilder<T> {
    T buildKey(IndexMetaInfo metaInfo);
}
