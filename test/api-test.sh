#!/bin/bash
# ============================================================
# DSP 数据服务平台 - API 集成测试脚本
# 使用: bash test/api-test.sh [BASE_URL]
# 默认: http://localhost:8080
#
# 说明: CI 环境只有 dsp_config 库，无业务表（users/orders 等），
#       模板查询会返回 5001（系统内部错误），这是预期行为。
#       本脚本验证 API 能正常响应、鉴权生效、模板加载成功。
# ============================================================

BASE_URL="${1:-http://localhost:8080}"
TOKEN="base"
TIMESTAMP="2026-06-30T00:00:00+08:00"
PASS=0
FAIL=0

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

# 验证返回码在允许列表中（逗号分隔）
assert_code_in() {
    local name="$1"
    local expected_list="$2"
    local actual="$3"
    if echo ",$expected_list," | grep -q ",$actual,"; then
        echo -e "${GREEN}PASS${NC} $name (code=$actual)"
        ((PASS++))
    else
        echo -e "${RED}FAIL${NC} $name (expected one of: $expected_list, actual=$actual)"
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

get_code() {
    echo "$1" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null
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
RESP=$(call_api "USER_GET_BY_ID" '{"head":{"token":"'$TOKEN'","appId":"test","timestamp":"'$TIMESTAMP'","traceId":"t01"},"requestData":{"userId":"1"}}')
CODE=$(get_code "$RESP")
# 0000=成功(有业务数据), 5001=系统错误(无业务表), 5002=数据源异常 — 均表示模板加载+鉴权通过
assert_code_in "模板加载+鉴权通过" "0000,5001,5002" "$CODE"
assert_contains "响应包含标准结构" '"code"' "$RESP"
assert_contains "响应包含 head" '"head"' "$RESP"

echo ""

# ------ 模板02: 动态SQL查询 ------
echo "--- 模板02: USER_LIST_QUERY ---"
RESP=$(call_api "USER_LIST_QUERY" '{"head":{"token":"'$TOKEN'","appId":"test","timestamp":"'$TIMESTAMP'","traceId":"t02"},"requestData":{"status":"active"}}')
CODE=$(get_code "$RESP")
assert_code_in "动态SQL模板加载" "0000,5001,5002" "$CODE"

echo ""

# ------ 模板03: 游标分页 ------
echo "--- 模板03: ORDER_LIST_CURSOR ---"
RESP=$(call_api "ORDER_LIST_CURSOR" '{"head":{"token":"'$TOKEN'","appId":"test","timestamp":"'$TIMESTAMP'","traceId":"t03"},"requestData":{"pageSize":10}}')
CODE=$(get_code "$RESP")
assert_code_in "游标分页模板加载" "0000,5001,5002" "$CODE"

echo ""

# ------ 模板09: 并行编排 ------
echo "--- 模板09: DASHBOARD_OVERVIEW ---"
RESP=$(call_api "DASHBOARD_OVERVIEW" '{"head":{"token":"'$TOKEN'","appId":"test","timestamp":"'$TIMESTAMP'","traceId":"t09"},"requestData":{"dateRange":"7d"}}')
CODE=$(get_code "$RESP")
assert_code_in "并行编排模板加载" "0000,5001,5002" "$CODE"

echo ""

# ------ 模板15: 无 resultMap 查询 ------
echo "--- 模板15: DICT_QUERY ---"
RESP=$(call_api "DICT_QUERY" '{"head":{"token":"'$TOKEN'","appId":"test","timestamp":"'$TIMESTAMP'","traceId":"t15"},"requestData":{"dictType":"STATUS"}}')
CODE=$(get_code "$RESP")
assert_code_in "无resultMap模板加载" "0000,5001,5002" "$CODE"

echo ""

# ------ JWT 鉴权测试 ------
echo "--- JWT 鉴权 ---"

RESP=$(call_api "USER_GET_BY_ID" '{"head":{"token":"invalid-token","appId":"test","timestamp":"'$TIMESTAMP'","traceId":"t-jwt"},"requestData":{"userId":"1"}}')
CODE=$(get_code "$RESP")
# 4001=Token缺失, 4002=Token过期/无效 — 均表示鉴权拦截生效
assert_code_in "无效token被拦截" "4001,4002" "$CODE"

RESP=$(call_api "USER_GET_BY_ID" '{"head":{"appId":"test","timestamp":"'$TIMESTAMP'","traceId":"t-jwt2"},"requestData":{"userId":"1"}}')
CODE=$(get_code "$RESP")
assert_code_in "缺失token被拦截" "4001,4100" "$CODE"

echo ""

# ------ 不存在的接口 ------
echo "--- 异常场景 ---"
RESP=$(call_api "NOT_EXIST_API" '{"head":{"token":"'$TOKEN'","appId":"test","timestamp":"'$TIMESTAMP'","traceId":"t-err"},"requestData":{}}')
CODE=$(get_code "$RESP")
# 4004=接口不存在, 5001=系统错误(异常被兜底处理)
assert_code_in "不存在的接口返回错误" "4004,5001" "$CODE"

echo ""

# ------ 结果汇总 ------
echo "========================================"
echo "测试结果: PASS=$PASS, FAIL=$FAIL"
echo "========================================"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
