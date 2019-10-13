package org.apache.skywalking.apm.testcase.spring.async;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author zhangwei
 */
public class CaseServlet extends HttpServlet {

    private static final long serialVersionUID = -5173829093752900411L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(AsyncConfig.class);
        AsyncBean async = applicationContext.getBean(AsyncBean.class);

        async.sendVisitBySystem();
        async.sendVisitByCustomize();

        PrintWriter writer = resp.getWriter();
        writer.write("Success");
        writer.flush();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }
}
