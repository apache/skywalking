# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#!/usr/bin/awk -f

BEGIN {
    in_storage_section=0;
    in_core_section=0;
    is_telemetry_section=0;
}

{
    if (in_storage_section == 0) {
        in_storage_section=$0 ~ /^storage:$/
    } else {
        in_storage_section=$0 ~ /^(#|\s{2})/
    }

    if (in_core_selection == 0) {
        is_core_selection=$0 ~ /^core:$/
    } else {
        is_core_selection=$0 ~ /^(#|\s{2})/
    }

    if (is_telemetry_section == 0) {
        is_telemetry_section=$0 ~ /^telemetry:$/
    } else {
        is_telemetry_section=$0 ~ /^(#|\s{2})/
    }

    if (in_storage_section == 1) {
        print
    } else if (is_core_selection == 1) {
        print $0
        print "  tool-profile-mock-core:"
        is_core_selection=0
    } else if (is_telemetry_section == 1) {
        print $0
        print "  none:"
        is_telemetry_section=0
    } else {
        print "#" $0
    }
}

