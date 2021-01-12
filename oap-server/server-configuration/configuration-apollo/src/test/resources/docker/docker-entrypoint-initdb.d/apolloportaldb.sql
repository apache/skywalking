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
-- Host: 127.0.0.1    Database: ApolloPortalDB
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
-- Current Database: `ApolloPortalDB`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `ApolloPortalDB` /*!40100 DEFAULT CHARACTER SET utf8mb4 */;

USE `ApolloPortalDB`;

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
INSERT INTO `App` VALUES (1,'SampleApp','Sample App','TEST1','样例部门1','apollo','apollo@acme.com','\0','default','2019-06-06 15:14:01','','2019-06-06 15:14:01');
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
INSERT INTO `AppNamespace` VALUES (1,'application','SampleApp','properties','\0','default app namespace','\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01');
/*!40000 ALTER TABLE `AppNamespace` ENABLE KEYS */;

--
-- Table structure for table `Authorities`
--

DROP TABLE IF EXISTS `Authorities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Authorities` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `Username` varchar(64) NOT NULL,
  `Authority` varchar(50) NOT NULL,
  PRIMARY KEY (`Id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Authorities`
--

/*!40000 ALTER TABLE `Authorities` DISABLE KEYS */;
INSERT INTO `Authorities` VALUES (1,'apollo','ROLE_user');
/*!40000 ALTER TABLE `Authorities` ENABLE KEYS */;

--
-- Table structure for table `Consumer`
--

DROP TABLE IF EXISTS `Consumer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Consumer` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
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
  KEY `DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Consumer`
--

/*!40000 ALTER TABLE `Consumer` DISABLE KEYS */;
INSERT INTO `Consumer` VALUES (1,'SkyWalking','SkyWalking','TEST1','样例部门1','apollo','apollo@acme.com','\0','apollo','2019-06-06 15:17:07','apollo','2019-06-06 15:17:07');
/*!40000 ALTER TABLE `Consumer` ENABLE KEYS */;

--
-- Table structure for table `ConsumerAudit`
--

DROP TABLE IF EXISTS `ConsumerAudit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ConsumerAudit` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `ConsumerId` int(11) unsigned DEFAULT NULL,
  `Uri` varchar(1024) NOT NULL DEFAULT '',
  `Method` varchar(16) NOT NULL DEFAULT '',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `IX_DataChange_LastTime` (`DataChange_LastTime`),
  KEY `IX_ConsumerId` (`ConsumerId`)
) ENGINE=InnoDB AUTO_INCREMENT=57 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ConsumerAudit`
--

/*!40000 ALTER TABLE `ConsumerAudit` DISABLE KEYS */;
INSERT INTO `ConsumerAudit` VALUES (1,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:27:32','2019-06-07 02:27:32'),(2,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:28:50','2019-06-07 02:28:50'),(3,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:28:50','2019-06-07 02:28:50'),(4,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:31:18','2019-06-07 02:31:18'),(5,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:31:50','2019-06-07 02:31:50'),(6,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:31:50','2019-06-07 02:31:50'),(7,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:34:37','2019-06-07 02:34:37'),(8,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:34:37','2019-06-07 02:34:37'),(9,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:36:21','2019-06-07 02:36:21'),(10,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:38:04','2019-06-07 02:38:04'),(11,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:38:04','2019-06-07 02:38:04'),(12,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:42:10','2019-06-07 02:42:10'),(13,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:42:51','2019-06-07 02:42:51'),(14,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:42:51','2019-06-07 02:42:51'),(15,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:43:33','2019-06-07 02:43:33'),(16,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:43:33','2019-06-07 02:43:33'),(17,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:44:34','2019-06-07 02:44:34'),(18,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:44:34','2019-06-07 02:44:34'),(19,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:52:32','2019-06-07 02:52:32'),(20,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:52:32','2019-06-07 02:52:32'),(21,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:54:21','2019-06-07 02:54:21'),(22,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:54:21','2019-06-07 02:54:21'),(23,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items/test-module.default.testKey?operator=apollo','DELETE','2019-06-07 02:54:31','2019-06-07 02:54:31'),(24,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:54:31','2019-06-07 02:54:31'),(25,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:55:47','2019-06-07 02:55:47'),(26,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:55:47','2019-06-07 02:55:47'),(27,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items/test-module.default.testKey?operator=apollo','DELETE','2019-06-07 02:55:57','2019-06-07 02:55:57'),(28,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:55:57','2019-06-07 02:55:57'),(29,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:58:12','2019-06-07 02:58:12'),(30,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:58:12','2019-06-07 02:58:12'),(31,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items/test-module.default.testKey?operator=apollo','DELETE','2019-06-07 02:58:22','2019-06-07 02:58:22'),(32,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:58:22','2019-06-07 02:58:22'),(33,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 02:59:03','2019-06-07 02:59:03'),(34,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:59:03','2019-06-07 02:59:03'),(35,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items/test-module.default.testKey?operator=apollo','DELETE','2019-06-07 02:59:13','2019-06-07 02:59:13'),(36,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 02:59:13','2019-06-07 02:59:13'),(37,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 03:01:38','2019-06-07 03:01:38'),(38,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 03:01:38','2019-06-07 03:01:38'),(39,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items/test-module.default.testKey?operator=apollo','DELETE','2019-06-07 03:01:48','2019-06-07 03:01:48'),(40,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 03:01:48','2019-06-07 03:01:48'),(41,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 03:02:19','2019-06-07 03:02:19'),(42,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 03:02:19','2019-06-07 03:02:19'),(43,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items/test-module.default.testKey?operator=apollo','DELETE','2019-06-07 03:02:29','2019-06-07 03:02:29'),(44,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 03:02:29','2019-06-07 03:02:29'),(45,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 03:04:05','2019-06-07 03:04:05'),(46,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 03:04:05','2019-06-07 03:04:05'),(47,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items/test-module.default.testKey?operator=apollo','DELETE','2019-06-07 03:04:15','2019-06-07 03:04:15'),(48,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 03:04:15','2019-06-07 03:04:15'),(49,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 03:05:21','2019-06-07 03:05:21'),(50,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 03:05:21','2019-06-07 03:05:21'),(51,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items/test-module.default.testKey?operator=apollo','DELETE','2019-06-07 03:05:31','2019-06-07 03:05:31'),(52,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 03:05:31','2019-06-07 03:05:31'),(53,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items','POST','2019-06-07 03:08:42','2019-06-07 03:08:42'),(54,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 03:08:42','2019-06-07 03:08:42'),(55,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/items/test-module.default.testKey?operator=apollo','DELETE','2019-06-07 03:08:52','2019-06-07 03:08:52'),(56,1,'/openapi/v1/envs/DEV/apps/SampleApp/clusters/default/namespaces/application/releases','POST','2019-06-07 03:08:52','2019-06-07 03:08:52');
/*!40000 ALTER TABLE `ConsumerAudit` ENABLE KEYS */;

--
-- Table structure for table `ConsumerRole`
--

DROP TABLE IF EXISTS `ConsumerRole`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ConsumerRole` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `ConsumerId` int(11) unsigned DEFAULT NULL,
  `RoleId` int(10) unsigned DEFAULT NULL,
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) DEFAULT '',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `IX_DataChange_LastTime` (`DataChange_LastTime`),
  KEY `IX_RoleId` (`RoleId`),
  KEY `IX_ConsumerId_RoleId` (`ConsumerId`,`RoleId`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ConsumerRole`
--

/*!40000 ALTER TABLE `ConsumerRole` DISABLE KEYS */;
INSERT INTO `ConsumerRole` VALUES (1,1,1,'\0','apollo','2019-06-07 02:28:21','apollo','2019-06-07 02:28:21');
/*!40000 ALTER TABLE `ConsumerRole` ENABLE KEYS */;

--
-- Table structure for table `ConsumerToken`
--

DROP TABLE IF EXISTS `ConsumerToken`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ConsumerToken` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `ConsumerId` int(11) unsigned DEFAULT NULL,
  `Token` varchar(128) NOT NULL DEFAULT '',
  `Expires` datetime NOT NULL DEFAULT '2099-01-01 00:00:00',
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  UNIQUE KEY `IX_Token` (`Token`),
  KEY `DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ConsumerToken`
--

/*!40000 ALTER TABLE `ConsumerToken` DISABLE KEYS */;
INSERT INTO `ConsumerToken` VALUES (1,1,'f71f002a4ff9845639ef655ee7019759e31449de','2099-01-01 00:00:00','\0','apollo','2019-06-06 15:17:07','apollo','2019-06-06 15:17:07');
/*!40000 ALTER TABLE `ConsumerToken` ENABLE KEYS */;

--
-- Table structure for table `Favorite`
--

DROP TABLE IF EXISTS `Favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Favorite` (
  `Id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `UserId` varchar(32) NOT NULL DEFAULT 'default',
  `AppId` varchar(500) NOT NULL DEFAULT 'default',
  `Position` int(32) NOT NULL DEFAULT '10000',
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `AppId` (`AppId`(191)),
  KEY `IX_UserId` (`UserId`),
  KEY `DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Favorite`
--

/*!40000 ALTER TABLE `Favorite` DISABLE KEYS */;
/*!40000 ALTER TABLE `Favorite` ENABLE KEYS */;

--
-- Table structure for table `Permission`
--

DROP TABLE IF EXISTS `Permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Permission` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `PermissionType` varchar(32) NOT NULL DEFAULT '',
  `TargetId` varchar(256) NOT NULL DEFAULT '',
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT '',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `IX_TargetId_PermissionType` (`TargetId`(191),`PermissionType`),
  KEY `IX_DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Permission`
--

/*!40000 ALTER TABLE `Permission` DISABLE KEYS */;
INSERT INTO `Permission` VALUES (1,'CreateCluster','SampleApp','\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(2,'CreateNamespace','SampleApp','\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(3,'AssignRole','SampleApp','\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(4,'ModifyNamespace','SampleApp+application','\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(5,'ReleaseNamespace','SampleApp+application','\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01');
/*!40000 ALTER TABLE `Permission` ENABLE KEYS */;

--
-- Table structure for table `Role`
--

DROP TABLE IF EXISTS `Role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Role` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `RoleName` varchar(256) NOT NULL DEFAULT '',
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) NOT NULL DEFAULT 'default',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `IX_RoleName` (`RoleName`(191)),
  KEY `IX_DataChange_LastTime` (`DataChange_LastTime`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Role`
--

/*!40000 ALTER TABLE `Role` DISABLE KEYS */;
INSERT INTO `Role` VALUES (1,'Master+SampleApp','\0','default','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(2,'ModifyNamespace+SampleApp+application','\0','default','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(3,'ReleaseNamespace+SampleApp+application','\0','default','2019-06-06 15:14:01','','2019-06-06 15:14:01');
/*!40000 ALTER TABLE `Role` ENABLE KEYS */;

--
-- Table structure for table `RolePermission`
--

DROP TABLE IF EXISTS `RolePermission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RolePermission` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `RoleId` int(10) unsigned DEFAULT NULL,
  `PermissionId` int(10) unsigned DEFAULT NULL,
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) DEFAULT '',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `IX_DataChange_LastTime` (`DataChange_LastTime`),
  KEY `IX_RoleId` (`RoleId`),
  KEY `IX_PermissionId` (`PermissionId`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `RolePermission`
--

/*!40000 ALTER TABLE `RolePermission` DISABLE KEYS */;
INSERT INTO `RolePermission` VALUES (1,1,1,'\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(2,1,2,'\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(3,1,3,'\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(4,2,4,'\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(5,3,5,'\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01');
/*!40000 ALTER TABLE `RolePermission` ENABLE KEYS */;

--
-- Table structure for table `ServerConfig`
--

DROP TABLE IF EXISTS `ServerConfig`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ServerConfig` (
  `Id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `Key` varchar(64) NOT NULL DEFAULT 'default',
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
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ServerConfig`
--

/*!40000 ALTER TABLE `ServerConfig` DISABLE KEYS */;
INSERT INTO `ServerConfig` VALUES (1,'apollo.portal.envs','dev','可支持的环境列表','\0','default','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(2,'organizations','[{\"orgId\":\"TEST1\",\"orgName\":\"样例部门1\"},{\"orgId\":\"TEST2\",\"orgName\":\"样例部门2\"}]','部门列表','\0','default','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(3,'superAdmin','apollo','Portal超级管理员','\0','default','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(4,'api.readTimeout','10000','http接口read timeout','\0','default','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(5,'consumer.token.salt','someSalt','consumer token salt','\0','default','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(6,'admin.createPrivateNamespace.switch','true','是否允许项目管理员创建私有namespace','\0','default','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(7,'configView.memberOnly.envs','dev','只对项目成员显示配置信息的环境列表，多个env以英文逗号分隔','\0','default','2019-06-06 15:14:01','','2019-06-06 15:14:01');
/*!40000 ALTER TABLE `ServerConfig` ENABLE KEYS */;

--
-- Table structure for table `UserRole`
--

DROP TABLE IF EXISTS `UserRole`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `UserRole` (
  `Id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `UserId` varchar(128) DEFAULT '',
  `RoleId` int(10) unsigned DEFAULT NULL,
  `IsDeleted` bit(1) NOT NULL DEFAULT b'0',
  `DataChange_CreatedBy` varchar(32) DEFAULT '',
  `DataChange_CreatedTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DataChange_LastModifiedBy` varchar(32) DEFAULT '',
  `DataChange_LastTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`Id`),
  KEY `IX_DataChange_LastTime` (`DataChange_LastTime`),
  KEY `IX_RoleId` (`RoleId`),
  KEY `IX_UserId_RoleId` (`UserId`,`RoleId`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `UserRole`
--

/*!40000 ALTER TABLE `UserRole` DISABLE KEYS */;
INSERT INTO `UserRole` VALUES (1,'apollo',1,'\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(2,'apollo',2,'\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01'),(3,'apollo',3,'\0','','2019-06-06 15:14:01','','2019-06-06 15:14:01');
/*!40000 ALTER TABLE `UserRole` ENABLE KEYS */;

--
-- Table structure for table `Users`
--

DROP TABLE IF EXISTS `Users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Users` (
  `Id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `Username` varchar(64) NOT NULL DEFAULT 'default',
  `Password` varchar(64) NOT NULL DEFAULT 'default',
  `Email` varchar(64) NOT NULL DEFAULT 'default',
  `Enabled` tinyint(4) DEFAULT NULL,
  PRIMARY KEY (`Id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Users`
--

/*!40000 ALTER TABLE `Users` DISABLE KEYS */;
INSERT INTO `Users` VALUES (1,'apollo','$2a$10$7r20uS.BQ9uBpf3Baj3uQOZvMVvB1RN3PYoKE94gtz2.WAOuiiwXS','apollo@acme.com',1);
/*!40000 ALTER TABLE `Users` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2019-06-07 11:25:01
