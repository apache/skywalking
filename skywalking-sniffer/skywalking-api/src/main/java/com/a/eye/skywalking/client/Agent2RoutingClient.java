package com.a.eye.skywalking.client;

import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.disruptor.ack.SendAckSpanEventHandler;
import com.a.eye.skywalking.disruptor.request.SendRequestSpanEventHandler;
import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.Client;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.SendResult;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.network.listener.client.StorageClientListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by wusheng on 2016/11/27.
 */
public class Agent2RoutingClient extends Thread {
    private static ILog logger = LogManager.getLogger(Agent2RoutingClient.class);

    private List<ServerAddr>  addrList;
    private Client client;
    private SpanStorageClient spanStorageClient;
    private NetworkListener   listener;
    private          SendRequestSpanEventHandler requestSpanDataSupplier = null;
    private          SendAckSpanEventHandler     ackSpanDataSupplier     = null;
    private volatile boolean                     connected               = false;

    public static Agent2RoutingClient INSTANCE = new Agent2RoutingClient();

    public Agent2RoutingClient() {
        String[] serverList = Config.SkyWalking.SERVERS.split(",");
        addrList = new ArrayList<>(serverList.length);
        for (String server : serverList) {
            String[] addrSegments = server.split(":");
            if (addrSegments.length != 2) {
                throw new IllegalArgumentException("server addr should like ip:port, illegal addr:" + server);
            }
            addrList.add(new ServerAddr(addrSegments[0], addrSegments[2]));
        }
        listener = new NetworkListener();
    }

    public void onReady() {
        this.connect();
        this.start();
    }

    public void setRequestSpanDataSupplier(SendRequestSpanEventHandler requestSpanDataSupplier) {
        this.requestSpanDataSupplier = requestSpanDataSupplier;
    }

    public void setAckSpanDataSupplier(SendAckSpanEventHandler ackSpanDataSupplier) {
        this.ackSpanDataSupplier = ackSpanDataSupplier;
    }

    private void connect() {
        try {
            if(client != null && !client.isShutdown()){
                client.shutdown();
            }
            int addrIdx = new Random().nextInt(addrList.size());
            ServerAddr addr = addrList.get(addrIdx);
            client = new Client(addr.ip, addr.port);
            spanStorageClient = client.newSpanStorageClient(listener);
            connected = true;
        } catch (Exception e) {
            HealthCollector.getCurrentHeathReading("Agent2RoutingClient").updateData(HeathReading.ERROR, "connect to routing node failure.");
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                while (connected && !client.isShutdown()) {
                    List<RequestSpan> requestData = this.requestSpanDataSupplier.getBufferData();
                    List<AckSpan> ackData = this.ackSpanDataSupplier.getBufferData();

                    if (requestData.size() > 0 || ackData.size() > 0) {
                        listener.begin();

                        spanStorageClient.sendRequestSpan(requestData);
                        spanStorageClient.sendACKSpan(ackData);

                        while (!listener.isBatchFinished()) {
                            LockSupport.parkNanos(1);
                        }
                    } else {
                        try {
                            Thread.sleep(10 * 1000L);
                        } catch (InterruptedException e) {

                        }
                    }
                }

                try {
                    Thread.sleep(30 * 1000L);
                } catch (InterruptedException e) {

                }

                this.connect();
            } catch (Throwable e) {
                logger.error("unexpected failure.", e);
            }
        }
    }


    class NetworkListener implements StorageClientListener {
        private volatile boolean batchFinished = false;

        void begin() {
            batchFinished = false;
        }

        boolean isBatchFinished() {
            return batchFinished;
        }

        @Override
        public void onError(Throwable throwable) {
            batchFinished = true;
            HealthCollector.getCurrentHeathReading("Agent2RoutingClient").updateData(HeathReading.ERROR, "send data to routing node failure.");
        }

        @Override
        public void onBatchFinished(SendResult sendResult) {
            batchFinished = true;
            HealthCollector.getCurrentHeathReading("Agent2RoutingClient").updateData(HeathReading.INFO, "batch send data to routing node.");
        }

    }


    class ServerAddr {
        String  ip;
        Integer port;

        public ServerAddr(String ip, String port) {
            this.ip = ip;
            try {
                this.port = Integer.parseInt(port);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("server addr should like ip:port, illegal port:" + port);
            }

        }
    }
}
