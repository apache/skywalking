public boolean equals(Object obj) {
    if (this == obj)
        return true;
    if (obj == null)
        return false;
    if (getClass() != obj.getClass())
        return false;

    org.apache.skywalking.oal.rt.metrics.${metricsName}Metrics metrics = (org.apache.skywalking.oal.rt.metrics.${metricsName}Metrics)obj;
    <#list fieldsFromSource as sourceField>
        <#if sourceField.isID()>
            <#if sourceField.getTypeName() == "java.lang.String">
                if (!${sourceField.fieldName}.equals(metrics.${sourceField.fieldName}))
            <#else>
                if (${sourceField.fieldName} != metrics.${sourceField.fieldName})
            </#if>
            return false;
        </#if>
    </#list>

    if (getTimeBucket() != metrics.getTimeBucket())
        return false;

    return true;
}