const JSON_HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "private, max-age=300",
};

const WEATHER_CACHE_TTL_MS = 10 * 60 * 1_000;
const WEATHER_CACHE_MAX_ENTRIES = 500;
const ALERT_CACHE_DEFAULT_TTL_MS = 60 * 60 * 1_000;
const ALERT_CACHE_MAX_ENTRIES = 250;
const MAX_OFFICIAL_ALERTS = 3;

type JsonRecord = Record<string, unknown>;

type OfficialAlert = {
  id: string;
  title: string;
  description: string;
  instruction: string;
  source: string;
  severity: string;
  urgency: string;
  startsAt: number;
  endsAt: number;
  official: boolean;
};

type WeatherPayload = {
  temperatureC: number;
  feelsLikeC: number;
  humidityPercent: number;
  windKph: number;
  condition: string;
  precipitationProbabilityPercent: number;
  nextRainAt: number | null;
  provider: string;
  officialAlertsAvailable: boolean;
  alerts: OfficialAlert[];
  fetchedAt: number;
};

type CacheEntry = {
  expiresAt: number;
  payload: WeatherPayload;
};

const weatherCache = new Map<string, CacheEntry>();
const alertCache = new Map<string, { expiresAt: number; alert: OfficialAlert }>();

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

function finiteCoordinate(value: string | null, min: number, max: number): number | null {
  if (value == null || value.trim() === "") return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < min || parsed > max) return null;
  return parsed;
}

function roundedCoordinate(value: number): number {
  // Weather does not need precise GPS. About 0.02° is roughly 2 km and lets nearby
  // users share provider/cache requests while avoiding exact device coordinates.
  return Math.round(value * 50) / 50;
}

function cacheKey(latitude: number, longitude: number): string {
  return `${latitude.toFixed(2)}:${longitude.toFixed(2)}`;
}

function getCachedWeather(key: string): WeatherPayload | null {
  const cached = weatherCache.get(key);
  if (!cached) return null;
  if (cached.expiresAt <= Date.now()) {
    weatherCache.delete(key);
    return null;
  }
  return cached.payload;
}

function setCachedWeather(key: string, payload: WeatherPayload): void {
  if (weatherCache.size >= WEATHER_CACHE_MAX_ENTRIES) {
    const oldestKey = weatherCache.keys().next().value as string | undefined;
    if (oldestKey) weatherCache.delete(oldestKey);
  }
  weatherCache.set(key, {
    expiresAt: Date.now() + WEATHER_CACHE_TTL_MS,
    payload,
  });
}

function weatherCondition(code: number): string {
  if (code === 0) return "Clear";
  if (code === 1 || code === 2) return "Mostly clear";
  if (code === 3) return "Cloudy";
  if (code === 45 || code === 48) return "Fog";
  if (code >= 51 && code <= 57) return "Drizzle";
  if (code >= 61 && code <= 67) return "Rain";
  if (code >= 71 && code <= 77) return "Snow";
  if (code >= 80 && code <= 82) return "Rain showers";
  if (code >= 85 && code <= 86) return "Snow showers";
  if (code >= 95) return "Thunderstorm";
  return "Weather";
}

function asRecord(value: unknown): JsonRecord {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as JsonRecord
    : {};
}

function englishDescription(value: unknown): string {
  if (typeof value === "string") return value.trim().slice(0, 8_000);
  if (!Array.isArray(value)) return "";

  const rows = value
    .map(asRecord)
    .filter((row) => typeof row.description === "string");

  const preferred = rows.find((row) =>
    String(row.language || "").toLowerCase().startsWith("en")
  ) ?? rows[0];

  return String(preferred?.description || "").trim().slice(0, 8_000);
}

async function fetchOpenWeatherAlert(
  alertId: string,
  apiKey: string,
): Promise<OfficialAlert | null> {
  const cached = alertCache.get(alertId);
  if (cached && cached.expiresAt > Date.now()) {
    return cached.alert;
  }
  if (cached) alertCache.delete(alertId);

  const url = new URL(
    `https://api.openweathermap.org/data/4.0/onecall/alert/${encodeURIComponent(alertId)}`,
  );
  url.searchParams.set("appid", apiKey);

  try {
    const response = await fetch(url, {
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(8_000),
    });
    if (!response.ok) {
      console.warn("blink-weather alert detail unavailable", response.status);
      return null;
    }

    const item = asRecord(await response.json());
    const source = String(item.sender_name || "Official weather authority").trim();
    const tags = Array.isArray(item.tags)
      ? item.tags.map(String).map((tag) => tag.trim()).filter(Boolean)
      : [];
    const title = String(item.event || "").trim() ||
      tags.slice(0, 3).join(", ") ||
      "Weather alert";
    const start = Number(item.start || 0);
    const end = Number(item.end || 0);

    const alert: OfficialAlert = {
      id: String(item.id || alertId).trim() || alertId,
      title,
      description: englishDescription(item.description),
      instruction: "",
      source,
      // One Call 4.0 alert detail currently exposes source/event/start/end/description/tags,
      // but not a standardized severity/urgency pair. Do not invent those values.
      severity: "unknown",
      urgency: "unknown",
      startsAt: Number.isFinite(start) ? start : 0,
      endsAt: Number.isFinite(end) ? end : 0,
      official: true,
    };

    if (alertCache.size >= ALERT_CACHE_MAX_ENTRIES) {
      const oldestKey = alertCache.keys().next().value as string | undefined;
      if (oldestKey) alertCache.delete(oldestKey);
    }
    const naturalExpiry = alert.endsAt > 0
      ? Math.max(Date.now() + 5 * 60 * 1_000, alert.endsAt * 1_000)
      : Date.now() + ALERT_CACHE_DEFAULT_TTL_MS;
    alertCache.set(alertId, {
      expiresAt: Math.min(naturalExpiry, Date.now() + 6 * 60 * 60 * 1_000),
      alert,
    });

    return alert;
  } catch (error) {
    console.warn("blink-weather alert detail request failed", error);
    return null;
  }
}

async function fetchOfficialAlerts(
  latitude: number,
  longitude: number,
): Promise<{ available: boolean; alerts: OfficialAlert[] }> {
  const apiKey = (Deno.env.get("OPENWEATHER_API_KEY") || "").trim();
  if (!apiKey) return { available: false, alerts: [] };

  const url = new URL("https://api.openweathermap.org/data/4.0/onecall/current");
  url.searchParams.set("lat", String(latitude));
  url.searchParams.set("lon", String(longitude));
  url.searchParams.set("units", "metric");
  url.searchParams.set("lang", "en");
  url.searchParams.set("appid", apiKey);

  try {
    const response = await fetch(url, {
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(8_000),
    });
    if (!response.ok) {
      console.warn("blink-weather One Call 4.0 unavailable", response.status);
      return { available: false, alerts: [] };
    }

    const payload = asRecord(await response.json());
    const first = Array.isArray(payload.data) ? asRecord(payload.data[0]) : {};
    const alertIds = Array.isArray(first.alerts)
      ? [...new Set(
        first.alerts
          .map(String)
          .map((id) => id.trim())
          .filter(Boolean),
      )].slice(0, MAX_OFFICIAL_ALERTS)
      : [];

    if (alertIds.length === 0) {
      return { available: true, alerts: [] };
    }

    const details = await Promise.all(
      alertIds.map((id) => fetchOpenWeatherAlert(id, apiKey)),
    );

    return {
      available: true,
      alerts: details.filter((item): item is OfficialAlert => item !== null),
    };
  } catch (error) {
    console.warn("blink-weather One Call 4.0 request failed", error);
    return { available: false, alerts: [] };
  }
}

async function fetchForecast(latitude: number, longitude: number) {
  const url = new URL("https://api.open-meteo.com/v1/forecast");
  url.searchParams.set("latitude", String(latitude));
  url.searchParams.set("longitude", String(longitude));
  url.searchParams.set(
    "current",
    "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code",
  );
  url.searchParams.set(
    "hourly",
    "precipitation_probability,rain,showers,weather_code",
  );
  url.searchParams.set("forecast_hours", "24");
  url.searchParams.set("timeformat", "unixtime");
  url.searchParams.set("timezone", "UTC");

  const response = await fetch(url, {
    headers: { Accept: "application/json" },
    signal: AbortSignal.timeout(8_000),
  });
  if (!response.ok) {
    throw new Error(`Open-Meteo request failed (${response.status})`);
  }

  const payload = asRecord(await response.json());
  const current = asRecord(payload.current);
  const hourly = asRecord(payload.hourly);

  const times = Array.isArray(hourly.time) ? hourly.time as unknown[] : [];
  const rain = Array.isArray(hourly.rain) ? hourly.rain as unknown[] : [];
  const showers = Array.isArray(hourly.showers) ? hourly.showers as unknown[] : [];
  const probabilities = Array.isArray(hourly.precipitation_probability)
    ? hourly.precipitation_probability as unknown[]
    : [];

  let nextRainAt: number | null = null;
  let strongestProbability = 0;
  for (let index = 0; index < times.length; index++) {
    const probability = Math.max(0, Math.min(100, Number(probabilities[index] || 0)));
    strongestProbability = Math.max(strongestProbability, probability);
    const liquid = Math.max(0, Number(rain[index] || 0)) +
      Math.max(0, Number(showers[index] || 0));
    if (nextRainAt == null && probability >= 50 && liquid > 0) {
      const epoch = Number(times[index]);
      if (Number.isFinite(epoch) && epoch > 0) nextRainAt = epoch;
    }
  }

  const temperature = Number(current.temperature_2m || 0);
  const feelsLike = Number(current.apparent_temperature || temperature);
  const humidity = Number(current.relative_humidity_2m || 0);
  const wind = Number(current.wind_speed_10m || 0);
  const code = Number(current.weather_code || 0);

  return {
    temperatureC: Number.isFinite(temperature) ? temperature : 0,
    feelsLikeC: Number.isFinite(feelsLike) ? feelsLike : 0,
    humidityPercent: Number.isFinite(humidity) ? Math.round(humidity) : 0,
    windKph: Number.isFinite(wind) ? wind : 0,
    condition: weatherCondition(Number.isFinite(code) ? code : 0),
    precipitationProbabilityPercent: Math.round(strongestProbability),
    nextRainAt,
  };
}

Deno.serve(async (req: Request) => {
  if (req.method !== "GET") {
    return json({ error: "Method not allowed" }, 405);
  }

  const url = new URL(req.url);
  const rawLatitude = finiteCoordinate(url.searchParams.get("lat"), -90, 90);
  const rawLongitude = finiteCoordinate(url.searchParams.get("lon"), -180, 180);
  if (rawLatitude == null || rawLongitude == null) {
    return json({ error: "Valid lat and lon are required." }, 400);
  }

  const latitude = roundedCoordinate(rawLatitude);
  const longitude = roundedCoordinate(rawLongitude);
  const key = cacheKey(latitude, longitude);
  const cached = getCachedWeather(key);
  if (cached) {
    return json({
      ...cached,
      cache: "edge-memory",
    });
  }

  try {
    const [forecast, official] = await Promise.all([
      fetchForecast(latitude, longitude),
      fetchOfficialAlerts(latitude, longitude),
    ]);

    const payload: WeatherPayload = {
      ...forecast,
      provider: official.available
        ? "Open-Meteo forecast + OpenWeather One Call 4.0 official alerts"
        : "Open-Meteo",
      officialAlertsAvailable: official.available,
      alerts: official.alerts,
      fetchedAt: Math.floor(Date.now() / 1_000),
    };

    setCachedWeather(key, payload);
    return json(payload);
  } catch (error) {
    console.error("blink-weather failed", error);
    return json({ error: "Weather data is temporarily unavailable." }, 503);
  }
});
