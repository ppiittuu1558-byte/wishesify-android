package pl.taxilab.test;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQ_LOCATION = 77;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText origin, destination;
    private TextView summary, uber, bolt, free, gpsState;
    private long pressedAt = 0;
    private boolean polling = false;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246,248,252));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(25), dp(18), dp(40));
        scroll.addView(box);
        setContentView(scroll);
        box.addView(label("TAXI LAB  /  PORÓWNAJ", 23, true, Color.rgb(20,48,87)));
        box.addView(label("Wersja testowa 0.4 — automatyczna sekwencja, bez zamawiania kursów", 13, false, Color.DKGRAY));
        box.addView(label("Dostępność odczytuje tylko ekrany trzech przewoźników podczas uruchomionego testu. Dane zapisywane są lokalnie. Nie potwierdzaj zamówienia bez sprawdzenia w aplikacji przewoźnika.", 13, false, Color.rgb(125,76,18)));
        box.addView(label("Miejsce odbioru", 15, true, Color.BLACK));
        origin = field("Moja lokalizacja");
        box.addView(origin);
        box.addView(label("Dokąd jedziemy?", 15, true, Color.BLACK));
        destination = field("Sienkiewicza 15, Kielce");
        box.addView(destination);
        gpsState = label("Przy opcji „Moja lokalizacja” Android poprosi o zgodę na lokalizację.", 12, false, Color.DKGRAY);
        box.addView(gpsState);
        box.addView(button("WŁĄCZ DOSTĘPNOŚĆ NA CZAS TESTU", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        box.addView(button("PORÓWNAJ • BOLT → UBER → FREENOW", v -> onCompare()));
        box.addView(button("PRZERWIJ TEST", v -> {
            QuoteTimingService.cancel();
            refresh();
        }));
        summary = label("", 15, true, Color.rgb(20,65,90));
        box.addView(summary);
        box.addView(label("WYNIKI — DOTKNIJ, ABY OTWORZYĆ PRZEWOŹNIKA", 17, true, Color.BLACK));
        bolt = label("", 16, true, Color.rgb(16,77,87));
        uber = label("", 16, true, Color.rgb(16,77,87));
        free = label("", 16, true, Color.rgb(16,77,87));
        box.addView(bolt);
        box.addView(uber);
        box.addView(free);
        bolt.setOnClickListener(v -> openCarrier(QuoteTimingService.BOLT));
        uber.setOnClickListener(v -> openCarrier(QuoteTimingService.UBER));
        free.setOnClickListener(v -> openCarrier(QuoteTimingService.FREE));
        box.addView(label("Cel: wszystkie trzy oferty w 3 sekundy. To cel testu, nie gwarancja. Nieodebrane albo niepewne oferty są oznaczane zamiast podawania zmyślonych cen. Czas przyjazdu i cena mogą zmienić się przed zamówieniem.", 13, false, Color.DKGRAY));
        refresh();
    }

    private void onCompare() {
        String dst = destination.getText().toString().trim();
        String src = origin.getText().toString().trim();
        if(dst.length() < 6) {gpsState.setText("Podaj dokładny adres docelowy.");return;}
        if(src.isEmpty()) src="Moja lokalizacja";
        pressedAt = SystemClock.elapsedRealtime();
        if(src.equalsIgnoreCase("Moja lokalizacja")) {
            if(Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                return;
            }
            locateAndBegin(dst);
        } else {
            final String pickup=src;
            gpsState.setText("Ustalam współrzędne podanych adresów...");
            new Thread(() -> {
                double[] a=geocode(pickup), b=geocode(dst);
                runOnUiThread(() -> begin(pickup,dst,a,b));
            }).start();
        }
    }

    private void locateAndBegin(String dst) {
        gpsState.setText("Pobieram bieżącą lokalizację telefonu...");
        LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(lm==null){gpsState.setText("Brak usługi lokalizacji. Wpisz adres odbioru.");return;}
        Location last=null;
        try {
            for(String provider:lm.getProviders(true)) {
                Location l=lm.getLastKnownLocation(provider);
                if(l!=null && (last==null||l.getTime()>last.getTime())) last=l;
            }
        } catch(SecurityException ex){gpsState.setText("Brak uprawnienia lokalizacji.");return;}
        final Location fallback=last;
        String provider=lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ?
            LocationManager.GPS_PROVIDER :
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?
            LocationManager.NETWORK_PROVIDER : null;
        if(provider==null){useLocation(fallback,dst);return;}
        if(Build.VERSION.SDK_INT>=30) {
            try {
                lm.getCurrentLocation(provider, null, getMainExecutor(), loc ->
                    useLocation(loc!=null?loc:fallback,dst));
            } catch(SecurityException ex){useLocation(fallback,dst);}
        } else {
            useLocation(fallback,dst);
        }
    }

    private void useLocation(Location loc, String dst) {
        if(loc==null || System.currentTimeMillis()-loc.getTime()>300000) {
            gpsState.setText("Brak świeżej pozycji GPS (do 5 minut). Włącz lokalizację albo wpisz adres odbioru.");
            return;
        }
        double[] gps={loc.getLatitude(),loc.getLongitude()};
        gpsState.setText(String.format(Locale.forLanguageTag("pl-PL"),
                "Odbiór: GPS %.5f, %.5f • dokładność ok. %.0f m",
                gps[0],gps[1],loc.hasAccuracy()?loc.getAccuracy():0f));
        new Thread(() -> {
            double[] drop=geocode(dst);
            runOnUiThread(() -> begin("Moja lokalizacja",dst,gps,drop));
        }).start();
    }

    private double[] geocode(String address) {
        try {
            if(!Geocoder.isPresent()) return null;
            Geocoder geo=new Geocoder(this,Locale.forLanguageTag("pl-PL"));
            List<Address> r=geo.getFromLocationName(address,1);
            if(r!=null && !r.isEmpty()) return new double[]{r.get(0).getLatitude(),r.get(0).getLongitude()};
        } catch(Exception ignored) { }
        return null;
    }

    private void begin(String src,String dst,double[] pickup,double[] dropoff) {
        if(pressedAt==0) pressedAt=SystemClock.elapsedRealtime();
        if(!QuoteTimingService.permissionEnabled(this)) {
            gpsState.setText("Najpierw włącz usługę dostępności Taxi Lab w ustawieniach Androida.");
            return;
        }
        boolean started=QuoteTimingService.startComparison(this,src,dst,pickup,dropoff,pressedAt);
        if(!started) gpsState.setText("Usługa dostępności nie jest połączona. Wyłącz i ponownie włącz ją w ustawieniach.");
        refresh();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] result) {
        super.onRequestPermissionsResult(requestCode,permissions,result);
        if(requestCode==REQ_LOCATION) {
            boolean granted=false;
            for(int g:result) if(g==PackageManager.PERMISSION_GRANTED) granted=true;
            if(granted) locateAndBegin(destination.getText().toString().trim());
            else gpsState.setText("Bez zgody na lokalizację wpisz ręcznie miejsce odbioru.");
        }
    }

    private void openCarrier(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if(i==null) {gpsState.setText("Nie znaleziono aplikacji "+QuoteTimingService.label(pkg));return;}
        try{startActivity(i);}catch(Exception ex){gpsState.setText("Nie można otworzyć przewoźnika: "+ex.getClass().getSimpleName());}
    }

    private void refresh() {
        if(summary==null) return;
        summary.setText(QuoteTimingService.summary(this));
        bolt.setText(QuoteTimingService.result(this,QuoteTimingService.BOLT));
        uber.setText(QuoteTimingService.result(this,QuoteTimingService.UBER));
        free.setText(QuoteTimingService.result(this,QuoteTimingService.FREE));
    }
    @Override protected void onResume() {
        super.onResume();
        polling=true;
        handler.post(poll);
    }
    @Override protected void onPause() {
        polling=false; handler.removeCallbacks(poll); super.onPause();
    }
    private final Runnable poll=new Runnable() {
        @Override public void run(){if(polling){refresh();handler.postDelayed(this,500);}}
    };
    private int dp(float d){return(int)(d*getResources().getDisplayMetrics().density+0.5);}
    private TextView label(String s,int size,boolean bold,int color) {
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);
        v.setPadding(0,dp(9),0,dp(9));if(bold)v.setTypeface(null,Typeface.BOLD);return v;
    }
    private EditText field(String initial) {
        EditText e=new EditText(this);e.setSingleLine(true);e.setText(initial);
        e.setTextSize(17);return e;
    }
    private Button button(String title,View.OnClickListener action) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(action);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(55));p.bottomMargin=dp(7);
        b.setLayoutParams(p);return b;
    }
}