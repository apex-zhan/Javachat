#!/bin/bash

# Redis高可用测试脚本
# 用于验证Redis高可用机制的各项功能

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="http://localhost:8080"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_test() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

# 测试计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 测试结果记录
test_result() {
    local test_name="$1"
    local result="$2"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if [ "$result" = "PASS" ]; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
        log_info "✓ $test_name - PASS"
    else
        FAILED_TESTS=$((FAILED_TESTS + 1))
        log_error "✗ $test_name - FAIL"
    fi
}

# HTTP请求函数
http_get() {
    local url="$1"
    local expected_status="${2:-200}"
    
    response=$(curl -s -w "HTTPSTATUS:%{http_code}" "$url" 2>/dev/null || echo "HTTPSTATUS:000")
    http_code=$(echo "$response" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
    body=$(echo "$response" | sed -E 's/HTTPSTATUS:[0-9]*$//')
    
    if [ "$http_code" = "$expected_status" ]; then
        echo "$body"
        return 0
    else
        return 1
    fi
}

# 等待服务启动
wait_for_service() {
    local url="$1"
    local timeout="${2:-60}"
    local count=0
    
    log_info "等待服务启动: $url"
    
    while [ $count -lt $timeout ]; do
        if curl -s "$url" > /dev/null 2>&1; then
            log_info "服务已启动"
            return 0
        fi
        sleep 1
        count=$((count + 1))
    done
    
    log_error "服务启动超时"
    return 1
}

# 测试Redis连接状态
test_redis_status() {
    log_test "测试Redis连接状态"
    
    if response=$(http_get "$BASE_URL/capi/redis/monitor/status"); then
        available=$(echo "$response" | grep -o '"available":[^,]*' | cut -d: -f2)
        if [ "$available" = "true" ]; then
            test_result "Redis连接状态" "PASS"
        else
            test_result "Redis连接状态" "FAIL"
        fi
    else
        test_result "Redis连接状态" "FAIL"
    fi
}

# 测试监控统计
test_monitor_stats() {
    log_test "测试监控统计"
    
    if response=$(http_get "$BASE_URL/capi/redis/monitor/stats"); then
        if echo "$response" | grep -q "totalRequests"; then
            test_result "监控统计" "PASS"
        else
            test_result "监控统计" "FAIL"
        fi
    else
        test_result "监控统计" "FAIL"
    fi
}

# 测试本地缓存统计
test_cache_stats() {
    log_test "测试本地缓存统计"
    
    if response=$(http_get "$BASE_URL/capi/redis/monitor/cache/stats"); then
        if echo "$response" | grep -q "tokenCacheSize"; then
            test_result "本地缓存统计" "PASS"
        else
            test_result "本地缓存统计" "FAIL"
        fi
    else
        test_result "本地缓存统计" "FAIL"
    fi
}

# 测试数据同步状态
test_sync_status() {
    log_test "测试数据同步状态"
    
    if response=$(http_get "$BASE_URL/capi/redis/monitor/sync/status"); then
        if echo "$response" | grep -q "pendingOperationCount"; then
            test_result "数据同步状态" "PASS"
        else
            test_result "数据同步状态" "FAIL"
        fi
    else
        test_result "数据同步状态" "FAIL"
    fi
}

# 测试熔断器重置
test_circuit_breaker_reset() {
    log_test "测试熔断器重置"
    
    if response=$(curl -s -X POST "$BASE_URL/capi/redis/monitor/circuit/reset" 2>/dev/null); then
        if echo "$response" | grep -q "success"; then
            test_result "熔断器重置" "PASS"
        else
            test_result "熔断器重置" "FAIL"
        fi
    else
        test_result "熔断器重置" "FAIL"
    fi
}

# 测试本地缓存清理
test_cache_clear() {
    log_test "测试本地缓存清理"
    
    if response=$(curl -s -X POST "$BASE_URL/capi/redis/monitor/cache/clear" 2>/dev/null); then
        if echo "$response" | grep -q "success"; then
            test_result "本地缓存清理" "PASS"
        else
            test_result "本地缓存清理" "FAIL"
        fi
    else
        test_result "本地缓存清理" "FAIL"
    fi
}

# 测试手动数据同步
test_manual_sync() {
    log_test "测试手动数据同步"
    
    if response=$(curl -s -X POST "$BASE_URL/capi/redis/monitor/sync/manual" 2>/dev/null); then
        if echo "$response" | grep -q "success"; then
            test_result "手动数据同步" "PASS"
        else
            test_result "手动数据同步" "FAIL"
        fi
    else
        test_result "手动数据同步" "FAIL"
    fi
}

# 测试缓存预热
test_cache_warmup() {
    log_test "测试缓存预热"
    
    if response=$(curl -s -X POST "$BASE_URL/capi/redis/monitor/cache/warmup" 2>/dev/null); then
        if echo "$response" | grep -q "success"; then
            test_result "缓存预热" "PASS"
        else
            test_result "缓存预热" "FAIL"
        fi
    else
        test_result "缓存预热" "FAIL"
    fi
}

# 测试数据一致性检查
test_consistency_check() {
    log_test "测试数据一致性检查"
    
    if response=$(http_get "$BASE_URL/capi/redis/monitor/consistency/check"); then
        if echo "$response" | grep -q "consistent"; then
            test_result "数据一致性检查" "PASS"
        else
            test_result "数据一致性检查" "FAIL"
        fi
    else
        test_result "数据一致性检查" "FAIL"
    fi
}

# 压力测试
stress_test() {
    log_test "执行压力测试"
    
    local concurrent_users=10
    local requests_per_user=100
    local total_requests=$((concurrent_users * requests_per_user))
    
    log_info "启动压力测试: ${concurrent_users}并发用户, 每用户${requests_per_user}请求"
    
    # 创建临时测试脚本
    cat > /tmp/stress_test.sh << 'EOF'
#!/bin/bash
BASE_URL="$1"
REQUESTS="$2"
USER_ID="$3"

success_count=0
error_count=0

for i in $(seq 1 $REQUESTS); do
    if curl -s "$BASE_URL/capi/redis/monitor/status" > /dev/null 2>&1; then
        success_count=$((success_count + 1))
    else
        error_count=$((error_count + 1))
    fi
done

echo "User $USER_ID: Success=$success_count, Error=$error_count"
EOF
    
    chmod +x /tmp/stress_test.sh
    
    # 启动并发测试
    start_time=$(date +%s)
    
    for i in $(seq 1 $concurrent_users); do
        /tmp/stress_test.sh "$BASE_URL" "$requests_per_user" "$i" &
    done
    
    wait
    
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    qps=$((total_requests / duration))
    
    log_info "压力测试完成: 总请求=${total_requests}, 耗时=${duration}秒, QPS=${qps}"
    
    # 清理临时文件
    rm -f /tmp/stress_test.sh
    
    if [ $qps -gt 50 ]; then
        test_result "压力测试(QPS>50)" "PASS"
    else
        test_result "压力测试(QPS>50)" "FAIL"
    fi
}

# 故障模拟测试
failover_simulation() {
    log_test "执行故障模拟测试"
    
    # 检查Docker容器是否存在
    if ! docker ps | grep -q "redis-master"; then
        log_warn "Redis容器未运行，跳过故障模拟测试"
        return
    fi
    
    # 记录故障前状态
    log_info "记录故障前Redis状态"
    before_status=$(http_get "$BASE_URL/capi/redis/monitor/status" 2>/dev/null || echo "")
    
    # 模拟Redis故障
    log_warn "模拟Redis Master故障"
    docker stop mallchat-redis-ha_redis-master_1 2>/dev/null || true
    
    # 等待熔断器触发
    sleep 10
    
    # 检查熔断器状态
    log_info "检查熔断器状态"
    if response=$(http_get "$BASE_URL/capi/redis/monitor/status" 2>/dev/null); then
        circuit_state=$(echo "$response" | grep -o '"circuitState":"[^"]*"' | cut -d'"' -f4)
        if [ "$circuit_state" = "OPEN" ] || [ "$circuit_state" = "HALF_OPEN" ]; then
            test_result "故障检测和熔断" "PASS"
        else
            test_result "故障检测和熔断" "FAIL"
        fi
    else
        test_result "故障检测和熔断" "FAIL"
    fi
    
    # 恢复Redis服务
    log_info "恢复Redis服务"
    docker start mallchat-redis-ha_redis-master_1 2>/dev/null || true
    
    # 等待服务恢复
    sleep 15
    
    # 检查恢复状态
    log_info "检查服务恢复状态"
    if response=$(http_get "$BASE_URL/capi/redis/monitor/status" 2>/dev/null); then
        available=$(echo "$response" | grep -o '"available":[^,]*' | cut -d: -f2)
        if [ "$available" = "true" ]; then
            test_result "故障恢复" "PASS"
        else
            test_result "故障恢复" "FAIL"
        fi
    else
        test_result "故障恢复" "FAIL"
    fi
}

# 生成测试报告
generate_report() {
    echo ""
    echo "=========================================="
    echo "           Redis高可用测试报告"
    echo "=========================================="
    echo "测试时间: $(date)"
    echo "总测试数: $TOTAL_TESTS"
    echo "通过测试: $PASSED_TESTS"
    echo "失败测试: $FAILED_TESTS"
    echo "成功率: $(( PASSED_TESTS * 100 / TOTAL_TESTS ))%"
    echo "=========================================="
    
    if [ $FAILED_TESTS -eq 0 ]; then
        log_info "🎉 所有测试通过！Redis高可用机制工作正常"
        return 0
    else
        log_error "❌ 有 $FAILED_TESTS 个测试失败，请检查系统状态"
        return 1
    fi
}

# 主测试流程
run_all_tests() {
    log_info "开始Redis高可用功能测试"
    
    # 等待服务启动
    if ! wait_for_service "$BASE_URL/capi/redis/monitor/status" 30; then
        log_error "服务未启动，无法进行测试"
        exit 1
    fi
    
    # 基础功能测试
    test_redis_status
    test_monitor_stats
    test_cache_stats
    test_sync_status
    
    # 管理功能测试
    test_circuit_breaker_reset
    test_cache_clear
    test_manual_sync
    test_cache_warmup
    test_consistency_check
    
    # 性能测试
    stress_test
    
    # 故障模拟测试
    failover_simulation
    
    # 生成报告
    generate_report
}

# 显示帮助信息
show_help() {
    echo "Redis高可用测试脚本"
    echo ""
    echo "使用方法:"
    echo "  $0 [命令]"
    echo ""
    echo "命令:"
    echo "  all        运行所有测试"
    echo "  basic      运行基础功能测试"
    echo "  stress     运行压力测试"
    echo "  failover   运行故障模拟测试"
    echo "  help       显示此帮助信息"
    echo ""
    echo "环境变量:"
    echo "  BASE_URL   应用服务地址 (默认: http://localhost:8080)"
    echo ""
    echo "示例:"
    echo "  $0 all"
    echo "  BASE_URL=http://192.168.1.100:8080 $0 basic"
}

# 主函数
main() {
    case "${1:-all}" in
        all)
            run_all_tests
            ;;
        basic)
            test_redis_status
            test_monitor_stats
            test_cache_stats
            test_sync_status
            generate_report
            ;;
        stress)
            stress_test
            generate_report
            ;;
        failover)
            failover_simulation
            generate_report
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "未知命令: $1"
            show_help
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"