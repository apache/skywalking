public org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.Builder serialize() {
    org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.Builder remoteBuilder = org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.newBuilder();
    <#list serializeFields.stringFields as field>
        remoteBuilder.addDataStrings(${field.getter}());
    </#list>

    <#list serializeFields.longFields as field>
        remoteBuilder.addDataLongs(${field.getter}());
    </#list>

    <#list serializeFields.doubleFields as field>
        remoteBuilder.addDataDoubles(${field.getter}());
    </#list>

    <#list serializeFields.intFields as field>
        remoteBuilder.addDataIntegers(${field.getter}());
    </#list>
    java.util.Iterator iterator;
    org.apache.skywalking.oap.server.core.remote.grpc.proto.DataIntLongPairList.Builder pairListBuilder;
    <#list serializeFields.intKeyLongValueHashMapFields as field>
        iterator = super.${field.getter}().values().iterator();
        pairListBuilder = org.apache.skywalking.oap.server.core.remote.grpc.proto.DataIntLongPairList.newBuilder();
        while (iterator.hasNext()) {
            pairListBuilder.addValue(((org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValue)(iterator.next())).serialize());
        }
        remoteBuilder.addDataLists(pairListBuilder);
    </#list>

    return remoteBuilder;
}