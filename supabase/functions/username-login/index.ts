import { handleUsernameLogin } from "./handler.ts";

// Entrypoint is intentionally minimal so tests can exercise the real handler
// with an isolated mock Auth backend without starting the public server.
Deno.serve(handleUsernameLogin);
