# break-journey-tracker

Real-time tracker and delay ripple-effect simulator for Souptik Tat's Kolkata to Hyderabad break journey (23 to 24 Nov 2026).

**Cab** (Dhakuria to Howrah) → **22895 Vande Bharat** (HWH to BBS) → **Auto/cab** (BBS to Baramunda ISBT) → **Shyamoli Paribahan Volvo 9600** (to Miyapur)

Java 21, Spring Boot 3.4.5, Maven, H2 in-memory, SSE, Swagger UI, Tailwind dashboard.

## Run

```bash
mvn spring-boot:run          # needs JDK 21 and Maven 3.9+
mvn test                     # unit + integration tests
```

| What | URL |
|---|---|
| Live dashboard | http://localhost:8080/ |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| H2 console | http://localhost:8080/h2-console (JDBC `jdbc:h2:mem:journeydb`, user `sa`, empty password) |

The itinerary is seeded on startup by `DataInitializer` (journey id `1`).

## Architecture

```
config      AppConfig, TrackerProperties, OpenApiConfig, JourneySeedData, DataInitializer
controller  JourneyController (REST + SSE)
dto         Records for requests, responses, live snapshot and alerts
entity      Journey, JourneyLeg
enums       TransportMode, LegStatus, JourneyStatus, RiskStatus
exception   ResourceNotFoundException, GlobalExceptionHandler (RFC 7807 ProblemDetail)
repository  JourneyRepository (fetches legs with an entity graph)
service     RiskAnalyzerService, JourneyService, JourneyMapper, LiveTrackingService, TimeUtil
simulator   SimulationClock (virtual clock), JourneySimulator (@Scheduled tick)
```

### Delay ripple model (`RiskAnalyzerService`)

- Fixed-departure legs (train, bus) follow the timetable plus their own reported delay. They never wait for you.
- Flexible legs (cab, auto/cab) leave at the later of their planned time and the previous leg's arrival plus a 10 minute handoff (`tracker.transfer-handoff-minutes`), then take their planned duration.
- Connection buffer = departure of the fixed leg minus your effective arrival at its boarding point.
- Risk: buffer **>= 40** min `SAFE`, **15 to 39** `TIGHT_CONNECTION`, **< 15** `CRITICAL_MISSED_CONNECTION`.
- The delay you report is the **total** delay versus the timetable (it replaces the previous value, `0` clears it).

| Train delay | Arrives BBS | Auto leaves | Reaches Baramunda | Buffer for 13:30 | Risk |
|---|---|---|---|---|---|
| 0 | 11:28 | 12:10 | 12:40 | 50 | SAFE |
| 30 | 11:58 | 12:10 | 12:40 | 50 | SAFE |
| 50 | 12:18 | 12:28 | 12:58 | 32 | TIGHT_CONNECTION |
| 65 | 12:33 | 12:43 | 13:13 | 17 | TIGHT_CONNECTION |
| 90 | 12:58 | 13:08 | 13:38 | -8 | CRITICAL_MISSED_CONNECTION |

The 30 minute "realistic ETA" allowance on the train (11:58) is shown on the leg as `realisticArrival`; it is informational and is not added to the timetable unless you report a delay.

### Live stream and simulation

`GET /api/v1/journeys/{id}/live-stream` (SSE) sends:

- `snapshot`: progress %, active leg, delay-adjusted ETAs, connection buffer, seconds to the bus departure, risk analysis.
- `alert`: human readable notices (delay reported, leg departed or arrived, simulation started or finished).

A snapshot is also sent every 5 seconds, and every second while a simulation runs.

The simulator starts at 04:00 on 23 Nov and advances the virtual clock by `tracker.simulation-speed-factor` seconds each real second (default 600, so the 28 hour journey plays in under 3 minutes). Leg statuses are derived from the delay-adjusted schedule and saved. You can inject delays while it runs. While a simulation is running it overwrites manual status changes.

## cURL examples

```bash
# Full journey, legs, PNRs, buffer metrics
curl -s localhost:8080/api/v1/journeys/1 | jq

# Vande Bharat 30 min late (stays SAFE), 65 min late (TIGHT), 90 min late (CRITICAL)
curl -s -X PATCH localhost:8080/api/v1/journeys/1/legs/2/delay -H 'Content-Type: application/json' -d '{"delayMinutes":30}'  | jq .riskAnalysis
curl -s -X PATCH localhost:8080/api/v1/journeys/1/legs/2/delay -H 'Content-Type: application/json' -d '{"delayMinutes":65}'  | jq .riskAnalysis
curl -s -X PATCH localhost:8080/api/v1/journeys/1/legs/2/delay -H 'Content-Type: application/json' -d '{"delayMinutes":90}'  | jq .riskAnalysis

# Update a leg status
curl -s -X PATCH localhost:8080/api/v1/journeys/1/legs/1/status -H 'Content-Type: application/json' -d '{"status":"IN_TRANSIT"}'

# Simulation
curl -s -X POST localhost:8080/api/v1/journeys/1/simulate/start
curl -s -X POST localhost:8080/api/v1/journeys/1/simulate/reset

# Watch the live stream
curl -N localhost:8080/api/v1/journeys/1/live-stream
```

## Endpoints

| Method | Path |
|---|---|
| GET | `/api/v1/journeys` |
| GET | `/api/v1/journeys/{id}` |
| GET | `/api/v1/journeys/{id}/risk` |
| PATCH | `/api/v1/journeys/{id}/legs/{legOrder}/delay` |
| PATCH | `/api/v1/journeys/{id}/legs/{legOrder}/status` |
| POST | `/api/v1/journeys/{id}/simulate/start` |
| POST | `/api/v1/journeys/{id}/simulate/reset` |
| GET | `/api/v1/journeys/{id}/simulate/status` |
| GET | `/api/v1/journeys/{id}/live-stream` |

## Tests

- `JourneyBufferCalculationTest`: 30 minute Vande Bharat delay (stays SAFE with 50 min buffer), 90 minute delay (CRITICAL, Khandagiri recommendation), exact 40/39 and 15/14 minute threshold boundaries, and a late cab eating the train boarding buffer.
- `JourneyApiIntegrationTest`: seeded data, delay endpoint, validation errors, status updates (MockMvc against the real context).

## Configuration (`application.yml`)

```yaml
tracker:
  simulation-speed-factor: 600
  transfer-handoff-minutes: 10
  safe-buffer-minutes: 40
  tight-buffer-minutes: 15
```
