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

package org.apache.skywalking.apm.plugin.pulsar;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerStats;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClientException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MockConsumer implements Consumer {

    @Override
    public String getTopic() {
        return null;
    }

    @Override
    public String getSubscription() {
        return null;
    }

    @Override
    public void unsubscribe() throws PulsarClientException {

    }

    @Override
    public CompletableFuture<Void> unsubscribeAsync() {
        return null;
    }

    @Override
    public Message receive() throws PulsarClientException {
        return null;
    }

    @Override
    public CompletableFuture<Message> receiveAsync() {
        return null;
    }

    @Override
    public Message receive(int i, TimeUnit timeUnit) throws PulsarClientException {
        return null;
    }

    @Override
    public void acknowledge(MessageId messageId) throws PulsarClientException {

    }

    @Override
    public void negativeAcknowledge(MessageId messageId) {

    }

    @Override
    public void acknowledgeCumulative(MessageId messageId) throws PulsarClientException {

    }

    @Override
    public CompletableFuture<Void> acknowledgeAsync(MessageId messageId) {
        return null;
    }

    @Override
    public CompletableFuture<Void> acknowledgeCumulativeAsync(MessageId messageId) {
        return null;
    }

    @Override
    public ConsumerStats getStats() {
        return null;
    }

    @Override
    public void close() throws PulsarClientException {

    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        return null;
    }

    @Override
    public boolean hasReachedEndOfTopic() {
        return false;
    }

    @Override
    public void redeliverUnacknowledgedMessages() {

    }

    @Override
    public void seek(MessageId messageId) throws PulsarClientException {

    }

    @Override
    public void seek(long l) throws PulsarClientException {

    }

    @Override
    public CompletableFuture<Void> seekAsync(MessageId messageId) {
        return null;
    }

    @Override
    public CompletableFuture<Void> seekAsync(long l) {
        return null;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public String getConsumerName() {
        return null;
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public CompletableFuture<Void> acknowledgeCumulativeAsync(Message message) {
        return null;
    }

    @Override
    public CompletableFuture<Void> acknowledgeAsync(Message message) {
        return null;
    }

    @Override
    public void acknowledgeCumulative(Message message) throws PulsarClientException {

    }

    @Override
    public void negativeAcknowledge(Message message) {

    }

    @Override
    public void acknowledge(Message message) throws PulsarClientException {

    }
}
