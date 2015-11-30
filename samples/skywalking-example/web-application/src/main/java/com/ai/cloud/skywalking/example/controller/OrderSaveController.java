package com.ai.cloud.skywalking.example.controller;

import com.ai.cloud.skywalking.api.BusinessKeyAppender;
import com.ai.cloud.skywalking.example.order.interfaces.IOrderMaintain;
import com.ai.cloud.skywalking.example.order.interfaces.parameter.OrderInfo;
import com.ai.cloud.skywalking.plugin.spring.Tracing;
import com.alibaba.dubbo.config.annotation.Reference;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/order")
public class OrderSaveController {

    @Reference
    private IOrderMaintain orderMaintain;

    @RequestMapping("/save")
    @Tracing
    public ModelAndView save(HttpServletRequest req) {
        String phoneNumber = req.getParameter("phoneNumber");
        String resourceId = req.getParameter("resourceId");
        String packageId = req.getParameter("packageId");
        String mail = req.getParameter("mail");

        String businessKey = "phoneNumber:" + phoneNumber + ",resourceId:" + resourceId + ",mail:" + mail;
        BusinessKeyAppender.setBusinessKey2Trace(businessKey);

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setResourceId(resourceId);
        orderInfo.setPhoneNumber(phoneNumber);
        orderInfo.setPackageId(packageId);
        orderInfo.setMailAccount(mail);
        String orderId = orderMaintain.saveOrder(orderInfo);
        System.out.println("orderId:" + orderId);
        ModelAndView view = new ModelAndView("order/saveOrderResult");
        return view;
    }
}
