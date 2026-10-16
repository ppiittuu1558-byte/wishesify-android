# Taxi Lab 0.4 — automatyczne porównanie, eksperyment
Celem jest użycie jednej trasy, przejście kolejno Bolt → Uber → FREENOW i pokazanie lokalnie odczytanych ofert.

## Ograniczenia świadomie widoczne użytkownikowi
- Android AccessibilityService obsługuje aktywny ekran, a nie trzy aplikacje jednocześnie.
- Automatyzacja pola wyszukiwania i wybierania sugestii jest heurystyczna: zależy od dostępności kontrolek konkretnych wersji aplikacji.
- Uber ma oficjalny deeplink przekazujący współrzędne; Bolt i FREENOW nie mają potwierdzonego ogólnodostępnego deeplinku do pobierania ofert.
- Odczyty (kwota, minuty) są kandydatami, nie potwierdzoną ceną i ETA kierowcy; wymagają testów z rzeczywistymi ekranami.
- 3 sekundy to cel do zmierzenia, nie osiągnięty wynik. Każdy przewoźnik ma timeout 8,5 s.
- Żadnego automatycznego zamawiania kursu, zatwierdzania płatności lub wysyłania treści ekranów na serwer.
- Przy użyciu „Moja lokalizacja” wymagany świeży GPS. W przypadku wyboru odbioru ręcznego należy porównać rzeczywiste punkty odbioru w aplikacjach.
- Zgodność z regulaminami przewoźników i polityką Google Play wymaga osobnego audytu przed publikacją.

## Test na telefonie
Włącz dostępność tylko na czas próby, przyznaj lokalizację, wpisz destynację i naciśnij PORÓWNAJ. Nie dotykaj pozostałych aplikacji; na końcu aplikacja spróbuje powrócić do Taxi Lab. Zrób zrzut wyników. Jeśli wynik jest brakujący, nie został automatycznie zweryfikowany. W razie problemów naciśnij PRZERWIJ TEST.