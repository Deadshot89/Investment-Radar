from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
manifest = ROOT / "app/src/main/AndroidManifest.xml"
styles = ROOT / "app/src/main/res/values/styles.xml"
main = ROOT / "app/src/main/java/de/tobias/launcher/recovery/MainActivity.java"

root = ET.parse(manifest).getroot()
android_name = "{http://schemas.android.com/apk/res/android}name"

permission_names = {x.attrib[android_name] for x in root.findall("uses-permission")}
assert "android.permission.QUERY_ALL_PACKAGES" not in permission_names

query_intents = root.findall("./queries/intent")
assert any(
    any(a.attrib[android_name] == "android.intent.action.MAIN" for a in qi.findall("action"))
    and any(c.attrib[android_name] == "android.intent.category.LAUNCHER" for c in qi.findall("category"))
    for qi in query_intents
), "targeted package visibility query missing"

activity = root.find("./application/activity")
assert activity is not None
assert activity.attrib["{http://schemas.android.com/apk/res/android}exported"] == "true"

filters = activity.findall("intent-filter")
sets = []
for f in filters:
    actions = {x.attrib[android_name] for x in f.findall("action")}
    cats = {x.attrib[android_name] for x in f.findall("category")}
    sets.append((actions, cats))

assert any(
    "android.intent.action.MAIN" in actions
    and "android.intent.category.HOME" in cats
    and "android.intent.category.DEFAULT" in cats
    for actions, cats in sets
)
assert any("android.intent.category.LAUNCHER" in cats for _, cats in sets)

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

assert "private final ArrayList<AppEntry> favoriteApps = new ArrayList<>();" in src
assert "favoriteApps.addAll(allApps.subList(" in src
assert "favoriteApps = allApps.subList(" not in src
assert "private GridView favoritesGrid" in src
assert "private void refreshFavorites()" in src
on_resume = src.split("protected void onResume()", 1)[1].split("}", 1)[0]
assert "reloadApps();" in on_resume and "refreshFavorites();" in on_resume
assert "launch(favoriteApps.get(position))" in src
assert "List<AppEntry> top =" not in src

print("PASS: launcher HOME contract, least-privilege visibility, app launch, wallpaper and favorites reload regressions")
