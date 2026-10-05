# Phase 1 + Phase 2 architecture

## Phase 2 live analysis
`analysis/LiveAnalysis.kt` provides immutable ErrorPoint/LiveAnalysis snapshots, a pure Kotlin LiveAnalyzer, GT-centered spherical azimuthal-equidistant offsets, and gap-preserving min/max chart reduction. Engine feeds only accepted Measuring samples into it. The service publishes analysis through StateFlow on location/timer updates; raw observations remain independently journaled. Rolling statistics advance with the monotonic timer even if no fixes arrive. Early stop freezes the elapsed analysis interval; scheduled completion clamps to the deadline.

`ui/LiveDashboard.kt` renders cm-based CEP95, acquisition diagnostics, Compose Canvas timeline and equal-scale scatter without adding a chart dependency. R7 empirical inclusion is displayed separately from a statistical confidence interval. Native Canvas text only labels axes; all calculations remain framework-free. Timeline reduction retains first/last/min/max per chunk and breaks lines across >3s gaps; scatter drawing samples at most 2000 points including largest error and last fix. Neither reduction changes statistics/export. Screen-on is a user-controlled Activity view flag, while service/wakelock ownership remains unchanged.

`recording/SessionReader.kt` reconstructs LiveAnalysis from GT and saved coordinates (never blindly trusts imported statistics). Android JsonReader streams exported JSON and skips raw arrays; journal reading processes individual lines. Interrupted/corrupt sessions are explicitly labeled and not silently called complete. The import limit is 50,000 location samples. The UI supports local history, selected-session export and A/B JSON comparison. Summary gains liveAnalysis diagnostics; metadata gains GT provenance/declared uncertainty/analysis definitions. All additions are backward-compatible schema 1 fields. Raw data and metre units remain unchanged.

GT uncertainty is descriptive user metadata, not a calibrated error bound to add to/subtract from observations. Occupied-second coverage is an acquisition diagnostic, not confidence. The distance model remains the documented Phase 1 mean-radius spherical approximation; centimetre display does not turn it into a survey-grade ellipsoid solver.

Compose drawing reference: https://developer.android.com/develop/ui/compose/graphics/draw/overview
Reported accuracy definition: https://developer.android.com/reference/android/location/Location#getAccuracy()

## Repository inspection
Initial repository contained only .git (no commits, Android source, conventions or Gradle configuration). A single app module is created. Versions are pinned: Gradle 8.7, AGP 8.5.2, Kotlin/Compose compiler plugin 2.0.21; compile/target SDK 34, min SDK 30 (including Android 11 TC58 deployments). Target 34 is for direct-install benchmark deployment, not a claim of current Play publishing compliance. Java 17 bytecode; local Android Studio JBR used for builds. Dependencies: AndroidX Compose Material3, activity-compose, lifecycle ViewModel, core, JUnit and test-only org.json. No maps, vendor SDK or ML dependency in Phase 1. Version upgrade lint advisories are retained; dependencies are pinned to the installed API 34/AGP compatibility set, not blindly upgraded to versions requiring a newer compile SDK.

## Boundaries and flow
`model` immutable coordinates, locations, satellite snapshots and session configuration → `analysis` pure Kotlin haversine/statistics → `session` pure Kotlin monotonic benchmark engine. `gnss` uses LocationManager GPS_PROVIDER and public GNSS callbacks; `recording` persists JSONL journal and produces JSON/CSV; `ui` Compose and ViewModel observe service StateFlow. Android types stay out of model/analysis/session.

Location callback → immutable sample → engine validates timestamp/window → journal → UI state. Satellite snapshots and raw measurements are independently journaled with monotonic receipt time; locations carry latest snapshot only if <=5 seconds old, otherwise null. Raw events are not synchronously equated to a location fix. Snapshot age is exported. Frequency labels are derived, constellation-aware, approximate classifications; original Hz remains authoritative.

Raw callback registration uses AndroidX LocationManagerCompat to cover the documented Android 11 pre-QPR1 registration bug. LocationCompat handles mock detection across API 30/31. API 31 SoC fields are version-guarded. Runtime information remains device-derived, independent of the manufacturer's OS compatibility documentation.

## Lifecycle choice
A location foreground service owns callbacks, monotonic timer and session, because a 30–60 minute experiment must continue after rotation/background/screen off. Start is an explicit visible user action with precise permission. Notification exposes Stop; no background location permission. Partial wakelock only during an active session supports timers while screen off. Service is START_NOT_STICKY: killed process is an interrupted experiment, never silently a continuous measurement. App-private session JSONL is flushed per event. Reopening the app can export the latest journal, including interrupted sessions. ViewModel retains input/screen state over rotation and owns no platform GNSS callbacks.

## Recording and export
Each session has an immutable metadata header, phase-tagged location samples, independently timestamped satellite/raw observations, and final summary. A dedicated I/O executor serializes journal appends and export so storage work does not block GNSS callbacks. Ordered finalization and export retain preceding events. Sessions survive Reset; latest session recovery supports export after process death. JSON export is an envelope with metadata, samples, satellites, rawObservations and summary. CSV is a normalized long-form table with record_type and payload_json plus location columns, retaining metadata/raw records without ambiguous multiple CSV tables. Null means unavailable, never zero. Export uses SAF ACTION_CREATE_DOCUMENT, no filesystem-wide permission. Retention is app-private until uninstall; user exports backups.

## Analysis and future extensions
Distances use documented mean-radius haversine, percentiles R7, GT-centered errors. Warm-up samples are excluded from summary. Statistics computed in engine updates, not Composables; at 1 Hz and <=3600 measurement points this is intentionally simple. No outlier filtering in P0; impossible/duplicate/stale/mock samples are rejected with counters. Missing fixes do not become zero-error samples. Future PositionCorrector consumes immutable baseline replay and emits distinct timestamped output. filtering/sensorfusion/inference packages will host P1–P6; Android sensor and LiteRT adapters remain platform-specific while transforms/statistics remain portable Kotlin.

## Validation boundaries
Unit tests cover numerical definitions and monotonic session boundaries. assembleDebug and lintDebug check Android integration. Actual GNSS callbacks, permissions, foreground restrictions, screen-off survival, export providers and raw observation availability require TC58/TC501 tests. Device firmware may throttle or omit raw measurements. Satellite visibility does not prove complete hardware support. Android GNSS fixes may use platform assistance; the app itself has no INTERNET permission, so offline-first does not guarantee a cold unaided GNSS receiver.

## API references
- https://developer.android.com/reference/android/location/LocationManager
- https://developer.android.com/reference/android/location/GnssMeasurement
- https://developer.android.com/reference/android/location/GnssClock
- https://developer.android.com/develop/sensors-and-location/location/permissions
- https://developer.android.com/develop/background-work/services/fgs/service-types#location
- https://developer.android.com/build/releases/past-releases/agp-8-5-0-release-notes
- https://developer.android.com/build/kotlin-support
- https://docs.zebra.com/us/en/mobile-computers/handheld/tc5-series/tc53-tc58-prg/t-enabling-developer-options.html
