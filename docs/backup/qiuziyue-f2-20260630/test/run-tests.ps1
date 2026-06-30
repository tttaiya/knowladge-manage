param(
  [string]$BaseUrl = 'http://127.0.0.1:18081/api/v1/knowledge-bases'
)

$env:BASE_URL = $BaseUrl
python knowledge-base-test.py
