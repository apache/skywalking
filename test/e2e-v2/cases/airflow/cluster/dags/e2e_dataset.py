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
from airflow.datasets import Dataset
from airflow.operators.bash import BashOperator

E2E_DATASET = Dataset("file:///tmp/swip7-e2e-dataset")

with DAG(
    dag_id="e2e_dataset_producer",
    start_date=datetime(2024, 1, 1),
    schedule=None,
    catchup=False,
    tags=["swip7", "e2e", "dataset"],
) as producer_dag:
    BashOperator(
        task_id="produce",
        bash_command="echo swip7-dataset-produce",
        outlets=[E2E_DATASET],
    )

with DAG(
    dag_id="e2e_dataset_consumer",
    start_date=datetime(2024, 1, 1),
    schedule=[E2E_DATASET],
    catchup=False,
    tags=["swip7", "e2e", "dataset"],
) as consumer_dag:
    BashOperator(
        task_id="consume",
        bash_command="echo swip7-dataset-consume",
    )
