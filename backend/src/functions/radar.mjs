import { app } from "@azure/functions";
import { queryRadar } from "../lib/radar.mjs";
import { selectBuyFilterResults } from "../lib/radarBuyFallback.mjs";

app.http("radar", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "radar",
  handler: async (request) => {
    try {
      const params = Object.fromEntries(request.query.entries());
      const query = {
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
      };

      const primary = await queryRadar(query);
      if (String(params.recommendation ?? "").toUpperCase() !== "BUY" || primary.total > 0) {
        return { status: 200, jsonBody: { ...primary, buyFallbackActive: false } };
      }

      const watchPage = await queryRadar({
        ...query,
        recommendation: "WATCH",
        page: 1,
        pageSize: 100,
        sort: "SCORE_DESC",
        tradeRepublicVerified: true
      });
      const fallback = selectBuyFilterResults(watchPage.items);
      return {
        status: 200,
        jsonBody: {
          ...primary,
          total: fallback.items.length,
          page: 1,
          pageSize: Number(params.pageSize) || primary.pageSize,
          hasMore: false,
          items: fallback.items,
          buyFallbackActive: fallback.buyFallbackActive
        }
      };
    } catch (error) {
      console.error("Radar request failed", error);
      return { status: 500, jsonBody: { error: "RADAR_UNAVAILABLE", message: String(error?.message ?? error) } };
    }
  }
});
