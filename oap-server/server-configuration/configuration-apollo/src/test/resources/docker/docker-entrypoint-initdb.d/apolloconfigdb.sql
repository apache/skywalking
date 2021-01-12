-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- MySQL dump 10.13  Distrib 5.7.16, for osx10.11 (x86_64)
--
-- Host: 127.0.0.1    Database: ApolloConfigDB
-- ------------------------------------------------------
-- Server version	5.7.26

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `ApolloConfigDB`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `ApolloConfigDB` /*!40100 DEFAULT CHARACTER SET utf8mb4 */;

USE `ApolloConfigDB`;

--
-- Table structure for table `App`
--

DROP TABLE IF EXISTS `App`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `App` (
  `Id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `AppId` varchar(500) NOT NULL DEFAULT 'default',
  `Name` varchar(500) NOT NULL DEFAULT 'default',
  `OrgId` varchar(32) NOT NULL DEFAULT 'default',
  `OrgName` varchar(64) NOT NULL DEFAULT 'default',
  `OwnerName` varchar(500) NOT NULL DEFAULT 'default',
  `OwnerEmail` varchar(500) NOT NULL DEFAULT 'default',
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `AppId` (`AppId`(191)),
  KEY `DataChange_LastTime` (`DataChange_LastTime`),
  KEY `IX_Name` (`Name`(191))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `App`
--

/*!40000 ALTER TABLE `App` DISABLE KEYS */;
INSERT INTO `App` VALUES (1,'SampleApp','Sample App','TEST1','','apollo','apollo@acme.com','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `App` ENABLE KEYS */;

--
-- Table structure for table `AppNamespace`
--

DROP TABLE IF EXISTS `AppNamespace`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `AppNamespace` (
  `Id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `Name` varchar(32) NOT NULL DEFAULT '',
  `AppId` varchar(32) NOT NULL DEFAULT '',
  `Format` varchar(32) NOT NULL DEFAULT 'properties',
  `IsPublic` bit(1) NOT NULL DEFAULT b'0',
  `Comment` varchar(64) NOT NULL DEFAULT '',
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT '',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `IX_AppId` (`AppId`),
  KEY `Name_AppId` (`Name`,`AppId`),
  KEY `DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `AppNamespace`
--

/*!40000 ALTER TABLE `AppNamespace` DISABLE KEYS */;
INSERT INTO `AppNamespace` VALUES (1,'application','SampleApp','properties','\0','default app namespace','\0','','2019-06-06 15:14:00','','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `AppNamespace` ENABLE KEYS */;

--
-- Table structure for table `Audit`
--

DROP TABLE IF EXISTS `Audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Audit` (
  `Id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `EntityName` varchar(50) NOT NULL DEFAULT 'default',
  `EntityId` int(10) unsigned DEFAULT NULL,
  `OpName` varchar(50) NOT NULL DEFAULT 'default',
  `Comment` varchar(500) DEFAULT NULL,
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Audit`
--

/*!40000 ALTER TABLE `Audit` DISABLE KEYS */;
INSERT INTO `Audit` VALUES (1,'Item',2,'INSERT',NULL,'\0','apollo','2019-06-07 02:28:50',NULL,'2019-06-07 02:28:50'),(2,'Release',2,'INSERT',NULL,'\0','apollo','2019-06-07 02:28:51',NULL,'2019-06-07 02:28:51'),(3,'ReleaseHistory',2,'INSERT',NULL,'\0','apollo','2019-06-07 02:28:51',NULL,'2019-06-07 02:28:51'),(4,'Item',2,'DELETE',NULL,'\0','apollo','2019-06-07 02:31:22',NULL,'2019-06-07 02:31:22'),(5,'Release',3,'INSERT',NULL,'\0','apollo','2019-06-07 02:31:25',NULL,'2019-06-07 02:31:25'),(6,'ReleaseHistory',3,'INSERT',NULL,'\0','apollo','2019-06-07 02:31:25',NULL,'2019-06-07 02:31:25'),(7,'Item',3,'INSERT',NULL,'\0','apollo','2019-06-07 02:31:50',NULL,'2019-06-07 02:31:50'),(8,'Release',4,'INSERT',NULL,'\0','apollo','2019-06-07 02:31:50',NULL,'2019-06-07 02:31:50'),(9,'ReleaseHistory',4,'INSERT',NULL,'\0','apollo','2019-06-07 02:31:50',NULL,'2019-06-07 02:31:50'),(10,'Item',3,'DELETE',NULL,'\0','apollo','2019-06-07 02:34:02',NULL,'2019-06-07 02:34:02'),(11,'Release',5,'INSERT',NULL,'\0','apollo','2019-06-07 02:34:04',NULL,'2019-06-07 02:34:04'),(12,'ReleaseHistory',5,'INSERT',NULL,'\0','apollo','2019-06-07 02:34:04',NULL,'2019-06-07 02:34:04'),(13,'Item',4,'INSERT',NULL,'\0','apollo','2019-06-07 02:34:37',NULL,'2019-06-07 02:34:37'),(14,'Release',6,'INSERT',NULL,'\0','apollo','2019-06-07 02:34:38',NULL,'2019-06-07 02:34:38'),(15,'ReleaseHistory',6,'INSERT',NULL,'\0','apollo','2019-06-07 02:34:38',NULL,'2019-06-07 02:34:38'),(16,'Item',4,'DELETE',NULL,'\0','apollo','2019-06-07 02:37:53',NULL,'2019-06-07 02:37:53'),(17,'Release',7,'INSERT',NULL,'\0','apollo','2019-06-07 02:37:57',NULL,'2019-06-07 02:37:57'),(18,'ReleaseHistory',7,'INSERT',NULL,'\0','apollo','2019-06-07 02:37:57',NULL,'2019-06-07 02:37:57'),(19,'Item',5,'INSERT',NULL,'\0','apollo','2019-06-07 02:38:04',NULL,'2019-06-07 02:38:04'),(20,'Release',8,'INSERT',NULL,'\0','apollo','2019-06-07 02:38:04',NULL,'2019-06-07 02:38:04'),(21,'ReleaseHistory',8,'INSERT',NULL,'\0','apollo','2019-06-07 02:38:04',NULL,'2019-06-07 02:38:04'),(22,'Item',5,'DELETE',NULL,'\0','apollo','2019-06-07 02:42:19',NULL,'2019-06-07 02:42:19'),(23,'Release',9,'INSERT',NULL,'\0','apollo','2019-06-07 02:42:21',NULL,'2019-06-07 02:42:21'),(24,'ReleaseHistory',9,'INSERT',NULL,'\0','apollo','2019-06-07 02:42:21',NULL,'2019-06-07 02:42:21'),(25,'Item',6,'INSERT',NULL,'\0','apollo','2019-06-07 02:42:51',NULL,'2019-06-07 02:42:51'),(26,'Release',10,'INSERT',NULL,'\0','apollo','2019-06-07 02:42:51',NULL,'2019-06-07 02:42:51'),(27,'ReleaseHistory',10,'INSERT',NULL,'\0','apollo','2019-06-07 02:42:51',NULL,'2019-06-07 02:42:51'),(28,'Item',6,'DELETE',NULL,'\0','apollo','2019-06-07 02:43:24',NULL,'2019-06-07 02:43:24'),(29,'Release',11,'INSERT',NULL,'\0','apollo','2019-06-07 02:43:29',NULL,'2019-06-07 02:43:29'),(30,'ReleaseHistory',11,'INSERT',NULL,'\0','apollo','2019-06-07 02:43:29',NULL,'2019-06-07 02:43:29'),(31,'Item',7,'INSERT',NULL,'\0','apollo','2019-06-07 02:43:33',NULL,'2019-06-07 02:43:33'),(32,'Release',12,'INSERT',NULL,'\0','apollo','2019-06-07 02:43:33',NULL,'2019-06-07 02:43:33'),(33,'ReleaseHistory',12,'INSERT',NULL,'\0','apollo','2019-06-07 02:43:33',NULL,'2019-06-07 02:43:33'),(34,'Item',7,'DELETE',NULL,'\0','apollo','2019-06-07 02:44:10',NULL,'2019-06-07 02:44:10'),(35,'Release',13,'INSERT',NULL,'\0','apollo','2019-06-07 02:44:12',NULL,'2019-06-07 02:44:12'),(36,'ReleaseHistory',13,'INSERT',NULL,'\0','apollo','2019-06-07 02:44:12',NULL,'2019-06-07 02:44:12'),(37,'Item',8,'INSERT',NULL,'\0','apollo','2019-06-07 02:44:34',NULL,'2019-06-07 02:44:34'),(38,'Release',14,'INSERT',NULL,'\0','apollo','2019-06-07 02:44:34',NULL,'2019-06-07 02:44:34'),(39,'ReleaseHistory',14,'INSERT',NULL,'\0','apollo','2019-06-07 02:44:34',NULL,'2019-06-07 02:44:34'),(40,'Item',8,'DELETE',NULL,'\0','apollo','2019-06-07 02:52:03',NULL,'2019-06-07 02:52:03'),(41,'Release',15,'INSERT',NULL,'\0','apollo','2019-06-07 02:52:05',NULL,'2019-06-07 02:52:05'),(42,'ReleaseHistory',15,'INSERT',NULL,'\0','apollo','2019-06-07 02:52:05',NULL,'2019-06-07 02:52:05'),(43,'Item',9,'INSERT',NULL,'\0','apollo','2019-06-07 02:52:32',NULL,'2019-06-07 02:52:32'),(44,'Release',16,'INSERT',NULL,'\0','apollo','2019-06-07 02:52:32',NULL,'2019-06-07 02:52:32'),(45,'ReleaseHistory',16,'INSERT',NULL,'\0','apollo','2019-06-07 02:52:32',NULL,'2019-06-07 02:52:32'),(46,'Item',9,'DELETE',NULL,'\0','apollo','2019-06-07 02:53:49',NULL,'2019-06-07 02:53:49'),(47,'Release',17,'INSERT',NULL,'\0','apollo','2019-06-07 02:53:51',NULL,'2019-06-07 02:53:51'),(48,'ReleaseHistory',17,'INSERT',NULL,'\0','apollo','2019-06-07 02:53:51',NULL,'2019-06-07 02:53:51'),(49,'Item',10,'INSERT',NULL,'\0','apollo','2019-06-07 02:54:21',NULL,'2019-06-07 02:54:21'),(50,'Release',18,'INSERT',NULL,'\0','apollo','2019-06-07 02:54:21',NULL,'2019-06-07 02:54:21'),(51,'ReleaseHistory',18,'INSERT',NULL,'\0','apollo','2019-06-07 02:54:21',NULL,'2019-06-07 02:54:21'),(52,'Item',10,'DELETE',NULL,'\0','apollo','2019-06-07 02:54:31',NULL,'2019-06-07 02:54:31'),(53,'Release',19,'INSERT',NULL,'\0','apollo','2019-06-07 02:54:31',NULL,'2019-06-07 02:54:31'),(54,'ReleaseHistory',19,'INSERT',NULL,'\0','apollo','2019-06-07 02:54:31',NULL,'2019-06-07 02:54:31'),(55,'Item',11,'INSERT',NULL,'\0','apollo','2019-06-07 02:55:47',NULL,'2019-06-07 02:55:47'),(56,'Release',20,'INSERT',NULL,'\0','apollo','2019-06-07 02:55:47',NULL,'2019-06-07 02:55:47'),(57,'ReleaseHistory',20,'INSERT',NULL,'\0','apollo','2019-06-07 02:55:47',NULL,'2019-06-07 02:55:47'),(58,'Item',11,'DELETE',NULL,'\0','apollo','2019-06-07 02:55:57',NULL,'2019-06-07 02:55:57'),(59,'Release',21,'INSERT',NULL,'\0','apollo','2019-06-07 02:55:57',NULL,'2019-06-07 02:55:57'),(60,'ReleaseHistory',21,'INSERT',NULL,'\0','apollo','2019-06-07 02:55:57',NULL,'2019-06-07 02:55:57'),(61,'Item',12,'INSERT',NULL,'\0','apollo','2019-06-07 02:58:12',NULL,'2019-06-07 02:58:12'),(62,'Release',22,'INSERT',NULL,'\0','apollo','2019-06-07 02:58:12',NULL,'2019-06-07 02:58:12'),(63,'ReleaseHistory',22,'INSERT',NULL,'\0','apollo','2019-06-07 02:58:12',NULL,'2019-06-07 02:58:12'),(64,'Item',12,'DELETE',NULL,'\0','apollo','2019-06-07 02:58:22',NULL,'2019-06-07 02:58:22'),(65,'Release',23,'INSERT',NULL,'\0','apollo','2019-06-07 02:58:22',NULL,'2019-06-07 02:58:22'),(66,'ReleaseHistory',23,'INSERT',NULL,'\0','apollo','2019-06-07 02:58:22',NULL,'2019-06-07 02:58:22'),(67,'Item',13,'INSERT',NULL,'\0','apollo','2019-06-07 02:59:03',NULL,'2019-06-07 02:59:03'),(68,'Release',24,'INSERT',NULL,'\0','apollo','2019-06-07 02:59:03',NULL,'2019-06-07 02:59:03'),(69,'ReleaseHistory',24,'INSERT',NULL,'\0','apollo','2019-06-07 02:59:03',NULL,'2019-06-07 02:59:03'),(70,'Item',13,'DELETE',NULL,'\0','apollo','2019-06-07 02:59:13',NULL,'2019-06-07 02:59:13'),(71,'Release',25,'INSERT',NULL,'\0','apollo','2019-06-07 02:59:13',NULL,'2019-06-07 02:59:13'),(72,'ReleaseHistory',25,'INSERT',NULL,'\0','apollo','2019-06-07 02:59:13',NULL,'2019-06-07 02:59:13'),(73,'Item',14,'INSERT',NULL,'\0','apollo','2019-06-07 03:01:38',NULL,'2019-06-07 03:01:38'),(74,'Release',26,'INSERT',NULL,'\0','apollo','2019-06-07 03:01:38',NULL,'2019-06-07 03:01:38'),(75,'ReleaseHistory',26,'INSERT',NULL,'\0','apollo','2019-06-07 03:01:38',NULL,'2019-06-07 03:01:38'),(76,'Item',14,'DELETE',NULL,'\0','apollo','2019-06-07 03:01:48',NULL,'2019-06-07 03:01:48'),(77,'Release',27,'INSERT',NULL,'\0','apollo','2019-06-07 03:01:49',NULL,'2019-06-07 03:01:49'),(78,'ReleaseHistory',27,'INSERT',NULL,'\0','apollo','2019-06-07 03:01:49',NULL,'2019-06-07 03:01:49'),(79,'Item',15,'INSERT',NULL,'\0','apollo','2019-06-07 03:02:19',NULL,'2019-06-07 03:02:19'),(80,'Release',28,'INSERT',NULL,'\0','apollo','2019-06-07 03:02:19',NULL,'2019-06-07 03:02:19'),(81,'ReleaseHistory',28,'INSERT',NULL,'\0','apollo','2019-06-07 03:02:19',NULL,'2019-06-07 03:02:19'),(82,'Item',15,'DELETE',NULL,'\0','apollo','2019-06-07 03:02:29',NULL,'2019-06-07 03:02:29'),(83,'Release',29,'INSERT',NULL,'\0','apollo','2019-06-07 03:02:29',NULL,'2019-06-07 03:02:29'),(84,'ReleaseHistory',29,'INSERT',NULL,'\0','apollo','2019-06-07 03:02:29',NULL,'2019-06-07 03:02:29'),(85,'Item',16,'INSERT',NULL,'\0','apollo','2019-06-07 03:04:05',NULL,'2019-06-07 03:04:05'),(86,'Release',30,'INSERT',NULL,'\0','apollo','2019-06-07 03:04:05',NULL,'2019-06-07 03:04:05'),(87,'ReleaseHistory',30,'INSERT',NULL,'\0','apollo','2019-06-07 03:04:05',NULL,'2019-06-07 03:04:05'),(88,'Item',16,'DELETE',NULL,'\0','apollo','2019-06-07 03:04:15',NULL,'2019-06-07 03:04:15'),(89,'Release',31,'INSERT',NULL,'\0','apollo','2019-06-07 03:04:15',NULL,'2019-06-07 03:04:15'),(90,'ReleaseHistory',31,'INSERT',NULL,'\0','apollo','2019-06-07 03:04:15',NULL,'2019-06-07 03:04:15'),(91,'Item',17,'INSERT',NULL,'\0','apollo','2019-06-07 03:05:21',NULL,'2019-06-07 03:05:21'),(92,'Release',32,'INSERT',NULL,'\0','apollo','2019-06-07 03:05:21',NULL,'2019-06-07 03:05:21'),(93,'ReleaseHistory',32,'INSERT',NULL,'\0','apollo','2019-06-07 03:05:21',NULL,'2019-06-07 03:05:21'),(94,'Item',17,'DELETE',NULL,'\0','apollo','2019-06-07 03:05:31',NULL,'2019-06-07 03:05:31'),(95,'Release',33,'INSERT',NULL,'\0','apollo','2019-06-07 03:05:31',NULL,'2019-06-07 03:05:31'),(96,'ReleaseHistory',33,'INSERT',NULL,'\0','apollo','2019-06-07 03:05:31',NULL,'2019-06-07 03:05:31'),(97,'Item',18,'INSERT',NULL,'\0','apollo','2019-06-07 03:08:42',NULL,'2019-06-07 03:08:42'),(98,'Release',34,'INSERT',NULL,'\0','apollo','2019-06-07 03:08:42',NULL,'2019-06-07 03:08:42'),(99,'ReleaseHistory',34,'INSERT',NULL,'\0','apollo','2019-06-07 03:08:42',NULL,'2019-06-07 03:08:42'),(100,'Item',18,'DELETE',NULL,'\0','apollo','2019-06-07 03:08:52',NULL,'2019-06-07 03:08:52'),(101,'Release',35,'INSERT',NULL,'\0','apollo','2019-06-07 03:08:52',NULL,'2019-06-07 03:08:52'),(102,'ReleaseHistory',35,'INSERT',NULL,'\0','apollo','2019-06-07 03:08:52',NULL,'2019-06-07 03:08:52');
/*!40000 ALTER TABLE `Audit` ENABLE KEYS */;

--
-- Table structure for table `Cluster`
--

DROP TABLE IF EXISTS `Cluster`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Cluster` (
  `Id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `Name` varchar(32) NOT NULL DEFAULT '',
  `AppId` varchar(32) NOT NULL DEFAULT '',
  `ParentClusterId` int(10) unsigned NOT NULL DEFAULT '0',
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT '',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `IX_AppId_Name` (`AppId`,`Name`),
  KEY `IX_ParentClusterId` (`ParentClusterId`),
  KEY `DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Cluster`
--

/*!40000 ALTER TABLE `Cluster` DISABLE KEYS */;
INSERT INTO `Cluster` VALUES (1,'default','SampleApp',0,'\0','','2019-06-06 15:14:00','','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `Cluster` ENABLE KEYS */;

--
-- Table structure for table `Commit`
--

DROP TABLE IF EXISTS `Commit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Commit` (
  `Id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `ChangeSets` longtext NOT NULL,
  `AppId` varchar(500) NOT NULL DEFAULT 'default',
  `ClusterName` varchar(500) NOT NULL DEFAULT 'default',
  `NamespaceName` varchar(500) NOT NULL DEFAULT 'default',
  `Comment` varchar(500) DEFAULT NULL,
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `DataChange_LastTime` (`DataChange_LastTime`),
  KEY `AppId` (`AppId`(191)),
  KEY `ClusterName` (`ClusterName`(191)),
  KEY `NamespaceName` (`NamespaceName`(191))
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Commit`
--

/*!40000 ALTER TABLE `Commit` DISABLE KEYS */;
INSERT INTO `Commit` VALUES (1,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":2,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:28:50\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:28:50\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:28:50','apollo','2019-06-07 02:28:50'),(2,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":2,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:28:50\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:31:22\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:31:23','apollo','2019-06-07 02:31:23'),(3,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":3,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:31:49\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:31:49\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:31:50','apollo','2019-06-07 02:31:50'),(4,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":3,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:31:50\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:34:01\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:34:02','apollo','2019-06-07 02:34:02'),(5,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":4,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:34:37\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:34:37\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:34:37','apollo','2019-06-07 02:34:37'),(6,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":4,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:34:37\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:37:53\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:37:53','apollo','2019-06-07 02:37:53'),(7,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":5,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:38:04\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:38:04\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:38:04','apollo','2019-06-07 02:38:04'),(8,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":5,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:38:04\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:42:18\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:42:19','apollo','2019-06-07 02:42:19'),(9,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":6,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:42:50\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:42:50\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:42:51','apollo','2019-06-07 02:42:51'),(10,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":6,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:42:51\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:43:23\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:43:24','apollo','2019-06-07 02:43:24'),(11,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":7,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:43:33\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:43:33\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:43:33','apollo','2019-06-07 02:43:33'),(12,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":7,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:43:33\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:44:10\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:44:10','apollo','2019-06-07 02:44:10'),(13,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":8,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:44:33\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:44:33\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:44:34','apollo','2019-06-07 02:44:34'),(14,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":8,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:44:34\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:52:02\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:52:03','apollo','2019-06-07 02:52:03'),(15,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":9,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:52:31\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:52:31\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:52:32','apollo','2019-06-07 02:52:32'),(16,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":9,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:52:32\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:53:48\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:53:49','apollo','2019-06-07 02:53:49'),(17,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":10,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:54:20\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:54:20\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:54:21','apollo','2019-06-07 02:54:21'),(18,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":10,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:54:21\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:54:30\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:54:31','apollo','2019-06-07 02:54:31'),(19,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":11,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:55:47\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:55:47\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:55:47','apollo','2019-06-07 02:55:47'),(20,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":11,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:55:47\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:55:56\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:55:57','apollo','2019-06-07 02:55:57'),(21,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":12,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:58:12\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:58:12\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:58:12','apollo','2019-06-07 02:58:12'),(22,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":12,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:58:12\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:58:22\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:58:22','apollo','2019-06-07 02:58:22'),(23,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":13,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:59:03\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:59:03\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:59:03','apollo','2019-06-07 02:59:03'),(24,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":13,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 10:59:03\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 10:59:13\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 02:59:13','apollo','2019-06-07 02:59:13'),(25,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":14,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 11:01:38\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 11:01:38\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 03:01:38','apollo','2019-06-07 03:01:38'),(26,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":14,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 11:01:38\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 11:01:48\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 03:01:48','apollo','2019-06-07 03:01:48'),(27,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":15,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 11:02:18\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 11:02:18\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 03:02:19','apollo','2019-06-07 03:02:19'),(28,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":15,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 11:02:19\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 11:02:28\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 03:02:29','apollo','2019-06-07 03:02:29'),(29,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":16,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 11:04:05\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 11:04:05\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 03:04:05','apollo','2019-06-07 03:04:05'),(30,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":16,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 11:04:05\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 11:04:15\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 03:04:15','apollo','2019-06-07 03:04:15'),(31,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":17,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 11:05:20\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 11:05:20\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 03:05:21','apollo','2019-06-07 03:05:21'),(32,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":17,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 11:05:21\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 11:05:31\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 03:05:31','apollo','2019-06-07 03:05:31'),(33,'{\"createItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":18,\"isDeleted\":false,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 11:08:41\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 11:08:41\"}],\"updateItems\":[],\"deleteItems\":[]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 03:08:42','apollo','2019-06-07 03:08:42'),(34,'{\"createItems\":[],\"updateItems\":[],\"deleteItems\":[{\"namespaceId\":1,\"key\":\"test-module.default.testKey\",\"value\":\"3000\",\"comment\":\"test key\",\"lineNum\":2,\"id\":18,\"isDeleted\":true,\"dataChangeCreatedBy\":\"apollo\",\"dataChangeCreatedTime\":\"2019-06-07 11:08:42\",\"dataChangeLastModifiedBy\":\"apollo\",\"dataChangeLastModifiedTime\":\"2019-06-07 11:08:51\"}]}','SampleApp','default','application',NULL,'\0','apollo','2019-06-07 03:08:52','apollo','2019-06-07 03:08:52');
/*!40000 ALTER TABLE `Commit` ENABLE KEYS */;

--
-- Table structure for table `GrayReleaseRule`
--

DROP TABLE IF EXISTS `GrayReleaseRule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `GrayReleaseRule` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `AppId` varchar(32) NOT NULL DEFAULT 'default',
  `ClusterName` varchar(32) NOT NULL DEFAULT 'default',
  `NamespaceName` varchar(32) NOT NULL DEFAULT 'default',
  `BranchName` varchar(32) NOT NULL DEFAULT 'default',
  `Rules` varchar(16000) DEFAULT '[]',
  `ReleaseId` int(11) unsigned NOT NULL DEFAULT '0',
  `BranchStatus` tinyint(2) DEFAULT '1',
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `DataChange_LastTime` (`DataChange_LastTime`),
  KEY `IX_Namespace` (`AppId`,`ClusterName`,`NamespaceName`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `GrayReleaseRule`
--

/*!40000 ALTER TABLE `GrayReleaseRule` DISABLE KEYS */;
/*!40000 ALTER TABLE `GrayReleaseRule` ENABLE KEYS */;

--
-- Table structure for table `Instance`
--

DROP TABLE IF EXISTS `Instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Instance` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `AppId` varchar(32) NOT NULL DEFAULT 'default',
  `ClusterName` varchar(32) NOT NULL DEFAULT 'default',
  `DataCenter` varchar(64) NOT NULL DEFAULT 'default',
  `Ip` varchar(32) NOT NULL DEFAULT '',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  UNIQUE KEY `IX_UNIQUE_KEY` (`AppId`,`ClusterName`,`Ip`,`DataCenter`),
  KEY `IX_IP` (`Ip`),
  KEY `IX_DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Instance`
--

/*!40000 ALTER TABLE `Instance` DISABLE KEYS */;
INSERT INTO `Instance` VALUES (1,'SampleApp','default','','localhost','2019-06-07 03:09:46','2019-06-07 03:09:46');
/*!40000 ALTER TABLE `Instance` ENABLE KEYS */;

--
-- Table structure for table `InstanceConfig`
--

DROP TABLE IF EXISTS `InstanceConfig`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `InstanceConfig` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `InstanceId` int(11) unsigned DEFAULT NULL,
  `ConfigAppId` varchar(32) NOT NULL DEFAULT 'default',
  `ConfigClusterName` varchar(32) NOT NULL DEFAULT 'default',
  `ConfigNamespaceName` varchar(32) NOT NULL DEFAULT 'default',
  `ReleaseKey` varchar(64) NOT NULL DEFAULT '',
  `ReleaseDeliveryTime` timestamp NULL DEFAULT NULL,
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  UNIQUE KEY `IX_UNIQUE_KEY` (`InstanceId`,`ConfigAppId`,`ConfigNamespaceName`),
  KEY `IX_ReleaseKey` (`ReleaseKey`),
  KEY `IX_DataChange_LastTime` (`DataChange_LastTime`),
  KEY `IX_Valid_Namespace` (`ConfigAppId`,`ConfigClusterName`,`ConfigNamespaceName`,`DataChange_LastTime`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `InstanceConfig`
--

/*!40000 ALTER TABLE `InstanceConfig` DISABLE KEYS */;
INSERT INTO `InstanceConfig` VALUES (1,1,'SampleApp','default','application','20190607110851-1dc557645477a0d9','2019-06-07 03:09:45','2019-06-07 03:09:45','2019-06-07 03:09:45');
/*!40000 ALTER TABLE `InstanceConfig` ENABLE KEYS */;

--
-- Table structure for table `Item`
--

DROP TABLE IF EXISTS `Item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Item` (
  `Id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `NamespaceId` int(10) unsigned NOT NULL DEFAULT '0',
  `Key` varchar(128) NOT NULL DEFAULT 'default',
  `Value` longtext NOT NULL,
  `Comment` varchar(1024) DEFAULT '',
  `LineNum` int(10) unsigned DEFAULT '0',
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `IX_GroupId` (`NamespaceId`),
  KEY `DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Item`
--

/*!40000 ALTER TABLE `Item` DISABLE KEYS */;
INSERT INTO `Item` VALUES (1,1,'timeout','100','sample timeout配置',1,'\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00'),(2,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 02:28:50','apollo','2019-06-07 02:31:22'),(3,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 02:31:50','apollo','2019-06-07 02:34:02'),(4,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 02:34:37','apollo','2019-06-07 02:37:53'),(5,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 02:38:04','apollo','2019-06-07 02:42:19'),(6,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 02:42:51','apollo','2019-06-07 02:43:24'),(7,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 02:43:33','apollo','2019-06-07 02:44:10'),(8,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 02:44:34','apollo','2019-06-07 02:52:03'),(9,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 02:52:32','apollo','2019-06-07 02:53:49'),(10,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 02:54:21','apollo','2019-06-07 02:54:31'),(11,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 02:55:47','apollo','2019-06-07 02:55:57'),(12,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 02:58:12','apollo','2019-06-07 02:58:22'),(13,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 02:59:03','apollo','2019-06-07 02:59:13'),(14,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 03:01:38','apollo','2019-06-07 03:01:48'),(15,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 03:02:19','apollo','2019-06-07 03:02:29'),(16,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 03:04:05','apollo','2019-06-07 03:04:15'),(17,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 03:05:21','apollo','2019-06-07 03:05:31'),(18,1,'test-module.default.testKey','3000','test key',2,'','apollo','2019-06-07 03:08:42','apollo','2019-06-07 03:08:52');
/*!40000 ALTER TABLE `Item` ENABLE KEYS */;

--
-- Table structure for table `Namespace`
--

DROP TABLE IF EXISTS `Namespace`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Namespace` (
  `Id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `AppId` varchar(500) NOT NULL DEFAULT 'default',
  `ClusterName` varchar(500) NOT NULL DEFAULT 'default',
  `NamespaceName` varchar(500) NOT NULL DEFAULT 'default',
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `AppId_ClusterName_NamespaceName` (`AppId`(191),`ClusterName`(191),`NamespaceName`(191)),
  KEY `DataChange_LastTime` (`DataChange_LastTime`),
  KEY `IX_NamespaceName` (`NamespaceName`(191))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Namespace`
--

/*!40000 ALTER TABLE `Namespace` DISABLE KEYS */;
INSERT INTO `Namespace` VALUES (1,'SampleApp','default','application','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `Namespace` ENABLE KEYS */;

--
-- Table structure for table `NamespaceLock`
--

DROP TABLE IF EXISTS `NamespaceLock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `NamespaceLock` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `NamespaceId` int(10) unsigned NOT NULL DEFAULT '0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT 'default',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `IsDeleted` bit(1) DEFAULT b'0',
  PRIMARY KEY (`Id`),
  UNIQUE KEY `IX_NamespaceId` (`NamespaceId`),
  KEY `DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `NamespaceLock`
--

/*!40000 ALTER TABLE `NamespaceLock` DISABLE KEYS */;
/*!40000 ALTER TABLE `NamespaceLock` ENABLE KEYS */;

--
-- Table structure for table `Release`
--

DROP TABLE IF EXISTS `Release`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Release` (
  `Id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `ReleaseKey` varchar(64) NOT NULL DEFAULT '',
  `Name` varchar(64) NOT NULL DEFAULT 'default',
  `Comment` varchar(256) DEFAULT NULL,
  `AppId` varchar(500) NOT NULL DEFAULT 'default',
  `ClusterName` varchar(500) NOT NULL DEFAULT 'default',
  `NamespaceName` varchar(500) NOT NULL DEFAULT 'default',
  `Configurations` longtext NOT NULL,
  `IsAbandoned` bit(1) NOT NULL DEFAULT b'0',
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `AppId_ClusterName_GroupName` (`AppId`(191),`ClusterName`(191),`NamespaceName`(191)),
  KEY `DataChange_LastTime` (`DataChange_LastTime`),
  KEY `IX_ReleaseKey` (`ReleaseKey`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Release`
--

/*!40000 ALTER TABLE `Release` DISABLE KEYS */;
INSERT INTO `Release` VALUES (1,'20161009155425-d3a0749c6e20bc15','20161009155424-release','Sample发布','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00'),(2,'20190607102850-1dc557645477a0b8','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:28:51','apollo','2019-06-07 02:28:51'),(3,'20190607103124-1dc557645477a0b9','20190607103123-release','','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:31:25','apollo','2019-06-07 02:31:25'),(4,'20190607103149-1dc557645477a0ba','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:31:50','apollo','2019-06-07 02:31:50'),(5,'20190607103403-1dc557645477a0bb','20190607103402-release','','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:34:04','apollo','2019-06-07 02:34:04'),(6,'20190607103437-1dc557645477a0bc','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:34:37','apollo','2019-06-07 02:34:37'),(7,'20190607103756-1dc557645477a0bd','20190607103754-release','','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:37:57','apollo','2019-06-07 02:37:57'),(8,'20190607103804-1dc557645477a0be','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:38:04','apollo','2019-06-07 02:38:04'),(9,'20190607104220-1dc557645477a0bf','20190607104219-release','','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:42:21','apollo','2019-06-07 02:42:21'),(10,'20190607104251-1dc557645477a0c0','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:42:51','apollo','2019-06-07 02:42:51'),(11,'20190607104329-1dc557645477a0c1','20190607104324-release','','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:43:29','apollo','2019-06-07 02:43:29'),(12,'20190607104333-1dc557645477a0c2','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:43:33','apollo','2019-06-07 02:43:33'),(13,'20190607104412-1dc557645477a0c3','20190607104411-release','','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:44:12','apollo','2019-06-07 02:44:12'),(14,'20190607104433-1dc557645477a0c4','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:44:34','apollo','2019-06-07 02:44:34'),(15,'20190607105205-1dc557645477a0c5','20190607105203-release','','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:52:05','apollo','2019-06-07 02:52:05'),(16,'20190607105232-1dc557645477a0c6','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:52:32','apollo','2019-06-07 02:52:32'),(17,'20190607105350-1dc557645477a0c7','20190607105349-release','','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:53:51','apollo','2019-06-07 02:53:51'),(18,'20190607105420-1dc557645477a0c8','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:54:21','apollo','2019-06-07 02:54:21'),(19,'20190607105430-1dc557645477a0c9','2019-06-07','test','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:54:31','apollo','2019-06-07 02:54:31'),(20,'20190607105547-1dc557645477a0ca','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:55:47','apollo','2019-06-07 02:55:47'),(21,'20190607105557-1dc557645477a0cb','2019-06-07','test','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:55:57','apollo','2019-06-07 02:55:57'),(22,'20190607105812-1dc557645477a0cc','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:58:12','apollo','2019-06-07 02:58:12'),(23,'20190607105822-1dc557645477a0cd','2019-06-07','test','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:58:22','apollo','2019-06-07 02:58:22'),(24,'20190607105903-1dc557645477a0ce','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:59:03','apollo','2019-06-07 02:59:03'),(25,'20190607105913-1dc557645477a0cf','2019-06-07','test','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 02:59:13','apollo','2019-06-07 02:59:13'),(26,'20190607110138-1dc557645477a0d0','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 03:01:38','apollo','2019-06-07 03:01:38'),(27,'20190607110148-1dc557645477a0d1','2019-06-07','test','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 03:01:49','apollo','2019-06-07 03:01:49'),(28,'20190607110218-1dc557645477a0d2','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 03:02:19','apollo','2019-06-07 03:02:19'),(29,'20190607110228-1dc557645477a0d3','2019-06-07','test','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 03:02:29','apollo','2019-06-07 03:02:29'),(30,'20190607110405-1dc557645477a0d4','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 03:04:05','apollo','2019-06-07 03:04:05'),(31,'20190607110415-1dc557645477a0d5','2019-06-07','test','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 03:04:15','apollo','2019-06-07 03:04:15'),(32,'20190607110521-1dc557645477a0d6','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 03:05:21','apollo','2019-06-07 03:05:21'),(33,'20190607110531-1dc557645477a0d7','2019-06-07','test','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 03:05:31','apollo','2019-06-07 03:05:31'),(34,'20190607110841-1dc557645477a0d8','2019-06-07','test','SampleApp','default','application','{\"test-module.default.testKey\":\"3000\",\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 03:08:42','apollo','2019-06-07 03:08:42'),(35,'20190607110851-1dc557645477a0d9','2019-06-07','test','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','apollo','2019-06-07 03:08:52','apollo','2019-06-07 03:08:52');
/*!40000 ALTER TABLE `Release` ENABLE KEYS */;

--
-- Table structure for table `ReleaseHistory`
--

DROP TABLE IF EXISTS `ReleaseHistory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ReleaseHistory` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `AppId` varchar(32) NOT NULL DEFAULT 'default',
  `ClusterName` varchar(32) NOT NULL DEFAULT 'default',
  `NamespaceName` varchar(32) NOT NULL DEFAULT 'default',
  `BranchName` varchar(32) NOT NULL DEFAULT 'default',
  `ReleaseId` int(11) unsigned NOT NULL DEFAULT '0',
  `PreviousReleaseId` int(11) unsigned NOT NULL DEFAULT '0',
  `Operation` tinyint(3) unsigned NOT NULL DEFAULT '0',
  `OperationContext` longtext NOT NULL,
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `IX_Namespace` (`AppId`,`ClusterName`,`NamespaceName`,`BranchName`),
  KEY `IX_ReleaseId` (`ReleaseId`),
  KEY `IX_DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ReleaseHistory`
--

/*!40000 ALTER TABLE `ReleaseHistory` DISABLE KEYS */;
INSERT INTO `ReleaseHistory` VALUES (1,'SampleApp','default','application','default',1,0,0,'{}','\0','apollo','2019-06-06 15:14:00','apollo','2019-06-06 15:14:00'),(2,'SampleApp','default','application','default',2,1,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:28:51','apollo','2019-06-07 02:28:51'),(3,'SampleApp','default','application','default',3,2,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:31:25','apollo','2019-06-07 02:31:25'),(4,'SampleApp','default','application','default',4,3,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:31:50','apollo','2019-06-07 02:31:50'),(5,'SampleApp','default','application','default',5,4,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:34:04','apollo','2019-06-07 02:34:04'),(6,'SampleApp','default','application','default',6,5,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:34:38','apollo','2019-06-07 02:34:38'),(7,'SampleApp','default','application','default',7,6,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:37:57','apollo','2019-06-07 02:37:57'),(8,'SampleApp','default','application','default',8,7,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:38:04','apollo','2019-06-07 02:38:04'),(9,'SampleApp','default','application','default',9,8,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:42:21','apollo','2019-06-07 02:42:21'),(10,'SampleApp','default','application','default',10,9,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:42:51','apollo','2019-06-07 02:42:51'),(11,'SampleApp','default','application','default',11,10,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:43:29','apollo','2019-06-07 02:43:29'),(12,'SampleApp','default','application','default',12,11,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:43:33','apollo','2019-06-07 02:43:33'),(13,'SampleApp','default','application','default',13,12,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:44:12','apollo','2019-06-07 02:44:12'),(14,'SampleApp','default','application','default',14,13,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:44:34','apollo','2019-06-07 02:44:34'),(15,'SampleApp','default','application','default',15,14,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:52:05','apollo','2019-06-07 02:52:05'),(16,'SampleApp','default','application','default',16,15,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:52:32','apollo','2019-06-07 02:52:32'),(17,'SampleApp','default','application','default',17,16,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:53:51','apollo','2019-06-07 02:53:51'),(18,'SampleApp','default','application','default',18,17,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:54:21','apollo','2019-06-07 02:54:21'),(19,'SampleApp','default','application','default',19,18,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:54:31','apollo','2019-06-07 02:54:31'),(20,'SampleApp','default','application','default',20,19,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:55:47','apollo','2019-06-07 02:55:47'),(21,'SampleApp','default','application','default',21,20,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:55:57','apollo','2019-06-07 02:55:57'),(22,'SampleApp','default','application','default',22,21,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:58:12','apollo','2019-06-07 02:58:12'),(23,'SampleApp','default','application','default',23,22,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:58:22','apollo','2019-06-07 02:58:22'),(24,'SampleApp','default','application','default',24,23,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:59:03','apollo','2019-06-07 02:59:03'),(25,'SampleApp','default','application','default',25,24,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 02:59:13','apollo','2019-06-07 02:59:13'),(26,'SampleApp','default','application','default',26,25,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 03:01:38','apollo','2019-06-07 03:01:38'),(27,'SampleApp','default','application','default',27,26,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 03:01:49','apollo','2019-06-07 03:01:49'),(28,'SampleApp','default','application','default',28,27,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 03:02:19','apollo','2019-06-07 03:02:19'),(29,'SampleApp','default','application','default',29,28,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 03:02:29','apollo','2019-06-07 03:02:29'),(30,'SampleApp','default','application','default',30,29,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 03:04:05','apollo','2019-06-07 03:04:05'),(31,'SampleApp','default','application','default',31,30,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 03:04:15','apollo','2019-06-07 03:04:15'),(32,'SampleApp','default','application','default',32,31,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 03:05:21','apollo','2019-06-07 03:05:21'),(33,'SampleApp','default','application','default',33,32,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 03:05:31','apollo','2019-06-07 03:05:31'),(34,'SampleApp','default','application','default',34,33,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 03:08:42','apollo','2019-06-07 03:08:42'),(35,'SampleApp','default','application','default',35,34,0,'{\"isEmergencyPublish\":false}','\0','apollo','2019-06-07 03:08:52','apollo','2019-06-07 03:08:52');
/*!40000 ALTER TABLE `ReleaseHistory` ENABLE KEYS */;

--
-- Table structure for table `ReleaseMessage`
--

DROP TABLE IF EXISTS `ReleaseMessage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ReleaseMessage` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `Message` varchar(1024) NOT NULL DEFAULT '',
  `DataChange_LastTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `DataChange_LastTime` (`DataChange_LastTime`),
  KEY `IX_Message` (`Message`(191))
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ReleaseMessage`
--

/*!40000 ALTER TABLE `ReleaseMessage` DISABLE KEYS */;
INSERT INTO `ReleaseMessage` VALUES (34,'SampleApp+default+application','2019-06-07 03:08:52');
/*!40000 ALTER TABLE `ReleaseMessage` ENABLE KEYS */;

--
-- Table structure for table `ServerConfig`
--

DROP TABLE IF EXISTS `ServerConfig`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ServerConfig` (
  `Id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `Key` varchar(64) NOT NULL DEFAULT 'default',
  `Cluster` varchar(32) NOT NULL DEFAULT 'default',
  `Value` varchar(2048) NOT NULL DEFAULT 'default',
  `Comment` varchar(1024) DEFAULT '',
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `IX_Key` (`Key`),
  KEY `DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ServerConfig`
--

/*!40000 ALTER TABLE `ServerConfig` DISABLE KEYS */;
INSERT INTO `ServerConfig` VALUES (1,'eureka.service.url','default','http://localhost:8080/eureka/','','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00'),(2,'namespace.lock.switch','default','false','','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00'),(3,'item.value.length.limit','default','20000','','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00'),(4,'config-service.cache.enabled','default','false','','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00'),(5,'item.key.length.limit','default','128','','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `ServerConfig` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2019-06-07 11:25:19
