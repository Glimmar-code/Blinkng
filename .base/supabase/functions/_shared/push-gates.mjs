/** Small, dependency-free policy decision shared by Deno and Node regression tests.
 * Fail closed on RPC errors or non-boolean answers; false is an explicit user opt-out.
 */
export function pushPreferenceDecision(allowed, error) {
  if (error || typeof allowed !== "boolean") return "retry";
  return allowed ? "send" : "skip";
}

/** A decoded JWT subject is NOT proof of identity. Only the Auth service's
 * authenticated getUser() result can authorize a client-originated push.
 */
export async function verifiedNotificationActor(authorization, getUser) {
  const bearer = authorization.match(/^Bearer\s+(\S+)$/i)?.[1];
  if (!bearer) return null;
  try {
    const { data, error } = await getUser(bearer);
    const id = data?.user?.id;
    return !error && typeof id === "string" &&
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)
      ? id : null;
  } catch {
    return null;
  }
}
