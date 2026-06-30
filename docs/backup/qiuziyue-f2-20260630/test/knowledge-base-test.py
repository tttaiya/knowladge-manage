import json
import os
import sys
import urllib.error
import urllib.request

BASE_URL = os.environ.get('BASE_URL', 'http://127.0.0.1:18081/api/v1/knowledge-bases')


def request(method, path='', body=None):
    data = None
    headers = {'Content-Type': 'application/json'}
    if body is not None:
        data = json.dumps(body).encode('utf-8')
    req = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as ex:
        return json.loads(ex.read().decode('utf-8'))
    except urllib.error.URLError as ex:
        return {'code': -1, 'message': '无法连接后端接口: %s' % ex}


def assert_true(label, condition):
    print(('PASS' if condition else 'FAIL') + ' - ' + label)
    return condition


def ensure_connected(result):
    if result.get('code') == -1:
        print('FAIL - ' + result.get('message', '接口连接失败'))
        sys.exit(1)


def main():
    create_body = {
        'name': 'JDK8测试知识库',
        'description': '自动化接口测试创建',
        'category': 'GENERAL',
        'retrievalStrategy': 'SEMANTIC',
        'chunkStrategy': 'HEADING',
        'chunkSize': 500,
        'chunkOverlap': 50,
        'separatorsJson': ''
    }

    created = request('POST', '', create_body)
    ensure_connected(created)
    assert_true('创建知识库', created.get('code') == 0)
    if created.get('code') != 0:
        sys.exit(1)

    kb_id = created['data']['id']

    detail = request('GET', '/' + str(kb_id))
    ensure_connected(detail)
    assert_true('查询详情', detail.get('code') == 0)

    page = request('GET', '?page=1&pageSize=10')
    ensure_connected(page)
    assert_true('分页查询', page.get('code') == 0)

    update_body = {
        'name': 'JDK8测试知识库-更新',
        'description': '自动化接口测试更新',
        'category': 'GENERAL',
        'retrievalStrategy': 'VECTOR_RERANK',
        'chunkStrategy': 'FIXED',
        'chunkSize': 300,
        'chunkOverlap': 30,
        'separatorsJson': '["\\n"]'
    }
    update_fail = request('PUT', '/' + str(kb_id), update_body)
    ensure_connected(update_fail)
    assert_true('未确认更新应被拦截', update_fail.get('code') != 0)

    update_success = request('PUT', '/' + str(kb_id) + '?confirmation=true', update_body)
    ensure_connected(update_success)
    assert_true('确认后更新成功', update_success.get('code') == 0)

    reprocess = request('POST', '/' + str(kb_id) + '/reprocess?confirmation=true')
    ensure_connected(reprocess)
    assert_true('重新切片成功', reprocess.get('code') == 0)

    delete_result = request('DELETE', '/' + str(kb_id))
    ensure_connected(delete_result)
    assert_true('删除成功', delete_result.get('code') == 0)


if __name__ == '__main__':
    main()
