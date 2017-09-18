package org.skywalking.apm.agent.core.context.ids;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.skywalking.apm.agent.core.context.ids.base64.Base64;
import org.skywalking.apm.network.proto.UniqueId;

/**
 * @author wusheng
 */
public class ID {
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private long part1;
    private long part2;
    private long part3;
    private String encoding;

    public ID(long part1, long part2, long part3) {
        this.part1 = part1;
        this.part2 = part2;
        this.part3 = part3;
        this.encoding = null;
    }

    public ID(String encodingString) {
        int index = 0;
        for (int part = 0; part < 3; part++) {
            String encodedString;
            char potentialTypeChar = encodingString.charAt(index);
            long value;
            if (potentialTypeChar == '#') {
                encodedString = encodingString.substring(index + 1, index + 5);
                index += 5;
                value = ByteBuffer.wrap(DECODER.decode(encodedString)).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(0);
            } else if (potentialTypeChar == '$') {
                encodedString = encodingString.substring(index + 1, index + 9);
                index += 9;
                value = ByteBuffer.wrap(DECODER.decode(encodedString)).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(0);
            } else {
                encodedString = encodingString.substring(index, index + 12);
                index += 12;
                value = ByteBuffer.wrap(DECODER.decode(encodedString)).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(0);
            }

            if (part == 0) {
                part1 = value;
            } else if (part == 1) {
                part2 = value;
            } else {
                part3 = value;
            }

        }
    }

    public String encode() {
        if (encoding == null) {
            encoding = long2Base64(part1) + long2Base64(part2) + long2Base64(part3);
        }
        return encoding;
    }

    private String long2Base64(long partN) {
        if (partN < 0) {
            throw new IllegalArgumentException("negative value.");
        }
        if (partN < 32768) {
            // 0 - 32767
            // "#" as a prefix of a short value with base64 encoding.
            byte[] data = new byte[2];
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put((short)partN);
            return '#' + ENCODER.encodeToString(data);
        } else if (partN <= 2147483647) {
            // 32768 - 2147483647
            // "$" as a prefix of an integer value (greater than a short) with base64 encoding.
            byte[] data = new byte[4];
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put((int)partN);
            return '$' + ENCODER.encodeToString(data);
        } else {
            // > 2147483647
            // a long value (greater than an integer)
            byte[] data = new byte[8];
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().put(partN);
            return ENCODER.encodeToString(data);
        }
    }

    @Override public String toString() {
        return part1 + "." + part2 + '.' + part3;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ID id = (ID)o;

        if (part1 != id.part1)
            return false;
        if (part2 != id.part2)
            return false;
        return part3 == id.part3;
    }

    @Override public int hashCode() {
        int result = (int)(part1 ^ (part1 >>> 32));
        result = 31 * result + (int)(part2 ^ (part2 >>> 32));
        result = 31 * result + (int)(part3 ^ (part3 >>> 32));
        return result;
    }

    public UniqueId transform() {
        return UniqueId.newBuilder().addIdParts(part1).addIdParts(part2).addIdParts(part3).build();
    }
}
