#!/bin/bash
# ============================================================
# DSP 数据服务平台 - API 集成测试脚本
# 使用: bash test/api-test.sh [BASE_URL]
# 默认: http://localhost:8080
# ============================================================

BASE_URL="${1:-http://localhost:8080}"
TOKEN="base"
PASS=0
FAIL=0

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

assert_code() {
    local name="$1"
    local expected="$2"
    local actual="$3"
    if [ "$actual" = "$expected" ]; then
        echo -e "${GREEN}PASS${NC} $name"
        ((PASS++))
    else
        echo -e "${RED}FAIL${NC} $name (expected=$expected, actual=$actual)"
        ((FAIL++))
    fi
}

assert_contains() {
    local name="$1"
    local expected="$2"
    local response="$3"
    if echo "$response" | grep -q "$expected"; then
        echo -e "${GREEN}PASS${NC} $name"
        ((PASS++))
    else
        echo -e "${RED}FAIL${NC} $name (expected to contain: $expected)"
        ((FAIL++))
    fi
}

call_api() {
    local transno="$1"
    local data="$2"
    curl -s -X POST "$BASE_URL/dsp/api/$transno" \
        -H "Content-Type: application/json" \
        -d "$data"
}

echo "========================================"
echo "DSP API 集成测试"
echo "Base URL: $BASE_URL"
echo "========================================"
echo ""

# ------ 模板01: 简单查询 ------
echo "--- 模板01: USER_GET_BY_ID ---"

RESP=$(call_api "USER_GET_BY_ID" '{
    "head":{"token":"'$TOKEN'","appId":"test","traceId":"t01"},
    "requestData":{"userId":"1"}
}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)
assert_code "userId=1 返回成功" "0000" "$CODE"
assert_contains "包含 userName" "userName" "$RESP"

RESP=$(call_api "USER_GET_BY_ID" '{
    "head":{"token":"'$TOKEN'","appId":"test","traceId":"t01b"},
    "requestData":{"userId":"999"}
}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)
assert_code "userId=999 无数据仍返回成功" "0000" "$CODE"

echo ""

# ------ 模板02: 动态SQL查询 ------
echo "--- 模板02: USER_LIST_QUERY ---"

RESP=$(call_api "USER_LIST_QUERY" '{
    "head":{"token":"'$TOKEN'","appId":"test","traceId":"t02"},
    "requestData":{"status":"active"}
}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)
assert_code "status=active 查询成功" "0000" "$CODE"

RESP=$(call_api "USER_LIST_QUERY" '{
    "head":{"token":"'$TOKEN'","appId":"test","traceId":"t02b"},
    "requestData":{"ids":["1","2","3"]}
}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)
assert_code "ids 批量查询成功" "0000" "$CODE"

echo ""

# ------ 模板03: 游标分页 ------
echo "--- 模板03: ORDER_LIST_CURSOR ---"

RESP=$(call_api "ORDER_LIST_CURSOR" '{
    "head":{"token":"'$TOKEN'","appId":"test","traceId":"t03"},
    "requestData":{"pageSize":10}
}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)
assert_code "游标分页查询成功" "0000" "$CODE"

echo ""

# ------ 模板09: 并行编排 ------
echo "--- 模板09: DASHBOARD_OVERVIEW ---"

RESP=$(call_api "DASHBOARD_OVERVIEW" '{
    "head":{"token":"'$TOKEN'","appId":"test","traceId":"t09"},
    "requestData":{"dateRange":"7d"}
}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)
assert_code "仪表盘并行查询成功" "0000" "$CODE"

echo ""

# ------ 模板15: 无 resultMap 查询 ------
echo "--- 模板15: DICT_QUERY ---"

RESP=$(call_api "DICT_QUERY" '{
    "head":{"token":"'$TOKEN'","appId":"test","traceId":"t15"},
    "requestData":{"dictType":"STATUS"}
}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)
assert_code "字典查询成功" "0000" "$CODE"

echo ""

# ------ JWT 鉴权测试 ------
echo "--- JWT 鉴权 ---"

RESP=$(call_api "USER_GET_BY_ID" '{
    "head":{"token":"invalid-token","appId":"test","traceId":"t-jwt"},
    "requestData":{"userId":"1"}
}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)
assert_code "无效token返回错误" "4001" "$CODE"

RESP=$(call_api "USER_GET_BY_ID" '{
    "head":{"appId":"test","traceId":"t-jwt2"},
    "requestData":{"userId":"1"}
}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)
assert_code "缺失token返回错误" "4001" "$CODE"

echo ""

# ------ 结果汇总 ------
echo "========================================"
echo "测试结果: PASS=$PASS, FAIL=$FAIL"
echo "========================================"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
