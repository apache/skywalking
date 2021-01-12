public int hashCode() {
int result = 17;
<#list fieldsFromSource as sourceField>
    <#if sourceField.isID()>
        <#if sourceField.getTypeName() == "java.lang.String">
            result = 31 * result + ${sourceField.fieldName}.hashCode();
        <#else>
            result += Const.ID_CONNECTOR + ${sourceField.fieldName};
        </#if>
    </#if>
</#list>
result = 31 * result + (int)getTimeBucket();
return result;
}