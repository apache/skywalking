# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

CREATE DATABASE IF NOT EXISTS test;
USE test;
CREATE TABLE IF NOT EXISTS `t1`(
   `te1` VARCHAR(100) NOT NULL
)ENGINE=InnoDB DEFAULT CHARSET=utf8;
SET GLOBAL event_scheduler = 1;

CREATE EVENT `event_1` 
ON SCHEDULE EVERY 1 SECOND 
DO INSERT INTO t1 values('test');

CREATE EVENT `event_2` 
ON SCHEDULE EVERY 1 SECOND 
DO UPDATE t1 SET `te1` = 1;

CREATE EVENT `event_3` 
ON SCHEDULE EVERY 1 SECOND 
DO DELETE FROM t1;

CREATE EVENT `event_4` 
ON SCHEDULE EVERY 1 SECOND 
DO COMMIT;

use mysql;
select sleep(3);
select sleep(4);
select sleep(5);