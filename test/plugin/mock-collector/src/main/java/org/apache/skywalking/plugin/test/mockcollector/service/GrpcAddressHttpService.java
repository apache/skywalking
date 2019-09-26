package org.apache.skywalking.plugin.test.mockcollector.service;

import com.google.gson.JsonArray;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.skywalking.plugin.test.mockcollector.util.ConfigReader;

public class GrpcAddressHttpService extends HttpServlet {

    public static String SERVLET_PATH = "/agent/gRPC";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(ConfigReader.getGrpcBindHost() + ":" + ConfigReader.getGrpcBindPort());
        resp.setContentType("text/json");
        resp.setCharacterEncoding("utf-8");
        resp.setStatus(200);

        PrintWriter out = resp.getWriter();
        out.print(jsonArray.toString());
        out.flush();
        out.close();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }
}
