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

package org.apache.skywalking.oap.trace.analysis.checker;

import java.util.ArrayList;
import java.util.List;

/**
 * What one side did with one segment, in order: the sources it sent, its naming calls, the sampler, segment-status,
 * searchable-tag and alias-cache reads, its log lines and the exception that stopped it, each annotated with the phase
 * and group the side's classifier reads from the stack, and, in a span's own run, the span's wire index; and, as a list of their own, the evaluation samples it took.
 * Recording is on only while the side analyzes a segment.
 */
final class CheckerEventLog {
    private final List<String> events = new ArrayList<>();
    private final List<String> owners = new ArrayList<>();
    private final List<String> samples = new ArrayList<>();
    private final List<String> sampleOwners = new ArrayList<>();
    /**
     * Positions of the span-loop lines, written by the loop itself outside any callback, whose span is told later.
     */
    private final List<Integer> pendingLoopLines = new ArrayList<>();
    private CheckerFrameClassifier classifier = frames -> null;
    private boolean recording;

    void setClassifier(final CheckerFrameClassifier classifier) {
        this.classifier = classifier;
    }

    void start() {
        events.clear();
        owners.clear();
        samples.clear();
        sampleOwners.clear();
        pendingLoopLines.clear();
        recording = true;
    }

    void stop() {
        recording = false;
    }

    boolean isRecording() {
        return recording;
    }

    /**
     * Record an event made on the current stack.
     */
    void event(final String event) {
        eventAt(event, new Throwable().getStackTrace());
    }

    /**
     * Record an event made on the given stack, such as an exception's.
     */
    void eventAt(final String event, final StackTraceElement[] frames) {
        if (!recording) {
            return;
        }
        CheckerFrameClassifier.CheckerAttribution where = classifier.classify(frames);
        if (where == null) {
            where = CheckerFrameClassifier.CheckerAttribution.UNKNOWN;
        }
        final Integer wireIndex = CheckerWireIndex.current();
        final boolean perSpan = "span".equals(where.getPhase()) || "first".equals(where.getPhase());
        String index = "";
        if (perSpan && wireIndex != null) {
            index = "#" + wireIndex;
        } else if (perSpan && CheckerFrameClassifier.LOOP_OWNERS.contains(where.getOwner())) {
            index = "#?";
            pendingLoopLines.add(events.size());
        }
        events.add("[" + where.getPhase() + index + " " + where.getGroup() + "] " + event);
        owners.add(where.getOwner());
    }

    /**
     * Record an evaluation sample, without an annotation: the port takes it in another phase by choice.
     */
    /**
     * Tell the span-loop lines written so far the span they were written for.
     */
    void resolveLoopLines(final int wireIndex) {
        for (final int position : pendingLoopLines) {
            events.set(position, events.get(position).replaceFirst("#\\?", "#" + wireIndex));
        }
        pendingLoopLines.clear();
    }

    /**
     * Tell the span-loop lines, in order, the spans they were written for, one each.
     */
    void resolveLoopLinesInOrder(final List<Integer> wireIndexes) {
        for (int i = 0; i < pendingLoopLines.size() && i < wireIndexes.size(); i++) {
            final int position = pendingLoopLines.get(i);
            events.set(position, events.get(position).replaceFirst("#\\?", "#" + wireIndexes.get(i)));
        }
        pendingLoopLines.clear();
    }

    void sample(final String context) {
        if (recording) {
            samples.add(context);
            final CheckerFrameClassifier.CheckerAttribution where = classifier.classify(new Throwable().getStackTrace());
            sampleOwners.add(where == null ? "-" : where.getOwner());
        }
    }

    List<String> events() {
        return new ArrayList<>(events);
    }

    /**
     * @return the owner of each event of {@link #events()}, at the same index
     */
    List<String> owners() {
        return new ArrayList<>(owners);
    }

    List<String> samples() {
        return new ArrayList<>(samples);
    }

    /**
     * @return the owner of each sample of {@link #samples()}, at the same index
     */
    List<String> sampleOwners() {
        return new ArrayList<>(sampleOwners);
    }
}
