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

/**
 * The <code>AbstractGet</code> implementations represent workers, which called by the server to allow a servlet to
 * handle a POST request.
 *
 * <p>verride the {@link #onReceive(Map, JsonObject)} method to support a search service.
 *
 * @author pengys5
 * @since v3.0-2017
 */
public abstract class AbstractPost extends AbstractServlet {

    public AbstractPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    /**
     * Add final modifier to avoid the subclass override this method.
     *
     * @param parameter {@link Object} data structure of the map
     * @param response {@link Object} is a out parameter
     * @throws Exception
     */
    @Override final protected void onWork(Object parameter, Object response) throws Exception {
        super.onWork(parameter, response);
    }

    static class PostWithHttpServlet extends HttpServlet {

        private Logger logger = LogManager.getFormatterLogger(PostWithHttpServlet.class);

        private final LocalSyncWorkerRef ownerWorkerRef;

        PostWithHttpServlet(LocalSyncWorkerRef ownerWorkerRef) {
            this.ownerWorkerRef = ownerWorkerRef;
        }

        /**
         * Override the {@link HttpServlet#doPost(HttpServletRequest, HttpServletResponse)} method, receive the
         * parameter from request then send parameter to the owner worker.
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
                Map<String, String[]> parameter = request.getParameterMap();
                ownerWorkerRef.ask(parameter, response);
            } catch (Exception e) {
                logger.error(e, e);
            }
        }
    }
}
