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
    <#list serializeFields.intLongValuePairListFields as field>
        java.util.Iterator iterator = super.getDetailGroup().values().iterator();
        while (iterator.hasNext()) {
            remoteBuilder.addDataIntLongPairList(((org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValue)(iterator.next())).serialize());
        }
    </#list>

    return remoteBuilder;
}