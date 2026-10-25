/**
 * Private, server-configured identifier aliases for the existing owner identity.
 * These helpers do not grant permissions. Supabase Auth verifies credentials;
 * the returned session must still match the configured owner UUID and email.
 */
export function resolveOwnerLoginAlias(
  identifier: string,
  rawAllowlist: string | undefined,
  expectedOwnerUid: string | undefined,
): string {
  if (!rawAllowlist || !/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(expectedOwnerUid || "")) {
    return "";
  }
  try {
    const parsed: unknown = JSON.parse(rawAllowlist);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return "";
    const entries = parsed as Record<string, unknown>;
    const normalized = identifier.trim().toLowerCase();
    const key = Object.keys(entries).find(k => k.trim().toLowerCase() === normalized);
    const mapped = key ? entries[key] : null;
    if (typeof mapped !== "string") return "";
    const email = mapped.trim().toLowerCase();
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? email : "";
  } catch {
    return "";
  }
}

export function isExpectedOwnerSession(
  data: unknown,
  primaryEmail: string,
  configuredUserId: string,
): boolean {
  if (!data || typeof data !== "object") return false;
  const user = (data as { user?: { id?: string; email?: string } }).user;
  return Boolean(
    user && user.id?.toLowerCase() === configuredUserId.toLowerCase() &&
    user.email?.trim().toLowerCase() === primaryEmail,
  );
}
