package pl.taxilab.test;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
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
    private TextView result, installed;

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
        panel.addView(text("Eksperyment techniczny • wersja 0.1", 14, false, Color.DKGRAY));
        panel.addView(text("To nie jest jeszcze porównywarka. Nie pobiera cen ani czasów oczekiwania.", 14, true, Color.rgb(135,75,24)));
        panel.addView(text("Wspólna trasa do testu",18,true,Color.BLACK));
        from = field("Galeria Echo, Kielce");
        to = field("Sienkiewicza 29, Kielce");
        panel.addView(text("Skąd",13,true,Color.DKGRAY));
        panel.addView(from);
        panel.addView(text("Dokąd",13,true,Color.DKGRAY));
        panel.addView(to);
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
        panel.addView(text("Wynik testu",18,true,Color.BLACK));
        result = text("Wybierz przycisk powyżej.",15,true,Color.rgb(11,87,94));
        panel.addView(result);
        panel.addView(text("Weryfikujemy uruchamianie aplikacji. Nie uzyskujemy danych z ich interfejsów ani nie zamawiamy przejazdów.",12,false,Color.DKGRAY));
    }
    @Override public void onResume() {
        super.onResume();
        if(installed != null) installed.setText("Uber: "+detected(UBER)+"  •  Bolt: "+detected(BOLT)+"  •  FREENOW: "+detected(FREENOW));
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
