#!/usr/bin/env bash
# Sends a few transactions to the running service and prints each decision.
# Usage: ./scripts/demo.sh   (after: docker compose up --build)
set -euo pipefail
URL=${URL:-http://localhost:8080/api/transactions/score}
ACCT="demo-$(date +%s)"
send() {
  curl -s -X POST "$URL" -H 'Content-Type: application/json' -d "{\"txnId\":\"$ACCT-$1\",\"accountId\":\"$ACCT\",
  \"amount\":$2,\"merchantCategory\":\"$3\",\"country\":\"$4\",\"deviceId\":\"$5\",\"timestamp\":\"$6\"}"
  echo
}
for i in 1 2 3 4 5 6; do send "$i" "4$i.00" groceries CA phone "2026-10-0${i}T12:00:00Z"; done
echo "--- account takeover: new device, other country 20 minutes later, gift cards, 20x the usual amount"
send 7 900.00 gift_cards NG laptop "2026-10-06T12:20:00Z"
