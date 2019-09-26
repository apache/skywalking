package org.apache.skywalking.plugin.test.mockcollector.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Writer;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.skywalking.plugin.test.mockcollector.entity.ValidateData;
import org.apache.skywalking.plugin.test.mockcollector.entity.ValidateDataSerializer;
import org.yaml.snakeyaml.Yaml;

public class ReceiveDataService extends HttpServlet {
    public static final String SERVLET_PATH = "/receiveData";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/json");
        resp.setCharacterEncoding("utf-8");
        resp.setStatus(200);
        Gson gson = new GsonBuilder().registerTypeAdapter(ValidateData.class, new ValidateDataSerializer()).create();
        System.out.println();
        Yaml yaml = new Yaml();
        Writer out = resp.getWriter();
        out.write(yaml.dump(yaml.load(gson.toJson(ValidateData.INSTANCE))));
        out.flush();
        out.close();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
