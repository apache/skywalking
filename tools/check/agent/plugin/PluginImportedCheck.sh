#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

plugin_dir=$1
for dir in `ls "./apm-sniffer/$plugin_dir/"`; do
	echo "Scanning $dir"
	for f in `find ./apm-sniffer/$plugin_dir/$dir -name *Instrumentation.java `; do
		NUM=`head -400 $f | grep ^import |grep -Ev "^import\s+(static\s+)*net.bytebuddy\\." \
			| grep -Ev "^import\s+(static\s+)*org.apache.skywalking\\." |grep -Ev "^import\s+(static\s+)*java*\\." | wc -l`
		if [ $NUM -gt 0 ] ; then
			echo "Plugin: $dir($f),  only allow to import JDK and ByteBuddy classes in Instrumentation definition.";
			exit 1;
		fi
	done
done
