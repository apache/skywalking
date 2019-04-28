## Support custom enhance 
Here is an optional plugin `apm-customize-enhance-plugin`

## Introduce
SkyWalking has provided [Java agent plugin development guide](https://github.com/apache/skywalking/blob/master/docs/en/guides/Java-Plugin-Development-Guide.md) to help developers to build new plugin. 

This plugin is not designed for replacement but for user convenience. The behaviour is very similar with [@Trace toolkit](Application-toolkit-trace.md), but without code change requirement, and more powerful, such as provide tag and log.                                                                                                      

## How to configure
Implementing enhancements to custom classes requires two steps.

1. Active the plugin, move the `optional-plugins/apm-customize-enhance-plugin.jar` to `plugin/apm-customize-enhance-plugin.jar`.
2. Set `plugin.customize.enhance_file` in agent.config, which targets to rule file, such as `/absolute/path/to/customize_enhance.xml`.
3. Set enhancement rules in `customize_enhance.xml`.
	```xml
	<?xml version="1.0" encoding="UTF-8"?>
	<enhanced>
	    <class class_name="test.apache.skywalking.testcase.customize.service.TestService1">
	        <method method="staticMethod()" operation_name="/is_static_method" static="true"/>
	        <method method="staticMethod(java.lang.String,int.class,java.util.Map,java.util.List,[Ljava.lang.Object;)" operation_name="/is_static_method_args" static="true">
	            <operation_name_suffix>arg[0]</operation_name_suffix>
	            <operation_name_suffix>arg[1]</operation_name_suffix>
	            <operation_name_suffix>arg[3].[0]</operation_name_suffix>
	            <tag key="tag_1">arg[2].['k1']</tag>
	            <tag key="tag_2">arg[4].[1]</tag>
	            <log key="log_1">arg[4].[2]</log>
	        </method>
	        <method method="method()" static="false"/>
	        <method method="method(java.lang.String,int.class)" operation_name="/method_2" static="false">
	            <operation_name_suffix>arg[0]</operation_name_suffix>
	            <tag key="tag_1">arg[0]</tag>
	            <log key="log_1">arg[1]</log>
	        </method>
	        <method method="method(test.apache.skywalking.testcase.customize.model.Model0,java.lang.String,int.class)" operation_name="/method_3" static="false">
	            <operation_name_suffix>arg[0].id</operation_name_suffix>
	            <operation_name_suffix>arg[0].model1.name</operation_name_suffix>
	            <operation_name_suffix>arg[0].model1.getId()</operation_name_suffix>
	            <tag key="tag_os">arg[0].os.[1]</tag>
	            <log key="log_map">arg[0].getM().['k1']</log>
	        </method>
	    </class>
	    <class class_name="test.apache.skywalking.testcase.customize.service.TestService2">
	        <method method="staticMethod(java.lang.String,int.class)" operation_name="/is_2_static_method" static="true">
	            <tag key="tag_2_1">arg[0]</tag>
	            <log key="log_1_1">arg[1]</log>
	        </method>
	        <method method="method([Ljava.lang.Object;)" operation_name="/method_4" static="false">
	            <tag key="tag_4_1">arg[0].[0]</tag>
	        </method>
	        <method method="method(java.util.List,int.class)" operation_name="/method_5" static="false">
	            <tag key="tag_5_1">arg[0].[0]</tag>
	            <log key="log_5_1">arg[1]</log>
	        </method>
	    </class>
	</enhanced>
	```

- Explanation of the configuration in the file

	| configuration  | explanation |
	|:----------------- |:---------------|
	| class_name | The enhanced class |
	| method | The interceptor method of the class |
	| operation_name | If fill it out, will use it instead of the default operation_name. |
	| operation_name\_suffix | What it means adding dynamic data after the operation_name. |
	| static | Is this method static. |
	| tag | Will add a tag in local span. The value of key needs to be represented on the XML node. |
	| log | Will add a log in local span. The value of key needs to be represented on the XML node. |
	| arg[x]   | What it means is to get the input arguments. such as arg[0] is means get first arguments. |
	| .[x]     | When the parsing object is Array or List, you can use it to get the object at the specified index. |
	| .['key'] | When the parsing object is Map, you can get the map 'key' through it.|

