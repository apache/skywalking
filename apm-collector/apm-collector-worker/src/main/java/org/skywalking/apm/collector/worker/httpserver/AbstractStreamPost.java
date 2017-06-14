package org.skywalking.apm.collector.worker.httpserver;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalSyncWorkerRef;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerInvokeException;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;

/**
 * The <code>AbstractStreamPost</code> implementations represent workers, which called by the server to allow a servlet
 * to handle a post request and get post the data by {@link BufferedReader}.
 *
 * <p>verride the {@link #onReceive(BufferedReader, JsonObject)} method to deserialize the json construct data by buffer
 * reader.
 *
 * @author pengys5
 * @since v3.1-2017
 */
public abstract class AbstractStreamPost extends AbstractServlet {

    public AbstractStreamPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    /**
     * Call the {@link #onReceive(BufferedReader, JsonObject)} method, build response data to writer if success or build
     * error response data to writer if detected exception.
     *
     * @param reader {@link BufferedReader} json construct
     * @param response {@link Object} is a out parameter
     * @throws ArgumentsParseException if the key could not contains in parameter
     * @throws WorkerInvokeException if any error is detected when call(or ask) worker
     * @throws WorkerNotFoundException if the worker reference could not found in context.
     */
    @Override final protected void onWork(Object reader,
        Object response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        JsonObject resJson = new JsonObject();
        try {
            onReceive((BufferedReader)reader, resJson);
            onSuccessResponse((HttpServletResponse)response, resJson);
        } catch (Exception e) {
            onErrorResponse(e, (HttpServletResponse)response);
        }
    }

    /**
     * Override the default implementation, forbidden to call this method.
     *
     * @param parameter {@link Map}, get the request parameter by key.
     * @param response {@link JsonObject}, set the response data as json object.
     * @throws ArgumentsParseException if the key could not contains in parameter
     * @throws WorkerInvokeException if any error is detected when call(or ask) worker
     * @throws WorkerNotFoundException if the worker reference could not found in context.
     */
    @Override final protected void onReceive(Map<String, String[]> parameter,
        JsonObject response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        throw new WorkerInvokeException("Use the other method with buffer reader parameter");
    }

    /**
     * Override this method to implementing business logic.
     *
     * @param reader {@link BufferedReader} json construct
     * @param response {@link Object} is a out parameter
     * @throws ArgumentsParseException if the key could not contains in parameter
     * @throws WorkerInvokeException if any error is detected when call(or ask) worker
     * @throws WorkerNotFoundException if the worker reference could not found in context.
     */
    protected abstract void onReceive(BufferedReader reader,
        JsonObject response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException;

    static class StreamPostWithHttpServlet extends HttpServlet {

        private Logger logger = LogManager.getFormatterLogger(AbstractPost.PostWithHttpServlet.class);

        private final LocalSyncWorkerRef ownerWorkerRef;

        StreamPostWithHttpServlet(LocalSyncWorkerRef ownerWorkerRef) {
            this.ownerWorkerRef = ownerWorkerRef;
        }

        /**
         * Override the {@link HttpServlet#doPost(HttpServletRequest, HttpServletResponse)} method, receive the
         * buffer from request then send buffer to the owner worker.
         *
         * @param request an {@link HttpServletRequest} object that contains the request the client has made of the
         * servlet
         * @param response {@link HttpServletResponse} object that contains the response the servlet sends to the
         * client
         * @throws ServletException if the request for the POST could not be handled
         * @throws IOException if an input or output error is detected when the servlet handles the request
         */
        @Override final protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
            try {
                BufferedReader reader = request.getReader();
                ownerWorkerRef.ask(reader, response);
            } catch (Exception e) {
                logger.error(e, e);
            }
        }
    }
}
