# Testing guide

## 1. Automated tests
```bash
mvn test
```
- `JourneyBufferCalculationTest` (pure unit test of the delay ripple and risk thresholds)
- `JourneyApiIntegrationTest` (MockMvc against the full Spring context and H2)

## 2. Manual walkthrough (browser + curl)
1. Start the app: `mvn spring-boot:run` (or `docker build -t break-journey-tracker . && docker run -p 8080:8080 break-journey-tracker`).
2. Open http://localhost:8080/ and confirm: passenger card for Souptik Tat, four legs, buffer card showing **50 min, Safe**.
3. Click **+30m train delay**: arrival at Bhubaneswar becomes 11:58, buffer stays 50, status stays Safe.
4. Click **+65m train delay**: buffer becomes 17, status Tight connection, a warning banner appears.
5. Click **+90m train delay**: buffer becomes -8, status Critical, banner recommends boarding at Khandagiri NH-16 bypass.
6. Click **Reset**: delays cleared, buffer back to 50.
7. Click **Run live simulation**: watch legs move from Scheduled to In transit to Completed, progress bar fill, alerts appear. It finishes in about 3 minutes.
8. In a terminal run `curl -N localhost:8080/api/v1/journeys/1/live-stream` and repeat steps 3 to 6 to see `snapshot` and `alert` events.

## 3. Postman
Import both files from `postman/` and follow the collection description. Run folders 0 to 6 in order.

## 4. Inspect the database
Open http://localhost:8080/h2-console, JDBC URL `jdbc:h2:mem:journeydb`, user `sa`, empty password.
```sql
SELECT id, passenger_name, status FROM journeys;
SELECT leg_order, transport_mode, status, delay_minutes FROM journey_legs ORDER BY leg_order;
```
