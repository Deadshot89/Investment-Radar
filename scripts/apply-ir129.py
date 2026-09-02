from pathlib import Path

main_path = Path("android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt")
build_path = Path("android/app/build.gradle.kts")
hotfix_path = Path("HOTFIX_1.1.29.md")

text = main_path.read_text(encoding="utf-8")

old_function = '''private fun openInvestment(context: android.content.Context, item: InvestmentItem) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Trade Republic ISIN", item.isin.trim()))

    val tradeRepublicIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setPackage("de.traderepublic.app")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(tradeRepublicIntent)
        android.widget.Toast.makeText(
            context,
            "ISIN kopiert – in Trade Republic in die Suche einfügen",
            android.widget.Toast.LENGTH_LONG
        ).show()
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(
            context,
            "Trade Republic konnte nicht geöffnet werden. ISIN wurde kopiert.",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}
'''

new_function = '''private const val TRADE_REPUBLIC_STOCK_BASE_URL = "https://app.traderepublic.com/stocks/"
private const val TRADE_REPUBLIC_BROWSE_URL = "https://app.traderepublic.com/browse/stock"

private fun openInvestment(context: android.content.Context, item: InvestmentItem) {
    val isin = item.isin.trim().uppercase()
    val fallbackSearch = isin.ifBlank { item.ticker.trim().uppercase() }
    if (fallbackSearch.isNotBlank()) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Trade Republic ISIN", fallbackSearch))
    }

    val hasDirectIsin = isTradeRepublicIsin(isin)
    val targetUrl = if (hasDirectIsin) {
        "$TRADE_REPUBLIC_STOCK_BASE_URL${Uri.encode(isin)}"
    } else {
        TRADE_REPUBLIC_BROWSE_URL
    }

    val targetIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(targetIntent)
        android.widget.Toast.makeText(
            context,
            if (hasDirectIsin) "Trade Republic öffnet ${item.ticker} direkt · ISIN zusätzlich kopiert" else "Trade Republic geöffnet · Suchwert kopiert",
            android.widget.Toast.LENGTH_LONG
        ).show()
    } catch (_: android.content.ActivityNotFoundException) {
        openTradeRepublicFallback(context, fallbackSearch)
    }
}

private fun isTradeRepublicIsin(value: String): Boolean =
    value.matches(Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]"))

private fun openTradeRepublicFallback(context: android.content.Context, fallbackSearch: String) {
    val browseIntent = Intent(Intent.ACTION_VIEW, Uri.parse(TRADE_REPUBLIC_BROWSE_URL)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(browseIntent)
        android.widget.Toast.makeText(
            context,
            if (fallbackSearch.isNotBlank()) "Trade-Republic-Aktienübersicht geöffnet · Suchwert kopiert" else "Trade-Republic-Aktienübersicht geöffnet",
            android.widget.Toast.LENGTH_LONG
        ).show()
    } catch (_: android.content.ActivityNotFoundException) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage("de.traderepublic.app")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(launcherIntent)
            android.widget.Toast.makeText(context, "Trade Republic geöffnet · Suchwert kopiert", android.widget.Toast.LENGTH_LONG).show()
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "Trade Republic konnte nicht geöffnet werden. Suchwert wurde kopiert.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
'''

if 'private const val TRADE_REPUBLIC_STOCK_BASE_URL = "https://app.traderepublic.com/stocks/"' not in text:
    if old_function not in text:
        raise SystemExit("old openInvestment function not found")
    text = text.replace(old_function, new_function, 1)

    if text.count('Text("Wertpapier öffnen")') != 2:
        raise SystemExit(f"expected two Wertpapier öffnen labels, found {text.count('Text(\"Wertpapier öffnen\")')}")
    text = text.replace('Text("Wertpapier öffnen")', 'Text("Trade Republic öffnen")')
    text = text.replace('Text("Trade Republic", fontWeight = FontWeight.Bold)', 'Text("Trade Republic öffnen", fontWeight = FontWeight.Bold)', 1)
    main_path.write_text(text, encoding="utf-8")
else:
    if text.count('Text("Trade Republic öffnen")') < 2:
        raise SystemExit("Trade Republic direct-link source is only partially applied")

build = build_path.read_text(encoding="utf-8")
if 'versionCode = 29' in build and 'versionName = "1.1.28"' in build:
    build = build.replace('versionCode = 29', 'versionCode = 30', 1)
    build = build.replace('versionName = "1.1.28"', 'versionName = "1.1.29"', 1)
    build = build.replace('// Build trigger: Investment Radar 1.1.28', '// Build trigger: Investment Radar 1.1.29', 1)
    build_path.write_text(build, encoding="utf-8")
elif 'versionCode = 30' not in build or 'versionName = "1.1.29"' not in build:
    raise SystemExit("unexpected Android version while applying 1.1.29")

hotfix_path.write_text('''# Investment Radar 1.1.29\n\n- Trade-Republic-Aktien werden über die ISIN direkt in der Trade-Republic-Web-App geöffnet\n- Android kann den Link an die Trade-Republic-App oder an den Browser übergeben\n- ISIN bzw. Ticker wird zusätzlich in die Zwischenablage kopiert\n- Fallback auf das Trade-Republic-Aktienuniversum, wenn keine gültige ISIN vorhanden ist\n- klare Buttons „Trade Republic öffnen“ in Empfehlung, Radar und Portfolio\n- Android 1.1.29 / versionCode 30\n- Backend bleibt 1.1.27\n''', encoding="utf-8")

print("Investment Radar 1.1.29 Trade Republic links are applied")
