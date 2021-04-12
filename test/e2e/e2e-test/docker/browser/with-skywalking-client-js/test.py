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

import os
import time

from selenium import webdriver as wd
from selenium.webdriver.common.desired_capabilities import DesiredCapabilities as DC

hub_remote_url = os.environ.get("HUB_REMOTE_URL", "http://selenium-hub:4444/wd/hub")
test_url = os.environ.get("TEST_URL", "http://test-ui:80/")


def test_screenshot():
    try:
        driver.get(test_url)
    except Exception as e:
        print(e)

try:
    driver = wd.Remote(
        command_executor=hub_remote_url,
        desired_capabilities=DC.CHROME)

    while True:
        test_screenshot()
        time.sleep(40)

finally:
    driver.quit()