package org.apache.skywalking.plugin.test.mockcollector.service;

import java.io.IOException;
import java.io.Writer;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.skywalking.plugin.test.mockcollector.entity.ValidateData;

public class ClearReceiveDataService extends HttpServlet {

    public static final String SERVLET_PATH = "/receiveData/clear";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/json");
        resp.setCharacterEncoding("utf-8");
        resp.setStatus(200);
        ValidateData.clearData();
        Writer out = resp.getWriter();
        out.flush();
        out.close();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
