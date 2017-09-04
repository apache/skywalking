package org.skywalking.apm.collector.client.h2;

import org.skywalking.apm.collector.core.client.ClientException;

/**
 * @author pengys5
 */
public class H2ClientException extends ClientException {

    public H2ClientException(String message) {
        super(message);
    }

    public H2ClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
