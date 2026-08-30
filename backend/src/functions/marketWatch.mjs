import { app } from "@azure/functions";
import { loadConfig } from "../lib/config.mjs";
import { loadQuotes } from "../lib/market.mjs";
import { evaluateSignals } from "../lib/signals.mjs";
import { loadState, saveState } from "../lib/state.mjs";
import { sendAlert } from "../lib/push.mjs";

app.timer("marketWatch", {
  schedule: "0 */15 * * * *", runOnStartup: false,
  handler: async (_timer, context) => {
    const config = await loadConfig();
    const quotes = await loadQuotes(config.items);
    const signals = evaluateSignals(config.items, quotes);
    const state = await loadState();
    for (const signal of signals) {
      if (state.fingerprints[signal.itemId] === signal.fingerprint) continue;
      const sent = await sendAlert(signal);
      context.log(`${signal.title} | push=${sent}`);
      state.fingerprints[signal.itemId] = signal.fingerprint;
      state.recent = [signal, ...state.recent.filter((a) => a.id !== signal.id)].slice(0, 50);
    }
    await saveState(state);
  }
});
