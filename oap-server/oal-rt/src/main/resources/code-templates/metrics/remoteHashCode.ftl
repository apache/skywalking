public int remoteHashCode() {
int result = 17;
<#list fieldsFromSource as sourceField>
    <#if sourceField.isID()>
        <#if sourceField.getTypeName() == "java.lang.String">
            result = 31 * result + ${sourceField.fieldName}.hashCode();
        <#else>
            result += org.apache.skywalking.oap.server.core.Const.ID_CONNECTOR + ${sourceField.fieldName};
        </#if>
    </#if>
</#list>
return result;
}