# Investment Radar 1.2.0

Android **1.2.0** (`versionCode 31`) · Backend **1.2.0** · Analysis Model V2

## Analyse V2

- Neues datengetriebenes Scoring mit **Qualität, Bewertung, Wachstum, Momentum und Risiko**.
- Objektive Einstufung in **BUY / WATCH / NO_BUY / REVIEW** statt statisch vorkonfigurierter Empfehlungen.
- Investment-Universum auf rund **40 kuratierte Aktien und ETFs** erweitert.
- Persönliche Monatsbudget-Verteilung berücksichtigt Score, Risiko, Depotgewicht und Konzentration.
- Bei sehr hoher Konzentration werden zusätzliche Käufe reduziert oder blockiert und in der App begründet.
- REVIEW-Logik reagiert unter anderem auf Score-Einbruch, Trendbruch, Schwellenwerte und ausreichend belegte fundamentale Verschlechterung.

## Radar-Suche & Filter

- Suche nach **Name, Ticker, ISIN und Typ**.
- Kombinierbare Filter für Empfehlung, Bestand, Watchlist, Datenqualität und Risiko.
- Sortierung unter anderem nach Score, persönlicher Allokation, 6-Monats-Momentum, Tagesbewegung und Name.
- Fehlende Werte bleiben bei Sortierung und Anzeige als nicht verfügbar erkennbar und werden nicht künstlich zu `0` gemacht.

## Wertpapier-Details

- Gemeinsame Detailansicht aus Radar und Portfolio.
- Anzeige der fünf Teil-Scores, Gesamtscore, Datenabdeckung, Momentum, Fundamentaldaten und Begründungen, soweit verfügbar.
- Kuratierte und lokal angelegte eigene Werte werden sauber unterschieden.
- Aktionen für Watchlist, Portfolio und Trade Republic bleiben direkt aus dem Wertpapier-Kontext erreichbar.

## Portfolio-Dashboard V2

- Neues Depot-Dashboard mit **Depotwert bzw. Teilwert, Einstand, Gewinn/Verlust und größter Position**.
- Fehlende aktuelle Kurse werden sichtbar gekennzeichnet; eine unvollständige Kursbasis erzeugt keine irreführende Gesamtperformance.
- Positionen zeigen Gewichtung, Score/Empfehlung, persönliche Monatsallokation und Konzentrationshinweise.
- Eigene Werte bleiben Bestandteil der lokalen Konzentrationsberechnung, sofern ein vergleichbarer EUR-Wert vorliegt.

## Alarmcenter & Direktnavigation

- Alarmcenter mit **gelesen/ungelesen, Filtern, einzeln löschen, alle gelesen und Speicher leeren**.
- Lokale Alarm-Einstellungen steuern BUY-, REVIEW-, Sell/Review- und Schwellenwert-Benachrichtigungen.
- Firebase Data-Messages werden vor der sichtbaren Benachrichtigung gegen die lokalen Präferenzen geprüft.
- Gelöschte Backend-Alarme werden per lokalem Tombstone nicht sofort wieder eingespielt.
- Alarm-Tap öffnet direkt das zugehörige Wertpapier. Ist ein alter Wert nicht mehr im Radar vorhanden, erscheint eine sichere Meldung statt einer leeren Navigation.

## Datenqualität & Quellen

- Fundamentaldaten und historische Kurse werden gecacht; Dashboard-Aufrufe lösen nicht bei jeder Anzeige teure Provider-Abfragen aus.
- Momentum wird über **1D, 1M, 3M, 6M und 12M** ausgewertet.
- Datenabdeckung und Aktualität werden getrennt berücksichtigt.
- Fehlende Fundamentaldaten bleiben `null`/nicht verfügbar und werden **nicht erfunden**.
- Bei Provider-Problemen darf nur ein zulässiger letzter gültiger Cache-Stand mit erkennbarer Datenqualität verwendet werden.

## Trade Republic & Updates

- Trade-Republic-Navigation nutzt den stabilen HTTPS-Handoff auf die Wertpapierseite und einen sicheren Browse-/Clipboard-Fallback.
- Manuelle Updateprüfung meldet jetzt ausdrücklich, wenn bereits die aktuelle Version installiert ist, und zeigt einen verständlichen Fehler bei fehlgeschlagener Prüfung.
- Der 1.1.28-Resume-Mechanismus für die Android-Berechtigung „Aus dieser Quelle zulassen“ bleibt erhalten.
- Android 1.2.0 wird auf `main` erst veröffentlicht, wenn `/api/health` das Live-Backend **1.2.0** bestätigt.

## Sicherheit / Datenschutz

- **Keine automatische Orderausführung.**
- **Keine automatischen Verkäufe.** REVIEW ist eine Prüfempfehlung und kein Verkaufsauftrag.
- Kein Brokerage-Login und kein Trade-Republic-Scraping.
- Portfolio, Käufe, Verkäufe, Watchlist, eigene Werte und persönliche Alarmzustände bleiben **lokal auf dem Gerät**.
- Feature-Branches deployen weder das Azure-Produktivbackend noch veröffentlichen sie eine Update-APK.
- Die Release-APK wird dauerhaft signiert und die Signatur vor der Veröffentlichung geprüft.
