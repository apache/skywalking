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

from datetime import datetime, timedelta

from airflow import DAG
from airflow.providers.standard.sensors.time_delta import TimeDeltaSensor

# Deferrable sensor so triggerer exports native triggers_* OTel counters.
with DAG(
    dag_id="e2e_deferrable",
    start_date=datetime(2024, 1, 1),
    schedule=None,
    catchup=False,
    tags=["swip7", "e2e", "deferrable"],
) as dag:
    TimeDeltaSensor(
        task_id="defer_wait",
        delta=timedelta(seconds=45),
        deferrable=True,
    )
