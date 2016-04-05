- 通过[log4j或者log4j2](../HOW_TO_FIND_TID.md)插件，显示tid，反映SDK的运行情况。
```
#tid:N/A，代表环境设置不正确或监控已经关闭
#tid: ,代表测试当前访问不在监控范围


#tid:1.0a2.1453065000002.c3f8779.27878.30.184，标识此次访问的tid信息，示例如下
[DEBUG] Returning handler method [public org.springframework.web.servlet.ModelAndView com.ai.cloud.skywalking.example.controller.OrderSaveController.save(javax.servlet.http.HttpServletRequest)] TID:1.0a2.1453192613272.2e0c63e.11144.58.1 2016-01-19 16:36:53.288 org.springframework.beans.factory.support.DefaultListableBeanFactory 
```

- 通过web应用的http调用入口，通过reponse的header信息，找到此次调用的traceid。前提：此web应用的url，已经使用skywalking-web-plugin进行监控。

