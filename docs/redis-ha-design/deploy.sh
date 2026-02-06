#!/bin/bash

# Redis高可用部署脚本
# 使用方法: ./deploy.sh [start|stop|restart|status]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/redis-sentinel-config.yml"
PROJECT_NAME="mallchat-redis-ha"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

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

# 检查依赖
check_dependencies() {
    log_info "检查依赖..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker未安装，请先安装Docker"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose未安装，请先安装Docker Compose"
        exit 1
    fi
    
    log_info "依赖检查通过"
}

# 创建必要的目录和文件
setup_environment() {
    log_info "设置环境..."
    
    # 创建数据目录
    mkdir -p "$SCRIPT_DIR/data/redis-master"
    mkdir -p "$SCRIPT_DIR/data/redis-slave1"
    mkdir -p "$SCRIPT_DIR/data/redis-slave2"
    
    # 创建配置目录
    mkdir -p "$SCRIPT_DIR/config"
    
    # 生成Sentinel配置文件
    for i in {1..3}; do
        cat > "$SCRIPT_DIR/config/sentinel$i.conf" << EOF
port 26379
dir /tmp
sentinel monitor mymaster redis-master 6379 2
sentinel auth-pass mymaster \${REDIS_PASSWORD}
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
sentinel parallel-syncs mymaster 1
loglevel notice
protected-mode no
EOF
    done
    
    # 生成环境变量文件
    if [ ! -f "$SCRIPT_DIR/.env" ]; then
        cat > "$SCRIPT_DIR/.env" << EOF
REDIS_PASSWORD=mallchat123456
EOF
        log_info "已生成.env文件，请根据需要修改Redis密码"
    fi
    
    log_info "环境设置完成"
}

# 启动服务
start_services() {
    log_info "启动Redis高可用集群..."
    
    cd "$SCRIPT_DIR"
    docker-compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" up -d
    
    # 等待服务启动
    log_info "等待服务启动..."
    sleep 10
    
    # 检查服务状态
    check_services_health
}

# 停止服务
stop_services() {
    log_info "停止Redis高可用集群..."
    
    cd "$SCRIPT_DIR"
    docker-compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" down
    
    log_info "服务已停止"
}

# 重启服务
restart_services() {
    log_info "重启Redis高可用集群..."
    stop_services
    sleep 5
    start_services
}

# 检查服务健康状态
check_services_health() {
    log_info "检查服务健康状态..."
    
    # 检查Redis Master
    if docker exec "${PROJECT_NAME}_redis-master_1" redis-cli -a "${REDIS_PASSWORD}" ping > /dev/null 2>&1; then
        log_info "✓ Redis Master运行正常"
    else
        log_error "✗ Redis Master运行异常"
    fi
    
    # 检查Redis Slaves
    for i in {1..2}; do
        if docker exec "${PROJECT_NAME}_redis-slave${i}_1" redis-cli -a "${REDIS_PASSWORD}" ping > /dev/null 2>&1; then
            log_info "✓ Redis Slave${i}运行正常"
        else
            log_error "✗ Redis Slave${i}运行异常"
        fi
    done
    
    # 检查Sentinel
    for i in {1..3}; do
        if docker exec "${PROJECT_NAME}_redis-sentinel${i}_1" redis-cli -p 26379 ping > /dev/null 2>&1; then
            log_info "✓ Redis Sentinel${i}运行正常"
        else
            log_error "✗ Redis Sentinel${i}运行异常"
        fi
    done
    
    # 检查主从复制状态
    log_info "检查主从复制状态..."
    REPLICATION_INFO=$(docker exec "${PROJECT_NAME}_redis-master_1" redis-cli -a "${REDIS_PASSWORD}" info replication 2>/dev/null)
    CONNECTED_SLAVES=$(echo "$REPLICATION_INFO" | grep "connected_slaves" | cut -d: -f2 | tr -d '\r')
    
    if [ "$CONNECTED_SLAVES" = "2" ]; then
        log_info "✓ 主从复制正常，已连接${CONNECTED_SLAVES}个从节点"
    else
        log_warn "⚠ 主从复制异常，仅连接${CONNECTED_SLAVES}个从节点"
    fi
}

# 显示服务状态
show_status() {
    log_info "Redis高可用集群状态:"
    
    cd "$SCRIPT_DIR"
    docker-compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" ps
    
    echo ""
    check_services_health
}

# 故障演练
failover_test() {
    log_info "开始故障转移测试..."
    
    # 停止Master节点
    log_warn "停止Redis Master节点..."
    docker-compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" stop redis-master
    
    # 等待故障转移
    log_info "等待Sentinel进行故障转移..."
    sleep 15
    
    # 检查新的Master
    for i in {1..2}; do
        ROLE=$(docker exec "${PROJECT_NAME}_redis-slave${i}_1" redis-cli -a "${REDIS_PASSWORD}" info replication 2>/dev/null | grep "role:master" || true)
        if [ -n "$ROLE" ]; then
            log_info "✓ Redis Slave${i}已提升为新的Master"
            break
        fi
    done
    
    # 重启原Master节点
    log_info "重启原Master节点..."
    docker-compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" start redis-master
    
    sleep 10
    check_services_health
    
    log_info "故障转移测试完成"
}

# 清理数据
cleanup() {
    log_warn "清理所有数据和容器..."
    
    cd "$SCRIPT_DIR"
    docker-compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" down -v
    
    # 删除数据目录
    rm -rf "$SCRIPT_DIR/data"
    rm -rf "$SCRIPT_DIR/config"
    
    log_info "清理完成"
}

# 显示帮助信息
show_help() {
    echo "Redis高可用部署脚本"
    echo ""
    echo "使用方法:"
    echo "  $0 [命令]"
    echo ""
    echo "命令:"
    echo "  start      启动Redis高可用集群"
    echo "  stop       停止Redis高可用集群"
    echo "  restart    重启Redis高可用集群"
    echo "  status     显示集群状态"
    echo "  test       进行故障转移测试"
    echo "  cleanup    清理所有数据和容器"
    echo "  help       显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 start"
    echo "  $0 status"
    echo "  $0 test"
}

# 主函数
main() {
    case "${1:-help}" in
        start)
            check_dependencies
            setup_environment
            start_services
            ;;
        stop)
            stop_services
            ;;
        restart)
            restart_services
            ;;
        status)
            show_status
            ;;
        test)
            failover_test
            ;;
        cleanup)
            cleanup
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