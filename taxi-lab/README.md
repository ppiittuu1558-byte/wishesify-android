# Taxi Lab 0.2 – diagnostyka osadzania aplikacji Android

Wersja oddzielna od Taxi Lab 0.1 (nowy identyfikator instalacyjny), by nie nadpisywać wcześniejszej aplikacji.

1. Zainstaluj APK na telefonie, na którym działają Uber, Bolt i FREENOW.
2. Uruchom **Taxi Lab Diagnostyka**, naciśnij **SPRAWDZ UBER, BOLT I FREENOW**.
3. Zrób zrzut lub przeczytaj liczby: `Zgoda publiczna` i `Ekran startowy` dla trzech przewoźników.

**Co weryfikuje test:** Flaga `ActivityInfo.FLAG_ALLOW_UNTRUSTED_ACTIVITY_EMBEDDING` we wszystkich aktywnościach zainstalowanych aplikacji, wskazująca, czy właściciel pozwala na osadzanie przez dowolnego niezaufanego hosta na Androidzie 13+.

**Czego NIE potwierdza:** Test nie sprawdza certyfikatów zaufanych partnerów, regulaminów, dostępu do cen ani automatyzacji zewnętrznych aplikacji; samodzielna aplikacja nie ma specjalnych uprawnień systemowych do osadzania bez zgody. Brak zgody publicznej oznacza, że Taxi Lab bez autoryzacji nie może korzystać z tej metody. Aby wyświetlać *trzy* żywe podglądy, potrzebne są dodatkowe rozwiązania i ograniczenia rozmiaru.

## Pobieranie
GitHub Actions > Taxi Lab - APK instalacyjny > ostatnia zakończona sukcesem kompilacja > TaxiLab-APK-test. Plik wynikowy w archiwum: `app-debug.apk`. Nie trzeba instalować Android Studio.
