package org.apache.skywalking.apm.testcase.netty.socketio;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.net.URISyntaxException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author MrPro
 */
public class SocketIOStarter {

    public static final Integer SERVER_PORT = 9092;
    public static final String LISTEN_EVENT_NAME = "send_data";
    public static final String SEND_EVENT_NAME = "get_data";

    public static SocketIOServer server;
    public static Socket client;

    public static void startServer() {
        Configuration config = new Configuration();
        config.setHostname("localhost");
        config.setPort(SERVER_PORT);

        server = new SocketIOServer(config);
        server.addEventListener(LISTEN_EVENT_NAME, String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackRequest) {
                // get message
            }
        });

        server.addConnectListener(new ConnectClientListener());

        server.start();

        // close server on kill signal
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.stop();
            }
        });
    }

    private static class ConnectClientListener implements ConnectListener {

        @Override
        public void onConnect(SocketIOClient client) {
            // connect client
        }
    }

    public static void startClientAndWaitConnect() throws URISyntaxException, InterruptedException {
        client = IO.socket("http://localhost:" + SERVER_PORT);
        LinkedBlockingQueue<Boolean> connected = new LinkedBlockingQueue<>(1);
        client.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                connected.add(true);
            }
        });
        client.connect();

        // wait connect to server
        connected.poll(5, TimeUnit.SECONDS);
    }

}
