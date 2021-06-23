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

package org.apache.skywalking.apm.testcase.neo4j.service;

import static org.neo4j.driver.Values.parameters;

import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.async.ResultCursor;
import org.neo4j.driver.internal.shaded.reactor.core.publisher.Flux;
import org.neo4j.driver.internal.shaded.reactor.core.publisher.Mono;
import org.neo4j.driver.reactive.RxResult;
import org.neo4j.driver.reactive.RxSession;
import org.springframework.stereotype.Service;

@Service
public class TestCaseService {

    private static final Logger LOGGER = LogManager.getLogger(TestCaseService.class);
    private static final String QUERY = "CREATE (a:Person {name: $name}) RETURN a.name";
    private static final SessionConfig SESSION_CONFIG = SessionConfig.forDatabase("neo4j");

    public void sessionScenarioTest(Driver driver) {
        try (Session session = driver.session(SESSION_CONFIG)) {
            final String result = session.run(QUERY, parameters("name", String.valueOf(new Random().nextInt())))
                    .single()
                    .get(0).asString();
            LOGGER.info("Result from simple session: {}", result);
        }

        final AsyncSession asyncSession = driver.asyncSession(SESSION_CONFIG);

        final String asyncResult = asyncSession
                .runAsync(QUERY, parameters("name", String.valueOf(new Random().nextInt())))
                .thenCompose(ResultCursor::singleAsync)
                .exceptionally(error -> {
                    error.printStackTrace();
                    return null;
                })
                .thenCompose(record -> asyncSession.closeAsync().thenApply(ignore -> record.get(0).asString()))
                .toCompletableFuture().join();
        LOGGER.info("Result from async session: {}", asyncResult);

        Flux.usingWhen(Mono.fromSupplier(() -> driver.rxSession(SESSION_CONFIG)),
                session -> Flux
                        .from(session.run(QUERY, parameters("name", String.valueOf(new Random().nextInt())))
                                .records())
                        .map(record -> record.get(0).asString())
                        .doOnNext(result -> LOGGER.info("Result from rx session: {}", result)),
                RxSession::close).blockLast();
    }

    public void transactionScenarioTest(Driver driver) {
        try (Session session = driver.session(SESSION_CONFIG)) {
            final String result = session
                    .writeTransaction(
                            transaction -> transaction
                                    .run(QUERY, parameters("name", String.valueOf(new Random().nextInt()))).single()
                                    .get(0).asString());
            LOGGER.info("Result from simple transaction: {}", result);
        }

        final AsyncSession asyncSession = driver.asyncSession(SESSION_CONFIG);
        final String asyncResult = asyncSession
                .writeTransactionAsync(
                        asyncTransaction -> asyncTransaction
                                .runAsync(QUERY, parameters("name", String.valueOf(new Random().nextInt())))
                                .thenCompose(ResultCursor::singleAsync)
                                .thenApply(record -> record.get(0).asString()))
                .toCompletableFuture()
                .join();
        LOGGER.info("Result from async transaction: {}", asyncResult);

        Flux.usingWhen(Mono.fromSupplier(() -> driver.rxSession(SESSION_CONFIG)),
                rxSession ->
                        rxSession.writeTransaction(rxTransaction -> {
                            final RxResult result = rxTransaction
                                    .run(QUERY, parameters("name", String.valueOf(new Random().nextInt())));
                            return Flux.from(result.records())
                                    .doOnNext(record -> LOGGER
                                            .info("Result from rx transaction: {}", record.get(0).asString()))
                                    .then(Mono.from(result.consume()));
                        }), RxSession::close).blockLast();
    }
}
