package org.apache.skywalking.apm.plugin.seata.enhanced;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;

abstract class EnhancedRequestHelper {
  private static final Charset UTF8 = Charset.forName("utf-8");

  static byte[] encode(final byte[] encodedPart,
                       final Map<String, String> headers) {
    final ByteBuffer byteBuffer = ByteBuffer.allocate(2048);

    byteBuffer.put(encodedPart);

    if (!headers.isEmpty()) {
      byteBuffer.putInt(headers.size());
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
      byteBuffer.putInt(0);
    }

    byteBuffer.flip();
    byte[] content = new byte[byteBuffer.limit()];
    byteBuffer.get(content);
    return content;
  }

  static void decode(final ByteBuffer byteBuffer,
                     final Map<String, String> headers) {
    final int headersCount = byteBuffer.getInt();
    for (int i = 0; i < headersCount; i++) {
      byte[] keyBs = new byte[byteBuffer.getShort()];
      byteBuffer.get(keyBs);
      byte[] valBs = new byte[byteBuffer.getShort()];
      byteBuffer.get(valBs);
      headers.put(new String(keyBs), new String(valBs));
    }
  }
}
