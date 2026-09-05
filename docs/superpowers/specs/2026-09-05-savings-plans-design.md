# Savings Plans Design

## Goal
Investment Radar gets a separate savings-plan subsystem. Planned savings plans never change the real portfolio until the user explicitly confirms that a scheduled execution actually happened.

## Confirmed behavior
- Savings plans are stored separately from actual portfolio positions and transactions.
- On a due date the app creates a pending execution and notifies the user.
- The user confirms only whether the execution happened; no manual price entry is required.
- On confirmation the app uses the best available current market price for that instrument, calculates purchased shares from the scheduled EUR amount, and records a real purchase transaction.
- The purchase updates shares, cost basis, invested amount, portfolio value inputs, and profit/loss through the existing portfolio calculation path.
- Rejecting/skipping a due execution leaves the portfolio unchanged and advances the plan to its next scheduled date.
- A scheduled execution can be confirmed only once. The stored execution record includes scheduled date, confirmation timestamp, amount, market price used, and calculated shares.
- Existing portfolio data remains the source of truth for actual holdings.

## Initial Trade Republic plans
The initial import reflects the Trade Republic screenshot supplied on 2026-09-05:
- Meta Platforms (A): EUR 10, twice monthly.
- Samsung (GDR): EUR 10, twice monthly.
- Private Equity: EUR 5, twice monthly.
- Private Equity: EUR 5, twice monthly as a separate plan.
- Microsoft: EUR 10, monthly.

The two Private Equity entries remain distinct because Trade Republic displays them as two separate plans. Their exact underlying instrument must not be guessed; until an unambiguous instrument identifier is available, the plans may be represented as separate named plans but must not post a purchase into an unrelated portfolio asset.

## Scheduling
A plan contains an amount, frequency, next due date and enabled state. Twice-monthly plans retain two execution opportunities per month; monthly plans retain one. Exact Trade Republic execution dates should be editable because the supplied overview establishes frequency but not reliable calendar dates.

## Notifications and UI
The portfolio area gets a Savings Plans entry showing active plans, amount/frequency, next due date and pending confirmation state. Due executions also appear through the existing notification/alert mechanism. The confirmation action presents the plan and amount, then offers `Ausgeführt` and `Nicht ausgeführt`.

## Price and failure handling
Confirmation first requests the current quote through the app's existing market-data path. If no valid positive price is available, the execution remains pending and no portfolio transaction is created. The user is told that confirmation could not be completed because a current price is unavailable. This prevents fabricated shares or a zero-price purchase.

## Data integrity
Posting is atomic from the feature's perspective: mark an execution confirmed only after the purchase has been persisted successfully. Use a stable execution id derived from plan id plus scheduled date to make repeated taps idempotent. Never alter imported/manual historical transactions when a plan is edited or deleted.

## Testing
Add unit tests for due-date generation, monthly and twice-monthly recurrence, idempotent confirmation, skipped execution, quote failure and purchase calculation. Add source/UI contract coverage proving that planned amounts do not enter portfolio totals before confirmation and that due notifications route to the confirmation flow. Run the complete Android contract suite, JVM suite and signed APK workflow before release.

## Release
This is an Android feature release after 2.1.2, so the Android version must be incremented before publication. Backend version remains unchanged unless implementation reveals a required backend contract change.