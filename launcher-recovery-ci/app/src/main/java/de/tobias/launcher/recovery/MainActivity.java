package de.tobias.launcher.recovery;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final ArrayList<AppEntry> allApps = new ArrayList<>();
    private final ArrayList<AppEntry> shownApps = new ArrayList<>();
    private final ArrayList<AppEntry> favoriteApps = new ArrayList<>();
    private GridView favoritesGrid;
    private GridView drawerGrid;
    private View drawerPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        reloadApps();
        setContentView(buildHome());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadApps();
        refreshFavorites();
        if (drawerGrid != null) drawerGrid.setAdapter(new AppAdapter(this, shownApps));
    }

    @Override
    public void onBackPressed() {
        if (drawerPanel != null && drawerPanel.getVisibility() == View.VISIBLE) {
            drawerPanel.setVisibility(View.GONE);
            return;
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
    }

    private View buildHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(18));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.argb(52, 0, 0, 0));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("HH:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(58);
        clock.setGravity(Gravity.CENTER);
        root.addView(clock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92)));

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d. MMMM");
        date.setFormat24Hour("EEEE, d. MMMM");
        date.setTextColor(Color.argb(220, 255, 255, 255));
        date.setTextSize(17);
        date.setGravity(Gravity.CENTER);
        root.addView(date, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        TextView spacer = new TextView(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        favoritesGrid = new GridView(this);
        favoritesGrid.setNumColumns(4);
        favoritesGrid.setVerticalSpacing(dp(12));
        favoritesGrid.setHorizontalSpacing(dp(8));
        refreshFavorites();
        favoritesGrid.setOnItemClickListener((parent, view, position, id) -> launch(favoriteApps.get(position)));
        root.addView(favoritesGrid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);

        Button homeRole = button("Als Standard-Launcher");
        homeRole.setOnClickListener(v -> requestHomeRole());
        dock.addView(homeRole, new LinearLayout.LayoutParams(0, dp(54), 1f));

        Button apps = button("Apps");
        apps.setOnClickListener(v -> showDrawer());
        LinearLayout.LayoutParams appsLp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        appsLp.setMargins(dp(10), 0, 0, 0);
        dock.addView(apps, appsLp);
        root.addView(dock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        drawerPanel = buildDrawer();
        drawerPanel.setVisibility(View.GONE);

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(root, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(drawerPanel, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return frame;
    }

    private View buildDrawer() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(28), dp(16), dp(16));
        panel.setBackgroundColor(Color.argb(242, 18, 20, 26));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        SearchView search = new SearchView(this);
        search.setQueryHint("Apps suchen");
        search.setIconifiedByDefault(false);
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextSubmit(String query) { filterApps(query); return true; }
            public boolean onQueryTextChange(String newText) { filterApps(newText); return true; }
        });
        top.addView(search, new LinearLayout.LayoutParams(0, dp(58), 1f));
        Button close = button("✕");
        close.setContentDescription("App-Übersicht schließen");
        close.setOnClickListener(v -> panel.setVisibility(View.GONE));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(58), dp(54));
        closeLp.setMargins(dp(8), 0, 0, 0);
        top.addView(close, closeLp);
        panel.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        drawerGrid = new GridView(this);
        drawerGrid.setNumColumns(4);
        drawerGrid.setVerticalSpacing(dp(14));
        drawerGrid.setHorizontalSpacing(dp(8));
        drawerGrid.setAdapter(new AppAdapter(this, shownApps));
        drawerGrid.setOnItemClickListener((parent, view, position, id) -> launch(shownApps.get(position)));
        panel.addView(drawerGrid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return panel;
    }

    private void showDrawer() {
        filterApps("");
        drawerPanel.setVisibility(View.VISIBLE);
    }

    private void reloadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(query, PackageManager.MATCH_ALL);
        allApps.clear();
        for (ResolveInfo info : infos) {
            if (info.activityInfo == null || getPackageName().equals(info.activityInfo.packageName)) continue;
            AppEntry e = new AppEntry();
            e.label = String.valueOf(info.loadLabel(pm));
            e.packageName = info.activityInfo.packageName;
            e.className = info.activityInfo.name;
            e.icon = info.loadIcon(pm);
            allApps.add(e);
        }
        Collections.sort(allApps, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        shownApps.clear();
        shownApps.addAll(allApps);
    }

    private void refreshFavorites() {
        favoriteApps.clear();
        favoriteApps.addAll(allApps.subList(0, Math.min(8, allApps.size())));
        if (favoritesGrid != null) favoritesGrid.setAdapter(new AppAdapter(this, favoriteApps));
    }

    private void filterApps(String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        shownApps.clear();
        for (AppEntry e : allApps) {
            if (needle.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(needle)) shownApps.add(e);
        }
        if (drawerGrid != null) drawerGrid.setAdapter(new AppAdapter(this, shownApps));
    }

    private void launch(AppEntry app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(app.packageName, app.className);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "App konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestHomeRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    Toast.makeText(this, "Launcher ist bereits Standard", Toast.LENGTH_SHORT).show();
                } else {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1001);
                }
                return;
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.argb(150, 30, 34, 44));
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    static final class AppEntry {
        String label;
        String packageName;
        String className;
        android.graphics.drawable.Drawable icon;
    }

    static final class AppAdapter extends BaseAdapter {
        private final Context context;
        private final List<AppEntry> apps;

        AppAdapter(Context context, List<AppEntry> apps) {
            this.context = context;
            this.apps = apps;
        }

        public int getCount() { return apps.size(); }
        public Object getItem(int p) { return apps.get(p); }
        public long getItemId(int p) { return p; }

        public View getView(int p, View convertView, ViewGroup parent) {
            AppEntry app = apps.get(p);
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(4, 10, 4, 8);

            ImageView icon = new ImageView(context);
            icon.setImageDrawable(app.icon);
            cell.addView(icon, new LinearLayout.LayoutParams(64, 64));

            TextView label = new TextView(context);
            label.setText(app.label);
            label.setTextColor(Color.WHITE);
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            cell.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return cell;
        }
    }
}
