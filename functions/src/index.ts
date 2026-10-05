import { initializeApp } from "firebase-admin/app";
import { getAppCheck } from "firebase-admin/app-check";
import { logger } from "firebase-functions";
import { defineSecret } from "firebase-functions/params";
import { onRequest } from "firebase-functions/v2/https";

initializeApp();

/** Set with `npx firebase-tools functions:secrets:set OPENAI_KEY`; never in the repo or the app. */
const openAiKey = defineSecret("OPENAI_KEY");

const OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
/** Mirrors AD_GENERATION_MODEL_ID in OpenAIClient.kt. Anything else is a 400, not a bill. */
const ALLOWED_MODELS = new Set(["gpt-4.1"]);
const APP_CHECK_HEADER = "x-firebase-appcheck";
/** Enough for openai-kotlin to keep its 429 back-off and for OpenAI support to trace a call. */
const FORWARDED_RESPONSE_HEADERS = ["content-type", "retry-after", "x-request-id", "openai-processing-ms"];

/** Same envelope OpenAI uses, so the Kotlin client parses proxy errors like upstream ones. */
function openAiError(message: string, code: string) {
  return { error: { message, type: "invalid_request_error", param: null, code } };
}

function usageOf(responseBody: string): unknown {
  try {
    return JSON.parse(responseBody)?.usage ?? null;
  } catch {
    return null;
  }
}

/**
 * Thin proxy in front of OpenAI's Responses API. The app never holds the key: it sends a Firebase
 * App Check token instead (composeApp/.../network/OpenAIEndpoint.kt), this function verifies the
 * token, pins the model and forwards the body unchanged. Status and body go back untouched, so
 * the client's retry on 429 (openai-kotlin RetryStrategy) keeps working.
 */
export const openai = onRequest(
  {
    region: "europe-west1",
    secrets: [openAiKey],
    // A listing call takes ~4 s at p50, but the client waits up to 2 min per attempt.
    timeoutSeconds: 180,
    memory: "256MiB",
    cpu: 1,
    // I/O-bound: one instance comfortably holds many in-flight calls. The instance cap is the cost
    // ceiling if a token ever leaks.
    concurrency: 80,
    maxInstances: 5,
    invoker: "public",
  },
  async (req, res) => {
    if (req.method !== "POST" || !/^\/(v1\/)?responses\/?$/.test(req.path)) {
      res.status(404).json(openAiError("Not found", "not_found"));
      return;
    }

    const appCheckToken = req.header(APP_CHECK_HEADER);
    if (!appCheckToken) {
      res.status(401).json(openAiError("Missing App Check token", "app_check_missing"));
      return;
    }
    let appId: string;
    try {
      ({ appId } = await getAppCheck().verifyToken(appCheckToken));
    } catch (error) {
      logger.warn("App Check token rejected", { error: String(error) });
      res.status(401).json(openAiError("Invalid App Check token", "app_check_invalid"));
      return;
    }

    const body = req.body;
    if (!body || typeof body !== "object" || Array.isArray(body)) {
      res.status(400).json(openAiError("Body must be a JSON object", "invalid_body"));
      return;
    }
    if (!ALLOWED_MODELS.has(body.model)) {
      res.status(400).json(openAiError(`Model not allowed: ${body.model}`, "model_not_allowed"));
      return;
    }

    const startedAt = Date.now();
    let upstream: Response;
    try {
      upstream = await fetch(OPENAI_RESPONSES_URL, {
        method: "POST",
        headers: {
          authorization: `Bearer ${openAiKey.value()}`,
          "content-type": "application/json",
        },
        body: JSON.stringify(body),
      });
    } catch (error) {
      logger.error("OpenAI unreachable", { appId, error: String(error) });
      res.status(502).json(openAiError("OpenAI unreachable", "upstream_unreachable"));
      return;
    }
    const responseBody = await upstream.text();

    logger.info("openai responses", {
      appId,
      status: upstream.status,
      model: body.model,
      durationMs: Date.now() - startedAt,
      usage: usageOf(responseBody),
    });

    res.status(upstream.status);
    for (const name of FORWARDED_RESPONSE_HEADERS) {
      const value = upstream.headers.get(name);
      if (value) res.set(name, value);
    }
    res.send(responseBody);
  },
);
