#!/bin/bash

# 获取当前脚本PID
self_pid=$$
parent_pid=$PPID
echo $self_pid
echo $PPID
# 主查询函数：排除自身PID
search_process() {
    pattern="$1"
    # 精确排除自身PID
    ps -eo pid,cmd -sort=start_time | grep "$pattern" | grep -vE "grep|$$|$self_pid|$parent_pid" | awk '{print $1}'
}

# 处理默认情况（无参数时查询java）
if [ $# -eq 0 ]; then
    # 使用[j]ava技巧避免自匹配
    pids=$(search_process "[j]ava")
    echo "$pids"
else
    # 有参数时直接查询
    pids=$(search_process "$1")
    echo "$pids"
fi