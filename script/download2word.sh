#!/bin/bash

# 检查参数是否正确
if [ $# -ne 3 ]; then
    echo "Usage: $0 <output_dir> <SPC> <page_title>"
    exit 1
fi

# 设置输出目录和参数
output_dir="$1"
SPC="$2"
page_title="$3"

# 检查输出目录是否存在，不存在则创建
if [ ! -d "$output_dir" ]; then
    echo "Creating output directory: $output_dir"
    mkdir -p "$output_dir"
    if [ $? -ne 0 ]; then
        echo "Error: Failed to create output directory"
        exit 1
    fi
fi

# 检查输出目录是否可写
if [ ! -w "$output_dir" ]; then
    echo "Error: Output directory is not writable: $output_dir"
    exit 1
fi

# 检查环境变量 SPC 是否已设置
if [ -z "$SPC" ]; then
    echo "Error: Environment variable SPC is not set."
    exit 1
fi

# 检查Confluence认证环境变量是否已设置
if [ -z "$CONFLUENCE_USERNAME" ] || [ -z "$CONFLUENCE_API_KEY" ]; then
    echo "Error: Environment variables CONFLUENCE_USERNAME and CONFLUENCE_API_KEY are not set."
    exit 1
fi

# 调用Python脚本
python3 << EOF
import requests
import os
import sys
from urllib.parse import quote

# 设置参数
output_dir = "$output_dir"
SPC = "$SPC"
page_title = "$page_title"
base_url = "http://192.168.140.19:8090"
api_url = f"{base_url}/rest/api"
username = os.environ.get("CONFLUENCE_USERNAME")
api_key = os.environ.get("CONFLUENCE_API_KEY")

# 搜索页面
search_url = f"{api_url}/content?title={quote(page_title)}&spaceKey={SPC}&expand=version,ancestors"
response = requests.get(search_url, auth=(username, api_key))
data = response.json()

if not data.get("results"):
    print(f"Error: Page with title '{page_title}' not found in space {SPC}")
    sys.exit(1)

page_id = data["results"][0]["id"]
version = data["results"][0]["version"]["number"]
print(f"Found page with ID: {page_id}, Version: {version}")

# 创建输出目录
output_path = os.path.join(output_dir, page_title)
os.makedirs(output_path, exist_ok=True)

# 导出Word文档
export_url = f"{base_url}/exportword?pageId={page_id}"
headers = {
    "Cookie": "confluence.list.pages.cookie=list-content-tree; cloudreve-session=MTc0NDE2Nzc4NHxOd3dBTkV0WlNWTlNWVVJNUlZSTE5WVllURWxFUkZGVVEwOUVWVmRHU0VWWVZWSkNUVVJVTTBNMVJrVkJWVnBMTkROWU1qWlNRMUU9fPYJ1-HWANLM8OE6zphSYcnSfPCOPO5cj-sRu0nme77L; JSESSIONID=05406B48A6F76AD967ED183C01AC3539; confluence.last-web-item-clicked=system.space.tools%2Foverview%2Fspacedetails; mywork.tab.tasks=false",
    "User-Agent": "Apifox/1.0.0 (https://apifox.com)",
    "Accept": "*/*",
    "Host": "192.168.140.19:8090",
    "Connection": "keep-alive"
}

response = requests.get(export_url, headers=headers, auth=(username, api_key))

# 保存文件
output_file = os.path.join(output_path, f"{page_title}.docx")
with open(output_file, "wb") as f:
    f.write(response.content)

print(f"Successfully exported: {output_file}")

# 获取子页面
children_url = f"{api_url}/content/{page_id}/child/page"
response = requests.get(children_url, auth=(username, api_key))
data = response.json()

# 处理子页面
for child in data.get("results", []):
    child_id = child["id"]
    child_title = child["title"]
    print(f"Processing child page: {child_title}")
    
    # 递归导出子页面
    export_url = f"{base_url}/exportword?pageId={child_id}"
    response = requests.get(export_url, headers=headers, auth=(username, api_key))
    
    child_file = os.path.join(output_path, f"{child_title}.docx")
    with open(child_file, "wb") as f:
        f.write(response.content)
    
    print(f"Successfully exported: {child_file}")
EOF 