#!/bin/bash

apt-get update && apt-get install -y cron

crontab /scripts/crontable.txt

service cron start

tail -f /dev/null
