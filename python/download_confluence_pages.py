import argparse
import os
import requests
from urllib.parse import quote


def main():
    # 解析命令行参数
    parser = argparse.ArgumentParser(description='下载Confluence指定页面及其子页面的Word文档。')
    parser.add_argument('output_dir', help='输出目录')
    parser.add_argument('SPC', help='Confluence空间键')
    parser.add_argument('page_title', help='页面标题')
    args = parser.parse_args()

    output_dir = args.output_dir
    SPC = args.SPC
    page_title = args.page_title

    # 检查输出目录是否存在，不存在则创建
    if not os.path.isdir(output_dir):
        print(f'Creating output directory: {output_dir}')
        try:
            os.makedirs(output_dir, exist_ok=True)
        except OSError as e:
            print(f'Error: Failed to create output directory: {e}')
            return

    # 检查输出目录是否可写
    if not os.access(output_dir, os.W_OK):
        print(f'Error: Output directory is not writable: {output_dir}')
        return

    # 检查环境变量
    username = os.environ.get('CONFLUENCE_USERNAME')
    api_key = os.environ.get('CONFLUENCE_API_KEY')
    if not username or not api_key:
        print('Error: Environment variables CONFLUENCE_USERNAME and CONFLUENCE_API_KEY are not set.')
        return

    base_url = 'http://192.168.140.19:8090'
    api_url = f'{base_url}/rest/api'

    # 搜索页面
    search_url = f'{api_url}/content?title={quote(page_title)}&spaceKey={SPC}&expand=version,ancestors'
    try:
        response = requests.get(search_url, auth=(username, api_key))
        response.raise_for_status()
        data = response.json()
    except requests.RequestException as e:
        print(f'Error: Failed to search page: {e}')
        return

    if not data.get('results'):
        print(f"Error: Page with title '{page_title}' not found in space {SPC}")
        return

    page_id = data['results'][0]['id']
    version = data['results'][0]['version']['number']
    print(f'Found page with ID: {page_id}, Version: {version}')

    # 创建输出目录
    output_path = os.path.join(output_dir, page_title)
    os.makedirs(output_path, exist_ok=True)

    # 导出Word文档
    export_url = f'{base_url}/exportword?pageId={page_id}'
    headers = {
        'Cookie': 'confluence.list.pages.cookie=list-content-tree; cloudreve-session=MTc0NDE2Nzc4NHxOd3dBTkV0WlNWTlNWVVJNUlZSTE5WVllURWxFUkZGVVEwOUVWVmRHU0VWWVZWSkNUVVJVTTBNMVJrVkJWVnBMTkROWU1qWlNRMUU9fPYJ1-HWANLM8OE6zphSYcnSfPCOPO5cj-sRu0nme77L; JSESSIONID=05406B48A6F76AD967ED183C01AC3539; confluence.last-web-item-clicked=system.space.tools%2Foverview%2Fspacedetails; mywork.tab.tasks=false',
        'User-Agent': 'Apifox/1.0.0 (https://apifox.com)',
        'Accept': '*/*',
        'Host': '192.168.140.19:8090',
        'Connection': 'keep-alive'
    }

    try:
        response = requests.get(export_url, headers=headers, auth=(username, api_key))
        response.raise_for_status()
        # 保存文件
        output_file = os.path.join(output_path, f'{page_title}.docx')
        with open(output_file, 'wb') as f:
            f.write(response.content)
        print(f'Successfully exported: {output_file}')
    except requests.RequestException as e:
        print(f'Error: Failed to export page: {e}')

    # 获取子页面
    children_url = f'{api_url}/content/{page_id}/child/page'
    try:
        response = requests.get(children_url, auth=(username, api_key))
        response.raise_for_status()
        data = response.json()
    except requests.RequestException as e:
        print(f'Error: Failed to get child pages: {e}')
        return

    # 处理子页面
    for child in data.get('results', []):
        child_id = child['id']
        child_title = child['title']
        print(f'Processing child page: {child_title}')

        # 递归导出子页面
        export_url = f'{base_url}/exportword?pageId={child_id}'
        try:
            response = requests.get(export_url, headers=headers, auth=(username, api_key))
            response.raise_for_status()
            child_file = os.path.join(output_path, f'{child_title}.doc')
            with open(child_file, 'wb') as f:
                f.write(response.content)
            print(f'Successfully exported: {child_file}')
        except requests.RequestException as e:
            print(f'Error: Failed to export child page: {e}')


if __name__ == '__main__':
    main()