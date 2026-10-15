# Taxi Lab Android 0.1 – test techniczny

Projekt testowy dla telefonów Android. Sprawdza:
- Bezpośrednie uruchomienie zainstalowanych aplikacji Uber, Bolt, FREENOW bez Google Play
- Próbę przekazania trasy do Ubera przez jego deep link
- Próby podziału ekranu między Taxi Lab a Boltem/FREENOW (zależne od Androida)

**Ważne:** Żaden z testów nie potwierdza możliwości jednoczesnego odczytywania cen i ETA. Aplikacja nie posiada takiego dostępu i nie wyświetla fikcyjnych cen.

## Instalacja
W repozytorium otwórz zakładkę **Actions**, znajdź **Taxi Lab - APK instalacyjny**, otwórz najnowszy udany przebieg i pobierz artefakt **TaxiLab-APK-test**. Rozpakuj ZIP i przenieś plik `app-debug.apk` na telefon. Android zapyta o pozwolenie na instalację z danego źródła.

## Wynik
Otwieranie przewoźników to tylko pierwszy test; weryfikacja automatycznego wprowadzania tras i dostępności aktualnych wycen jest odrębnym etapem.
