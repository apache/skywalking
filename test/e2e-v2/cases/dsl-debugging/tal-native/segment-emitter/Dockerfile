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

FROM python:3.11-slim
# The SkyWalking Python agent ships the native protocol's generated code, which the emitter sends segments with; its
# agent dependencies, psutil among them, are not needed for that.
RUN pip install --no-cache-dir --no-deps apache-skywalking==1.1.0 \
    && pip install --no-cache-dir grpcio==1.68.1 protobuf==5.29.3
COPY emitter.py /
CMD ["python", "/emitter.py"]
