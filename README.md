# My Movie – movies + Live Sports (ad-free, personal use)

Kotlin · Jetpack Compose · Material 3 · Media3 · Room · Retrofit · Coil · WorkManager. No ads, no analytics SDKs, no account.

## Build with GitHub Actions
Push every file at the path shown, then open Actions -> "Build APK" -> download `MyMovie-debug-apk`.
(`.github/workflows/main.yml` builds with Gradle 8.9 / JDK 17; no wrapper jar needed.)

## First run
Settings -> **TMDB account & API key**: tap *Get your free key*, paste the v3 key or v4 Read Access Token, *Test connection*, *Save*.
Credentials are encrypted with the Android Keystore, never logged, never inside the APK.
Live Sports works immediately (TheSportsDB free key). Optional keys in Settings -> Sports data:
TheSportsDB premium (live scores, timelines) and football-data.org free (tables with form, top scorers, today's matches).

## Sources
- **Full movie**: Internet Archive "Feature Films" items that pass checks (collection, not restricted/removed, real video file); licence shown; download only when licence metadata is explicit. Your own https direct links (you confirm authorization) and files from your device.
- **Trailer / clip / preview**: YouTube videos from TMDB, labelled, opened externally.
- **External viewing**: TMDB provider listings for your region.
- **Live sports video**: only stream links you add that you are authorized to use. Nothing is invented.

## Notes
Downloads are resumable (HTTP Range) with a foreground notification. Free sports APIs have rate limits and no real-time scores; the app says so instead of faking data.
