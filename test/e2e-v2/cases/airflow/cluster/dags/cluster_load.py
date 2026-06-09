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

from datetime import datetime

from airflow import DAG
from airflow.providers.standard.operators.bash import BashOperator

# Sustained load for real-cluster e2e (queued / running / scheduled gauges).
with DAG(
    dag_id="cluster_load",
    start_date=datetime(2024, 1, 1),
    schedule=None,
    catchup=False,
    tags=["swip7", "e2e", "load"],
    max_active_runs=4,
) as dag:
    for index in range(1, 9):
        BashOperator(
            task_id=f"sleep_{index}",
            bash_command=f"echo load-{index}-start && sleep 75 && echo load-{index}-done",
        )
