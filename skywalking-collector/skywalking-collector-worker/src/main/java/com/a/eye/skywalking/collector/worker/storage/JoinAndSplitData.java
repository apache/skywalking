package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.selector.AbstractHashMessage;

import java.util.*;

/**
 * @author pengys5
 */
public class JoinAndSplitData extends AbstractHashMessage implements Data {

    public static final String SPLIT = ",";

    private String id;

    private Map<String, Set<String>> data = new HashMap<>();

    public JoinAndSplitData(String key) {
        super(key);
        this.id = key;
    }

    public String getId() {
        return id;
    }

    public void set(String attributeName, String value) {
        if (!data.containsKey(attributeName)) {
            data.put(attributeName, new HashSet<>());
        }
        data.get(attributeName).add(value);
    }

    public void merge(JoinAndSplitData source) {
        source.data.forEach((attributeName, valueSet) -> valueSet.forEach(value -> set(attributeName, value)));
    }

    public void merge(Map<String, ?> source) {
        source.forEach((column, dbValue) -> {
            if (!AbstractIndex.TIME_SLICE.equals(column) && !AbstractIndex.AGG_COLUMN.equals(column)) {
                String[] dbValues = String.valueOf(dbValue).split(SPLIT);
                for (String value : dbValues) {
                    set(column, value);
                }
            }
        });
    }

    public Map<String, String> asMap() {
        Map<String, String> source = new HashMap<>();
        data.forEach((attributeName, valueSet) -> {
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
            source.put(attributeName, builder.toString());
        });

        return source;
    }
}