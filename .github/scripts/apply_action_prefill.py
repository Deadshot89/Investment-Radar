from pathlib import Path

path = Path('android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt')
text = path.read_text()

replacements = [
('''    var moneyActionItemsById by remember { mutableStateOf<Map<String, InvestmentItem>>(emptyMap()) }
    var investmentDialogItem by remember { mutableStateOf<InvestmentItem?>(null) }
    var investmentDialogEntryType by remember { mutableStateOf("BUY") }
''', '''    var moneyActionItemsById by remember { mutableStateOf<Map<String, InvestmentItem>>(emptyMap()) }
    var pendingActionAmountEur by remember { mutableStateOf<Double?>(null) }
    var investmentDialogItem by remember { mutableStateOf<InvestmentItem?>(null) }
    var investmentDialogEntryType by remember { mutableStateOf("BUY") }
'''),
('''                        AppOverlay.PURCHASE_HISTORY -> {
                            investmentDialogItem = null
                            investmentDialogEntryType = "BUY"
                        }
''', '''                        AppOverlay.PURCHASE_HISTORY -> {
                            investmentDialogItem = null
                            investmentDialogEntryType = "BUY"
                            pendingActionAmountEur = null
                        }
'''),
('''                                            if (item != null) {
                                                investmentDialogEntryType = "BUY"
                                                investmentDialogItem = item
''', '''                                            if (item != null) {
                                                pendingActionAmountEur = action.displayAmountEur
                                                investmentDialogEntryType = "BUY"
                                                investmentDialogItem = item
'''),
('''                                            if (item != null) {
                                                investmentDialogEntryType = "SELL"
                                                investmentDialogItem = item
''', '''                                            if (item != null) {
                                                pendingActionAmountEur = action.displayAmountEur
                                                investmentDialogEntryType = "SELL"
                                                investmentDialogItem = item
'''),
('''                        if (item != null) {
                            investmentDialogEntryType = "BUY"
                            investmentDialogItem = item
''', '''                        if (item != null) {
                            pendingActionAmountEur = action.displayAmountEur
                            investmentDialogEntryType = "BUY"
                            investmentDialogItem = item
'''),
('''                        if (item != null) {
                            investmentDialogEntryType = "SELL"
                            investmentDialogItem = item
''', '''                        if (item != null) {
                            pendingActionAmountEur = action.displayAmountEur
                            investmentDialogEntryType = "SELL"
                            investmentDialogItem = item
'''),
('''            budgetHistory = budgetState.history,
            initialEntryType = investmentDialogEntryType,
            onDismiss = {
                investmentDialogItem = null
                investmentDialogEntryType = "BUY"
            },
''', '''            budgetHistory = budgetState.history,
            initialEntryType = investmentDialogEntryType,
            initialAmountEur = pendingActionAmountEur,
            onDismiss = {
                investmentDialogItem = null
                investmentDialogEntryType = "BUY"
                pendingActionAmountEur = null
            },
'''),
('''    budgetHistory: List<BudgetHistoryItem>,
    initialEntryType: String = "BUY",
    onDismiss: () -> Unit,
''', '''    budgetHistory: List<BudgetHistoryItem>,
    initialEntryType: String = "BUY",
    initialAmountEur: Double? = null,
    onDismiss: () -> Unit,
'''),
('''    var dateText by remember(item.id) { mutableStateOf(todayPurchaseDate()) }
    var amountText by remember(item.id) { mutableStateOf("") }
''', '''    var dateText by remember(item.id) { mutableStateOf(todayPurchaseDate()) }
    var amountText by remember(item.id, initialAmountEur) {
        mutableStateOf(initialAmountEur?.takeIf { it > 0.0 }?.let(::formatEditableNumber).orEmpty())
    }
''')
]

for index, (old, new) in enumerate(replacements, start=1):
    if old not in text:
        raise SystemExit(f'anchor {index} missing')
    text = text.replace(old, new, 1)

path.write_text(text)
