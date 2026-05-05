# Infrastructure Setup

## Port Conflicts
If you have local services running on these ports, stop them or update the compose file:
- 5432 (Postgres): Often used by local Postgres installations.
- 6379 (Redis): Often used by local Redis services.
- 9092 (Kafka): Standard Kafka port.

## Setup Steps
1. `cd infra`
2. `make up`
3. Verify Postgres extensions:
   `docker exec -it infra-postgres-1 psql -U user -d logintel -c 'SELECT * FROM pg_extension'`
   (Ensure `vector`, `pg_trgm`, `pgcrypto` are listed).
4. Verify Kafka KRaft:
    - Create a test topic:
      `docker exec -it infra-kafka-1 kafka-topics --bootstrap-server localhost:9092 --create --topic test-topic`
    - Produce a message:
      `docker exec -it infra-kafka-1 kafka-console-producer --bootstrap-server localhost:9092 --topic test-topic` (Type 'hello' and hit Enter)
    - Consume a message:
      `docker exec -it infra-kafka-1 kafka-console-consumer --bootstrap-server localhost:9092 --topic test-topic --from-beginning`

## Access
- Kafka UI: http://localhost:8080
- Grafana: http://localhost:3000 (admin/admin)
- Adminer: http://localhost:8081
