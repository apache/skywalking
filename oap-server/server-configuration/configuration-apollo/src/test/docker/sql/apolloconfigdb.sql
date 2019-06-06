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
--

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

CREATE DATABASE IF NOT EXISTS ApolloConfigDB DEFAULT CHARACTER SET = utf8mb4;

USE ApolloConfigDB;

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

LOCK TABLES `App` WRITE;
/*!40000 ALTER TABLE `App` DISABLE KEYS */;
INSERT INTO `App` VALUES (1,'SampleApp','Sample App','TEST1','样例部门1','apollo','apollo@acme.com','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `App` ENABLE KEYS */;
UNLOCK TABLES;

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

LOCK TABLES `AppNamespace` WRITE;
/*!40000 ALTER TABLE `AppNamespace` DISABLE KEYS */;
INSERT INTO `AppNamespace` VALUES (1,'application','SampleApp','properties','\0','default app namespace','\0','','2019-06-06 15:14:00','','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `AppNamespace` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Audit`
--

LOCK TABLES `Audit` WRITE;
/*!40000 ALTER TABLE `Audit` DISABLE KEYS */;
/*!40000 ALTER TABLE `Audit` ENABLE KEYS */;
UNLOCK TABLES;

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

LOCK TABLES `Cluster` WRITE;
/*!40000 ALTER TABLE `Cluster` DISABLE KEYS */;
INSERT INTO `Cluster` VALUES (1,'default','SampleApp',0,'\0','','2019-06-06 15:14:00','','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `Cluster` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Commit`
--

LOCK TABLES `Commit` WRITE;
/*!40000 ALTER TABLE `Commit` DISABLE KEYS */;
/*!40000 ALTER TABLE `Commit` ENABLE KEYS */;
UNLOCK TABLES;

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

LOCK TABLES `GrayReleaseRule` WRITE;
/*!40000 ALTER TABLE `GrayReleaseRule` DISABLE KEYS */;
/*!40000 ALTER TABLE `GrayReleaseRule` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Instance`
--

LOCK TABLES `Instance` WRITE;
/*!40000 ALTER TABLE `Instance` DISABLE KEYS */;
/*!40000 ALTER TABLE `Instance` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `InstanceConfig`
--

LOCK TABLES `InstanceConfig` WRITE;
/*!40000 ALTER TABLE `InstanceConfig` DISABLE KEYS */;
/*!40000 ALTER TABLE `InstanceConfig` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Item`
--

LOCK TABLES `Item` WRITE;
/*!40000 ALTER TABLE `Item` DISABLE KEYS */;
INSERT INTO `Item` VALUES (1,1,'timeout','100','sample timeout配置',1,'\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `Item` ENABLE KEYS */;
UNLOCK TABLES;

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

LOCK TABLES `Namespace` WRITE;
/*!40000 ALTER TABLE `Namespace` DISABLE KEYS */;
INSERT INTO `Namespace` VALUES (1,'SampleApp','default','application','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `Namespace` ENABLE KEYS */;
UNLOCK TABLES;

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

LOCK TABLES `NamespaceLock` WRITE;
/*!40000 ALTER TABLE `NamespaceLock` DISABLE KEYS */;
/*!40000 ALTER TABLE `NamespaceLock` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Release`
--

LOCK TABLES `Release` WRITE;
/*!40000 ALTER TABLE `Release` DISABLE KEYS */;
INSERT INTO `Release` VALUES (1,'20161009155425-d3a0749c6e20bc15','20161009155424-release','Sample发布','SampleApp','default','application','{\"timeout\":\"100\"}','\0','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `Release` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ReleaseHistory`
--

LOCK TABLES `ReleaseHistory` WRITE;
/*!40000 ALTER TABLE `ReleaseHistory` DISABLE KEYS */;
INSERT INTO `ReleaseHistory` VALUES (1,'SampleApp','default','application','default',1,0,0,'{}','\0','apollo','2019-06-06 15:14:00','apollo','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `ReleaseHistory` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ReleaseMessage`
--

LOCK TABLES `ReleaseMessage` WRITE;
/*!40000 ALTER TABLE `ReleaseMessage` DISABLE KEYS */;
/*!40000 ALTER TABLE `ReleaseMessage` ENABLE KEYS */;
UNLOCK TABLES;

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

LOCK TABLES `ServerConfig` WRITE;
/*!40000 ALTER TABLE `ServerConfig` DISABLE KEYS */;
INSERT INTO `ServerConfig` VALUES (1,'eureka.service.url','default','http://localhost:8080/eureka/','Eureka服务Url，多个service以英文逗号分隔','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00'),(2,'namespace.lock.switch','default','false','一次发布只能有一个人修改开关','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00'),(3,'item.value.length.limit','default','20000','item value最大长度限制','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00'),(4,'config-service.cache.enabled','default','false','ConfigService是否开启缓存，开启后能提高性能，但是会增大内存消耗！','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00'),(5,'item.key.length.limit','default','128','item key 最大长度限制','\0','default','2019-06-06 15:14:00','','2019-06-06 15:14:00');
/*!40000 ALTER TABLE `ServerConfig` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2019-06-06 23:24:47
