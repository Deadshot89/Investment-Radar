const TR_WS_URL = "wss://api.traderepublic.com";
const TR_WS_CONNECT_VERSION = "34";
const DEFAULT_EXCHANGE = "LSX";
const DAILY_RESOLUTION_MS = 86_400_000;
const BATCH_SIZE = 30;

export async function loadTradeRepublicQuotes(items, { requestBatch = requestTradeRepublicBatch } = {}) {
  const eligible = items.filter(isTradeRepublicInstrument);
  const result = new Map();
  if (eligible.length === 0) return result;

  const primary = await requestInChunks(eligible.map((item) => ({
    key: item.id,
    payload: { type: "ticker", id: `${item.isin}.${preferredExchange(item)}` }
  })), requestBatch);

  const failed = [];
  for (const item of eligible) {
    const response = primary.get(item.id);
    const quote = normalizeTradeRepublicQuote(response?.data, item, preferredExchange(item));
    if (quote.price != null) result.set(item.id, quote);
    else failed.push(item);
  }

  if (failed.length === 0) return result;
  const metadata = await requestInChunks(failed.map((item) => ({
    key: item.id,
    payload: { type: "instrument", id: item.isin }
  })), requestBatch);

  const retryRequests = [];
  const retryExchange = new Map();
  for (const item of failed) {
    const exchange = firstExchange(metadata.get(item.id)?.data);
    if (!exchange || exchange === preferredExchange(item)) {
      result.set(item.id, emptyTradeRepublicQuote(
        item,
        mergeErrors(primary.get(item.id)?.error, metadata.get(item.id)?.error, "Kein alternativer TR-Handelsplatz verfügbar")
      ));
      continue;
    }
    retryExchange.set(item.id, exchange);
    retryRequests.push({ key: item.id, payload: { type: "ticker", id: `${item.isin}.${exchange}` } });
  }

  const retried = retryRequests.length ? await requestInChunks(retryRequests, requestBatch) : new Map();
  for (const item of failed) {
    if (!retryExchange.has(item.id)) continue;
    const exchange = retryExchange.get(item.id);
    const response = retried.get(item.id);
    const quote = normalizeTradeRepublicQuote(response?.data, item, exchange);
    result.set(
      item.id,
      quote.price != null
        ? quote
        : emptyTradeRepublicQuote(item, mergeErrors(primary.get(item.id)?.error, response?.error, "Trade-Republic-Kurs nicht verfügbar"))
    );
  }
  return result;
}

export async function loadTradeRepublicHistories(items, { requestBatch = requestTradeRepublicBatch } = {}) {
  const eligible = items.filter(isTradeRepublicInstrument);
  const result = new Map();
  if (eligible.length === 0) return result;

  const primary = await requestInChunks(eligible.map((item) => ({
    key: item.id,
    payload: {
      type: "aggregateHistoryLight",
      id: `${item.isin}.${preferredExchange(item)}`,
      range: "1y",
      resolution: DAILY_RESOLUTION_MS
    }
  })), requestBatch);

  const failed = [];
  for (const item of eligible) {
    const loaded = normalizeTradeRepublicHistory(primary.get(item.id)?.data);
    if (loaded.points.length > 1) result.set(item.id, loaded);
    else failed.push(item);
  }

  if (failed.length === 0) return result;
  const metadata = await requestInChunks(failed.map((item) => ({
    key: item.id,
    payload: { type: "instrument", id: item.isin }
  })), requestBatch);

  const retryRequests = [];
  const retryExchange = new Map();
  for (const item of failed) {
    const exchange = firstExchange(metadata.get(item.id)?.data);
    if (!exchange || exchange === preferredExchange(item)) {
      result.set(item.id, {
        points: [],
        source: "Trade Republic",
        error: mergeErrors(primary.get(item.id)?.error, metadata.get(item.id)?.error, "Keine TR-Historie verfügbar")
      });
      continue;
    }
    retryExchange.set(item.id, exchange);
    retryRequests.push({
      key: item.id,
      payload: {
        type: "aggregateHistoryLight",
        id: `${item.isin}.${exchange}`,
        range: "1y",
        resolution: DAILY_RESOLUTION_MS
      }
    });
  }

  const retried = retryRequests.length ? await requestInChunks(retryRequests, requestBatch) : new Map();
  for (const item of failed) {
    if (!retryExchange.has(item.id)) continue;
    const response = retried.get(item.id);
    const loaded = normalizeTradeRepublicHistory(response?.data);
    result.set(item.id, loaded.points.length > 1 ? loaded : {
      points: [],
      source: "Trade Republic",
      error: mergeErrors(primary.get(item.id)?.error, response?.error, "Keine TR-Historie verfügbar")
    });
  }
  return result;
}

async function requestInChunks(requests, requestBatch, batchSize = BATCH_SIZE) {
  const result = new Map();
  for (let offset = 0; offset < requests.length; offset += batchSize) {
    const chunk = requests.slice(offset, offset + batchSize);
    const loaded = await requestBatch(chunk);
    for (const request of chunk) {
      result.set(request.key, loaded.get(request.key) ?? { data: null, error: "Keine Trade-Republic-Antwort" });
    }
  }
  return result;
}

export async function requestTradeRepublicBatch(requests, {
  WebSocketImpl = globalThis.WebSocket,
  timeoutMs = 15_000
} = {}) {
  const output = new Map();
  if (!Array.isArray(requests) || requests.length === 0) return output;
  if (typeof WebSocketImpl !== "function") {
    for (const request of requests) {
      output.set(request.key, { data: null, error: "Trade-Republic-WebSocket nicht verfügbar" });
    }
    return output;
  }

  return new Promise((resolve) => {
    const socket = new WebSocketImpl(TR_WS_URL);
    const bySubscription = new Map();
    let connected = false;
    let settled = false;
    const timer = setTimeout(() => finish("Trade-Republic-WebSocket Timeout"), timeoutMs);

    function finish(timeoutError = null) {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      for (const request of requests) {
        if (!output.has(request.key)) {
          output.set(request.key, { data: null, error: timeoutError || "Keine Trade-Republic-Antwort" });
        }
      }
      try { socket.close(); } catch {}
      resolve(output);
    }

    function maybeFinish() {
      if (output.size >= requests.length) finish();
    }

    socket.addEventListener("open", () => {
      socket.send(`connect ${TR_WS_CONNECT_VERSION} ${JSON.stringify({ locale: "en" })}`);
    });
    socket.addEventListener("error", () => finish("Trade-Republic-WebSocket nicht erreichbar"));
    socket.addEventListener("close", () => {
      if (!settled) finish("Trade-Republic-WebSocket vor vollständiger Antwort geschlossen");
    });
    socket.addEventListener("message", (event) => {
      const text = typeof event.data === "string" ? event.data : String(event.data ?? "");
      if (!connected && /connected/i.test(text)) {
        connected = true;
        requests.forEach((request, index) => {
          const subscriptionId = index + 1;
          bySubscription.set(subscriptionId, request);
          socket.send(`sub ${subscriptionId} ${JSON.stringify(request.payload)}`);
        });
        return;
      }

      const match = text.trim().match(/^(\d+)\s+([ADE])\s*(.*)$/s);
      if (!match) return;
      const subscriptionId = Number(match[1]);
      const marker = match[2];
      const body = match[3] ?? "";
      const request = bySubscription.get(subscriptionId);
      if (!request || output.has(request.key)) return;

      if (marker === "E") {
        output.set(request.key, { data: null, error: body.trim() || "Trade-Republic-Providerfehler" });
        maybeFinish();
        return;
      }
      if (marker !== "A" && marker !== "D") return;

      try {
        output.set(request.key, { data: body.trim() ? JSON.parse(body) : {}, error: null });
      } catch {
        output.set(request.key, { data: null, error: "Trade-Republic-Antwort ist kein JSON" });
      }
      try { socket.send(`unsub ${subscriptionId}`); } catch {}
      maybeFinish();
    });
  });
}

export function normalizeTradeRepublicQuote(data, item, exchange = DEFAULT_EXCHANGE) {
  const price = finiteNumber(data?.last?.price ?? data?.ask?.price ?? data?.bid?.price);
  const previous = finiteNumber(data?.pre?.price);
  const percentChange = price != null && previous != null && previous > 0
    ? ((price - previous) / previous) * 100
    : null;
  return {
    symbol: `${item?.isin ?? item?.ticker ?? ""}.${exchange}`,
    name: item?.name,
    price,
    currency: "EUR",
    percentChange,
    marketOpen: null,
    timestamp: finiteNumber(data?.last?.time) ?? finiteNumber(data?.ask?.time) ?? finiteNumber(data?.bid?.time) ?? undefined,
    source: "Trade Republic",
    delayed: String(data?.qualityId ?? "").toLowerCase() !== "realtime",
    error: price == null ? "Trade-Republic-Kurs leer" : null
  };
}

export function normalizeTradeRepublicHistory(data) {
  const points = (Array.isArray(data?.aggregates) ? data.aggregates : [])
    .map((value) => ({
      time: finiteNumber(value?.time),
      close: finiteNumber(value?.close ?? value?.adjValue)
    }))
    .filter((point) => point.time != null && point.close != null && point.close > 0)
    .sort((a, b) => a.time - b.time);
  return {
    points,
    source: "Trade Republic",
    error: points.length > 1 ? null : "Trade-Republic-Historie leer"
  };
}

function isTradeRepublicInstrument(item) {
  return item?.tradeRepublicEligible === true &&
    /^[A-Z]{2}[A-Z0-9]{10}$/.test(String(item?.isin ?? "").toUpperCase());
}

function preferredExchange(item) {
  return String(item?.tradeRepublicExchange ?? DEFAULT_EXCHANGE).trim().toUpperCase() || DEFAULT_EXCHANGE;
}

function firstExchange(instrument) {
  const active = Array.isArray(instrument?.exchanges)
    ? instrument.exchanges.find((entry) => entry?.active !== false && entry?.slug)
    : null;
  const ids = Array.isArray(instrument?.exchangeIds) ? instrument.exchangeIds : [];
  return String(active?.slug ?? ids[0] ?? "").trim().toUpperCase();
}

function emptyTradeRepublicQuote(item, error) {
  return {
    symbol: String(item?.isin ?? item?.ticker ?? ""),
    name: item?.name,
    price: null,
    currency: "EUR",
    percentChange: null,
    marketOpen: null,
    source: "Trade Republic",
    delayed: false,
    error
  };
}

function finiteNumber(value) {
  if (value == null || (typeof value === "string" && !value.trim())) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function mergeErrors(...errors) {
  return [...new Set(errors.filter(Boolean).map(String))].join(" · ");
}
