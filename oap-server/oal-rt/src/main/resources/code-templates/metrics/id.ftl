public String id() {
String splitJointId = String.valueOf(getTimeBucket());
<#list fieldsFromSource as sourceField>
    <#if sourceField.isID()>
        <#if sourceField.getTypeName() == "java.lang.String">
            splitJointId += org.apache.skywalking.oap.server.core.Const.ID_CONNECTOR + ${sourceField.fieldName};
        <#else>
            splitJointId += org.apache.skywalking.oap.server.core.Const.ID_CONNECTOR + String.valueOf(${sourceField.fieldName});
        </#if>
    </#if>
</#list>
return splitJointId;
}