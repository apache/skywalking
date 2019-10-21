/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.testcase.mongodb.controller;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.BsonDocument;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.mongodb.client.model.Filters.eq;

@RestController
@RequestMapping("/case")
public class CaseController {

    private Logger logger = LogManager.getLogger(CaseController.class);

    @Value(value = "${mongodb.host}")
    private String host;

    @Value(value = "${mongodb.port:27017}")
    private Integer port;

    @GetMapping("/healthCheck")
    public String health() {
        return "success";
    }

    @RequestMapping("/mongodb")
    public String mongoDBCase() {
        logger.info("mongodb host: {} ", host);
        MongoClient mongoClient = new MongoClient(host, port);
        MongoDatabase db = mongoClient.getDatabase("test-database");
        db.createCollection("testCollection");
        try {
            MongoCollection<Document> collection = db.getCollection("testCollection");
            Document document = Document.parse("{id: 1, name: \"test\"}");
            collection.insertOne(document);

            FindIterable<Document> findIterable = collection.find(eq("name", "org"));
            Document findDocument = findIterable.first();
            logger.info("find id[{}] document, and the name is {}", findDocument.get("id"), findDocument.get("name"));

            collection.updateOne(eq("name", "org"), BsonDocument.parse("{ $set : { \"name\": \"testA\"} }"));

            findIterable = collection.find(eq("name", "testA"));
            findDocument = findIterable.first();
            logger.info("find id[{}] document, and the name is {}", findDocument.get("id"), findDocument.get("name"));

            collection.deleteOne(eq("id", "1"));
        } finally {
            mongoClient.dropDatabase("test-database");
        }

        return "success";
    }
}
