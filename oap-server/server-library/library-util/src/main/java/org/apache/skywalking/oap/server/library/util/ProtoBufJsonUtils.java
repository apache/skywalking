package org.apache.skywalking.oap.server.library.util;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;

public class ProtoBufJsonUtils {

    public static String toJSON(Message sourceMessage) throws IOException {
        return JsonFormat.printer().print(sourceMessage);
    }

    /**
     * Extract data from a JSON String and use them to construct a Protocol Buffers Message.
     *
     * @param json          A JSON data string to parse
     * @param targetBuilder A Message builder to use to construct the resulting Message
     * @return the constructed Message
     * @throws com.google.protobuf.InvalidProtocolBufferException Thrown in case of invalid Message data
     */
    public static Message fromJSON(String json, Message.Builder targetBuilder) throws IOException {
        JsonFormat.parser()
                  .usingTypeRegistry(JsonFormat.TypeRegistry.newBuilder()
                                                            .add(targetBuilder.getDescriptorForType())
                                                            .build())
                  .ignoringUnknownFields()
                  .merge(json, targetBuilder);
        return targetBuilder.build();
    }
}
