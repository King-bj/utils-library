#!/bin/bash

# 检查参数是否正确
if [ $# -ne 3 ]; then
    echo "Usage: $0 <md_files_path> <SPC> <stage>"
    exit 1
fi

md_files_path="$1"
SPC="$2"
stage="$3"

# 检查路径是否存在
if [ ! -d "$md_files_path" ]; then
    echo "Error: Directory '$md_files_path' not found."
    exit 1
fi

# 检查环境变量 SPC 是否已设置
if [ -z "$SPC" ]; then
    echo "Error: Environment variable SPC is not set."
    exit 1
fi

# 定义 URL
url="http://192.168.140.19:8090/"

source /opt/app/target/code/md_to_conf/venv/bin/activate

# 遍历指定路径下的所有 .md 文件
for md_file in "$md_files_path"/*.md; do
    if [ -f "$md_file" ]; then
        # 使用完整文件路径
        python3 /opt/app/target/code/md_to_conf/md2conf.py -o "$url" -n "$md_file" "$SPC" -a "$stage" --simulate
        
        # 检查命令是否执行成功
        if [ $? -ne 0 ]; then
            echo "Error: Failed to process file '$md_file'"
        fi
    fi
done

echo "All MD files have been processed."
