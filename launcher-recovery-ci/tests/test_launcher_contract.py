from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
manifest = ROOT / "app/src/main/AndroidManifest.xml"
styles = ROOT / "app/src/main/res/values/styles.xml"
main = ROOT / "app/src/main/java/de/tobias/launcher/recovery/MainActivity.java"

ns = {"a": "http://schemas.android.com/apk/res/android"}
root = ET.parse(manifest).getroot()

# Privacy/security regression: the launcher must not request broad package visibility.
permission_names = {
    x.attrib["{http://schemas.android.com/apk/res/android}name"]
    for x in root.findall("uses-permission")
}
assert "android.permission.QUERY_ALL_PACKAGES" not in permission_names
query_intents = root.findall("./queries/intent")
assert any(
    any(a.attrib["{http://schemas.android.com/apk/res/android}name"] == "android.intent.action.MAIN" for a in qi.findall("action"))
    and any(c.attrib["{http://schemas.android.com/apk/res/android}name"] == "android.intent.category.LAUNCHER" for c in qi.findall("category"))
    for qi in query_intents
), "targeted package visibility query missing"

activity = root.find("./application/activity")
assert activity is not None
assert activity.attrib["{http://schemas.android.com/apk/res/android}exported"] == "true"
filters = activity.findall("intent-filter")
sets = []
for f in filters:
    actions = {x.attrib["{http://schemas.android.com/apk/res/android}name"] for x in f.findall("action")}
    cats = {x.attrib["{http://schemas.android.com/apk/res/android}name"] for x in f.findall("category")}
    sets.append((actions,cats))
assert any("android.intent.action.MAIN" in a and "android.intent.category.HOME" in c and "android.intent.category.DEFAULT" in c for a,c in sets)
assert any("android.intent.category.LAUNCHER" in c for _,c in sets)

style_text = styles.read_text(encoding="utf-8")
assert 'android:windowShowWallpaper">true<' in style_text

src = main.read_text(encoding="utf-8")
for required in [
    "RoleManager.ROLE_HOME",
    "Intent.CATEGORY_LAUNCHER",
    "queryIntentActivities",
    "startActivity(intent)",
    "buildHome()",
    "buildDrawer()",
    "Apps suchen",
]:
    assert required in src, required

print("PASS: least-privilege package visibility, HOME contract, default-role flow, wallpaper visibility, app discovery, app launch, home+drawer UI")

# Regression: favorites must own their storage, not keep a live ArrayList.subList view.
assert "private final ArrayList<AppEntry> favoriteApps = new ArrayList<>();" in src
assert "favoriteApps.addAll(allApps.subList(" in src
assert "favoriteApps = allApps.subList(" not in src

# Regression: favorites must be refreshed after reloadApps(), not kept as a one-time local snapshot.
assert "private final ArrayList<AppEntry> favoriteApps" in src
assert "private GridView favoritesGrid" in src
assert "private void refreshFavorites()" in src
on_resume = src.split("protected void onResume()", 1)[1].split("}", 1)[0]
assert "reloadApps();" in on_resume and "refreshFavorites();" in on_resume
assert "launch(favoriteApps.get(position))" in src
assert "List<AppEntry> top =" not in src
print("PASS: favorites refresh is bound to app reload and click handling uses refreshed entries")

# Regression: bringing the singleTask launcher HOME must dismiss transient drawer state.
assert "protected void onNewIntent(Intent intent)" in src
on_new_intent = src.split("protected void onNewIntent(Intent intent)", 1)[1].split("}", 1)[0]
assert "Intent.ACTION_MAIN" in on_new_intent
assert "Intent.CATEGORY_HOME" in on_new_intent
assert "drawerPanel.setVisibility(View.GONE);" in on_new_intent
print("PASS: HOME re-entry dismisses transient drawer state")

# Regression: reopening the drawer resets both visible query text and filtered data.
assert "private SearchView drawerSearch;" in src
show_drawer = src.split("private void showDrawer()", 1)[1].split("}", 1)[0]
assert 'drawerSearch.setQuery("", false);' in show_drawer
assert 'filterApps("");' in show_drawer
print("PASS: reopening the drawer resets search query and results together")
