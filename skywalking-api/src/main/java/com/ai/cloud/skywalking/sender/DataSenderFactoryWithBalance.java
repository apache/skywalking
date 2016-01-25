package com.ai.cloud.skywalking.sender;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.util.StringUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.ai.cloud.skywalking.conf.Config.Sender.*;

public class DataSenderFactoryWithBalance {

    private static Logger logger = Logger
            .getLogger(DataSenderFactoryWithBalance.class.getName());
    // unUsedServerAddress存放没有使用的服务器地址，
    private static List<InetSocketAddress> unusedServerAddresses = new ArrayList<InetSocketAddress>();

    private static List<DataSender> usingDataSender = new ArrayList<DataSender>();
    private static int maxKeepConnectingSenderSize;

    private static int calculateMaxKeeperConnectingSenderSize(int allAddressSize) {
        if (CONNECT_PERCENT <= 0 || CONNECT_PERCENT > 100) {
            logger.log(Level.ALL, "CONNECT_PERCENT must between 1 and 100");
            System.exit(-1);
        }
        return (int) Math.ceil(allAddressSize
                * ((1.0 * CONNECT_PERCENT / 100) % 100));
    }

    // 初始化服务端的地址数据
    static {
        // 获取数据
        if (StringUtil.isEmpty(Config.Sender.SERVERS_ADDR)) {
            throw new IllegalArgumentException(
                    "Collection service configuration error.");
        }

        // 初始化地址
        Set<InetSocketAddress> tmpInetSocketAddress = new HashSet<InetSocketAddress>();
        for (String serverConfig : Config.Sender.SERVERS_ADDR.split(";")) {
            String[] server = serverConfig.split(":");
            if (server.length != 2)
                throw new IllegalArgumentException(
                        "Collection service configuration error.");
            tmpInetSocketAddress.add(new InetSocketAddress(server[0], Integer
                    .valueOf(server[1])));
        }

        unusedServerAddresses.addAll(tmpInetSocketAddress);

        // 根据配置的服务器集群的地址，来计算保持连接的Sender的数量
        maxKeepConnectingSenderSize = calculateMaxKeeperConnectingSenderSize(tmpInetSocketAddress
                .size());
        // 最大连接消费线程小于保持连接的Sender的数量，就不需要保持那么多的保持连接的Sender的数量
        if (maxKeepConnectingSenderSize > Config.Consumer.MAX_CONSUMER
                * Config.Buffer.POOL_SIZE) {
            maxKeepConnectingSenderSize = Config.Consumer.MAX_CONSUMER
                    * Config.Buffer.POOL_SIZE;
        }

        new DataSenderChecker().start();
    }

    // 获取连接
    public static IDataSender getSender() {
        DataSenderWithCopies readySender = new DataSenderWithCopies(maxKeepConnectingSenderSize);
        while (true) {
            try {
                if (usingDataSender.size() > 0) {
                    int index = ThreadLocalRandom.current().nextInt(0,
                            usingDataSender.size());
                    if (usingDataSender.get(index).getStatus() == DataSender.SenderStatus.READY) {
                        while (readySender.append(usingDataSender.get(index))) {
                            if (++index == usingDataSender.size()) {
                                index = 0;
                            }
                        }
                        break;
                    }
                }

                if (!readySender.isReady()) {
                    try {
                        Thread.sleep(RETRY_GET_SENDER_WAIT_INTERVAL);
                    } catch (InterruptedException e) {
                        logger.log(Level.ALL, "Sleep failed", e);
                    }
                }
            } catch (Throwable e) {
                logger.log(Level.ALL, "get sender failed", e);
            }

        }

        return readySender;
    }

    // 定时Sender状态检查
    public static class DataSenderChecker extends Thread {
        public DataSenderChecker() {
            super("Data-Sender-Checker");
        }

        @Override
        public void run() {
            long sleepTime = 0;
            while (true) {
                try {
                    DataSender newSender;
                    // removing failed sender
                    Iterator<DataSender> senderIterator = usingDataSender
                            .iterator();
                    DataSender tmpDataSender;
                    while (senderIterator.hasNext()) {
                        tmpDataSender = senderIterator.next();
                        if (tmpDataSender.getStatus() == DataSender.SenderStatus.FAILED) {
                            tmpDataSender.close();
                            unusedServerAddresses.add(tmpDataSender
                                    .getServerIp());
                            senderIterator.remove();
                        }
                    }

                    // try to fill up senders. if size is not enough.
                    while (usingDataSender.size() < maxKeepConnectingSenderSize) {
                        if ((newSender = findReadySender()) == null) {
                            // no available sender. ignore.
                            break;
                        }
                        usingDataSender.add(newSender);

                    }

                    // try to switch.
                    if (sleepTime >= SWITCH_SENDER_INTERVAL) {
                        // if sender is enough, go to switch for balancing.
                        if (usingDataSender.size() >= maxKeepConnectingSenderSize) {
                            DataSender toBeSwitchSender;
                            DataSender tmpSender;

                            int toBeSwitchIndex;

                            if (usingDataSender.size() - 1 > 0) {
                                toBeSwitchIndex = ThreadLocalRandom.current()
                                        .nextInt(0, usingDataSender.size() - 1);
                            } else {
                                toBeSwitchIndex = 0;
                            }

                            toBeSwitchSender = usingDataSender
                                    .get(toBeSwitchIndex);
                            tmpSender = findReadySender();
                            if (tmpSender != null) {
                                usingDataSender.set(toBeSwitchIndex, tmpSender);
                                try {
                                    Thread.sleep(CLOSE_SENDER_COUNTDOWN);
                                } catch (InterruptedException e) {
                                    logger.log(Level.ALL, "Sleep Failed");
                                }
                                toBeSwitchSender.close();
                                unusedServerAddresses.remove(tmpSender
                                        .getServerIp());
                                unusedServerAddresses.add(toBeSwitchSender
                                        .getServerIp());
                            }
                        }
                        sleepTime = 0;
                    }
                } catch (Throwable e) {
                    logger.log(Level.ALL, "DataSenderChecker running failed", e);
                }

                sleepTime += CHECKER_THREAD_WAIT_INTERVAL;
                try {
                    Thread.sleep(CHECKER_THREAD_WAIT_INTERVAL);
                } catch (InterruptedException e) {
                    logger.log(Level.ALL, "Sleep failed");
                }

            }
        }
    }

    private static DataSender findReadySender() {

        DataSender result = null;
        int index = ThreadLocalRandom.current().nextInt(0,
                unusedServerAddresses.size());
        for (int i = 0; i < unusedServerAddresses.size(); i++, index++) {

            if (index == unusedServerAddresses.size()) {
                index = 0;
            }

            try {
                result = new DataSender(unusedServerAddresses.get(index));
                unusedServerAddresses.remove(index);
                break;
            } catch (IOException e) {
                if (result != null) {
                    result.close();
                }
                continue;
            }
        }

        return result;
    }

    public static void unRegister(DataSender socket) {
        int index = usingDataSender.indexOf(socket);
        if (index != -1) {
            usingDataSender.get(index)
                    .setStatus(DataSender.SenderStatus.FAILED);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Span span = new Span("1.0a2.1453508782702.e8f7323.4115.762.256@~ @~0@~http://m.aisse.asiainfo.com/aisseMobilePage/toAisseMobilePage@~1453508782702@~85@~ITSC-MIS-LEV-web01/10.1.31.12@~1@~org.springframework.web.util.NestedServletException: Request processing failed; nested exception is java.lang.RuntimeException: org.springframework.transaction.CannotCreateTransactionException: Could not open JDBC Connection for transaction; nested exception is java.sql.SQLException: ORA-00257: archiver error. Connect internal only, until freed.#~#~org.springframework.transaction.CannotCreateTransactionException: Could not open JDBC Connection for transaction; nested exception is java.sql.SQLException: ORA-00257: archiver error. Connect internal only, until freed.#~#~\tat org.springframework.jdbc.datasource.DataSourceTransactionManager.doBegin(DataSourceTransactionManager.java:241)#~\tat org.springframework.transaction.support.AbstractPlatformTransactionManager.getTransaction(AbstractPlatformTransactionManager.java:372)#~\tat org.springframework.transaction.interceptor.TransactionAspectSupport.createTransactionIfNecessary(TransactionAspectSupport.java:417)#~\tat org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:255)#~\tat org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:94)#~\tat org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:172)#~\tat org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:204)#~\tat com.sun.proxy.$Proxy28.searchByNt(Unknown Source)#~\tat com.ai.aisse.core.rest.impl.ExpenseInitApiImpl.searchMembersinfo(ExpenseInitApiImpl.java:22)#~\tat com.alibaba.dubbo.common.bytecode.Wrapper3.invokeMethod(Wrapper3.java)#~\tat com.alibaba.dubbo.rpc.proxy.javassist.JavassistProxyFactory$1.doInvoke(JavassistProxyFactory.java:46)#~\tat com.alibaba.dubbo.rpc.proxy.AbstractProxyInvoker.invoke(AbstractProxyInvoker.java:72)#~\tat com.alibaba.dubbo.rpc.protocol.InvokerWrapper.invoke(InvokerWrapper.java:53)#~\tat com.ai.cloud.skywalking.plugin.dubbo.SWDubboEnhanceFilter.invoke(SWDubboEnhanceFilter.java:19)#~\tat com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper$1.invoke(ProtocolFilterWrapper.java:91)#~\tat com.alibaba.dubbo.rpc.filter.ExceptionFilter.invoke(ExceptionFilter.java:64)#~\tat com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper$1.invoke(ProtocolFilterWrapper.java:91)#~\tat com.alibaba.dubbo.rpc.filter.TimeoutFilter.invoke(TimeoutFilter.java:42)#~\tat com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper$1.invoke(ProtocolFilterWrapper.java:91)#~\tat com.alibaba.dubbo.monitor.support.MonitorFilter.invoke(MonitorFilter.java:75)#~\tat com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper$1.invoke(ProtocolFilterWrapper.java:91)#~\tat com.alibaba.dubbo.rpc.protocol.dubbo.filter.TraceFilter.invoke(TraceFilter.java:78)#~\tat com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper$1.invoke(ProtocolFilterWrapper.java:91)#~\tat com.alibaba.dubbo.rpc.filter.ContextFilter.invoke(ContextFilter.java:70)#~\tat com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper$1.invoke(ProtocolFilterWrapper.java:91)#~\tat com.alibaba.dubbo.rpc.filter.GenericFilter.invoke(GenericFilter.java:132)#~\tat com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper$1.invoke(ProtocolFilterWrapper.java:91)#~\tat com.alibaba.dubbo.rpc.filter.ClassLoaderFilter.invoke(ClassLoaderFilter.java:38)#~\tat com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper$1.invoke(ProtocolFilterWrapper.java:91)#~\tat com.alibaba.dubbo.rpc.filter.EchoFilter.invoke(EchoFilter.java:38)#~\tat com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper$1.invoke(ProtocolFilterWrapper.java:91)#~\tat com.alibaba.dubbo.rpc.protocol.dubbo.DubboProtocol$1.reply(DubboProtocol.java:113)#~\tat com.alibaba.dubbo.remoting.exchange.support.header.HeaderExchangeHandler.handleRequest(HeaderExchangeHandler.java:84)#~\tat com.alibaba.dubbo.remoting.exchange.support.header.HeaderExchangeHandler.received(HeaderExchangeHandler.java:170)#~\tat com.alibaba.dubbo.remoting.transport.DecodeHandler.received(DecodeHandler.java:52)#~\tat com.alibaba.dubbo.remoting.transport.dispatcher.ChannelEventRunnable.run(ChannelEventRunnable.java:82)#~\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)#~\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:615)#~\tat java.lang.Thread.run(Thread.java:745)#~Caused by: java.sql.SQLException: ORA-00257: archiver error. Connect internal only, until freed.#~#~\tat oracle.jdbc.driver.SQLStateMapping.newSQLException(SQLStateMapping.java:70)#~\tat oracle.jdbc.driver.DatabaseError.newSQLException(DatabaseError.java:112)#~\tat oracle.jdbc.driver.DatabaseError.throwSqlException(DatabaseError.java:173)#~\tat oracle.jdbc.driver.T4CTTIoer.processError(T4CTTIoer.java:455)#~\tat oracle.jdbc.driver.T4CTTIoer.processError(T4CTTIoer.java:406)#~\tat oracle.jdbc.driver.T4CTTIoer.processError(T4CTTIoer.java:399)#~\tat oracle.jdbc.driver.T4CTTIoauthenticate.receiveOsesskey(T4CTTIoauthenticate.java:306)#~\tat oracle.jdbc.driver.T4CConnection.logon(T4CConnection.java:387)#~\tat oracle.jdbc.driver.PhysicalConnection.<init>(PhysicalConnection.java:490)#~\tat oracle.jdbc.driver.T4CConnection.<init>(T4CConnection.java:202)#~\tat oracle.jdbc.driver.T4CDriverExtension.getConnection(T4CDriverExtension.java:33)#~\tat oracle.jdbc.driver.OracleDriver.connect(OracleDriver.java:474)#~\tat com.ai.cloud.skywalking.plugin.jdbc.TracingDriver.connect(TracingDriver.java:24)#~\tat org.apache.commons.dbcp2.DriverConnectionFactory.createConnection(DriverConnectionFactory.java:39)#~\tat org.apache.commons.dbcp2.PoolableConnectionFactory.makeObject(PoolableConnectionFactory.java:205)#~\tat org.apache.commons.pool2.impl.GenericObjectPool.create(GenericObjectPool.java:861)#~\tat org.apache.commons.pool2.impl.GenericObjectPool.borrowObject(GenericObjectPool.java:435)#~\tat org.apache.commons.pool2.impl.GenericObjectPool.borrowObject(GenericObjectPool.java:363)#~\tat org.apache.commons.dbcp2.PoolingDataSource.getConnection(PoolingDataSource.java:102)#~\tat org.apache.commons.dbcp2.BasicDataSource.getConnection(BasicDataSource.java:1413)#~\tat org.springframework.jdbc.datasource.DataSourceTransactionManager.doBegin(DataSourceTransactionManager.java:203)#~\t... 38 more#~#~\tat org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:981)#~\tat org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:860)#~\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:624)#~\tat org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:845)#~\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:731)#~\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:303)#~\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:208)#~\tat org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:52)#~\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:241)#~\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:208)#~\tat com.ai.sso.app.authentication.AuthenticationFilter.doFilter(AuthenticationFilter.java:70)#~\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:241)#~\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:208)#~\tat com.ai.net.xss.filter.XSSFilter.doFilter(XSSFilter.java:38)#~\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:241)#~\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:208)#~\tat org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:121)#~\tat org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:107)#~\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:241)#~\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:208)#~\tat com.ai.cloud.skywalking.plugin.web.SkyWalkingFilter.doFilter(SkyWalkingFilter.java:57)#~\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:241)#~\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:208)#~\tat org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:220)#~\tat org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:122)#~\tat org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:505)#~\tat org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:170)#~\tat com.googlecode.psiprobe.Tomcat70AgentValve.invoke(Tomcat70AgentValve.java:38)#~\tat org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:103)#~\tat org.apache.catalina.valves.AccessLogValve.invoke(AccessLogValve.java:957)#~\tat org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:116)#~\tat org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:423)#~\tat org.apache.coyote.http11.AbstractHttp11Processor.process(AbstractHttp11Processor.java:1079)#~\tat org.apache.coyote.AbstractProtocol$AbstractConnectionHandler.process(AbstractProtocol.java:620)#~\tat org.apache.tomcat.util.net.AprEndpoint$SocketProcessor.doRun(AprEndpoint.java:2476)#~\tat org.apache.tomcat.util.net.AprEndpoint$SocketProcessor.run(AprEndpoint.java:2465)#~\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)#~\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:615)#~\tat org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61)#~\tat java.lang.Thread.run(Thread.java:745)#~Caused by: java.lang.RuntimeException: org.springframework.transaction.CannotCreateTransactionException: Could not open JDBC Connection for transaction; nested exception is java.sql.SQLException: ORA-00257: archiver error. Connect internal only, until freed.#~#~org.springframework.transaction.CannotCreateTransactionException: Could not open JDBC Connection for transaction; nested exception is java.sql.SQLException: ORA-00257: archiver error. Connect internal only, until freed.#~#~\tat org.springframework.jdbc.datasource.DataSourceTransactionManager.doBegin(DataSourceTransactionManager.java:241)#~\tat org.springframework.transaction.support.AbstractPlatformTransactionManager.getTransaction(AbstractPlatformTransactionManager.java:372)#~\tat org.springframework.transaction.interceptor.TransactionAspectSupport.createTransactionIfNecessary(TransactionAspectSupport.java:417)#~\tat org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:255)#~\tat org.springframewo  \u0002.1.0a2.1453508787798.e8f7323.4115.1104.70@~0@~0@~com.ai.aisse.controller.myAisse.aisseMyselfPage.myAisse(com.ai.net.xss.wrapper.XssRequestWrapper,org.apache.catalina.connector.ResponseFacade,org.springframework.validation.support.BindingAwareModelMap)@~1453508787800@~4@~ITSC-MIS-LEV-web01/10.1.31.12@~0@~ @~M@~false@~ @~4115@~aisse-mobile-web@~5@~L");
        getSender().send(span.toString());

        Thread.sleep(2000L);
    }
}
