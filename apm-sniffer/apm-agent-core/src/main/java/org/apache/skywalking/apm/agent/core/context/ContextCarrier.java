/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.agent.core.context;

import java.io.Serializable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.apm.agent.core.base64.Base64;
import org.apache.skywalking.apm.agent.core.conf.Constants;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * {@link ContextCarrier} is a data carrier of {@link TracingContext}. It holds the snapshot (current state) of {@link
 * TracingContext}.
 * <p>
 */
@Setter(AccessLevel.PACKAGE)
public class ContextCarrier implements Serializable {
    @Getter
    private String traceId;
    /**
     * The segment id of the parent.
     */
    @Getter
    private String traceSegmentId;
    /**
     * The span id in the parent segment.
     */
    @Getter
    private int spanId = -1;
    @Getter
    private String parentService = Constants.EMPTY_STRING;
    @Getter
    private String parentServiceInstance = Constants.EMPTY_STRING;
    /**
     * The endpoint(entrance URI/method signature) of the parent service.
     */
    @Getter
    private String parentEndpoint;
    /**
     * The network address(ip:port, hostname:port) used in the parent service to access the current service.
     */
    @Getter
    private String addressUsedAtClient;
    /**
     * The extension context contains the optional context to enhance the analysis in some certain scenarios.
     */
    @Getter(AccessLevel.PACKAGE)
    private ExtensionContext extensionContext = new ExtensionContext();
    /**
     * User's custom context container. The context propagates with the main tracing context.
     */
    @Getter(AccessLevel.PACKAGE)
    private CorrelationContext correlationContext = new CorrelationContext();

    /**
     * @return the list of items, which could exist in the current tracing context.
     */
    public CarrierItem items() {
        SW8ExtensionCarrierItem sw8ExtensionCarrierItem = new SW8ExtensionCarrierItem(extensionContext, null);
        SW8CorrelationCarrierItem sw8CorrelationCarrierItem = new SW8CorrelationCarrierItem(
            correlationContext, sw8ExtensionCarrierItem);
        SW8CarrierItem sw8CarrierItem = new SW8CarrierItem(this, sw8CorrelationCarrierItem);
        return new CarrierItemHead(sw8CarrierItem);
    }

    /**
     * @return the injector for the extension context.
     */
    public ExtensionInjector extensionInjector() {
        return new ExtensionInjector(extensionContext);
    }

    /**
     * Extract the extension context to tracing context
     */
    void extractExtensionTo(TracingContext tracingContext) {
        tracingContext.getExtensionContext().extract(this);
        // The extension context could have field not to propagate further, so, must use the this.* to process.
        this.extensionContext.handle(tracingContext.activeSpan());
    }

    /**
     * Extract the correlation context to tracing context
     */
    void extractCorrelationTo(TracingContext tracingContext) {
        tracingContext.getCorrelationContext().extract(this);
        // The correlation context could have field not to propagate further, so, must use the this.* to process.
        this.correlationContext.handle(tracingContext.activeSpan());
    }

    /**
     * Serialize this {@link ContextCarrier} to a {@link String}, with '|' split.
     *
     * @return the serialization string.
     */
    String serialize(HeaderVersion version) {
        if (this.isValid(version)) {
            return StringUtil.join(
                '-',
                "1",
                Base64.encode(this.getTraceId()),
                Base64.encode(this.getTraceSegmentId()),
                this.getSpanId() + "",
                Base64.encode(this.getParentService()),
                Base64.encode(this.getParentServiceInstance()),
                Base64.encode(this.getParentEndpoint()),
                Base64.encode(this.getAddressUsedAtClient())
            );
        }
        return "";
    }

    /**
     * Initialize fields with the given text.
     *
     * @param text carries {@link #traceSegmentId} and {@link #spanId}, with '|' split.
     */
    ContextCarrier deserialize(String text, HeaderVersion version) {
        if (text == null) {
            return this;
        }
        if (HeaderVersion.v3.equals(version)) {
            String[] parts = text.split("-", 8);
            if (parts.length == 8) {
                try {
                    // parts[0] is sample flag, always trace if header exists.
                    this.traceId = Base64.decode2UTFString(parts[1]);
                    this.traceSegmentId = Base64.decode2UTFString(parts[2]);
                    this.spanId = Integer.parseInt(parts[3]);
                    this.parentService = Base64.decode2UTFString(parts[4]);
                    this.parentServiceInstance = Base64.decode2UTFString(parts[5]);
                    this.parentEndpoint = Base64.decode2UTFString(parts[6]);
                    this.addressUsedAtClient = Base64.decode2UTFString(parts[7]);
                } catch (IllegalArgumentException ignored) {

                }
            }
        }
        return this;
    }

    public boolean isValid() {
        return isValid(HeaderVersion.v3);
    }

    /**
     * Make sure this {@link ContextCarrier} has been initialized.
     *
     * @return true for unbroken {@link ContextCarrier} or no-initialized. Otherwise, false;
     */
    boolean isValid(HeaderVersion version) {
        if (HeaderVersion.v3 == version) {
            return StringUtil.isNotEmpty(traceId)
                && StringUtil.isNotEmpty(traceSegmentId)
                && getSpanId() > -1
                && StringUtil.isNotEmpty(parentService)
                && StringUtil.isNotEmpty(parentServiceInstance)
                && StringUtil.isNotEmpty(parentEndpoint)
                && StringUtil.isNotEmpty(addressUsedAtClient);
        }
        return false;
    }

    public enum HeaderVersion {
        v3
    }
}
