/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.finagle;

import com.twitter.io.Buf;
import com.twitter.io.Bufs;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.StringUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.apache.skywalking.apm.plugin.finagle.Constants.EMPTY_SWCONTEXTCARRIER;

public class CodecUtils {

    static ILog LOGGER = LogManager.getLogger(CodecUtils.class);

    private static ThreadLocal<ByteArrayOutputStream> REUSED_BOS = new ThreadLocal<ByteArrayOutputStream>() {
        @Override
        protected ByteArrayOutputStream initialValue() {
            return new ByteArrayOutputStream(128);
        }
    };

    private static ByteArrayOutputStream getBos() {
        ByteArrayOutputStream bos = REUSED_BOS.get();
        bos.reset();
        return bos;
    }

    /**
     * Encodes the swContextCarrier to byte array.
     *
     * SWContextCarrier consists of some strings, such as operation name, {@link CarrierItem#getHeadKey()},
     * {@link CarrierItem#getHeadValue()}. We encode each string to byte array by
     * {@link #encodeStringToBytes(String)}, then assemble these byte arrays in a certain order, each byte array has
     * a fixed length of 4 bytes before it to record the length. The format as follow:
     *
     * |length of following byte array,4 byte|byte array|length of following byte array,4 byte|byte array|...
     *
     * The first byte array is operation name, followed by byte array of the key of first {@code CarrierItem},
     * followed by byte array of the value of first {@code CarrierItem}, followed by byte array of second {@code
     * CarrierItem}, and so on.
     *
     * @param swContextCarrier the swContextCarrier to encode
     * @return
     */
    static Buf encode(SWContextCarrier swContextCarrier) {
        if (StringUtil.isNotEmpty(swContextCarrier.getOperationName())
                && swContextCarrier.getCarrier() != null) {
            ByteArrayOutputStream bos = getBos();
            try (DataOutputStream dos = new DataOutputStream(bos)) {
                putString(dos, swContextCarrier.getOperationName());
                CarrierItem next = swContextCarrier.getCarrier().items();
                while (next.hasNext()) {
                    next = next.next();
                    if (next.getHeadKey() != null && next.getHeadValue() != null) {
                        putString(dos, next.getHeadKey());
                        putString(dos, next.getHeadValue());
                    }
                }
                bos.flush();
                return Bufs.ownedBuf(bos.toByteArray());
            } catch (Exception e) {
                LOGGER.error("encode swContextCarrier exception.", e);
            }
        }
        return Bufs.EMPTY;
    }

    /**
     * Decodes byte array to SWContextCarrier.
     *
     * First we read 4 bytes from byte array, composing them into an int value n, and read the following n bytes,
     * decodes them to a string by {@link #decodeStringFromBytes(byte[])}, and so on.
     *
     * The first string is operation name, other strings are a list pair of key and value, can be translated to
     * {@link ContextCarrier}
     *
     * For convenient, we use {@link ByteBuffer} to wrap byte array.
     *
     * @param buf the byte array to decode
     * @return
     */
    static SWContextCarrier decode(Buf buf) {
        try {
            byte[] bytes = Bufs.ownedByteArray(buf);
            if (bytes == null || bytes.length == 0) {
                return EMPTY_SWCONTEXTCARRIER;
            }
            ContextCarrier contextCarrier = new ContextCarrier();
            SWContextCarrier swContextCarrier = new SWContextCarrier();
            swContextCarrier.setContextCarrier(contextCarrier);

            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            String operationName = getNextString(byteBuffer);
            if (operationName != null) {
                swContextCarrier.setOperationName(operationName);
            }

            Map<String, String> data = readToMap(byteBuffer);
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                next.setHeadValue(data.get(next.getHeadKey()));
            }
            return swContextCarrier;
        } catch (Exception e) {
            LOGGER.error("decode swContextCarrier exception.", e);
        }
        return EMPTY_SWCONTEXTCARRIER;
    }

    private static void putString(DataOutputStream dos, String value) throws IOException {
        byte[] bytes = encodeStringToBytes(value);
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }

    private static String getNextString(ByteBuffer byteBuffer) {
        if (byteBuffer.hasRemaining()) {
            byte[] bytes = new byte[byteBuffer.getInt()];
            byteBuffer.get(bytes);
            return decodeStringFromBytes(bytes);
        }
        return null;
    }

    private static Map<String, String> readToMap(ByteBuffer byteBuffer) {
        Map<String, String> data = new HashMap<>();
        String key = null;
        while ((key = getNextString(byteBuffer)) != null) {
            data.put(key, getNextString(byteBuffer));
        }
        return data;
    }

    private static byte[] encodeStringToBytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String decodeStringFromBytes(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
