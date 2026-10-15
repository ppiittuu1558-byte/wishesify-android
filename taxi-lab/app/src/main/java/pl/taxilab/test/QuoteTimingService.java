package pl.taxilab.test;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Pattern;

public final class QuoteTimingService extends AccessibilityService {
    private static final String PREFS = "taxi_lab_time_probe";
    private static final String UBER = "com.ubercab";
    private static final String BOLT = "ee.mtakso.client";
    private static final String FREE = "taxi.android.client";
    private static final long LIMIT_MS = 120_000L;
    private static final Pattern PRICE = Pattern.compile(
        "(?iu)(?:\\b[0-9]{1,4}(?:[,.][0-9]{1,2})?\\s*(?:zł|PLN)\\b|\\bPLN\\s*[0-9]{1,4}(?:[,.][0-9]{1,2})?)");
    private static final Pattern MINUTES = Pattern.compile(
        "(?iu)\\b(?:za\\s*)?[0-9]{1,2}\\s*(?:min\\.?|minut(?:a|y|ę)?)\\b");

    private long lastScan = 0L;

    public static void begin(Context ctx, String pkg) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString("pkg", pkg).putString("state", "running")
            .putLong("t0", SystemClock.elapsedRealtime())
            .putLong("foreground", -1).putLong("fare", -1).putLong("minutes", -1)
            .apply();
    }

    public static boolean permissionEnabled(Context ctx) {
        int global = Settings.Secure.getInt(ctx.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        String enabled = Settings.Secure.getString(ctx.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return global == 1 && enabled != null &&
                enabled.contains(ctx.getPackageName() + "/pl.taxilab.test.QuoteTimingService");
    }

    public static String report(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String state = sp.getString("state", "none");
        if("none".equals(state)) return "Nie ma jeszcze pomiaru.";
        String pkg=sp.getString("pkg","");
        String name = UBER.equals(pkg) ? "Uber" : BOLT.equals(pkg) ? "Bolt" : FREE.equals(pkg) ? "FREENOW" : "?";
        long t0=sp.getLong("t0",0);
        long focus=sp.getLong("foreground",-1);
        long fare=sp.getLong("fare",-1);
        long min=sp.getLong("minutes",-1);
        StringBuilder s = new StringBuilder(name);
        s.append(" — ").append("complete".equals(state)?"wykryto kandydatów":
          "expired".equals(state)?"przekroczono 120 sekund":"w trakcie");
        s.append("\nOtwarcie interfejsu: ").append(fmt(focus,t0));
        s.append("\nPierwszy tekst przypominający cenę: ").append(fmt(fare,t0));
        s.append("\nPierwszy tekst w minutach: ").append(fmt(min,t0));
        if(fare>0 && min>0) {
            long both=Math.max(fare,min)-t0;
            s.append("\nObie informacje po: ").append(String.format(Locale.forLanguageTag("pl-PL"),"%.2f s",both/1000.0));
            s.append(both<=15000?" [cel 15 s: TAK]":" [cel 15 s: NIE]");
        }
        s.append("\nUwaga: wykrywanie automatyczne może pomylić cenę kursu z promocją, a czas przyjazdu z długością przejazdu. Wymaga weryfikacji na ekranie.");
        return s.toString();
    }

    private static String fmt(long at,long start) {
        return at < start || start<=0 ? "nie wykryto" :
                String.format(Locale.forLanguageTag("pl-PL"), "%.2f s", (at-start)/1000.0);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event == null || event.getPackageName() == null) return;
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if(!"running".equals(sp.getString("state",""))) return;
        String wanted = sp.getString("pkg","");
        if(!wanted.equals(event.getPackageName().toString())) return;
        long t0=sp.getLong("t0",0), now=SystemClock.elapsedRealtime();
        if(t0<=0 || now<t0) return;
        if(now-t0>LIMIT_MS) {
            sp.edit().putString("state","expired").apply();
            return;
        }
        if(sp.getLong("foreground",-1)<0) sp.edit().putLong("foreground",now).apply();
        if(now-lastScan<300) return;
        lastScan=now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if(root==null || root.getPackageName()==null || !wanted.equals(root.getPackageName().toString())) return;
        boolean price=false, eta=false;
        ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.push(root);
        int visited=0;
        while(!nodes.isEmpty() && visited++ < 300 && (!price || !eta)) {
            AccessibilityNodeInfo node=nodes.pop();
            if(node == null) continue;
            if(node.isVisibleToUser()) {
                CharSequence t=node.getText(), d=node.getContentDescription();
                if(t!=null){String v=t.toString();if(v.length()<300) {
                    price |= PRICE.matcher(v).find();
                    eta |= MINUTES.matcher(v).find();
                }}
                if(d!=null){String v=d.toString();if(v.length()<300){
                    price |= PRICE.matcher(v).find();
                    eta |= MINUTES.matcher(v).find();
                }}
            }
            for(int i=0;i<node.getChildCount() && visited+nodes.size()<350;i++){
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null) nodes.push(child);
            }
        }
        SharedPreferences.Editor editor=sp.edit();
        long fare=sp.getLong("fare",-1), mins=sp.getLong("minutes",-1);
        if(price && fare<0) {editor.putLong("fare",now);fare=now;}
        if(eta && mins<0) {editor.putLong("minutes",now);mins=now;}
        if(fare>0 && mins>0) editor.putString("state","complete");
        editor.apply();
    }

    @Override public void onInterrupt() { }
}
