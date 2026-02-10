public org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.Builder serialize() {
org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.Builder remoteBuilder = org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.newBuilder();
<#if serializeFields.stringFields?? && serializeFields.stringFields?size gt 0>
    <#list serializeFields.stringFields as field>
        remoteBuilder.addDataStrings(${field.getter}() != null ? ${field.getter}() : "");
    </#list>
</#if>
<#if serializeFields.longFields?? && serializeFields.longFields?size gt 0>
    <#list serializeFields.longFields as field>
        remoteBuilder.addDataLongs(${field.getter}());
    </#list>
</#if>
<#if serializeFields.doubleFields?? && serializeFields.doubleFields?size gt 0>
    <#list serializeFields.doubleFields as field>
        remoteBuilder.addDataDoubles(${field.getter}());
    </#list>
</#if>
<#if serializeFields.intFields?? && serializeFields.intFields?size gt 0>
    <#list serializeFields.intFields as field>
        remoteBuilder.addDataIntegers(${field.getter}());
    </#list>
</#if>
<#if serializeFields.objectFields?? && serializeFields.objectFields?size gt 0>
    <#list serializeFields.objectFields as field>
        remoteBuilder.addDataObjectStrings(${field.getter}().toStorageData());
    </#list>
</#if>
return remoteBuilder;
}
