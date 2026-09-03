import { app } from "@azure/functions";
import { queryRadar } from "../lib/radar.mjs";

app.http("radar", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "radar",
  handler: async (request) => {
    try {
      const params = Object.fromEntries(request.query.entries());
      return {
        status: 200,
        jsonBody: await queryRadar({
          query: params.q ?? "",
          type: params.type,
          region: params.region,
          country: params.country,
          sector: params.sector,
          recommendation: params.recommendation,
          qualityTier: params.qualityTier,
          riskMax: params.riskMax,
          sort: params.sort,
          page: params.page,
          pageSize: params.pageSize,
          tradeRepublicVerified: params.tradeRepublicVerified
        })
      };
    } catch (error) {
      console.error("Radar request failed", error);
      return { status: 500, jsonBody: { error: "RADAR_UNAVAILABLE", message: String(error?.message ?? error) } };
    }
  }
});
