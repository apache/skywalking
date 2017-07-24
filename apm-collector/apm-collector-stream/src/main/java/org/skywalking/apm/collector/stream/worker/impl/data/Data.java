package org.skywalking.apm.collector.stream.worker.impl.data;

/**
 * @author pengys5
 */
public class Data {
    private int defineId;
    private String[] dataStrings;
    private Long[] dataLongs;
    private Float[] dataFloats;

    public Data(int defineId, int stringCapacity, int longCapacity, int floatCapacity) {
        this.defineId = defineId;
        this.dataStrings = new String[stringCapacity];
        this.dataLongs = new Long[longCapacity];
        this.dataFloats = new Float[floatCapacity];
    }

    public void setDataString(int position, String value) {
        dataStrings[position] = value;
    }

    public void setDataLong(int position, Long value) {
        dataLongs[position] = value;
    }

    public void setDataFloat(int position, Float value) {
        dataFloats[position] = value;
    }

    public String getDataString(int position) {
        return dataStrings[position];
    }

    public Long getDataLong(int position) {
        return dataLongs[position];
    }

    public Float getDataFloat(int position) {
        return dataFloats[position];
    }

    public String id() {
        return dataStrings[0];
    }

    public int getDefineId() {
        return defineId;
    }
}
