public org.apache.skywalking.oap.server.core.storage.StorageData storage2Entity( org.apache.skywalking.oap.server.core.storage.type.Convert2Entity converter) {
${metricsClassPackage}${metricsName}Metrics metrics = new ${metricsClassPackage}${metricsName}Metrics();
<#list fieldsFromSource as field>
    <#if field.typeName == "long" || field.typeName == "int" || field.typeName == "double" || field.typeName == "float">
        metrics.${field.fieldSetter}(((Number)converter.get("${field.columnName}")).${field.typeName}Value());
    <#elseif field.typeName == "java.lang.String">
        metrics.${field.fieldSetter}((String)converter.get("${field.columnName}"));
    <#else>
        metrics.${field.fieldSetter}(new ${field.typeName}((String)converter.get("${field.columnName}")));
    </#if>
</#list>
<#list persistentFields as field>
    <#if field.typeName == "long" || field.typeName == "int" || field.typeName == "double" || field.typeName == "float">
        metrics.${field.fieldSetter}(((Number)converter.get("${field.columnName}")).${field.typeName}Value());
    <#elseif field.typeName == "java.lang.String">
        metrics.${field.fieldSetter}((String)converter.get("${field.columnName}"));
    <#else>
        metrics.${field.fieldSetter}(new ${field.typeName}((String)converter.get("${field.columnName}")));
    </#if>
</#list>
return metrics;
}