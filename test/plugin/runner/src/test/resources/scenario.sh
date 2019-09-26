#!/usr/bin/env bash

PRG="$0"
PRGDIR=`dirname "$PRG"`
[ -z "$SCENARIO_HOME" ] && SCENARIO_HOME=`cd "$PRGDIR" >/dev/null; pwd`

${running_script}