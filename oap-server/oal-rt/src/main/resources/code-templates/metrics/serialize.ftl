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

<#list serializeFields.dataTableFields as field>
    remoteBuilder.addDataTableStrings(${field.getter}().toStorageData());
</#list>

return remoteBuilder;
}