import test from "node:test";
import assert from "node:assert/strict";
import { createInstrumentDetailHandler } from "../src/functions/instrumentDetail.mjs";

function context() {
  return {
    errors: [],
    error(...args) { this.errors.push(args); }
  };
}

test("instrument detail reads Azure route id from request.params", async () => {
  const seen = [];
  const handler = createInstrumentDetailHandler({
    getDetail: async (id) => {
      seen.push(id);
      return { id, name: "3M" };
    }
  });

  const response = await handler({ params: { id: "tr-us88579y1010" } }, context());

  assert.deepEqual(seen, ["tr-us88579y1010"]);
  assert.equal(response.status, 200);
  assert.equal(response.jsonBody.id, "tr-us88579y1010");
});

test("instrument detail keeps context.params as a compatibility fallback", async () => {
  const handler = createInstrumentDetailHandler({
    getDetail: async (id) => ({ id })
  });

  const response = await handler({}, { params: { id: "legacy-id" } });

  assert.equal(response.status, 200);
  assert.equal(response.jsonBody.id, "legacy-id");
});

test("instrument detail rejects a missing route id", async () => {
  const handler = createInstrumentDetailHandler({
    getDetail: async () => { throw new Error("must not run"); }
  });

  const response = await handler({ params: {} }, context());

  assert.equal(response.status, 400);
  assert.equal(response.jsonBody.error, "INSTRUMENT_ID_REQUIRED");
});

test("instrument detail returns 404 when the instrument does not exist", async () => {
  const handler = createInstrumentDetailHandler({
    getDetail: async () => null
  });

  const response = await handler({ params: { id: "missing" } }, context());

  assert.equal(response.status, 404);
  assert.equal(response.jsonBody.id, "missing");
});
