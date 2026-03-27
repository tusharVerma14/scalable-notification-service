#!/bin/bash
# ================================================================
# RUN ALL K6 TESTS
# ================================================================
#
# PREREQUISITES:
#   brew install k6
#
# Make sure your notification server is running first.
# ================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

mkdir -p "$RESULTS_DIR"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo ""
echo -e "${BLUE}================================================================${NC}"
echo -e "${BLUE}   Notification Server — Load Test Suite${NC}"
echo -e "${BLUE}   Run: $TIMESTAMP${NC}"
echo -e "${BLUE}================================================================${NC}"
echo ""

# Health check before starting
echo -e "${YELLOW}Checking server health...${NC}"
if ! curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/notify \
    -X POST -H 'Content-Type: application/json' \
    -d '{"targetUserId":"healthcheck","title":"test","body":"test","channels":["WEBSOCKET"]}' \
    | grep -q "200\|429"; then
  echo -e "${RED}Server is not responding! Start it first.${NC}"
  echo "   Run: ./gradlew bootRun"
  exit 1
fi
echo -e "${GREEN}Server is up!${NC}"
echo ""

# ----------------------------------------------------------------
run_test() {
  local test_num=$1
  local test_name=$2
  local script=$3
  local result_file="$RESULTS_DIR/${TIMESTAMP}_test${test_num}_results.txt"

  echo -e "${YELLOW}----------------------------------------------------${NC}"
  echo -e "${YELLOW}TEST ${test_num}: ${test_name}${NC}"
  echo -e "${YELLOW}----------------------------------------------------${NC}"
  echo ""

  if k6 run "$SCRIPT_DIR/$script" 2>&1 | tee "$result_file"; then
    echo ""
    echo -e "${GREEN}TEST ${test_num} PASSED${NC}"
  else
    echo ""
    echo -e "${RED}TEST ${test_num} failed thresholds (check results above)${NC}"
  fi

  echo ""
  echo -e "   Full results saved: ${result_file}"
  echo ""

  # Cool-down between tests
  echo -e "${YELLOW}Cooling down 10 seconds before next test...${NC}"
  sleep 10
}

# ----------------------------------------------------------------

run_test 1 "REST Throughput & Latency" "test1_rest_throughput.js"
run_test 2 "WebSocket Concurrency" "test2_websocket_connections.js"
run_test 3 "End-to-End Delivery Latency" "test3_e2e_delivery.js"
run_test 4 "Spike Test" "test4_spike.js"

# ----------------------------------------------------------------
echo -e "${BLUE}================================================================${NC}"
echo -e "${GREEN}ALL TESTS COMPLETE${NC}"
echo -e "${BLUE}================================================================${NC}"
echo ""
echo "Results saved in: $RESULTS_DIR/"
echo ""
echo "What to look for in results:"
echo "  - GREEN  = threshold passed"
echo "  - RED    = threshold violated (needs tuning)"
echo ""
echo "Key metrics to check:"
echo "  notification_send_latency_ms  -> REST API response time"
echo "  ws_connect_time_ms            -> WebSocket connection time"
echo "  e2e_delivery_latency_ms       -> Full notification round-trip"
echo "  spike_error_rate              -> % of errors during spike"
echo ""
