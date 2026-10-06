# BLINK Items external keys

BLINK Items is designed so the app can build and its normal forecast can work before optional provider keys are configured.

## Google Maps Android SDK

Production Live Location needs a Google Maps Platform Android key.

- Enable **Maps SDK for Android** in the BLINK Google Cloud project.
- Restrict the key to Android applications.
- Package name: `com.aistudio.blink.appvtwo`.
- Add the SHA-1 fingerprint for the BLINK production signing certificate.
- Restrict API access to **Maps SDK for Android**.
- Save the value as the GitHub Actions repository secret `MAPS_API_KEY`.
- Do not commit the key to `.env`, source code, screenshots, issues, or PRs.

The signed production workflow intentionally refuses to publish if `MAPS_API_KEY` is missing. Test/debug builds can still use the placeholder value.

## OpenWeather One Call 4.0

Official national weather alerts are optional and server-side.

- Create/activate an OpenWeather One Call 4.0 API key.
- In the Blink Supabase project, save it as the Edge Function secret `OPENWEATHER_API_KEY`.
- Do not add the real key to Android, Windows, GitHub source, or `.env.example`.

The deployed `blink-weather` function:
- uses Open-Meteo for normal forecast data;
- uses OpenWeather One Call 4.0 only for official national alert references/details;
- never falls back to deprecated One Call 3.0;
- rounds coordinates before sending them to weather providers;
- caches nearby weather responses for 10 minutes per Edge isolate;
- caches official alert details to reduce repeated billable provider calls;
- continues to return normal forecasts if the OpenWeather key is missing or the official-alert provider is unavailable.

Adding or rotating `OPENWEATHER_API_KEY` does not require shipping a new Android APK because it is read by the Supabase Edge Function at runtime.
