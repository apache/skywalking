#!/bin/bash

len=$(/usr/local/bin/redis-cli -h redis_1 slowlog len)

if [[ $len -gt 0 ]]; then
   
    result=$(/usr/local/bin/redis-cli -h redis_1 slowlog get $len)

    single_line_log="$(echo "$result" | tr '\n' ' ')"
    processed_result=$(echo "$single_line_log" | sed  's/\([0-9]\{1,3\}\.\)\{3\}[0-9]\{1,3\}:[0-9]\{1,5\}/&\n/g')
    echo "$processed_result" >> /scripts/slowlog.log
fi
/usr/local/bin/redis-cli -h redis_1 slowlog reset
