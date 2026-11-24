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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import lombok.extern.slf4j.Slf4j;

/**
 * The debugging captures listening to this node's {@link SpanPairing}. With none, as unless a capture runs, the
 * pairing pays one volatile read per half.
 */
@Slf4j
public final class PairingDebugGate {
    private static final PairingDebugListener[] NONE = new PairingDebugListener[0];

    /**
     * Replaced, never changed in place, so a reader iterates a stable array.
     */
    private volatile PairingDebugListener[] listeners = NONE;

    public boolean isOn() {
        return listeners.length > 0;
    }

    public synchronized void add(final PairingDebugListener listener) {
        final PairingDebugListener[] current = listeners;
        for (final PairingDebugListener existing : current) {
            if (existing == listener) {
                return;
            }
        }
        final PairingDebugListener[] next = new PairingDebugListener[current.length + 1];
        System.arraycopy(current, 0, next, 0, current.length);
        next[current.length] = listener;
        listeners = next;
    }

    public synchronized void remove(final PairingDebugListener listener) {
        final PairingDebugListener[] current = listeners;
        int kept = 0;
        final PairingDebugListener[] next = new PairingDebugListener[current.length];
        for (final PairingDebugListener existing : current) {
            if (existing != listener) {
                next[kept++] = existing;
            }
        }
        if (kept == current.length) {
            return;
        }
        final PairingDebugListener[] trimmed = new PairingDebugListener[kept];
        System.arraycopy(next, 0, trimmed, 0, kept);
        listeners = kept == 0 ? NONE : trimmed;
    }

    void received(final PairingFact half, final PairingMatch match) {
        for (final PairingDebugListener listener : listeners) {
            try {
                listener.received(half, match);
            } catch (final RuntimeException e) {
                log.debug("A pairing debug capture dropped a received half: {}", e.getMessage(), e);
            }
        }
    }

    void leftUnmatched(final PairingFact half, final String cause) {
        for (final PairingDebugListener listener : listeners) {
            try {
                listener.leftUnmatched(half, cause);
            } catch (final RuntimeException e) {
                log.debug("A pairing debug capture dropped an unmatched half: {}", e.getMessage(), e);
            }
        }
    }
}
