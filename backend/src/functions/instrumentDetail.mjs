import { app } from "@azure/functions";
import { getInstrumentDetail } from "../lib/radar.mjs";

export function createInstrumentDetailHandler({ getDetail = getInstrumentDetail } = {}) {
  return async (request, context) => {
    const id = String(request?.params?.id ?? context?.params?.id ?? "").trim();
    if (!id) return { status: 400, jsonBody: { error: "INSTRUMENT_ID_REQUIRED" } };
    try {
      const detail = await getDetail(id);
      if (!detail) return { status: 404, jsonBody: { error: "INSTRUMENT_NOT_FOUND", id } };
      return { status: 200, jsonBody: detail };
    } catch (error) {
      context?.error?.("Instrument detail failed", error);
      return {
        status: 500,
        jsonBody: {
          error: "INSTRUMENT_DETAIL_UNAVAILABLE",
          message: String(error?.message ?? error)
        }
      };
    }
  };
}

app.http("instrumentDetail", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "instrument/{id}",
  handler: createInstrumentDetailHandler()
});
