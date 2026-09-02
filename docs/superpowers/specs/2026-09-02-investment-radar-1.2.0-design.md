# Investment Radar 1.2.0 – Design

Date: 2026-09-02
Status: proposed, awaiting user review
Target: Android app + Azure Functions backend

## 1. Goal

Investment Radar 1.2.0 turns the existing six-value, partly preconfigured recommendation system into a data-driven investment radar. The app must rank a broader universe, explain its decisions, adapt monthly allocations to the user's local portfolio, provide stronger review/sell signals, improve alerts, and keep direct Trade Republic and in-app update flows simple.

The product remains decision support only. It never places orders or trades automatically.

## 2. Scope

1. Data-driven scoring across quality, valuation, growth, momentum and risk.
2. Expand the curated universe from 6 to about 40 stocks and ETFs.
3. Portfolio-aware monthly allocation on-device.
4. Sell/review logic V2.
5. User-configurable alert preferences.
6. Fundamental-data enrichment with graceful fallback.
7. Multi-horizon momentum: 1D, 1M, 3M, 6M, 12M.
8. More robust Trade Republic routing.
9. Explicit manual update-check feedback.
10. Alert center with read state, filters, delete and direct asset navigation.

## 3. Architectural choice

Use a hybrid architecture.

### Backend responsibilities

The backend owns objective market analysis:
- curated investment universe and static metadata
- quotes and FX conversion
- historical prices
- available fundamental metrics
- provider/cache health
- five component scores and objective total score
- objective BUY/WATCH/NO BUY/REVIEW classification
- market-triggered alert candidates

### Android responsibilities

The Android app owns personal state:
- monthly budget
- purchases/sales and current portfolio
- watchlist
- custom investments
- alert preferences
- read/deleted alert state
- portfolio concentration and allocation overlay

Portfolio holdings are not uploaded to the backend. This avoids adding a user account or personal portfolio storage service in 1.2.0.

## 4. Data model V2

Each backend investment item exposes the existing identity and quote fields plus:

- `scoreTotal`: 0–100
- `scoreQuality`: 0–100 or null
- `scoreValuation`: 0–100 or null
- `scoreGrowth`: 0–100 or null
- `scoreMomentum`: 0–100 or null
- `scoreRisk`: 0–100 or null, where higher means more attractive/lower risk
- `coverage`: 0–100, percentage of expected scoring inputs available
- `recommendation`: `BUY`, `WATCH`, `NO_BUY`, `REVIEW`
- `recommendationReasons`: concise ordered reasons
- `momentum`: returns for 1D/1M/3M/6M/12M when available
- `fundamentals`: normalized metrics when available
- `analysisAsOf`
- provider/source metadata for stale or missing fields

Raw missing values remain null. The system must never replace unavailable fundamentals with invented numbers.

## 5. Scoring V2

### 5.1 Equity weights

For stocks, the target weights are:
- Quality: 25%
- Valuation: 20%
- Growth: 20%
- Momentum: 20%
- Risk: 15%

### 5.2 ETF weights

ETFs are scored with a separate profile because company fundamentals do not apply directly:
- Quality/structure: 30%
- Valuation/exposure proxy: 10%
- Growth/exposure proxy: 10%
- Momentum: 30%
- Risk: 20%

ETF static quality/structure inputs may include curated metadata such as diversification, replication structure and cost only when explicitly present in configuration. Missing ETF inputs reduce coverage rather than being guessed.

### 5.3 Missing-data handling

The total score is a weighted average of available components, renormalized to the weights actually present. `coverage` shows how much of the intended model was available.

Rules:
- coverage >= 70: normal classification
- coverage 50–69: classification allowed but UI labels confidence as reduced
- coverage < 50: no BUY classification; maximum result is WATCH unless a hard REVIEW condition applies

### 5.4 Recommendation thresholds

Default objective thresholds:
- BUY: total >= 75, coverage >= 50, no hard review condition
- WATCH: total 55–74
- NO_BUY: total < 55
- REVIEW: hard deterioration signal or explicit sell/review condition

The backend returns reasons such as "strong 6M momentum", "valuation expensive versus configured range", "earnings growth deteriorating", or "data coverage limited". Android displays a short top-three explanation.

## 6. Fundamental analysis

Twelve Data remains the primary provider to avoid adding a second paid market-data dependency. Fundamental endpoints are used when available for the configured plan.

Normalized stock metrics should prefer, when available:
- forward/trailing P/E
- price-to-sales or EV/EBITDA
- free-cash-flow yield or equivalent cash-flow valuation metric
- revenue growth
- EPS/earnings growth
- operating or net margin
- ROE/ROIC where available
- debt/leverage metric

The scoring layer operates on a normalized provider-independent object so another provider could later be added without changing Android contracts.

If the Twelve Data plan does not expose a metric or endpoint:
- dashboard remains available
- missing component inputs stay null
- coverage is reduced
- no fabricated fallback is used
- cached last-known valid fundamentals may be used within the defined stale window and are marked stale

## 7. Historical data and momentum

Historical closes are cached and used to calculate:
- 1D return
- approximately 1M return
- approximately 3M return
- approximately 6M return
- approximately 12M return

Momentum score favors broad confirmation instead of a single daily move. One-day movement has the lowest influence; 3M and 6M have the greatest influence. Missing horizons reduce momentum coverage.

Historical data does not need to be fetched on every dashboard request.

## 8. Cache strategy and provider load

Separate caches prevent a 40-value universe from multiplying provider calls on every app refresh.

Target freshness:
- live/delayed quote cache: 5 minutes during normal use
- FX cache: existing daily/reference behavior retained
- historical series: 6 hours
- fundamentals: 24 hours
- scoring snapshot: recomputed when an input cache changes, otherwise reusable

If a provider call fails, last-known valid cache may be used with an explicit stale marker. Cache age limits are enforced; stale data is never presented as fresh.

## 9. Investment universe

Expand `investments.json` to approximately 40 curated assets rather than implementing unrestricted search in 1.2.0.

Selection principles:
- diversified core ETFs
- factor/quality/value ETFs
- large, liquid quality equities across multiple sectors
- valid ticker/market symbol
- ISIN when available for Trade Republic routing
- explicit type/risk/static ETF metadata

The universe is configuration-driven and can be changed without Android releases when backend schema remains compatible.

## 10. Portfolio-aware allocation

The backend does not know the user's portfolio. Android applies a personal overlay to backend BUY candidates.

Inputs:
- monthly budget
- objective score
- current portfolio market value or invested-value fallback
- current asset weight
- risk
- asset type

Rules:
- only objective BUY candidates receive new budget
- higher scores receive higher base weight
- existing concentration reduces or blocks additional allocation
- >= 40% current portfolio share blocks a normal new allocation to that asset unless there are no viable alternatives; UI explains the block
- 30–39.9% applies a strong penalty
- 20–29.9% applies a mild penalty
- risk 4–5 reduces maximum allocation
- allocation is redistributed across remaining BUY candidates
- final integer-euro allocations sum to the selected monthly budget when at least one eligible BUY candidate exists
- if no eligible BUY candidate exists, the app explicitly recommends holding cash instead of forcing an investment

Custom assets can count toward concentration when a EUR-comparable value is available, but they do not receive automatic BUY recommendations unless backend analysis exists for them.

## 11. Sell/review logic V2

REVIEW is intentionally not an automatic sell instruction.

Hard review candidates include:
- configured absolute review threshold reached
- exceptional 1D loss beyond configured threshold
- objective score falls below a review floor for a held asset
- score deterioration of at least 15 points versus last valid scoring snapshot
- medium/long momentum breakdown across multiple horizons
- severe fundamental deterioration when supported by fresh/acceptable data
- explicit backend manual review/sell override remains supported

The alert reason identifies which trigger fired. Repeated identical conditions remain deduplicated until the condition clears and later recurs.

## 12. Alerts V2

### 12.1 Alert types

Support:
- BUY opportunity
- REVIEW
- SELL/manual sell-review
- threshold/price event
- system/data issue only when user action is useful

### 12.2 Android preferences

Create an `AlertPreferencesStore` with defaults:
- buy opportunities: on
- review alerts: on
- sell/manual sell-review alerts: on
- threshold alerts: on
- minimum severity: normal
- optional custom daily-drop threshold for local display/filtering

Preferences are editable from the Alerts screen.

### 12.3 Push transport

Firebase pushes become data-focused messages so the Android messaging service can apply local preferences before displaying a notification. The received event may still be stored in the alert center even if notification display is suppressed, depending on the event class.

Holding-specific REVIEW/SELL topics remain. Global BUY events use the existing general investment topic.

## 13. Alert center

Replace the simple list with a dedicated alert-center state model.

Features:
- All / Buy / Review / Sell filters
- unread badge/count
- mark read when opened
- mark all read
- delete one alert
- clear stored alerts with confirmation
- tap alert to open the related investment in-app when resolvable
- direct Trade Republic action from asset detail remains available

Read/deleted state is local. Backend recent alerts may reappear only if they have a new ID/fingerprint; deleted local IDs are tombstoned for a bounded retention period so refresh does not immediately restore them.

## 14. Trade Republic routing

For valid ISIN assets:
1. Build official HTTPS stock URL `https://app.traderepublic.com/stocks/<ISIN>`.
2. First try an ACTION_VIEW intent targeted to package `de.traderepublic.app` when Android can resolve it.
3. If not resolvable or launch fails, use a normal ACTION_VIEW HTTPS intent.
4. If the ISIN is missing/invalid, open `https://app.traderepublic.com/browse/stock`.
5. Continue copying ISIN/ticker to clipboard as manual-search fallback.

Do not invent or depend on an undocumented `traderepublic://` custom scheme.

## 15. Update UX

Startup update checks stay silent when the installed version is current.

Manual Update button behavior:
- newer release: existing update dialog
- current version: visible confirmation `Du nutzt bereits die aktuelle Version <version>.`
- network/API failure: concise retryable error

The 1.1.28 permission-resume behavior remains unchanged.

## 16. Android module boundaries

Do not add more responsibility to the already large `MainActivity.kt`.

Introduce focused units, expected names may vary during implementation:
- `RecommendationEngine.kt`: Android portfolio overlay and presentation mapping only
- `AlertPreferencesStore.kt`
- `AlertCenterStore.kt` or evolved `AlertStore.kt`
- `TradeRepublicNavigator.kt`
- dedicated Compose screen/components for Alert Center and settings

Backend introduces focused units:
- `fundamentals.mjs`
- `history.mjs`
- `scoring.mjs`
- `analysisCache.mjs` or equivalent focused cache modules

Existing quote/FX modules stay responsible for their current concerns.

## 17. API compatibility

The dashboard response is additive wherever possible. Existing identity/quote fields remain so 1.1.29 clients do not crash while 1.2.0 backend is deployed.

The old static `status` field may remain temporarily as a compatibility alias of the new objective recommendation. New Android code uses `recommendation` and score fields.

Backend deployment should precede or be compatible with Android publication. A backend failure must not corrupt local portfolio state.

## 18. Error handling

- Quote missing: show last valid cache if within policy; otherwise explicit unavailable state.
- Fundamentals unavailable: reduce coverage; do not fail dashboard.
- History unavailable: momentum coverage reduced; do not fail dashboard.
- FX unavailable: retain existing EUR fallback behavior and display uncertainty.
- All market providers unavailable: return last acceptable scoring snapshot when possible; otherwise dashboard shows data-quality warning instead of false recommendations.
- Trade Republic launch failure: browser/browse fallback and copied identifier.
- Update API failure: manual check shows error; startup check remains non-disruptive.

## 19. Testing strategy

Development follows test-first implementation.

### Backend tests

Add deterministic unit tests for:
- equity component scoring
- ETF scoring profile
- weight renormalization with missing data
- coverage gating prevents low-data BUY
- recommendation thresholds
- 1M/3M/6M/12M momentum calculations
- cache freshness/stale behavior
- REVIEW triggers and deduplication
- dashboard compatibility fields
- provider failure does not fabricate fundamentals

### Android tests/regressions

Add tests for:
- portfolio concentration penalties
- integer budget always sums correctly when eligible candidates exist
- cash recommendation when no BUY candidate is eligible
- local alert preference filtering
- read/unread/delete/tombstone behavior
- manual update current-version feedback
- Trade Republic package-targeted URL with browser fallback

Existing updater and Trade Republic regression tests remain in CI.

### Production verification

Before release:
- backend test suite green
- Android unit/regression suite green
- signed release APK builds and signature verifies
- branch build must not publish
- main build publishes only after merge
- backend health and dashboard schema verified after deployment
- release asset versionName/versionCode verified

## 20. Versioning and rollout

Target release:
- Android `versionName 1.2.0`
- Android `versionCode 31`
- Backend `1.2.0`

Rollout sequence:
1. implement in feature branch(es) with CI publication disabled off main
2. deploy backward-compatible backend
3. verify live dashboard/health
4. merge Android changes
5. main CI builds signed APK and publishes GitHub release `v1.2.0`
6. 1.1.29 users receive it through the existing in-app updater

## 21. Explicit non-goals for 1.2.0

- no automatic trading/order submission
- no brokerage account login
- no Trade Republic scraping
- no unrestricted thousands-of-symbol search
- no server-side storage of the user's portfolio
- no financial-adviser claim or guaranteed-return language
- no invented fundamental values when providers do not return data

## 22. Acceptance criteria

1. Recommendations are derived from scored data, not a hard-coded `KAUFEN` status alone.
2. About 40 configured assets can be ranked without making every dashboard refresh fetch all expensive data again.
3. Each analyzed asset shows total score, component scores, data coverage and concise reasons.
4. 1D/1M/3M/6M/12M momentum is available when history exists.
5. Missing fundamentals lower coverage but never take the whole dashboard down.
6. The user's monthly allocation reacts to existing portfolio concentration and sums to the chosen budget or explicitly recommends cash.
7. Held assets can trigger explainable REVIEW alerts from deterioration, not only raw price drops.
8. Alert preferences suppress unwanted notifications locally.
9. Alert Center supports filters, read state and deletion.
10. Trade Republic uses targeted HTTPS first and safe fallback.
11. Manual Update check confirms when the installed app is current.
12. Existing local holdings, purchases, sales, custom assets and watchlist remain compatible after update.
13. CI verifies scoring, allocation, alerts, updater, Trade Republic navigation, APK build and signature.
14. No automatic orders are placed.