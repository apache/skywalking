public org.apache.skywalking.oap.server.core.analysis.metrics.Metrics toHour() {
    org.apache.skywalking.oal.rt.metrics.${metricsName}Metrics metrics = new org.apache.skywalking.oal.rt.metrics.${metricsName}Metrics();
    <#list fieldsFromSource as field>
        <#if field.columnName == "time_bucket">
            metrics.setTimeBucket(toTimeBucketInHour());
        <#elseif field.typeName == "java.lang.String" || field.typeName == "long" || field.typeName == "int" || field.typeName == "double" || field.typeName == "float">
            metrics.${field.fieldSetter}(this.${field.fieldGetter}());
        <#else>
            ${field.typeName} newValue = new ${field.typeName}();
            newValue.copyFrom(this.${field.fieldGetter}());
            metrics.${field.fieldSetter}(newValue);
        </#if>
    </#list>
    <#list persistentFields as field>
        <#if field.columnName == "time_bucket">
            metrics.setTimeBucket(toTimeBucketInHour());
        <#elseif field.typeName == "java.lang.String" || field.typeName == "long" || field.typeName == "int" || field.typeName == "double" || field.typeName == "float">
            metrics.${field.fieldSetter}(this.${field.fieldGetter}());
        <#else>
            ${field.typeName} newValue = new ${field.typeName}();
            newValue.copyFrom(this.${field.fieldGetter}());
            metrics.${field.fieldSetter}(newValue);
        </#if>
    </#list>
    return metrics;
}