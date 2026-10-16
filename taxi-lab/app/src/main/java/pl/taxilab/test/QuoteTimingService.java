package pl.taxilab.test;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuoteTimingService extends AccessibilityService {
    public static final String UBER="com.ubercab";
    public static final String BOLT="ee.mtakso.client";
    public static final String FREE="taxi.android.client";
    private static final String[] ORDER={BOLT,UBER,FREE};
    private static final String PREFS="taxi_lab_auto_04";
    private static final long PROVIDER_TIMEOUT=8500;
    private static final Pattern PRICE=Pattern.compile("(?iu)(?:\\b(\\d{1,4}(?:[,.]\\d{1,2})?)\\s*(?:zł|PLN)\\b|\\bPLN\\s*(\\d{1,4}(?:[,.]\\d{1,2})?))");
    private static final Pattern MIN=Pattern.compile("(?iu)\\b(\\d{1,2})\\s*(?:min\\.?|minut(?:a|y|ę)?|minutes?)\\b");
    private static volatile QuoteTimingService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean running=false, locationAuto=false;
    private int index=0, phase=0, stable=0;
    private long stepStart=0, lastAction=0;
    private String origin="", destination="", lastCandidate="", token="";
    private double[] pickup, dropoff;
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(!running) return;
            if(SystemClock.elapsedRealtime()-stepStart>=PROVIDER_TIMEOUT){
                store(ORDER[index],"brak wyniku (limit 8,5 s)",null,null);
                next();
            } else {
                scan();
                if(running) handler.postDelayed(this,280);
            }
        }
    };

    public static String label(String p){return UBER.equals(p)?"Uber":BOLT.equals(p)?"Bolt":FREE.equals(p)?"FREENOW":"Nieznany";}
    public static boolean permissionEnabled(Context ctx) {
        int global=Settings.Secure.getInt(ctx.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0);
        String en=Settings.Secure.getString(ctx.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return global==1 && en!=null && en.contains(ctx.getPackageName()+"/pl.taxilab.test.QuoteTimingService");
    }
    public static boolean startComparison(Context ctx,String src,String dest,double[] pickup,double[] dropoff,long requestedAt) {
        QuoteTimingService s=instance;
        if(s==null)return false;
        s.handler.post(()->s.start(src,dest,pickup,dropoff,requestedAt));
        return true;
    }
    public static void cancel(){
        QuoteTimingService s=instance;
        if(s!=null)s.handler.post(()->s.finish("Przerwano test",true));
    }
    private SharedPreferences prefs(){return getSharedPreferences(PREFS,MODE_PRIVATE);}
    private static SharedPreferences read(Context ctx){return ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static String k(String pkg,String field){return pkg.replace('.','_')+"_"+field;}

    public static String summary(Context ctx){
        SharedPreferences p=read(ctx);
        if(!p.contains("state"))return "Nie przeprowadzono porównania.";
        String state=p.getString("state","?");
        long started=p.getLong("started",0), ended=p.getLong("finished",0);
        long time=(ended>0?ended:SystemClock.elapsedRealtime())-started;
        String header=p.getString("from","")+" → "+p.getString("to","");
        return "Trasa: "+header+"\n"+p.getString("info","")+
                "\nStan: "+state+"\nCzas od kliknięcia: "+
                String.format(Locale.forLanguageTag("pl-PL"),"%.2f s",Math.max(0,time)/1000d)+
                (ended>0?(time<=3000?" • cel 3 s: TAK":" • cel 3 s: NIE"):"");
    }
    public static String result(Context ctx,String pkg){
        SharedPreferences p=read(ctx);
        String status=p.getString(k(pkg,"status"),"oczekuje");
        String price=p.getString(k(pkg,"price"),"");
        String eta=p.getString(k(pkg,"eta"),"");
        long delay=p.getLong(k(pkg,"elapsed"),-1);
        return label(pkg)+"\n"+status+
            (price.isEmpty()?"":"\nKandydat ceny: "+price)+
            (eta.isEmpty()?"":"\nMinuty z ekranu: "+eta+" (rodzaj czasu do weryfikacji)")+
            (delay<0?"":"\nPomiar od początku: "+String.format(Locale.forLanguageTag("pl-PL"),"%.2f s",delay/1000d))+
            "\n";
    }
    @Override protected void onServiceConnected(){
        super.onServiceConnected();instance=this;
    }
    @Override public void onDestroy(){
        if(instance==this) instance=null;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    private void start(String src,String dst,double[] fromCoord,double[] destCoord,long at) {
        if(running) finish("Przerwano poprzedni test",true);
        origin=src;destination=dst;pickup=fromCoord;dropoff=destCoord;
        locationAuto=src.equalsIgnoreCase("Moja lokalizacja");
        index=0;running=true;token=Long.toString(at);
        SharedPreferences.Editor e=prefs().edit().clear();
        e.putString("state","w toku").putString("info","Sekwencja: Bolt → Uber → FREENOW")
         .putString("from",src).putString("to",dst).putLong("started",at).putLong("finished",0);
        for(String pkg:ORDER)e.putString(k(pkg,"status"),"oczekuje");
        e.apply();
        launch();
    }
    private Intent intentFor(String pkg) {
        Intent i=null;
        if(UBER.equals(pkg) && dropoff!=null) {
            Uri.Builder b=Uri.parse("uber://riderequest").buildUpon();
            if(pickup!=null){
                b.appendQueryParameter("pickup[latitude]",Double.toString(pickup[0]));
                b.appendQueryParameter("pickup[longitude]",Double.toString(pickup[1]));
                b.appendQueryParameter("pickup[nickname]",origin);
            } else b.appendQueryParameter("pickup","my_location");
            b.appendQueryParameter("dropoff[latitude]",Double.toString(dropoff[0]));
            b.appendQueryParameter("dropoff[longitude]",Double.toString(dropoff[1]));
            b.appendQueryParameter("dropoff[nickname]",destination);
            b.appendQueryParameter("dropoff[formatted_address]",destination);
            i=new Intent(Intent.ACTION_VIEW,b.build());
            i.setPackage(UBER);
        }
        if(i==null)i=getPackageManager().getLaunchIntentForPackage(pkg);
        if(i!=null)i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }
    private void launch() {
        if(!running)return;
        handler.removeCallbacks(tick);
        phase=0;stable=0;lastCandidate="";lastAction=0;
        stepStart=SystemClock.elapsedRealtime();
        String pkg=ORDER[index];
        prefs().edit().putString(k(pkg,"status"),"otwieram i szukam trasy").apply();
        Intent intent=intentFor(pkg);
        if(intent==null){
            store(pkg,"brak zainstalowanej aplikacji",null,null);
            next();return;
        }
        try {
            startActivity(intent);
            handler.postDelayed(tick,350);
        }catch(Exception ex){
            // Some Android releases prohibit background activity launches.
            store(pkg,"Android odmówił uruchomienia: "+ex.getClass().getSimpleName(),null,null);
            next();
        }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(!running||event==null||event.getPackageName()==null)return;
        if(ORDER[index].equals(event.getPackageName().toString())) {
            long now=SystemClock.elapsedRealtime();
            if(now-lastAction>=180)scan();
        }
    }

    private static final class Item {
        AccessibilityNodeInfo node;
        String txt;
        Item(AccessibilityNodeInfo n,String s){node=n;txt=s;}
    }
    private List<Item> nodes(AccessibilityNodeInfo root) {
        ArrayList<Item> out=new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();
        q.add(root);
        int count=0;
        while(!q.isEmpty() && count++<450) {
            AccessibilityNodeInfo n=q.poll();
            if(n==null)continue;
            if(n.isVisibleToUser()) {
                String t=n.getText()==null?"":n.getText().toString();
                String d=n.getContentDescription()==null?"":n.getContentDescription().toString();
                String h=n.getHintText()==null?"":n.getHintText().toString();
                out.add(new Item(n,t+" "+d+" "+h));
            }
            for(int j=0;j<n.getChildCount();j++){
                AccessibilityNodeInfo c=n.getChild(j);
                if(c!=null)q.add(c);
            }
        }
        return out;
    }
    private static String normalized(String txt){
        return Normalizer.normalize(txt==null?"":txt,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
    }
    private static String street(String address) {
        String s=normalized(address).trim().replaceAll("\\b(ulica|ul\\.)\\s*","");
        return s.split("[,0-9]")[0].trim();
    }
    private boolean destinationVisible(List<Item> ns){
        String street=street(destination);
        if(street.length()<4)return false;
        for(Item item:ns) if(normalized(item.txt).contains(street))return true;
        return false;
    }
    private static AccessibilityNodeInfo clickable(AccessibilityNodeInfo n){
        AccessibilityNodeInfo cur=n;
        for(int i=0;cur!=null && i<5;i++,cur=cur.getParent()){
            if(cur.isClickable()&&cur.isEnabled())return cur;
        }
        return null;
    }
    private boolean click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n=clickable(node);
        if(n==null)return false;
        boolean ok=n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if(ok)lastAction=SystemClock.elapsedRealtime();
        return ok;
    }
    private boolean setText(AccessibilityNodeInfo n,String text){
        if(!n.isEditable())return false;
        Bundle args=new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        boolean ok=n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
        if(ok)lastAction=SystemClock.elapsedRealtime();
        return ok;
    }
    private void scan(){
        if(!running)return;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null||root.getPackageName()==null||!ORDER[index].equals(root.getPackageName().toString()))return;
        List<Item> ns=nodes(root);
        boolean routeVisible=destinationVisible(ns);
        if(phase<3 && SystemClock.elapsedRealtime()-lastAction>450) navigate(ns,routeVisible);
        if(!running)return;
        // Never claim a fare without seeing the requested destination on this carrier's screen.
        if(!routeVisible)return;
        String price=null, minutes=null;
        for(Item it:ns) {
            String t=it.txt;
            if(t.length()>250 || normalized(t).matches(".*(rabat|promocja|discount|kupon|voucher).*"))continue;
            if(price==null) {
                Matcher m=PRICE.matcher(t);
                if(m.find())price=(m.group(1)!=null?m.group(1):m.group(2))+" zł";
            }
            if(minutes==null){
                Matcher m=MIN.matcher(t);
                if(m.find())minutes=m.group(1)+" min";
            }
            if(price!=null&&minutes!=null)break;
        }
        if(price!=null&&minutes!=null){
            String candidate=price+"|"+minutes;
            if(candidate.equals(lastCandidate))stable++;
            else {lastCandidate=candidate;stable=1;}
            if(stable>=2){
                store(ORDER[index],"ODCZYT TESTOWY — sprawdź cenę i czas przyjazdu",price,minutes);
                next();
            }
        }
    }
    private void navigate(List<Item> ns,boolean routeVisible) {
        if(routeVisible){phase=3;return;}
        long now=SystemClock.elapsedRealtime();
        if(now-lastAction<450)return;
        // Search is limited to clearly named destination controls. Never touch 'Order' or payment.
        if(phase==0){
            for(Item it:ns) {
                String t=normalized(it.txt);
                if(t.length()>150)continue;
                if(t.contains("dokad")||t.contains("where to")||
                    t.contains("destination")||t.contains("cel podrozy")||
                    t.contains("adres docelowy")||t.contains("gdzie jedziemy")){
                    if(click(it.node)){phase=1;return;}
                }
            }
            phase=1;
        }
        if(phase==1){
            AccessibilityNodeInfo edit=null;
            for(Item it:ns){
                if(it.node.isEditable()&&it.node.isEnabled()){
                    String t=normalized(it.txt);
                    if(t.contains("skad")||t.contains("pickup")||t.contains("from"))continue;
                    edit=it.node;
                    if(t.contains("dokad")||t.contains("destination"))break;
                }
            }
            if(edit!=null && setText(edit,destination)){phase=2;return;}
        }
        if(phase==2){
            String st=street(destination);
            for(Item it:ns){
                if(it.node.isEditable())continue;
                String t=normalized(it.txt);
                if(st.length()>=4 && t.contains(st) && t.length()<180){
                    if(click(it.node)){phase=3;return;}
                }
            }
        }
    }
    private void store(String pkg,String status,String price,String minutes){
        SharedPreferences p=prefs();
        long started=p.getLong("started",SystemClock.elapsedRealtime());
        SharedPreferences.Editor e=p.edit()
            .putString(k(pkg,"status"),status)
            .putLong(k(pkg,"elapsed"),SystemClock.elapsedRealtime()-started);
        if(price!=null)e.putString(k(pkg,"price"),price);
        if(minutes!=null)e.putString(k(pkg,"eta"),minutes);
        e.apply();
    }
    private void next(){
        if(!running)return;
        index++;
        if(index>=ORDER.length){finish("Zebrano wyniki lub oznaczono braki",false);return;}
        handler.postDelayed(this::launch,160);
    }
    private void finish(String msg,boolean cancelled){
        if(!running)return;
        running=false;
        handler.removeCallbacks(tick);
        prefs().edit().putString("state",cancelled?"przerwano":"zakończony")
            .putString("info",msg).putLong("finished",SystemClock.elapsedRealtime()).apply();
        if(!cancelled) {
            Intent i=new Intent(this,MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try{startActivity(i);}catch(Exception ignored){}
        }
    }
    @Override public void onInterrupt() {}
}