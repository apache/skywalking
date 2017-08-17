package org.skywalking.apm.collector.stream.worker.impl.data;

import com.google.protobuf.ByteString;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.stream.worker.selector.AbstractHashMessage;

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

    public RemoteData serialize() {
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.setIntegerCapacity(integerCapacity);
        builder.setDoubleCapacity(doubleCapacity);
        builder.setStringCapacity(stringCapacity);
        builder.setLongCapacity(longCapacity);
        builder.setByteCapacity(byteCapacity);
        builder.setBooleanCapacity(booleanCapacity);

        for (int i = 0; i < dataStrings.length; i++) {
            builder.setDataStrings(i, dataStrings[i]);
        }
        for (int i = 0; i < dataIntegers.length; i++) {
            builder.setDataIntegers(i, dataIntegers[i]);
        }
        for (int i = 0; i < dataDoubles.length; i++) {
            builder.setDataDoubles(i, dataDoubles[i]);
        }
        for (int i = 0; i < dataLongs.length; i++) {
            builder.setDataLongs(i, dataLongs[i]);
        }
        for (int i = 0; i < dataBooleans.length; i++) {
            builder.setDataBooleans(i, dataBooleans[i]);
        }
        for (int i = 0; i < dataBytes.length; i++) {
            builder.setDataBytes(i, ByteString.copyFrom(dataBytes[i]));
        }
        return builder.build();
    }
}
