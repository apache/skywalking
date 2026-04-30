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

package org.apache.skywalking.oap.server.receiver.runtimerule.cluster;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ForwardRequest;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ForwardResponse;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ResumeAck;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ResumeRequest;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ResumeState;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.RuntimeRuleClusterServiceGrpc;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.SuspendAck;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.SuspendRequest;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.SuspendState;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.SuspendResumeCoordinator;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLScriptKey;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.SuspendResult;
import org.apache.skywalking.oap.server.receiver.runtimerule.rest.RuntimeRuleService;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState;

/**
 * Server-side handler for the three cluster-internal runtime-rule RPCs — see
 * {@link RuntimeRuleClusterClient} for the client side:
 * <ul>
 *   <li><b>Suspend / Resume</b> — STRUCTURAL-apply pause-and-resume bracket. The selected
 *       main broadcasts Suspend at the start of a structural cutover, then Resume on
 *       success or rollback. Peers flip {@code DSLRuntimeState.suspended} via
 *       {@link SuspendResumeCoordinator#peerSuspend} / {@link SuspendResumeCoordinator#peerResume}
 *       so dispatch is paused on every node while the schema is moving.</li>
 *   <li><b>Forward</b> — single-main routing for {@code addOrUpdate}, {@code inactivate},
 *       {@code delete}. Non-main OAPs receive an operator's REST call, forward it via
 *       this RPC to the cluster's main, and relay the response back to the operator.
 *       The handler dispatches by operation string into {@link RuntimeRuleService}'s
 *       {@code execute*} entry points, which run the same workflow direct HTTP callers
 *       run. Unknown operations return {@code 400 forward_unknown_operation}.</li>
 * </ul>
 *
 * <p>Suspend records {@link DSLRuntimeState.SuspendOrigin#PEER} so the state flip is atomic
 * w.r.t. concurrent local work and distinct from a SELF-origin suspend that would be set if
 * this node were itself the main. Resume clears only the PEER origin — SELF-origin suspends
 * (an in-flight local apply on this node) are never cleared by peer Resume.
 *
 * <p>Both RPCs are idempotent: repeated Suspend with the same origin returns
 * {@code ALREADY_SUSPENDED}; Resume with PEER already cleared returns
 * {@code NOT_SUSPENDED_BY_SENDER}. Self-broadcast is suppressed by comparing
 * {@code sender_node_id} against this node's own instance id.
 *
 * <p>Origin-conflict rejection: if Suspend arrives while SELF origin is already set on this
 * node (routing misfire — two OAPs think they're main), the handler returns
 * {@code REJECTED}. Main-side caller logs and drops the conflicting apply.
 *
 * <p>The receiver does NOT wait for the main's DDL to complete; peers pick up new content on
 * their next dslManager tick via {@link
 * org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO}. The
 * 60 s self-heal in the dslManager is the backstop for the narrow case where the main
 * crashes after Suspend but before Resume.
 */
@Slf4j
public class RuntimeRuleClusterServiceImpl
    extends RuntimeRuleClusterServiceGrpc.RuntimeRuleClusterServiceImplBase {

    private final DSLManager dslManager;
    /** This OAP instance's cluster identifier. Used to suppress self-broadcast loops. */
    private final String selfNodeId;
    /**
     * Bridge to the REST handler's workflow. Late-bound via {@code @Setter} because the
     * cluster service registers with the gRPC server during module {@code start()} before
     * the REST handler / service is constructed (the handler transitively references this
     * cluster service via {@code RuntimeRuleClusterClient}). Null-guarded in {@link #forward}
     * for boot-time safety; a forward that arrives before the service is wired returns 503
     * so operators see a clear "not ready" signal instead of an NPE.
     */
    @Setter
    private volatile RuntimeRuleService runtimeRuleService;

    public RuntimeRuleClusterServiceImpl(final DSLManager dslManager, final String selfNodeId) {
        this.dslManager = dslManager;
        this.selfNodeId = selfNodeId;
    }

    @Override
    public void suspend(final SuspendRequest request,
                        final StreamObserver<SuspendAck> responseObserver) {
        final String catalog = request.getCatalog();
        final String name = request.getName();

        // Suppress accidental self-loop: a broadcast that comes back to the sender must not
        // drain the sender's bundle twice. The fan-out side filters self out, but belt-and-
        // suspenders here because cluster peer lists can include self under some provider impls.
        if (Objects.equals(selfNodeId, request.getSenderNodeId())) {
            responseObserver.onNext(SuspendAck.newBuilder()
                .setNodeId(selfNodeId)
                .setState(SuspendState.ALREADY_SUSPENDED)
                .setDetail("self-broadcast suppressed")
                .build());
            responseObserver.onCompleted();
            return;
        }

        final SuspendResult result;
        try {
            result = dslManager.getSuspendCoord().peerSuspend(catalog, name);
        } catch (final Throwable t) {
            log.error("runtime-rule Suspend handler failed for {}/{}: {}",
                catalog, name, t.getMessage(), t);
            responseObserver.onNext(SuspendAck.newBuilder()
                .setNodeId(selfNodeId)
                .setState(SuspendState.SUSPEND_STATE_UNSPECIFIED)
                .setDetail("peer suspend failed: " + t.getMessage())
                .build());
            responseObserver.onCompleted();
            return;
        }

        final SuspendAck ack;
        switch (result) {
            case SUSPENDED:
                log.info("runtime-rule Suspend accepted for {}/{} (sender={}, reason={})",
                    catalog, name, request.getSenderNodeId(), request.getReason());
                ack = SuspendAck.newBuilder()
                    .setNodeId(selfNodeId)
                    .setState(SuspendState.SUSPENDED)
                    .setDetail("entry dispatch parked (PEER origin); measure and L2 handlers remain live")
                    .build();
                break;
            case ALREADY_SUSPENDED:
                ack = SuspendAck.newBuilder()
                    .setNodeId(selfNodeId)
                    .setState(SuspendState.ALREADY_SUSPENDED)
                    .setDetail("idempotent replay; PEER origin already held")
                    .build();
                break;
            case NOT_PRESENT:
                log.debug("runtime-rule Suspend received for {}/{} but bundle is NOT_PRESENT", catalog, name);
                ack = SuspendAck.newBuilder()
                    .setNodeId(selfNodeId)
                    .setState(SuspendState.NOT_PRESENT)
                    .setDetail("no local bundle for this (catalog, name)")
                    .build();
                break;
            case REJECTED_ORIGIN_CONFLICT:
            default:
                // This node is itself mid-apply (SELF origin held). Refusing avoids the BOTH
                // state that correct single-main routing never produces. Main-side caller
                // inspects the REJECTED ack and surfaces it to the operator.
                ack = SuspendAck.newBuilder()
                    .setNodeId(selfNodeId)
                    .setState(SuspendState.REJECTED)
                    .setDetail("origin conflict: local apply in flight (SELF origin held); "
                        + "routing misfire — only one main per (catalog, name) is permitted")
                    .build();
                break;
        }
        responseObserver.onNext(ack);
        responseObserver.onCompleted();
    }

    @Override
    public void resume(final ResumeRequest request,
                       final StreamObserver<ResumeAck> responseObserver) {
        final String catalog = request.getCatalog();
        final String name = request.getName();

        if (Objects.equals(selfNodeId, request.getSenderNodeId())) {
            responseObserver.onNext(ResumeAck.newBuilder()
                .setNodeId(selfNodeId)
                .setState(ResumeState.NOT_SUSPENDED_BY_SENDER)
                .setDetail("self-broadcast suppressed")
                .build());
            responseObserver.onCompleted();
            return;
        }

        // Snapshot the pre-resume state so we can distinguish BOTH → SELF (PARTIALLY_RESUMED)
        // from PEER → NONE (RESUMED) after the origin mutation.
        final String key = DSLScriptKey.key(catalog, name);
        final AppliedRuleScript beforeScript = dslManager.getRules().get(key);
        final DSLRuntimeState before = beforeScript == null ? null : beforeScript.getState();
        if (before == null) {
            responseObserver.onNext(ResumeAck.newBuilder()
                .setNodeId(selfNodeId)
                .setState(ResumeState.RESUME_NOT_PRESENT)
                .setDetail("no local bundle for this (catalog, name)")
                .build());
            responseObserver.onCompleted();
            return;
        }
        final DSLRuntimeState.SuspendOrigin originBefore = before.getSuspendOrigin();

        try {
            dslManager.getSuspendCoord().peerResume(catalog, name);
        } catch (final Throwable t) {
            log.error("runtime-rule Resume handler failed for {}/{}: {}",
                catalog, name, t.getMessage(), t);
            responseObserver.onNext(ResumeAck.newBuilder()
                .setNodeId(selfNodeId)
                .setState(ResumeState.RESUME_STATE_UNSPECIFIED)
                .setDetail("peer resume failed: " + t.getMessage())
                .build());
            responseObserver.onCompleted();
            return;
        }

        final ResumeAck ack;
        if (originBefore == DSLRuntimeState.SuspendOrigin.NONE
                || originBefore == DSLRuntimeState.SuspendOrigin.SELF) {
            // PEER was never set, or Resume already replayed. Idempotent no-op.
            ack = ResumeAck.newBuilder()
                .setNodeId(selfNodeId)
                .setState(ResumeState.NOT_SUSPENDED_BY_SENDER)
                .setDetail("PEER origin was not set; idempotent no-op")
                .build();
        } else if (originBefore == DSLRuntimeState.SuspendOrigin.BOTH) {
            log.info("runtime-rule Resume for {}/{} cleared PEER; SELF still held — "
                + "bundle remains SUSPENDED until local apply completes", catalog, name);
            ack = ResumeAck.newBuilder()
                .setNodeId(selfNodeId)
                .setState(ResumeState.PARTIALLY_RESUMED)
                .setDetail("PEER origin cleared; SELF origin still held (local apply in flight)")
                .build();
        } else {
            // originBefore == PEER → cleared to NONE → RUNNING.
            log.info("runtime-rule Resume accepted for {}/{} (sender={}, reason={})",
                catalog, name, request.getSenderNodeId(), request.getReason());
            ack = ResumeAck.newBuilder()
                .setNodeId(selfNodeId)
                .setState(ResumeState.RESUMED)
                .setDetail("entry dispatch resumed; bundle back to RUNNING")
                .build();
        }
        responseObserver.onNext(ack);
        responseObserver.onCompleted();
    }

    /**
     * Run a forwarded HTTP write on this node. Sender was told by its local {@code MainRouter}
     * that this OAP is the hash-selected main for {@code (catalog, name)}. The handler
     * dispatches to {@link RuntimeRuleService}'s {@code execute*} entry points, which run the
     * same workflow (suspend / apply / persist / resume-on-failure) that a direct HTTP
     * caller would hit, with the internal {@code forwarded=true} flag so the REST handler
     * skips its own MainRouter check (otherwise double-checking could infinite-loop on
     * cluster-view divergence) and instead uses the plain {@link DSLManager} lock + apply
     * path — or, if this node also doesn't consider itself main (cluster views disagree),
     * returns HTTP 421 to the sender so it can surface a clear "cluster routing misfire"
     * signal to the operator.
     */
    @Override
    public void forward(final ForwardRequest request,
                        final StreamObserver<ForwardResponse> responseObserver) {
        final RuntimeRuleService service = runtimeRuleService;
        if (service == null) {
            // Module still booting, or REST handler was never wired. 503 tells sender to
            // retry; self-heal isn't applicable for a Forward request.
            responseObserver.onNext(ForwardResponse.newBuilder()
                .setNodeId(selfNodeId)
                .setHttpStatus(503)
                .setBody(forwardErrorBody("forward_target_unavailable",
                    "forward target not yet wired on this OAP"))
                .build());
            responseObserver.onCompleted();
            return;
        }

        if (Objects.equals(selfNodeId, request.getSenderNodeId())) {
            // A forward that loops back to the sender is always a bug (either the sender's
            // mainFor mapped to itself, or cluster membership flapped). Refuse to execute
            // so the operator sees the anomaly instead of the loop silently completing.
            log.warn("runtime-rule Forward received from self for {}/{} — refusing to execute",
                request.getCatalog(), request.getName());
            responseObserver.onNext(ForwardResponse.newBuilder()
                .setNodeId(selfNodeId)
                .setHttpStatus(400)
                .setBody(forwardErrorBody("forward_self_loop",
                    "forward arrived from self; check cluster peer list"))
                .build());
            responseObserver.onCompleted();
            return;
        }

        final String catalog = request.getCatalog();
        final String name = request.getName();
        final String operation = request.getOperation();
        log.info("runtime-rule Forward received: op={} {}/{} (sender={})",
            operation, catalog, name, request.getSenderNodeId());

        final RuntimeRuleService.ForwardResult result;
        try {
            switch (operation == null ? "" : operation) {
                case "addOrUpdate":
                    result = service.executeAddOrUpdate(catalog, name,
                        request.getBody().toByteArray(),
                        request.getAllowStorageChange(),
                        request.getForceReapply());
                    break;
                case "inactivate":
                    result = service.executeInactivate(catalog, name);
                    break;
                case "delete":
                    // /delete carries an optional mode (e.g. "revertToBundled") in the
                    // request body so the main can mirror the originator's intent.
                    final byte[] deleteBody = request.getBody().toByteArray();
                    final String deleteMode = deleteBody.length == 0
                        ? ""
                        : new String(deleteBody, StandardCharsets.UTF_8);
                    result = service.executeDelete(catalog, name, deleteMode);
                    break;
                default:
                    responseObserver.onNext(ForwardResponse.newBuilder()
                        .setNodeId(selfNodeId)
                        .setHttpStatus(400)
                        .setBody(forwardErrorBody("forward_unknown_operation",
                            "unknown operation: " + operation))
                        .build());
                    responseObserver.onCompleted();
                    return;
            }
        } catch (final Throwable t) {
            log.error("runtime-rule Forward execution failed for {}/{}: {}",
                catalog, name, t.getMessage(), t);
            responseObserver.onNext(ForwardResponse.newBuilder()
                .setNodeId(selfNodeId)
                .setHttpStatus(500)
                .setBody(forwardErrorBody("forward_execution_failed", t.getMessage()))
                .build());
            responseObserver.onCompleted();
            return;
        }

        responseObserver.onNext(ForwardResponse.newBuilder()
            .setNodeId(selfNodeId)
            .setHttpStatus(result.getHttpStatus())
            .setBody(result.getJsonBody())
            .build());
        responseObserver.onCompleted();
    }

    private static String forwardErrorBody(final String applyStatus, final String message) {
        final JsonObject body = new JsonObject();
        body.addProperty("applyStatus", applyStatus);
        body.addProperty("message", message == null ? "" : message);
        return GSON.toJson(body);
    }

    private static final Gson GSON = new Gson();
}
