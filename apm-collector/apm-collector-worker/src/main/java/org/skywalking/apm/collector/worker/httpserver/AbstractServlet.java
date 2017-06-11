package org.skywalking.apm.collector.worker.httpserver;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorker;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;

/**
 * The <code>AbstractServlet</code> implementations represent workers, which called by the server to allow a servlet to
 * handle a request at least one method, e.g. doGet, doPost, doPut, doDelete.
 * <p>Provide default {@link #onWork(Object, Object)} implementation, support the data structure of the map from a http
 * call.
 *
 * @author pengys5
 * @since v3.1-2017
 */
public abstract class AbstractServlet extends AbstractLocalSyncWorker {

    public AbstractServlet(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    /**
     * Override this method to implementing business logic.
     *
     * @param parameter {@link Object} data structure of the map
     * @param response {@link Object} is a out parameter
     * @throws Exception
     */
    @Override protected void onWork(Object parameter, Object response) throws Exception {
        JsonObject resJson = new JsonObject();
        try {
            onReceive((Map<String, String[]>)parameter, resJson);
            onSuccessResponse((HttpServletResponse)response, resJson);
        } catch (Exception e) {
            onErrorResponse(e, (HttpServletResponse)response);
        }
    }

    /**
     * Override this method to implementing business logic.
     *
     * @param parameter {@link Map}, get the request parameter by key.
     * @param response {@link JsonObject}, set the response data as json object.
     * @throws Exception if any error is detected when worker execute business logic.
     */
    protected abstract void onReceive(Map<String, String[]> parameter, JsonObject response) throws Exception;

    /**
     * Set the worker response and the success status into the servlet response object
     *
     * @param response {@link HttpServletResponse} object that contains the response the servlet reply to the client
     * @param resJson {@link JsonObject} object that contains the response from worker
     * @throws IOException
     */
    protected void onSuccessResponse(HttpServletResponse response, JsonObject resJson) throws IOException {
        resJson.addProperty("isSuccess", true);
        reply(response, resJson, HttpServletResponse.SC_OK);
    }

    /**
     * Reply the error message and server error code to client.
     *
     * @param exception a {@link Exception} when the worker handles the request
     * @param response {@link HttpServletResponse} object that contains the response the servlet reply to the client
     * @throws IOException if an input or output error is detected when the servlet handles the request
     */
    protected void onErrorResponse(Exception exception, HttpServletResponse response) throws IOException {
        JsonObject resJson = new JsonObject();
        resJson.addProperty("isSuccess", false);
        resJson.addProperty("reason", exception.getMessage());

        reply(response, resJson, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * Build the response head and body
     *
     * @param response {@link HttpServletResponse} object that contains the response the servlet reply to the client
     * @param resJson {@link JsonObject} object that contains the response from worker
     * @param status http status code
     * @throws IOException if an input or output error is detected when the servlet handles the request
     */
    private void reply(HttpServletResponse response, JsonObject resJson, int status) throws IOException {
        response.setContentType("text/json");
        response.setCharacterEncoding("utf-8");
        response.setStatus(status);

        PrintWriter out = response.getWriter();
        out.print(resJson);
        out.flush();
        out.close();
    }
}
