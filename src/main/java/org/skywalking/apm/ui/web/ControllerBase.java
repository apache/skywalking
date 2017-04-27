package org.skywalking.apm.ui.web;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

public abstract class ControllerBase {
	public void reply(String result, HttpServletResponse response) throws IOException {
		response.setContentType("application/json; charset=UTF-8");
		response.setStatus(HttpServletResponse.SC_OK);
		PrintWriter writer = response.getWriter();
		writer.write(result);
	}
}
