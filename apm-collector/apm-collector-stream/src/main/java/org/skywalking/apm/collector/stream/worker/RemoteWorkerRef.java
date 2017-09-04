package org.skywalking.apm.collector.stream.worker;

import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.client.grpc.GRPCClient;
import org.skywalking.apm.collector.remote.grpc.proto.Empty;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteCommonServiceGrpc;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class RemoteWorkerRef extends WorkerRef {

    private final Logger logger = LoggerFactory.getLogger(RemoteWorkerRef.class);

    private final Boolean acrossJVM;
    private final RemoteCommonServiceGrpc.RemoteCommonServiceStub stub;
    private StreamObserver<RemoteMessage> streamObserver;
    private final AbstractRemoteWorker remoteWorker;

    public RemoteWorkerRef(Role role, AbstractRemoteWorker remoteWorker) {
        super(role);
        this.remoteWorker = remoteWorker;
        this.acrossJVM = false;
        this.stub = null;
    }

    public RemoteWorkerRef(Role role, GRPCClient client) {
        super(role);
        this.remoteWorker = null;
        this.acrossJVM = true;
        this.stub = RemoteCommonServiceGrpc.newStub(client.getChannel());
        createStreamObserver();
    }

    @Override
    public void tell(Object message) throws WorkerInvokeException {
        if (acrossJVM) {
            RemoteData remoteData = getRole().dataDefine().serialize(message);

            RemoteMessage.Builder builder = RemoteMessage.newBuilder();
            builder.setWorkerRole(getRole().roleName());
            builder.setRemoteData(remoteData);

            streamObserver.onNext(builder.build());
        } else {
            remoteWorker.allocateJob(message);
        }
    }

    public Boolean isAcrossJVM() {
        return acrossJVM;
    }

    private void createStreamObserver() {
        StreamStatus status = new StreamStatus(false);
        streamObserver = stub.call(new StreamObserver<Empty>() {
            @Override public void onNext(Empty empty) {
            }

            @Override public void onError(Throwable throwable) {
                logger.error(throwable.getMessage(), throwable);
            }

            @Override public void onCompleted() {
                status.finished();
            }
        });
    }

    class StreamStatus {
        private volatile boolean status;

        public StreamStatus(boolean status) {
            this.status = status;
        }

        public boolean isFinish() {
            return status;
        }

        public void finished() {
            this.status = true;
        }

        /**
         * @param maxTimeout max wait time, milliseconds.
         */
        public void wait4Finish(long maxTimeout) {
            long time = 0;
            while (!status) {
                if (time > maxTimeout) {
                    break;
                }
                try2Sleep(5);
                time += 5;
            }
        }

        /**
         * Try to sleep, and ignore the {@link InterruptedException}
         *
         * @param millis the length of time to sleep in milliseconds
         */
        private void try2Sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {

            }
        }
    }
}
