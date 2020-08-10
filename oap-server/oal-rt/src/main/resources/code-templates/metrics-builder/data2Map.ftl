public java.util.Map data2Map(org.apache.skywalking.oap.server.core.storage.StorageData input) {
${metricsClassPackage}${metricsName}Metrics storageData = (${metricsClassPackage}${metricsName}Metrics)input;
java.util.Map map = new java.util.HashMap();
<#list fieldsFromSource as field>
    <#if field.typeName == "long">
        map.put((Object)"${field.columnName}", new Long(storageData.${field.fieldGetter}()));
    <#elseif field.typeName == "int">
        map.put((Object)"${field.columnName}", new Integer(storageData.${field.fieldGetter}()));
    <#elseif field.typeName == "double">
        map.put((Object)"${field.columnName}", new Double(storageData.${field.fieldGetter}()));
    <#elseif field.typeName == "float">
        map.put((Object)"${field.columnName}", new Float(storageData.${field.fieldGetter}()));
    <#else>
        map.put((Object)"${field.columnName}", storageData.${field.fieldGetter}());
    </#if>
</#list>
<#list persistentFields as field>
    <#if field.typeName == "long">
        map.put((Object)"${field.columnName}", new Long(storageData.${field.fieldGetter}()));
    <#elseif field.typeName == "int">
        map.put((Object)"${field.columnName}", new Integer(storageData.${field.fieldGetter}()));
    <#elseif field.typeName == "double">
        map.put((Object)"${field.columnName}", new Double(storageData.${field.fieldGetter}()));
    <#elseif field.typeName == "float">
        map.put((Object)"${field.columnName}", new Float(storageData.${field.fieldGetter}()));
    <#else>
        map.put((Object)"${field.columnName}", storageData.${field.fieldGetter}());
    </#if>
</#list>
return map;
}