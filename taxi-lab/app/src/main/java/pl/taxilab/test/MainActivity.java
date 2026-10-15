package pl.taxilab.test;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String UBER = "com.ubercab";
    private static final String BOLT = "ee.mtakso.client";
    private static final String FREENOW = "taxi.android.client";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText from, to;
    private TextView result, installed, timingInfo;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(244,247,252));
        LinearLayout panel = new LinearLayout(this);
        panel.setPadding(dp(20),dp(20),dp(20),dp(30));
        panel.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(panel);
        setContentView(scroll);
        panel.addView(text("TAXI LAB / ANDROID", 23, true, Color.rgb(20,44,80)));
        panel.addView(text("Eksperyment techniczny • wersja 0.3", 14, false, Color.DKGRAY));
        panel.addView(text("To pomiar czasu pojawienia się danych na ekranie; nie gwarantuje prawidłowego rozpoznania oferty.", 14, true, Color.rgb(135,75,24)));
        panel.addView(text("Wspólna trasa do testu",18,true,Color.BLACK));
        from = field("Galeria Echo, Kielce");
        to = field("Sienkiewicza 29, Kielce");
        panel.addView(text("Skąd",13,true,Color.DKGRAY));
        panel.addView(from);
        panel.addView(text("Dokąd",13,true,Color.DKGRAY));
        panel.addView(to);
        panel.addView(text("AUTOMATYCZNY POMIAR CZASU",19,true,Color.rgb(20,80,130)));
        panel.addView(text("Taxi Lab zmierzy chwile otwarcia wybranego przewoznika oraz pierwsze elementy ekranu przypominajace cene i czas w minutach. Nie odczytuje danych z kont ani nie wysyla informacji na serwer.",13,false,Color.DKGRAY));
        panel.addView(text("Uprawnienie Dostepnosc jest wrazliwe. Wlacz tylko na czas testow i wylacz po zakonczonym eksperymencie.",13,true,Color.rgb(135,75,24)));
        panel.addView(btn("WŁĄCZ ODCZYT NA CZAS TESTU", v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }));
        panel.addView(btn("MIERZ UBER", v -> startTiming(UBER)));
        panel.addView(btn("MIERZ BOLT", v -> startTiming(BOLT)));
        panel.addView(btn("MIERZ FREENOW", v -> startTiming(FREENOW)));
        timingInfo = text("Nie ma jeszcze pomiaru.",14,false,Color.rgb(11,87,94));
        panel.addView(timingInfo);
        panel.addView(text("Dla Bolta i FREENOW trase ustawiasz jeszcze recznie. Pomiar obejmuje takze te czynnosc, wiec NIE oznacza czasu przyszlej zautomatyzowanej porownywarki.",12,false,Color.DKGRAY));
        panel.addView(text("Otwieranie bez sklepu Play",18,true,Color.BLACK));
        installed = text("",13,false,Color.DKGRAY);
        panel.addView(installed);
        panel.addView(btn("URUCHOM UBER", v -> open(UBER,false)));
        panel.addView(btn("URUCHOM BOLT", v -> open(BOLT,false)));
        panel.addView(btn("URUCHOM FREENOW", v -> open(FREENOW,false)));
        panel.addView(text("Przekazanie trasy do Ubera",18,true,Color.BLACK));
        panel.addView(btn("OTWÓRZ UBER Z TRASĄ",v -> routeUber()));
        panel.addView(text("Eksperyment z dwoma oknami",18,true,Color.BLACK));
        panel.addView(text("Android może odmówić podziału ekranu. Zwykle nie da się uzyskać trzech obcych aplikacji obok siebie.",13,false,Color.DKGRAY));
        panel.addView(btn("BOLT W SĄSIEDNIM OKNIE",v -> open(BOLT,true)));
        panel.addView(btn("FREENOW W SĄSIEDNIM OKNIE",v -> open(FREENOW,true)));
        panel.addView(text("Test zgody na osadzanie obcych aplikacji",18,true,Color.BLACK));
        panel.addView(text("Kontrolujemy oficjalna flage Androida. Nie odczytujemy ekranu, kont ani cen.",13,false,Color.DKGRAY));
        panel.addView(btn("SPRAWDZ UBER, BOLT I FREENOW", v -> checkEmbedding()));
        panel.addView(text("Wynik testu",18,true,Color.BLACK));
        result = text("Wybierz przycisk powyżej.",15,true,Color.rgb(11,87,94));
        panel.addView(result);
        panel.addView(text("Weryfikujemy uruchamianie aplikacji. Nie uzyskujemy danych z ich interfejsów ani nie zamawiamy przejazdów.",12,false,Color.DKGRAY));
    }
    @Override public void onResume() {
        super.onResume();
        if(installed != null) installed.setText("Uber: "+detected(UBER)+"  •  Bolt: "+detected(BOLT)+"  •  FREENOW: "+detected(FREENOW));
        if(timingInfo != null) timingInfo.setText((QuoteTimingService.permissionEnabled(this)?"Dostepnosc: WLACZONA":"Dostepnosc: WYLACZONA") + "\\n" + QuoteTimingService.report(this));
    }
    private String detected(String pkg){return getPackageManager().getLaunchIntentForPackage(pkg)==null?"brak":"jest";}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(String s,int size,boolean bold,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(0,dp(8),0,dp(8));if(bold)t.setTypeface(null, Typeface.BOLD);return t;
    }
    private EditText field(String value){
        EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setTextSize(15);return e;
    }
    private Button btn(String title, View.OnClickListener listener){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        b.setOnClickListener(listener);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(53));
        p.bottomMargin=dp(8);b.setLayoutParams(p);return b;
    }
    private void say(String msg){result.setText(msg);}
    private void open(String pkg,boolean adjacent){
        Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent==null){say("Nie wykryto aplikacji: "+pkg);return;}
        if(adjacent)intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        try {startActivity(intent);say("Wysłano polecenie uruchomienia "+pkg+(adjacent?" obok Taxi Lab. O wyniku decyduje Android.":"."));}
        catch(Exception ex){say("Nie uruchomiono: "+ex.getClass().getSimpleName());}
    }
    private void startTiming(String pkg) {
        if(!QuoteTimingService.permissionEnabled(this)){
            say("Najpierw wlacz uprawnienie Dostepnosc dla Taxi Lab - pomiar czasu ofert. Po wyrazeniu zgody wroc i wybierz pomiar.");
            return;
        }
        Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent==null){say("Nie wykryto aplikacji przewoznika. Zainstaluj ja przed pomiarem.");return;}
        QuoteTimingService.begin(this,pkg);
        try {
            startActivity(intent);
        } catch(Exception e) {
            say("Blad uruchomienia: "+e.getClass().getSimpleName());
        }
    }
    private void checkEmbedding() {
        PackageManager pm = getPackageManager();
        StringBuilder report = new StringBuilder();
        report.append("Android API: ").append(android.os.Build.VERSION.SDK_INT).append("\n");
        report.append("Zgoda na osadzanie przez dowolna aplikacje:\n\n");
        appendEmbeddingReport(pm,report,"Uber",UBER);
        appendEmbeddingReport(pm,report,"Bolt",BOLT);
        appendEmbeddingReport(pm,report,"FREENOW",FREENOW);
        report.append("\nWAZNE: Brak publicznej zgody nie dowodzi braku mozliwej integracji partnerskiej. ");
        report.append("Zgoda na samo osadzanie nie oznacza zgody na pobieranie cen ani na trzy okna.");
        say(report.toString());
    }
    private void appendEmbeddingReport(PackageManager pm, StringBuilder report, String name, String pkg) {
        report.append(name).append(" (").append(pkg).append(")\n");
        if(android.os.Build.VERSION.SDK_INT < 33){
            report.append("System nie obsluguje standardowego cross-app embedding (wymagany Android 13+).\n\n");
            return;
        }
        try {
            PackageInfo info=pm.getPackageInfo(pkg,PackageManager.GET_ACTIVITIES);
            ActivityInfo[] activities=info.activities;
            if(activities==null||activities.length==0) {
                report.append("Brak danych o ekranach aplikacji.\n\n"); return;
            }
            int enabled=0;
            StringBuilder names = new StringBuilder();
            for(ActivityInfo act:activities) {
                if ((act.flags & ActivityInfo.FLAG_ALLOW_UNTRUSTED_ACTIVITY_EMBEDDING)!=0) {
                    enabled++;
                    if(names.length()<220)names.append(" • ").append(act.name).append("\n");
                }
            }
            report.append("Zgoda publiczna: ").append(enabled>0?"TAK":"NIE");
            report.append(" (").append(enabled).append("/").append(activities.length).append(" ekranow)\n");
            Intent launch=pm.getLaunchIntentForPackage(pkg);
            if(launch!=null && launch.getComponent()!=null) {
                try {
                    ActivityInfo start=pm.getActivityInfo(launch.getComponent(),0);
                    boolean allowed=(start.flags & ActivityInfo.FLAG_ALLOW_UNTRUSTED_ACTIVITY_EMBEDDING)!=0;
                    report.append("Ekran startowy: ").append(allowed?"TAK":"NIE").append("\n");
                } catch(Exception e) {
                    report.append("Ekran startowy: nieodczytany\n");
                }
            }
            if(enabled>0)report.append("Wybrane ekrany: \n").append(names);
        }catch(PackageManager.NameNotFoundException ex){
            report.append("NIE WYKRYTO pakietu (albo niewidoczny dla testu)\n");
        }catch(Exception ex){
            report.append("Blad odczytu: ").append(ex.getClass().getSimpleName()).append("\n");
        }
        report.append("\n");
    }
    private void routeUber(){
        if(from.getText().toString().trim().isEmpty()||to.getText().toString().trim().isEmpty()){say("Podaj oba adresy.");return;}
        if(getPackageManager().getLaunchIntentForPackage(UBER)==null){say("Zainstaluj Uber i spróbuj ponownie.");return;}
        if(!Geocoder.isPresent()){say("Telefon nie obsługuje rozpoznawania adresów.");return;}
        String src=from.getText().toString().trim(),dst=to.getText().toString().trim();
        say("Sprawdzam adresy na telefonie...");
        worker.execute(()->{
            try {
                Geocoder geocoder=new Geocoder(getApplicationContext(), Locale.forLanguageTag("pl-PL"));
                List<Address> aa=geocoder.getFromLocationName(src,1), bb=geocoder.getFromLocationName(dst,1);
                if(aa==null||aa.isEmpty()||bb==null||bb.isEmpty()){runOnUiThread(()->say("Nie odnaleziono jednego z adresów."));return;}
                Address a=aa.get(0),b=bb.get(0);
                Uri uri=Uri.parse("uber://riderequest").buildUpon()
                    .appendQueryParameter("pickup[latitude]",String.valueOf(a.getLatitude()))
                    .appendQueryParameter("pickup[longitude]",String.valueOf(a.getLongitude()))
                    .appendQueryParameter("pickup[nickname]",src)
                    .appendQueryParameter("dropoff[latitude]",String.valueOf(b.getLatitude()))
                    .appendQueryParameter("dropoff[longitude]",String.valueOf(b.getLongitude()))
                    .appendQueryParameter("dropoff[nickname]",dst).build();
                runOnUiThread(()->{
                    try {Intent i=new Intent(Intent.ACTION_VIEW,uri);i.setPackage(UBER);startActivity(i);say("Otworzono Uber z proponowaną trasą. Zweryfikuj adresy, zanim zamówisz.");}
                    catch(Exception ex){say("Uber nie przyjął linku trasy: "+ex.getClass().getSimpleName());}
                });
            }catch(Exception ex){runOnUiThread(()->say("Nie mogę ustalić współrzędnych: "+ex.getClass().getSimpleName()));}
        });
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
