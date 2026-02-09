public org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.Builder serialize() {
org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.Builder remoteBuilder = org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.newBuilder();
<#if serializeFields.getStringFields()?? && serializeFields.getStringFields()?size gt 0>
    <#list serializeFields.getStringFields() as field>
        remoteBuilder.addDataStrings(${field});
    </#list>
</#if>
<#if serializeFields.getLongFields()?? && serializeFields.getLongFields()?size gt 0>
    <#list serializeFields.getLongFields() as field>
        remoteBuilder.addDataLongs(${field});
    </#list>
</#if>
<#if serializeFields.getDoubleFields()?? && serializeFields.getDoubleFields()?size gt 0>
    <#list serializeFields.getDoubleFields() as field>
        remoteBuilder.addDataDoubles(${field});
    </#list>
</#if>
<#if serializeFields.getIntFields()?? && serializeFields.getIntFields()?size gt 0>
    <#list serializeFields.getIntFields() as field>
        remoteBuilder.addDataIntegers(${field});
    </#list>
</#if>
<#if serializeFields.getObjectFields()?? && serializeFields.getObjectFields()?size gt 0>
    <#list serializeFields.getObjectFields() as field>
        remoteBuilder.addDataObjectStrings(${field.fieldName}.toStorageData());
    </#list>
</#if>
remoteBuilder.setDataLongs(0, getTimeBucket());
return remoteBuilder;
}
