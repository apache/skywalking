#!/bin/bash

hostname=$(hostname)
ip_address=$(hostname --ip-address)
echo -e "hostname: ${hostname}(${ip_address}) \n"

cpu_usage_rate=$(grep 'cpu ' /proc/stat | awk '{usage=($2+$4)*100/($2+$4+$5)} END {print usage "%"}')
load_average=$(uptime |grep -o --color=never "load average:.*")

echo -e "CPU usage rate: ${cpu_usage_rate}, ${load_average} \n"

running=$(docker ps -q |wc -l)
all=$(docker ps -aq |wc -l)
volumes=$(docker volume ls |wc -l)

echo -e "docker stats: running=${running:=0}, all=${all:=0}, volumes=${volumes:=0}\n"

echo -e "Memory usage:"
free -m

echo "Disk usage:"
df -h