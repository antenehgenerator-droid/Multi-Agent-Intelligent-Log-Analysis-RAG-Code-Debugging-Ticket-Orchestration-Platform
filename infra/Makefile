# infra/Makefile
.PHONY: up down logs psql redis-cli kafka-topics

up:
	docker-compose up -d

down:
	docker-compose down

logs:
	docker-compose logs -f

psql:
	docker exec -it infra-postgres-1 psql -U user -d logintel

redis-cli:
	docker exec -it infra-redis-1 redis-cli

kafka-topics:
	docker exec -it infra-kafka-1 kafka-topics --bootstrap-server localhost:9092 --list
