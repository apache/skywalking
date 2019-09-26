package org.apache.skywalking.plugin.test.mockcollector.entity;

public class ValidateData {
    public static ValidateData INSTANCE = new ValidateData();
    private RegistryItem registryItem;
    private SegmentItems segmentItem;

    public ValidateData() {
        registryItem = new RegistryItem();
        segmentItem = new SegmentItems();
    }

    public RegistryItem getRegistryItem() {
        return registryItem;
    }

    public SegmentItems getSegmentItem() {
        return segmentItem;
    }

    public static void clearData(){
        System.out.println("Clear Data");
        INSTANCE.segmentItem = new SegmentItems();
        INSTANCE.registryItem.getOperationNames().clear();
    }
}
