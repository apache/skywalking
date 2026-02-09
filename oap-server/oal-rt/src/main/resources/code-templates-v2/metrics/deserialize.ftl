public void deserialize(org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData remoteData) {
<#if serializeFields.getStringFields()?? && serializeFields.getStringFields()?size gt 0>
    <#list serializeFields.getStringFields() as field>
        ${field} = remoteData.getDataStrings(${field_index});
    </#list>
</#if>
<#if serializeFields.getLongFields()?? && serializeFields.getLongFields()?size gt 0>
    <#list serializeFields.getLongFields() as field>
        <#if field_index == 0>
            setTimeBucket(remoteData.getDataLongs(${field_index}));
        <#else>
            ${field} = remoteData.getDataLongs(${field_index});
        </#if>
    </#list>
</#if>
<#if serializeFields.getDoubleFields()?? && serializeFields.getDoubleFields()?size gt 0>
    <#list serializeFields.getDoubleFields() as field>
        ${field} = remoteData.getDataDoubles(${field_index});
    </#list>
</#if>
<#if serializeFields.getIntFields()?? && serializeFields.getIntFields()?size gt 0>
    <#list serializeFields.getIntFields() as field>
        ${field} = remoteData.getDataIntegers(${field_index});
    </#list>
</#if>
<#if serializeFields.getObjectFields()?? && serializeFields.getObjectFields()?size gt 0>
    <#list serializeFields.getObjectFields() as field>
        ${field.fieldName} = new ${field.className}(remoteData.getDataObjectStrings(${field_index}));
    </#list>
</#if>
}
