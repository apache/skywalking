package org.skywalking.apm.collector.core.stream;

/**
 * @author pengys5
 */
public class Data extends AbstractHashMessage {
    private final int stringCapacity;
    private final int longCapacity;
    private final int doubleCapacity;
    private final int integerCapacity;
    private final int booleanCapacity;
    private final int byteCapacity;
    private String[] dataStrings;
    private Long[] dataLongs;
    private Double[] dataDoubles;
    private Integer[] dataIntegers;
    private Boolean[] dataBooleans;
    private byte[][] dataBytes;

    public Data(String id, int stringCapacity, int longCapacity, int doubleCapacity, int integerCapacity,
        int booleanCapacity, int byteCapacity) {
        super(id);
        this.dataStrings = new String[stringCapacity];
        this.dataStrings[0] = id;
        this.dataLongs = new Long[longCapacity];
        this.dataDoubles = new Double[doubleCapacity];
        this.dataIntegers = new Integer[integerCapacity];
        this.dataBooleans = new Boolean[booleanCapacity];
        this.dataBytes = new byte[byteCapacity][];
        this.stringCapacity = stringCapacity;
        this.longCapacity = longCapacity;
        this.doubleCapacity = doubleCapacity;
        this.integerCapacity = integerCapacity;
        this.booleanCapacity = booleanCapacity;
        this.byteCapacity = byteCapacity;
    }

    public void setDataString(int position, String value) {
        dataStrings[position] = value;
    }

    public void setDataLong(int position, Long value) {
        dataLongs[position] = value;
    }

    public void setDataDouble(int position, Double value) {
        dataDoubles[position] = value;
    }

    public void setDataInteger(int position, Integer value) {
        dataIntegers[position] = value;
    }

    public void setDataBoolean(int position, Boolean value) {
        dataBooleans[position] = value;
    }

    public void setDataBytes(int position, byte[] dataBytes) {
        this.dataBytes[position] = dataBytes;
    }

    public String getDataString(int position) {
        return dataStrings[position];
    }

    public Long getDataLong(int position) {
        return dataLongs[position];
    }

    public Double getDataDouble(int position) {
        return dataDoubles[position];
    }

    public Integer getDataInteger(int position) {
        return dataIntegers[position];
    }

    public Boolean getDataBoolean(int position) {
        return dataBooleans[position];
    }

    public byte[] getDataBytes(int position) {
        return dataBytes[position];
    }

    public String id() {
        return dataStrings[0];
    }
}
