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

package org.apache.skywalking.apm.plugin.seata.enhanced;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * @author kezhenxu94
 */
abstract class EnhancedRequestHelper {
    private static final Charset UTF8 = Charset.forName("utf-8");
    private static final short MAGIC = (short) 0xdada;

    static byte[] encode(final byte[] encodedPart,
                         final Map<String, String> headers) {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(2048);

        byteBuffer.put(encodedPart);

        if (!headers.isEmpty()) {
            byteBuffer.putShort((short) headers.size());
            for (final Map.Entry<String, String> entry : headers.entrySet()) {
                byte[] keyBs = entry.getKey().getBytes(UTF8);
                byteBuffer.putShort((short) keyBs.length);
                if (keyBs.length > 0) {
                    byteBuffer.put(keyBs);
                }
                byte[] valBs = entry.getValue().getBytes(UTF8);
                byteBuffer.putShort((short) valBs.length);
                if (valBs.length > 0) {
                    byteBuffer.put(valBs);
                }
            }
        } else {
            byteBuffer.putShort((short) 0);
        }

        byteBuffer.flip();
        byte[] content = new byte[byteBuffer.limit()];
        byteBuffer.get(content);
        return content;
    }

    static void decode(final ByteBuffer byteBuffer,
                       final Map<String, String> headers) {
        // There may be cases where the TC is enhanced by SkyWalking
        // but the TM is not
        if (!byteBuffer.hasRemaining()) {
            return;
        }
        final int headersCount = byteBuffer.getShort();
        for (int i = 0; i < headersCount; i++) {
            byte[] keyBs = new byte[byteBuffer.getShort()];
            byteBuffer.get(keyBs);
            byte[] valBs = new byte[byteBuffer.getShort()];
            byteBuffer.get(valBs);
            headers.put(new String(keyBs), new String(valBs));
        }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    static boolean decode(final ByteBuf byteBuffer,
                          final Map<String, String> headers) {
        // There may be cases where the TC is enhanced by SkyWalking
        // but the TM is not
        if (byteBuffer.readableBytes() <= 2) {
            return false;
        }

        final short possibleMagic = byteBuffer.readShort();
        if (possibleMagic == MAGIC) {
            return true;
        }

        final int headersCount = possibleMagic;

        for (int i = 0; i < headersCount; i++) {
            if (byteBuffer.readableBytes() < 2) {
                return false;
            }

            final short keyLength = byteBuffer.readShort();
            if (byteBuffer.readableBytes() < keyLength) {
                return false;
            }
            byte[] keyBs = new byte[keyLength];
            byteBuffer.readBytes(keyBs);

            if (byteBuffer.readableBytes() < 2) {
                return false;
            }
            final short valLength = byteBuffer.readShort();
            if (byteBuffer.readableBytes() < valLength) {
                return false;
            }
            byte[] valBs = new byte[valLength];
            byteBuffer.readBytes(valBs);

            headers.put(new String(keyBs), new String(valBs));
        }
        return true;
    }
}
