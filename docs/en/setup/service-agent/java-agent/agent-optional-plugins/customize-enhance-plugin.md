## Support custom enhance 
Here is an optional plugin `apm-customize-enhance-plugin`

## Introduce
- The purpose of this plugin is to achieve Class enhancements to some extent through non-intrusive forms.
- The core idea is that there is no intrusion, so that no code related to this project appears in the project code.
- Implemented a custom enhancement of the two languages, the first is 'default', it looks more like [@Trace](Application-toolkit-trace.md) non-intrusive implementation, internal tag records need to be used, ActiveSpan.tag to achieve, of course, 'defalut' is to support static methods, The two are 'SpEL'. On the basis of default, you can use SpEL to extend the operationName suffix, already log, and tag extension.                                                                                                      

## How to configure
Implementing enhancements to custom classes requires two steps.
 1. Set through the system environment variable, you need to add `skywalking.customize.enhance_file`.
 2. Configure your configuration file according to the demo below.
```xml
<?xml version="1.0" encoding="UTF-8"?>

<enhanced>
    <class class_name="com.xyz.demo.order.Payment">
        <method method="pay(com.xyz.demo.order.CheckDO)" operation_name="/generating_one" language="SpEL">

        </method>
    </class>
</enhanced>
```


