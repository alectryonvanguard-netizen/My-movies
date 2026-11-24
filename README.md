# My Movie – Personal Unlimited Movie Hub

Private, ad-free Android app (Kotlin, Jetpack Compose, Material 3, Media3, Room, Retrofit, Coil).
No ad SDKs, no analytics, no tracking.

## Setup
1. Open the `MyMovie` folder in Android Studio (Ladybug or newer, JDK 17). Let Gradle sync
   (Studio creates the Gradle wrapper jar if missing).
2. Get a **free TMDB API key (v3 "API Key")**: https://www.themoviedb.org/settings/api
3. Either add it to `local.properties` (git-ignored):
   `TMDB_API_KEY=your_key_here`
   or paste it in the app under **Settings -> TMDB API key** (stored privately on the device).
4. Run on a device/emulator with Android 7.0+ (API 24).

Without a key the app still opens: Free-to-watch (Internet Archive), My Library, local files
and Downloads work; TMDB-powered screens show a clear "add your key" message.

## Where playable content comes from (only real, authorized sources)
- **Internet Archive "feature_films"** – public-domain/openly hosted films (play and download).
- **Your own sources** – any direct video link (.mp4/.m3u8/.mpd/...) you are licensed to use, or other
  links opened in your browser; **video files from your device**, attached per title.
- **Where to watch** – TMDB/JustWatch provider data for your region, opened in the provider's own app/site.
If nothing is available for a title, the app says so; it never invents a stream.

Downloads are offered only for Internet Archive items. Direct `http://` (non-HTTPS) links are blocked by Android's default policy.
