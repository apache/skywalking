#!/usr/bin/env bash

# use POSTIX interface, symlink is followed automatically
SW_SERVER_BIN="${BASH_SOURCE-$0}"
SW_SERVER_BIN="$(dirname "${SW_SERVER_BIN}")"
SW_SERVER_BIN="$(cd "${SW_SERVER_BIN}"; pwd)"

# 设置Skywalking的目录基本信息
. "SW_SERVER_BIN/swEnv.sh"


