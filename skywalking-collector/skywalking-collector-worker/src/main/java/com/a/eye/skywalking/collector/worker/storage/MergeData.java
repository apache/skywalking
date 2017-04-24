package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.selector.AbstractHashMessage;

import java.util.*;

/**
 * @author pengys5
 */
public class MergeData extends AbstractHashMessage implements Data {

    public static final String SPLIT = ",";

    private String id;

    private Map<String, Set<String>> mergeData = new HashMap<>();

    public MergeData(String key) {
        super(key);
        this.id = key;
    }

    public String getId() {
        return id;
    }

    public void setMergeData(String column, String value) {
        if (!mergeData.containsKey(column)) {
            mergeData.put(column, new HashSet<>());
        }
        mergeData.get(column).add(value);
    }

    public void merge(MergeData data) {
        data.mergeData.forEach((column, valueSet) -> valueSet.forEach(value -> setMergeData(column, value)));
    }

    public void merge(Map<String, ?> dbData) {
        dbData.forEach((column, dbValue) -> {
            if (!AbstractIndex.TIME_SLICE.equals(column) && !AbstractIndex.AGG_COLUMN.equals(column)) {
                String[] dbValues = String.valueOf(dbValue).split(SPLIT);
                for (String value : dbValues) {
                    setMergeData(column, value);
                }
            }
        });
    }

    public Map<String, String> asMap() {
        Map<String, String> source = new HashMap<>();
        mergeData.forEach((column, valueSet) -> {
            Iterator<String> iterator = valueSet.iterator();
            StringBuilder builder = new StringBuilder();

            int i = 0;

            while (iterator.hasNext()) {
                if (i == 0) {
                    builder.append(iterator.next());
                } else {
                    builder.append(SPLIT).append(iterator.next());
                }
                i++;
            }
            source.put(column, builder.toString());
        });

        return source;
    }
}