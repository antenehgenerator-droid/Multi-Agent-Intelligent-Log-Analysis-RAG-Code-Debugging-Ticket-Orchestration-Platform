#!/usr/bin/env bash
# Pipe logs.raw from the beginning into a replay topic for downstream reprocessing tests.
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
SRC_TOPIC="${SOURCE_TOPIC:-logs.raw}"
DST_TOPIC="${REPLAY_TOPIC:-logs.reprocessed}"

kafka-console-consumer --bootstrap-server "$BOOTSTRAP" \
    --topic "$SRC_TOPIC" \
    --from-beginning \
    --property print.key=true |
  kafka-console-producer --bootstrap-server "$BOOTSTRAP" \
      --topic "$DST_TOPIC"
