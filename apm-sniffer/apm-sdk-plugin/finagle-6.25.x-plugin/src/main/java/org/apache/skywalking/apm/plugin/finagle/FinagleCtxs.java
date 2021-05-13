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

package org.apache.skywalking.apm.plugin.finagle;

import com.twitter.finagle.context.Contexts;
import com.twitter.finagle.context.LocalContext;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;

class FinagleCtxs {

    static LocalContext.Key<AbstractSpan> SW_SPAN = null;

    static LocalContext.Key<String> PEER_HOST = null;

    static {
        try {
            Constructor constructor = LocalContext.Key.class.getConstructor(LocalContext.class);
            SW_SPAN = (LocalContext.Key<AbstractSpan>) constructor.newInstance(Contexts.local());
            PEER_HOST = (LocalContext.Key<String>) constructor.newInstance(Contexts.local());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    static AbstractSpan getSpan() {
        if (Contexts.local().contains(SW_SPAN)) {
            AbstractSpan abstractSpan = Contexts.local().apply(SW_SPAN);
            return abstractSpan;
        }
        return null;
    }

    @Nullable
    static SWContextCarrier getSWContextCarrier() {
        if (Contexts.broadcast().contains(SWContextCarrier$.MODULE$)) {
            SWContextCarrier swContextCarrier = Contexts.broadcast().apply(SWContextCarrier$.MODULE$);
            return swContextCarrier;
        }
        return null;
    }

    @Nullable
    static String getPeerHost() {
        if (Contexts.local().contains(PEER_HOST)) {
            String peerHost = Contexts.local().apply(PEER_HOST);
            return peerHost;
        }
        return null;
    }
}
