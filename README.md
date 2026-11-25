# Hívásátirányító (Android)

Időzített hívásátirányítás: megadott dátum+órától dátum+óráig a bejövő hívások
egy becenévvel kiválasztott telefonszámra mennek.

## Használat
1. **Becenevek** (jobb felső menü) → `+` → becenév + telefonszám
   (vagy „Kiválasztás a névjegyekből”).
2. Főképernyő → `+` → becenév kiválasztása, **Ettől** és **Eddig** időpont → Mentés.
3. A kezdés időpontjában az app beállítja az átirányítást, a végén kikapcsolja.
   - Koppintás egy ütemezésre: szerkesztés · hosszan nyomva: törlés · kapcsoló: ideiglenes tiltás.
   - Ha két időszak átfedi egymást, a később kezdődő számít.
   - „Kikapcsolás most”: azonnal leállítja az átirányítást.
   - „Állapot újraküldése”: újra elküldi a szolgáltatónak a jelenleg érvényes beállítást.

## Hogyan működik
Az app a szabványos GSM-kódokat „tárcsázza” (`TelecomManager.placeCall`):
- bekapcsolás: `**21*<szám>#` (feltétel nélküli átirányítás)
- kikapcsolás: `##21#`

Az időzítés `AlarmManager` pontos ébresztőivel történik, újraindítás után
automatikusan visszaállnak. Ha feltételes átirányítást szeretnél
(foglalt/nem fogadott/nem elérhető), a `Forwarding.kt` fájlban írd át a kódokat
`**004*<szám>#` / `##004#`-re.

## Fordítás
1. Android Studio → **Open** → ez a mappa.
2. Az első szinkronizáláskor a Studio letölti a Gradle-t és a függőségeket
   (ha a wrapper hiányzik, a Studio felajánlja a létrehozását).
3. Telefon USB-hibakereséssel → **Run**, vagy *Build → Build APK(s)*.

Követelmény: Android 8.0+ (API 26), SIM-kártyás telefon.

## Engedélyek
- **Telefonhívások** – a kódok elküldéséhez (kötelező).
- **Értesítések** – visszajelzés be/kikapcsoláskor.
- **Riasztások és emlékeztetők** – pontos időzítés (Android 12-n kérheti külön).

## Tudnivalók
- A szolgáltatónak támogatnia kell az MMI-kódos átirányítást (a hazai
  szolgáltatóknál ez általában működik). Az átirányított hívás díjköteles lehet.
- Kettős SIM esetén az alapértelmezett hívó SIM-en állítja be.
- Akkumulátorkímélő módban egyes gyártóknál (Xiaomi, Huawei, Samsung)
  érdemes az appot kivenni az akkumulátor-optimalizálás alól.
- Az app azt tárolja, amit *ő* állított be; ha kézzel módosítod az
  átirányítást a telefon beállításaiban, használd az „Állapot újraküldése” gombot.
