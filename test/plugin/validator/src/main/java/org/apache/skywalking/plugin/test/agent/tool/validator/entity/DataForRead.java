package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

import java.util.ArrayList;
import java.util.List;

public class DataForRead implements Data {
    private RegistryItemsForRead registryItems;
    private List<SegmentItemForRead> segmentItems;

    public void setRegistryItems(RegistryItemsForRead registryItems) {
        this.registryItems = registryItems;
    }

    public RegistryItemsForRead getRegistryItems() {
        return registryItems;
    }

    public List<SegmentItemForRead> getSegmentItems() {
        return segmentItems;
    }

    public void setSegmentItems(List<SegmentItemForRead> segmentItems) {
        this.segmentItems = segmentItems;
    }

    @Override
    public RegistryItemsForRead registryItems() {
        return registryItems;
    }

    @Override
    public List<SegmentItem> segmentItems() {
        if (this.segmentItems == null) {
            return null;
        }

        return new ArrayList<>(this.segmentItems);
    }

}
