public int remoteHashCode() {
int result = 17;
<#list fieldsFromSource as sourceField>
    <#if sourceField.isID()>
        <#if sourceField.getTypeName() == "java.lang.String">
            result = 31 * result + ${sourceField.fieldName}.hashCode();
        <#else>
            result = 31 * result + ${sourceField.fieldName};
        </#if>
    </#if>
</#list>
return result;
}