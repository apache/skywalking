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

package org.apache.skywalking.oap.server.ai.agent.conversation.fold;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Getter;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Ref;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionFlowRound;

/**
 * The fold of a conversation's rounds, as the Sessionizer's <code>sessionflow.View</code> folds them: entities
 * keyed by id, a higher revision replaces a lower one, a tombstone removes, absence means unchanged.
 *
 * <p>Rounds must fold in order and each must name the previous round's commit digest. A gap in the stored
 * rounds is bridged by {@link #applyAfterGap}: what the absent rounds carried stays as it was, and the document
 * names them, so a viewer sees as much as landed.
 */
@Getter
public final class ConversationFold {
    private final Map<String, SessionFlowRound.Node> nodes = new LinkedHashMap<>();
    private final Map<String, SessionFlowRound.Relation> relations = new LinkedHashMap<>();
    private final Map<String, SessionFlowRound.Unresolved> unresolved = new LinkedHashMap<>();
    private String conversation;
    private String session;
    private String parser;
    private String policy;
    private long round;
    private String digest;
    private long throughSeq;
    private String inputDigest;
    private String sessionFromTime;
    private String sessionThroughTime;
    private Map<String, List<SessionFlowRound.Node>> kids;
    private Map<String, List<SessionFlowRound.Relation>> from;
    private Map<String, List<SessionFlowRound.Relation>> to;

    /**
     * Fold one round on top of the current head, or refuse it the way the Sessionizer's <code>Apply</code>
     * does: a round out of order, naming another head, or from another conversation, session, parser or policy
     * is not merged, and the reason is returned in the Sessionizer's words for the document to carry.
     *
     * @param r the next round of the chain
     * @return null when the round was folded, else why it was refused
     */
    @Nullable
    public String apply(final SessionFlowRound r) {
        final SessionFlowRound.Header h = r.getHeader();
        if (h.getRound() != round + 1) {
            return "sessionflow: cannot apply round " + h.getRound() + " to a view at round " + round
                + ": rounds must fold in order";
        }
        if (round > 0 && !nullToEmpty(h.getPrevious()).equals(nullToEmpty(digest))) {
            return "sessionflow: round " + h.getRound() + " names previous \"" + nullToEmpty(h.getPrevious())
                + "\", but the view's head is \"" + nullToEmpty(digest) + "\"";
        }
        if (round == 0) {
            conversation = h.getConversation();
            session = h.getSession();
            parser = h.getParser();
            policy = h.getPolicy();
        } else {
            if (!nullToEmpty(conversation).equals(nullToEmpty(h.getConversation()))) {
                return "sessionflow: round " + h.getRound() + " belongs to conversation \"" + nullToEmpty(h.getConversation())
                    + "\", the chain to \"" + nullToEmpty(conversation) + "\"";
            }
            if (!nullToEmpty(session).equals(nullToEmpty(h.getSession()))) {
                return "sessionflow: round " + h.getRound() + " carries session \"" + nullToEmpty(h.getSession())
                    + "\", the chain \"" + nullToEmpty(session) + "\"";
            }
            if (!nullToEmpty(parser).equals(nullToEmpty(h.getParser()))) {
                return "sessionflow: round " + h.getRound() + " was produced by parser \"" + nullToEmpty(h.getParser())
                    + "\", the chain by \"" + nullToEmpty(parser) + "\"; a chain is one interpretation";
            }
            if (!nullToEmpty(policy).equals(nullToEmpty(h.getPolicy()))) {
                return "sessionflow: round " + h.getRound() + " was produced under policy \"" + nullToEmpty(h.getPolicy())
                    + "\", the chain under \"" + nullToEmpty(policy) + "\"";
            }
        }
        for (final SessionFlowRound.Node n : r.getNodes()) {
            if (n.isTombstone()) {
                nodes.remove(n.getId());
            } else {
                nodes.put(n.getId(), n);
            }
        }
        for (final SessionFlowRound.Relation rel : r.getRelations()) {
            if (rel.isTombstone()) {
                relations.remove(rel.getId());
            } else {
                relations.put(rel.getId(), rel);
            }
        }
        for (final SessionFlowRound.Unresolved u : r.getUnresolved()) {
            if (u.isTombstone()) {
                unresolved.remove(u.getId());
            } else {
                unresolved.put(u.getId(), u);
            }
        }
        kids = null;
        from = null;
        to = null;
        round = h.getRound();
        digest = r.getCommitDigest();
        throughSeq = h.getThroughSeq();
        inputDigest = h.getInputDigest();
        // the session's range is what the newest round says it is; a round from before the header carried it,
        // or one built by hand, leaves what the rounds before it said, so a read bounded by the range stays bounded
        if (!nullToEmpty(h.getSessionFromTime()).isEmpty()) {
            sessionFromTime = h.getSessionFromTime();
        }
        if (!nullToEmpty(h.getSessionThroughTime()).isEmpty()) {
            sessionThroughTime = h.getSessionThroughTime();
        }
        return null;
    }

    /**
     * Fold a stored round that follows a gap: the rounds between the head and it are absent, so its previous
     * digest is taken on trust and what the absent rounds carried stays as it was. When nothing folded before it,
     * the conversation's identity is read from its header, as {@link #apply} reads it from round 1. It is refused
     * for the same reasons as {@link #apply}, and then the head stays where it was.
     *
     * @param r the round the chain resumes at
     * @return null when the round was folded, else why it was refused
     */
    @Nullable
    public String applyAfterGap(final SessionFlowRound r) {
        final SessionFlowRound.Header h = r.getHeader();
        final long headRound = round;
        final String headDigest = digest;
        final boolean first = round == 0;
        if (first) {
            conversation = h.getConversation();
            session = h.getSession();
            parser = h.getParser();
            policy = h.getPolicy();
        }
        round = h.getRound() - 1;
        digest = h.getPrevious();
        final String refused = apply(r);
        if (refused != null) {
            round = headRound;
            digest = headDigest;
            if (first) {
                conversation = null;
                session = null;
                parser = null;
                policy = null;
            }
        }
        return refused;
    }

    private static String nullToEmpty(@Nullable final String s) {
        return s == null ? "" : s;
    }

    /**
     * @param id a node id
     * @return the node's children in record order
     */
    public List<SessionFlowRound.Node> children(final String id) {
        index();
        return kids.getOrDefault(id, Collections.emptyList());
    }

    public List<SessionFlowRound.Relation> relationsFrom(final String id) {
        index();
        return from.getOrDefault(id, Collections.emptyList());
    }

    public List<SessionFlowRound.Relation> relationsTo(final String id) {
        index();
        return to.getOrDefault(id, Collections.emptyList());
    }

    @Nullable
    public SessionFlowRound.Node node(final String id) {
        return nodes.get(id);
    }

    /**
     * @param kind a node kind
     * @return the nodes of that kind, in record order
     */
    public List<SessionFlowRound.Node> nodesOfKind(final String kind) {
        final List<SessionFlowRound.Node> out = new ArrayList<>();
        for (final SessionFlowRound.Node n : nodes.values()) {
            if (kind.equals(n.getKind())) {
                out.add(n);
            }
        }
        return inOrder(out);
    }

    /**
     * @return the open unresolved references, by id
     */
    public List<SessionFlowRound.Unresolved> openUnresolved() {
        final List<SessionFlowRound.Unresolved> out = new ArrayList<>();
        for (final SessionFlowRound.Unresolved u : unresolved.values()) {
            if ("open".equals(u.getState())) {
                out.add(u);
            }
        }
        out.sort(Comparator.comparing(SessionFlowRound.Unresolved::getId));
        return out;
    }

    /**
     * Record order: by the record a node stands on, then by id; a positioned node before one without a
     * reference.
     *
     * @param nodes the nodes
     * @return a sorted copy
     */
    public static List<SessionFlowRound.Node> inOrder(final List<SessionFlowRound.Node> nodes) {
        final List<SessionFlowRound.Node> out = new ArrayList<>(nodes);
        out.sort(ConversationFold::compare);
        return out;
    }

    public static int compare(final SessionFlowRound.Node a, final SessionFlowRound.Node b) {
        final Ref ap = a.getRef();
        final Ref bp = b.getRef();
        if (ap != null && bp != null) {
            if (ap.getSeq() != bp.getSeq()) {
                return Long.compare(ap.getSeq(), bp.getSeq());
            }
            if (ap.getRow() != bp.getRow()) {
                return Long.compare(ap.getRow(), bp.getRow());
            }
            if (ap.getBlock() != null && bp.getBlock() != null && !ap.getBlock().equals(bp.getBlock())) {
                return Integer.compare(ap.getBlock(), bp.getBlock());
            }
        } else if (ap != null) {
            return -1;
        } else if (bp != null) {
            return 1;
        }
        return a.getId().compareTo(b.getId());
    }

    private void index() {
        if (kids != null) {
            return;
        }
        final Map<String, List<SessionFlowRound.Node>> k = new HashMap<>();
        for (final SessionFlowRound.Node n : nodes.values()) {
            if (n.getParent() != null && !n.getParent().isEmpty()) {
                k.computeIfAbsent(n.getParent(), x -> new ArrayList<>()).add(n);
            }
        }
        for (final List<SessionFlowRound.Node> list : k.values()) {
            list.sort(ConversationFold::compare);
        }
        final Map<String, List<SessionFlowRound.Relation>> f = new HashMap<>();
        final Map<String, List<SessionFlowRound.Relation>> t = new HashMap<>();
        for (final SessionFlowRound.Relation r : relations.values()) {
            f.computeIfAbsent(r.getFrom(), x -> new ArrayList<>()).add(r);
            t.computeIfAbsent(r.getTo(), x -> new ArrayList<>()).add(r);
        }
        kids = k;
        from = f;
        to = t;
    }

    private static String first12(final String s) {
        return s == null ? "" : s.substring(0, Math.min(12, s.length()));
    }
}
