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

package org.apache.skywalking.apm.agent.core.remote;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

/**
 * Add agent version(Described in MANIFEST.MF) to the connection establish stage.
 *
 * @author wusheng
 */
public class AgentIDDecorator implements ChannelDecorator {
    private static final ILog logger = LogManager.getLogger(AgentIDDecorator.class);
    private static final Metadata.Key<String> AGENT_VERSION_HEAD_HEADER_NAME =
        Metadata.Key.of("Agent-Version", Metadata.ASCII_STRING_MARSHALLER);
    private String version = "UNKNOWN";

    public AgentIDDecorator() {
        try {
            Enumeration<URL> resources = AgentIDDecorator.class.getClassLoader().getResources(JarFile.MANIFEST_NAME);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream is = url.openStream()) {
                    if (is != null) {
                        Manifest manifest = new Manifest(is);
                        Attributes mainAttribs = manifest.getMainAttributes();
                        String projectName = mainAttribs.getValue("Implementation-Vendor-Id");
                        if (projectName != null) {
                            if ("org.apache.skywalking".equals(projectName)) {
                                version = mainAttribs.getValue("Implementation-Version");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Can't read version from MANIFEST.MF in the agent jar");
        }
    }

    @Override public Channel build(Channel channel) {
        return ClientInterceptors.intercept(channel, new ClientInterceptor() {
            @Override
            public <REQ, RESP> ClientCall<REQ, RESP> interceptCall(MethodDescriptor<REQ, RESP> method,
                CallOptions options, Channel channel) {
                return new ForwardingClientCall.SimpleForwardingClientCall<REQ, RESP>(channel.newCall(method, options)) {
                    @Override
                    public void start(Listener<RESP> responseListener, Metadata headers) {
                        headers.put(AGENT_VERSION_HEAD_HEADER_NAME, version);

                        super.start(responseListener, headers);
                    }
                };
            }
        });
    }
}
