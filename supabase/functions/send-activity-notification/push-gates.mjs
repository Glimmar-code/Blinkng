/** Small, dependency-free policy decision shared by Deno and Node regression tests.
 * Fail closed on RPC errors or non-boolean answers; false is an explicit user opt-out.
 */
export function pushPreferenceDecision(allowed, error) {
  if (error || typeof allowed !== "boolean") return "retry";
  return allowed ? "send" : "skip";
}
