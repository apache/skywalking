package com.a.eye.skywalking.storage.data.index;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 2016/11/6.
 */
public class IndexMetaCollections {

    public static <T> List<IndexMetaGroup<T>> group(IndexMetaCollection indexMetaCollection,
            GroupKeyBuilder<T> builder) {
        List<IndexMetaGroup<T>> indexMetaGroups = new ArrayList<IndexMetaGroup<T>>();

        for (IndexMetaInfo metaInfo : indexMetaCollection) {
            T key = builder.buildKey(metaInfo);

            int index = indexMetaGroups.indexOf(new IndexMetaGroup(key));
            IndexMetaGroup metaGroup;

            if (index == -1) {
                metaGroup = new IndexMetaGroup(key);
                indexMetaGroups.add(metaGroup);
            } else {
                metaGroup = indexMetaGroups.get(index);
            }

            metaGroup.addIndexMetaInfo(metaInfo);
        }

        return indexMetaGroups;
    }
}
