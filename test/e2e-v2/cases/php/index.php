<?php
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

usleep(500000);
$uri = $_SERVER['REQUEST_URI'];

if($uri == '/php/info') {
    $ch = curl_init();
    curl_setopt($ch, CURLOPT_URL, "http://provider:9090/info");
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, 1);
    $output = curl_exec($ch);
    if (curl_errno($ch)) {
        $error_code = curl_errno($ch);
        $error_message = curl_error($ch);
        throw new Exception("curl failed: $error_code $error_message");
    }
    curl_close($ch);
}
