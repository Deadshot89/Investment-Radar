import { app } from "@azure/functions";
import { buildAnalysisSnapshot } from "../lib/dashboard.mjs";
import { evaluateSignals } from "../lib/signals.mjs";
import { saveState } from "../lib/state.mjs";
import { sendAlert } from "../lib/push.mjs";
import { mergeRecentAlerts, updateAnalysisMemory } from "../lib/marketWatchState.mjs";

app.timer("marketWatch", {
  schedule: "0 */15 * * * *",
  runOnStartup: false,
  handler: async (_timer, context) => {
    const snapshot = await buildAnalysisSnapshot({ refreshAnalysis: true });
    const state = snapshot.state;
    const signals = evaluateSignals(snapshot.items, snapshot.quotes, {
      previousScores: state.previousScores,
      previousRecommendations: state.previousRecommendations,
      // Backend emits held-only signals to asset-specific holding topics.
      // Only devices subscribed because that asset is held receive them.
      heldIds: new Set(snapshot.items.map((item) => item.id))
    });
    const previouslyActive = new Set(state.activeFingerprints ?? []);
    const currentlyActive = new Set(signals.map((signal) => signal.fingerprint));
    const newlyTriggered = [];

    for (const signal of signals) {
      if (previouslyActive.has(signal.fingerprint)) continue;
      const sent = await sendAlert(signal);
      context.log(`${signal.title} | push=${sent}`);
      newlyTriggered.push(signal);
    }

    let nextState = updateAnalysisMemory(state, snapshot.items);
    nextState = {
      ...nextState,
      activeFingerprints: [...currentlyActive],
      recent: mergeRecentAlerts(newlyTriggered, state.recent)
    };
    await saveState(nextState);
  }
});
