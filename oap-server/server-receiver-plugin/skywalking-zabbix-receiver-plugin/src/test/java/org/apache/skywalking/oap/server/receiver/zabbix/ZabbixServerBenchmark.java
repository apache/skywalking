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

package org.apache.skywalking.oap.server.receiver.zabbix;

import org.apache.skywalking.oap.server.library.server.tcp.TCPServerException;
import org.apache.skywalking.oap.server.library.server.tcp.TCPServerManager;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.ZabbixTCPBinder;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.ZabbixModuleConfig;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@State(Scope.Benchmark)
public class ZabbixServerBenchmark {

    private static final String TCP_HOST = "0.0.0.0";
    private static final int TCP_PORT = 13800;

    @Param({"1", "2", "3"})
    private int bossGroupCount;

    @Param({"0", "1", "10"})
    private int workerGroupCount;

    @Param({"1", "2", "10", "20"})
    private int connectionCount;

    private byte[] activeChecksRequest;
    private Socket[] sockets;

    @Setup
    public void setup() throws TCPServerException, IOException {
        TCPServerManager serverManager = new TCPServerManager(TCP_HOST, bossGroupCount, workerGroupCount);
        ZabbixModuleConfig config = new ZabbixModuleConfig();
        config.setPort(TCP_PORT);
        serverManager.addBinder(new ZabbixTCPBinder(config, null));
        serverManager.startAllServer();

        activeChecksRequest = buildZabbixRequestData("{\"request\":\"active checks\",\"host\":\"zabbix-test-agent\"}");
        sockets = new Socket[connectionCount];
        for (int i = 0; i < connectionCount; i++) {
            sockets[i] = new Socket();
            sockets[i].connect(new InetSocketAddress(TCP_HOST, TCP_PORT));
        }
    }

    @Benchmark
    public void activeChecks() throws IOException {
        for (Socket socket : sockets) {
            tryToGettingResponse(socket, activeChecksRequest, 839);
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(ZabbixServerBenchmark.class.getName())
            .addProfiler(GCProfiler.class)
            .jvmArgsAppend("-Xmx512m", "-Xms512m")
            .forks(1)
            .build();
        new Runner(opt).run();
    }

    private byte[] tryToGettingResponse(Socket socket, byte[] request, int respBodyCount) throws IOException {
        socket.getOutputStream().write(request);

        byte[] content = new byte[respBodyCount];
        socket.getInputStream().read(content);
        return content;
    }

    private byte[] buildZabbixRequestData(String content) {
        // Build header
        byte[] payload = content.getBytes();
        int payloadLength = payload.length;
        byte[] header = new byte[] {
            'Z', 'B', 'X', 'D', '\1',
            (byte)(payloadLength & 0xFF),
            (byte)(payloadLength >> 8 & 0xFF),
            (byte)(payloadLength >> 16 & 0xFF),
            (byte)(payloadLength >> 24 & 0xFF),
            '\0', '\0', '\0', '\0'};

        byte[] packet = new byte[header.length + payloadLength];
        System.arraycopy(header, 0, packet, 0, header.length);
        System.arraycopy(payload, 0, packet, header.length, payloadLength);

        return packet;
    }
}
