protected String id0() {
StringBuilder splitJointId = new StringBuilder(String.valueOf(getTimeBucket()));
<#list fieldsFromSource as sourceField>
    <#if sourceField.isID()>
        splitJointId.append(org.apache.skywalking.oap.server.core.Const.ID_CONNECTOR)
                    .append(${sourceField.fieldName});
    </#if>
</#list>
return splitJointId.toString();
}
