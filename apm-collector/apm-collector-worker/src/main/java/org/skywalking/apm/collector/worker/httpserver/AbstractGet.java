package org.skywalking.apm.collector.worker.httpserver;

import com.google.gson.JsonObject;
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
 * The <code>AbstractGet</code> implementations represent workers, which called by the server to allow a servlet to
 * handle a GET request.
 *
 * <p>verride the {@link #onReceive(Map, JsonObject)} method to support a search service.
 *
 * @author pengys5
 * @since v3.0-2017
 */
public abstract class AbstractGet extends AbstractServlet {

    protected AbstractGet(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    /**
     * Forbid the subclasses override this method.
     *
     * @param parameter {@link Object} data structure of the map
     * @param response {@link Object} is a out parameter
     * @throws Exception
     */
    @Override final protected void onWork(Object parameter,
        Object response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        super.onWork(parameter, response);
    }

    static class GetWithHttpServlet extends HttpServlet {

        private Logger logger = LogManager.getFormatterLogger(GetWithHttpServlet.class);

        private final LocalSyncWorkerRef ownerWorkerRef;

        GetWithHttpServlet(LocalSyncWorkerRef ownerWorkerRef) {
            this.ownerWorkerRef = ownerWorkerRef;
        }

        /**
         * Override the {@link HttpServlet#doGet(HttpServletRequest, HttpServletResponse)} method, receive the parameter
         * from request then send parameter to the owner worker.
         *
         * @param request an {@link HttpServletRequest} object that contains the request the client has made of the
         * servlet
         * @param response an {@link HttpServletResponse} object that contains the response the servlet sends to the
         * client
         * @throws ServletException if the request for the GET could not be handled
         * @throws IOException if an input or output error is detected when the servlet handles the GET request
         */
        @Override final protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
            try {
                Map<String, String[]> parameter = request.getParameterMap();
                ownerWorkerRef.ask(parameter, response);
            } catch (Exception e) {
                logger.error(e, e);
            }
        }
    }
}
