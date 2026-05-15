package com.platform.load

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/** Steady-state load: 1000 virtual users/sec for 2 minutes (≈1000 HTTP ingest requests/sec when RTT is low). */
class LogIngestSteadySimulation extends Simulation {

  val baseUrl =
    sys.props.get("baseUrl").orElse(sys.env.get("BASE_URL")).getOrElse("http://localhost:8050")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .contentTypeHeader("application/json")
    .shareConnections
    .maxConnectionsPerHost(8000)

  val ingest = http("ingest")
    .post("/api/v1/logs:ingest")
    .body(
      StringBody(
        """{"events":[{"service":"gatling-load","level":"INFO","message":"steady load","occurredAt":"2026-05-11T12:00:00Z"}]}"""
      )
    )

  val scn = scenario("steady_logs").exec(ingest)

  setUp(
    scn.inject(constantUsersPerSec(1000).during(2.minutes))
  ).protocols(httpProtocol)
}
