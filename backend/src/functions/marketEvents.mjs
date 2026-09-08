import { app } from "@azure/functions";
import { parseMarketEventIds, queryMarketEvents } from "../lib/marketEvents.mjs";
import { buildConfiguredMarketEventProviders } from "../lib/marketEventProviders.mjs";

const RESPONSE_HEADERS = Object.freeze({
  "Cache-Control": "no-store",
  "Access-Control-Allow-Origin": "*"
});

export function createMarketEventsHandler({
  buildProviders = () => buildConfiguredMarketEventProviders(),
  now = () => new Date()
} = {}) {
  return async (request, context) => {
    try {
      const url = new URL(request.url);
      const ids = parseMarketEventIds(url.searchParams.get("ids") ?? "");
      const { providers, trustedSources } = buildProviders();
      const result = await queryMarketEvents(ids, {
        providers,
        trustedSources,
        now
      });

      return {
        status: 200,
        jsonBody: result,
        headers: RESPONSE_HEADERS
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : "Market Events konnten nicht geladen werden";
      context?.error?.(error);
      return {
        status: 400,
        jsonBody: { error: message },
        headers: RESPONSE_HEADERS
      };
    }
  };
}

app.http("marketEvents", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "market-events",
  handler: createMarketEventsHandler()
});
