from pathlib import Path

path = Path('android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt')
text = path.read_text()


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count < 1:
        raise SystemExit(f'{label}: anchor missing')
    text = text.replace(old, new, 1)


def replace_all(old: str, new: str, expected_min: int, label: str):
    global text
    count = text.count(old)
    if count < expected_min:
        raise SystemExit(f'{label}: expected at least {expected_min}, got {count}')
    text = text.replace(old, new)

replace_once(
'''    var moneyActionItemsById by remember { mutableStateOf<Map<String, InvestmentItem>>(emptyMap()) }
    var pendingActionAmountEur by remember { mutableStateOf<Double?>(null) }
    var investmentDialogItem by remember { mutableStateOf<InvestmentItem?>(null) }
''',
'''    var moneyActionItemsById by remember { mutableStateOf<Map<String, InvestmentItem>>(emptyMap()) }
    var pendingActionAmountEur by remember { mutableStateOf<Double?>(null) }
    var pendingActionShares by remember { mutableStateOf<Double?>(null) }
    var pendingActionPrefillMessage by remember { mutableStateOf<String?>(null) }
    var investmentDialogItem by remember { mutableStateOf<InvestmentItem?>(null) }
''',
'state'
)

replace_once(
'''                        AppOverlay.PURCHASE_HISTORY -> {
                            investmentDialogItem = null
                            investmentDialogEntryType = "BUY"
                            pendingActionAmountEur = null
                        }
''',
'''                        AppOverlay.PURCHASE_HISTORY -> {
                            investmentDialogItem = null
                            investmentDialogEntryType = "BUY"
                            pendingActionAmountEur = null
                            pendingActionShares = null
                            pendingActionPrefillMessage = null
                        }
''',
'back cleanup'
)

buy_old = '''                                            if (item != null) {
                                                pendingActionAmountEur = action.displayAmountEur
                                                investmentDialogEntryType = "BUY"
                                                investmentDialogItem = item
'''
buy_new = '''                                            if (item != null) {
                                                val prefill = RecommendationTradePrefill.calculate(
                                                    type = action.type,
                                                    amountEur = action.displayAmountEur,
                                                    priceEur = euroComparablePrice(item),
                                                    heldShares = positions[item.id]?.shares
                                                )
                                                pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }
                                                pendingActionShares = prefill.shares
                                                pendingActionPrefillMessage = prefill.message
                                                investmentDialogEntryType = "BUY"
                                                investmentDialogItem = item
'''
replace_all(buy_old, buy_new, 2, 'buy action prefill')

sell_old = '''                                            if (item != null) {
                                                pendingActionAmountEur = action.displayAmountEur
                                                investmentDialogEntryType = "SELL"
                                                investmentDialogItem = item
'''
sell_new = '''                                            if (item != null) {
                                                val prefill = RecommendationTradePrefill.calculate(
                                                    type = action.type,
                                                    amountEur = action.displayAmountEur,
                                                    priceEur = euroComparablePrice(item),
                                                    heldShares = positions[item.id]?.shares
                                                )
                                                pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }
                                                pendingActionShares = prefill.shares
                                                pendingActionPrefillMessage = prefill.message
                                                investmentDialogEntryType = "SELL"
                                                investmentDialogItem = item
'''
replace_all(sell_old, sell_new, 2, 'sell action prefill')

# The BudgetDialog action block has a different indentation level.
buy2_old = '''                        if (item != null) {
                            pendingActionAmountEur = action.displayAmountEur
                            investmentDialogEntryType = "BUY"
                            investmentDialogItem = item
'''
buy2_new = '''                        if (item != null) {
                            val prefill = RecommendationTradePrefill.calculate(
                                type = action.type,
                                amountEur = action.displayAmountEur,
                                priceEur = euroComparablePrice(item),
                                heldShares = positions[item.id]?.shares
                            )
                            pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }
                            pendingActionShares = prefill.shares
                            pendingActionPrefillMessage = prefill.message
                            investmentDialogEntryType = "BUY"
                            investmentDialogItem = item
'''
replace_all(buy2_old, buy2_new, 1, 'budget buy prefill')

sell2_old = '''                        if (item != null) {
                            pendingActionAmountEur = action.displayAmountEur
                            investmentDialogEntryType = "SELL"
                            investmentDialogItem = item
'''
sell2_new = '''                        if (item != null) {
                            val prefill = RecommendationTradePrefill.calculate(
                                type = action.type,
                                amountEur = action.displayAmountEur,
                                priceEur = euroComparablePrice(item),
                                heldShares = positions[item.id]?.shares
                            )
                            pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }
                            pendingActionShares = prefill.shares
                            pendingActionPrefillMessage = prefill.message
                            investmentDialogEntryType = "SELL"
                            investmentDialogItem = item
'''
replace_all(sell2_old, sell2_new, 1, 'budget sell prefill')

replace_once(
'''            initialEntryType = investmentDialogEntryType,
            initialAmountEur = pendingActionAmountEur,
            onDismiss = {
                investmentDialogItem = null
                investmentDialogEntryType = "BUY"
                pendingActionAmountEur = null
            },
''',
'''            initialEntryType = investmentDialogEntryType,
            initialAmountEur = pendingActionAmountEur,
            initialShares = pendingActionShares,
            initialPrefillMessage = pendingActionPrefillMessage,
            onDismiss = {
                investmentDialogItem = null
                investmentDialogEntryType = "BUY"
                pendingActionAmountEur = null
                pendingActionShares = null
                pendingActionPrefillMessage = null
            },
''',
'dialog invocation'
)

replace_once(
'''    initialEntryType: String = "BUY",
    initialAmountEur: Double? = null,
    onDismiss: () -> Unit,
''',
'''    initialEntryType: String = "BUY",
    initialAmountEur: Double? = null,
    initialShares: Double? = null,
    initialPrefillMessage: String? = null,
    onDismiss: () -> Unit,
''',
'dialog signature'
)

replace_once(
'''    var feeText by remember(item.id) { mutableStateOf("") }
    var sharesText by remember(item.id) { mutableStateOf("") }
''',
'''    var feeText by remember(item.id) { mutableStateOf("") }
    var sharesText by remember(item.id, initialShares) {
        mutableStateOf(initialShares?.takeIf { it > 0.0 }?.let(::formatEditableNumber).orEmpty())
    }
''',
'shares state'
)

replace_once(
'''                Text(
                    "Aktueller Live-Kurs: ${euroComparablePrice(item)?.let(::formatMoney) ?: "–"} · nur Orientierung, kein automatischer Einstand",
                    color = RadarCyan,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )

                NeonPanel(accent = RadarPurple) {
''',
'''                Text(
                    "Aktueller Live-Kurs: ${euroComparablePrice(item)?.let(::formatMoney) ?: "–"} · nur Orientierung, kein automatischer Einstand",
                    color = RadarCyan,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                initialPrefillMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        "$message Bitte Betrag, Kurs und Anteile vor der Bestätigung prüfen.",
                        color = RadarYellow,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                NeonPanel(accent = RadarPurple) {
''',
'prefill message'
)

path.write_text(text)
