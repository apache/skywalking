package org.skywalking.apm.agent.core.remote;

/**
 * The <code>RESTResponseStatusError</code> represents the REST-Service discovery got an unexpected response code.
 * Most likely, the response code is not 200.
 *
 * @author wusheng
 */
class RESTResponseStatusError extends Exception {
    RESTResponseStatusError(int responseCode) {
        super("Unexpected service response code: " + responseCode);
    }
}
