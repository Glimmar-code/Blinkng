const JSON_HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "private, max-age=300",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

function finiteCoordinate(value: string | null, min: number, max: number): number | null {
  if (value == null || value.trim() === "") return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < min || parsed > max) return null;
  return parsed;
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

async function fetchOfficialAlerts(
  latitude: number,
  longitude: number,
): Promise<{ available: boolean; alerts: OfficialAlert[] }> {
  const key = (Deno.env.get("OPENWEATHER_API_KEY") || "").trim();
  if (!key) return { available: false, alerts: [] };

  const url = new URL("https://api.openweathermap.org/data/3.0/onecall");
  url.searchParams.set("lat", String(latitude));
  url.searchParams.set("lon", String(longitude));
  url.searchParams.set("exclude", "current,minutely,hourly,daily");
  url.searchParams.set("units", "metric");
  url.searchParams.set("appid", key);

  try {
    const response = await fetch(url, {
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(8_000),
    });
    if (!response.ok) {
      console.warn("blink-weather official alerts unavailable", response.status);
      return { available: false, alerts: [] };
    }

    const payload = await response.json() as Record<string, unknown>;
    const rows = Array.isArray(payload.alerts) ? payload.alerts : [];
    const alerts: OfficialAlert[] = rows.slice(0, 12).map((raw, index) => {
      const item = (raw || {}) as Record<string, unknown>;
      const source = String(item.sender_name || "Official weather authority").trim();
      const title = String(item.event || "Weather alert").trim();
      const start = Number(item.start || 0);
      const end = Number(item.end || 0);
      return {
        id: [source, title, Number.isFinite(start) ? start : 0, index].join(":"),
        title,
        description: String(item.description || "").trim().slice(0, 8_000),
        instruction: "",
        source,
        severity: "unknown",
        urgency: "unknown",
        startsAt: Number.isFinite(start) ? start : 0,
        endsAt: Number.isFinite(end) ? end : 0,
        official: true,
      };
    });

    return { available: true, alerts };
  } catch (error) {
    console.warn("blink-weather official alert request failed", error);
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

  const payload = await response.json() as Record<string, unknown>;
  const current = (payload.current || {}) as Record<string, unknown>;
  const hourly = (payload.hourly || {}) as Record<string, unknown>;

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
    const liquid = Math.max(0, Number(rain[index] || 0)) + Math.max(0, Number(showers[index] || 0));
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
  const latitude = finiteCoordinate(url.searchParams.get("lat"), -90, 90);
  const longitude = finiteCoordinate(url.searchParams.get("lon"), -180, 180);
  if (latitude == null || longitude == null) {
    return json({ error: "Valid lat and lon are required." }, 400);
  }

  try {
    const [forecast, official] = await Promise.all([
      fetchForecast(latitude, longitude),
      fetchOfficialAlerts(latitude, longitude),
    ]);

    return json({
      ...forecast,
      provider: official.available
        ? "Open-Meteo forecast + official authority alerts"
        : "Open-Meteo",
      officialAlertsAvailable: official.available,
      alerts: official.alerts,
      fetchedAt: Math.floor(Date.now() / 1000),
    });
  } catch (error) {
    console.error("blink-weather failed", error);
    return json({ error: "Weather data is temporarily unavailable." }, 503);
  }
});
