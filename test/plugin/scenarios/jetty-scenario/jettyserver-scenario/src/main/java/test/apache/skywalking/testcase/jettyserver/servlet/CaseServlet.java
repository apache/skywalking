package test.apache.skywalking.testcase.jettyserver.servlet;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CaseServlet extends HttpServlet{
    public static String SERVLET_PATH = "/case/receiveContext-0";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }
    }
}
