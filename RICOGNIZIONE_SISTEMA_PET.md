# Ricognizione del sistema pet HPET

> Stato analizzato: branch `main`, commit `45cb488` (`HPET 6.0`), 21 agosto 2026.
>
> Questo documento descrive il comportamento osservato nel codice corrente. Quando README, configurazione e implementazione non coincidono, viene riportato come fonte di verità il codice Java.

## 1. Quadro generale

HPET è un plugin Paper 26.2 / Java 25 che mostra pet quasi interamente client-side tramite PacketEvents. Il server non crea normalmente vere entità Bukkit per i pet: invia invece pacchetti di spawn, metadata, equipaggiamento, teletrasporto e distruzione ai giocatori presenti nel mondo.

Il sistema è diviso in quattro livelli principali:

1. `PetTypesHandler` legge `pets.yml` e costruisce i tipi/configurazioni dei pet (`PetType`).
2. `PetsHandler` crea e registra le istanze attive associate a un proprietario (`UserPet`).
3. Le implementazioni `FakeEntity` renderizzano il pet e il nametag tramite PacketEvents.
4. Un workload sincrono eseguito ogni tick aggiorna movimento, visibilità, skin e, in teoria, ability.

Schema sintetico:

```text
pets.yml
   |
   v
PetTypesHandler -----> PetType
                         |
comando / GUI / API      |
   |                     |
   +---- selectPet ------+
             |
             v
         PetsHandler -----> UserPet
                              |
                              +--> Animation
                              +--> Nametag
                              +--> FakeEntity
                                      |
                                      v
                              pacchetti PacketEvents
```

## 2. Avvio, reload e spegnimento

Entry point: `it.heron.hpet.main.PetPlugin`.

All'avvio (`onEnable` -> `load`) il plugin:

1. imposta il singleton `PetPlugin.instance`;
2. usa sempre il metadata handler `Metadata1_21`;
3. controlla se PacketEvents è attivo, ma non interrompe il caricamento quando manca;
4. copia, se assenti, `config.yml` e `pets.yml` nella cartella dati;
5. carica i moduli;
6. registra la GUI e il comando `/hpet`;
7. esegue `RuntimeCompatibilityValidator`;
8. prova a ripristinare da database il pet degli utenti già online.

I moduli dichiarati in `ModulesHandler` sono:

- database;
- loader dei tipi di pet;
- handler delle istanze attive;
- ability (attualmente vuoto);
- PlaceholderAPI, Vault, ItemsAdder e HeadDatabase;
- integrazioni vanish;
- messaggi/localizzazione.

I moduli sono memorizzati in una `HashMap` e poi caricati iterandone i valori. L'ordine non è garantito, nonostante esistano dipendenze logiche (per esempio i pet interrogano il database e alcuni hook). Questo è un punto fragile da sistemare prima di aggiungere altre dipendenze tra moduli.

Durante un reload:

- tutte le istanze attive vengono despawnate e deregistrate;
- vengono rimossi i listener del plugin;
- i moduli vengono scaricati e ricaricati;
- i pet degli utenti online vengono ripristinati da `LastPet`;
- viene emesso solo `HPETReloadPluginEvent`.

Durante lo spegnimento avviene lo stesso unload, senza nuovo load.

## 3. Caricamento e classificazione dei tipi di pet

Fonte: `src/main/resources/pets.yml`, copiata in `plugins/HPET/pets.yml` al primo avvio.

`PetTypesHandler` esamina tutte le sezioni root di `pets.yml`. Una sezione viene caricata soltanto se `skins` è una lista non vuota. Il tipo viene dedotto automaticamente dal contenuto:

| Formato rilevato | Classe creata | Rendering |
|---|---|---|
| prima skin con prefisso `MOB:` | `MobPetType` | fake mob |
| tutte le skin corrispondono a `MATERIAL:id` o `MATERIAL id` | `CustomModelPetType` | item sul casco di un fake armor stand |
| qualsiasi altro formato | `HeadPetType` | testa/item nella mano di un fake armor stand |

Conseguenze importanti:

- viene supportato realmente `MOB:<EntityType>`, ma solo usando la prima skin;
- i formati storici `MYTHICMOB:`, `MODELENGINE:` e `ITEMSADDER:` non hanno un loader dedicato nel codice corrente;
- la configurazione `skins.level.*` presente nell'esempio `mythicmob` non è una lista e quindi quel pet viene saltato;
- un valore lungo oltre 64 caratteri viene trattato come texture Base64;
- `HDB:<id>` passa da HeadDatabase;
- gli altri valori head vengono interpretati come nome giocatore;
- un errore su un singolo pet viene loggato e non impedisce il caricamento degli altri.

### Campi di `pets.yml` effettivamente letti

| Campo | Default | Uso reale corrente |
|---|---:|---|
| `displayname` | id del pet | nome GUI e nametag iniziale |
| `description` | lista vuota | lore dell'icona GUI |
| `skins` | obbligatorio | classificazione e aspetto |
| `x` | `0` | offset fisso X rispetto al proprietario |
| `y` | `1` | altezza/nome; l'offset base Y del pet diventa `y - 1` |
| `z` | `0` | offset fisso Z rispetto al proprietario |
| `nametag.x/y/z` | `0` | correzione ulteriore del nametag |
| `yaw` | `0` | rotazione aggiunta a yaw proprietario + 200° + calibrazione globale |
| `animation` | `glide` | selezione dell'animazione runtime |
| `distance` | `1` | correzione orizzontale del nametag per il rendering in mano; non determina la posizione generale del pet |
| `price` | assente | espone parzialmente lo stato di acquistabilità, senza acquisto funzionante |

Campi presenti nei file/documentazione ma non collegati al runtime corrente: `abilities`, `level`, `particle`, `visible`, `balloon`, `balloon_height` e il gruppo comportamentale del pet.

## 4. Selezione e ciclo di vita di un pet attivo

La selezione passa da `PetAPI.selectPet` a `PetsHandler.selectPet`.

Flusso reale:

1. viene caricato o creato il record `PetLevel` per `(UUID proprietario, tipo pet)`;
2. vengono rimossi **tutti** i pet già attivi dello stesso proprietario;
3. in base al `PetType` viene costruita una delle istanze:
   - `MobPetType` -> `MobUserPet`;
   - `CustomModelPetType` -> `StackUserPet`;
   - `HeadPetType` -> `HandUserPet`;
4. se `LastPet` contiene un nome personalizzato per lo stesso tipo, il nome viene riapplicato;
5. pet e nametag vengono spawnati;
6. l'istanza viene registrata nel set dei pet attivi e nella coda dei workload;
7. `LastPet` viene creato/aggiornato con tipo e nome.

Nonostante il README parli di più pet simultanei divisi in gruppi, il codice rimuove sempre tutte le istanze del proprietario. Il comportamento corrente è quindi: **massimo un pet attivo per UUID**.

La rimozione runtime chiama `despawn()` e toglie l'istanza dal set. Non cancella però `LastPet`: dopo un reload del plugin, il pet rimosso viene nuovamente selezionato per gli utenti ancora online.

Non esiste un listener `PlayerJoinEvent` nel codice corrente. `spawnDatabasePet` viene chiamato solo durante il load del plugin per i giocatori già online. Un giocatore che entra successivamente non riceve automaticamente il proprio ultimo pet tramite questo codice.

## 5. Tick, movimento e animazioni

`PetsHandler` avvia un task Bukkit sincrono ogni tick. `WorkloadRunnable` usa una coda round-robin e lavora per un massimo indicativo di 2,5 ms a tick. Ogni pet registrato possiede un `UserPetsWorkload` che viene reinserito in coda finché l'istanza resta registrata.

Ogni `AbstractUserPet.tick()`:

1. recupera l'entità proprietaria tramite UUID;
2. calcola lo stato vanish/invisibilità;
3. spawna o despawna il pet se cambia la visibilità effettiva;
4. ogni 2 tick calcola la nuova posizione e invia il teletrasporto;
5. prova a eseguire l'ability del tipo, se presente.

Se il proprietario non esiste o non è valido, il tick termina ma il workload rimane registrato: non è presente una pulizia automatica su quit/rimozione dell'entità.

La posizione è calcolata come:

```text
posizione proprietario
+ offset fisso (x, y - 1, z)
+ offset dell'animazione
```

Lo yaw è:

```text
yaw proprietario + 200° + pet.yaw + fix.yawCalibration
```

Animazioni riconosciute: `bounce`, `glide`, `slow_glide`, `glitch`, `side`, `walk`, `follow`, `none`. Un nome sconosciuto ricade su `glide`.

Le animazioni cambiano principalmente l'offset verticale. `walk` usa il blocco più alto alle coordinate X/Z del proprietario; `follow` non implementa inseguimento fisico o interpolazione, ma restituisce soltanto un offset verticale fisso. Gli offset X/Z non vengono ruotati rispetto alla direzione del giocatore.

Per i pet con più skin, `HandUserPet` cambia skin ogni 14 tick. Il README che parla di 4 tick non corrisponde al codice corrente.

## 6. Rendering packet-only

### Pet head e custom model

Entrambi usano un fake armor stand invisibile:

- `HandUserPet` mette l'item nella mano principale;
- `StackUserPet` mette l'item nello slot helmet;
- la scala è `1.15` per le player head e `1.08` per i custom model;
- metadata, attributo scale ed equipaggiamento sono inviati tramite PacketEvents.

Esistono correzioni specifiche per centrare il nametag rispetto alla trasformazione client della mano/teschio in 26.2.

### Pet mob

`MobUserPet` genera un `FakeMobEntity` del tipo Bukkit configurato. Non invia metadata specifici, AI, pose o equipaggiamento: è una rappresentazione packet-only teletrasportata dal tick.

### Distribuzione dei pacchetti

`AbstractFakeEntity` invia ogni pacchetto a **tutti i player presenti nello stesso mondo**. Non applica tracking range, distanza, permessi o filtri per singolo viewer.

Il set dei destinatari viene calcolato solo quando viene inviato un pacchetto. Non esiste una procedura esplicita per mostrare a un giocatore appena entrato fake entity già spawnate; i successivi pacchetti di teleport non sostituiscono necessariamente il pacchetto di spawn mancante.

Quando pet e nametag sono entrambi visibili nello stesso mondo, i due teleport vengono racchiusi tra packet bundle delimiter per ridurre il disallineamento visivo.

## 7. Nametag e rinomina

Con `nametags.enable: true`, il nametag è un secondo fake armor stand; `DisplayNametag` esiste ma non viene selezionato da `NametagGenerator`. Con nametag disabilitati viene usato `NoNametag`.

Il formato supporta:

- `{name}` / `%name%`;
- `{level}` / `%level%`;
- `{player}` / `%player%`.

La rinomina:

- richiede esplicitamente `pet.rename`;
- mantiene i colori soltanto con `pet.rename.color`;
- tronca alla lunghezza visibile `nametags.maxlength`;
- sostituisce le sottostringhe elencate in `nametags.invalidnames` con `*`;
- aggiorna il nametag attivo e persiste il nome in `LastPet`.

La modifica del livello con i comandi cambia invece solo il campo in memoria: non salva `PetLevel` e non rigenera il testo del nametag.

## 8. Visibilità e vanish

La visibilità effettiva è `visible && !vanished`.

`InvisibilityHandler` controlla sempre:

- invisibilità Bukkit;
- pozione di invisibilità;
- modalità spectator;
- stato Spigot hidden players.

Se disponibili, aggiunge CMI, Essentials e SuperVanish/PremiumVanish. Quando lo stato cambia, pet e nametag vengono despawnati o respawnati globalmente.

La voce `vanish` di `config.yml` non viene letta: l'integrazione è sempre applicata. Anche il campo `visible` di ciascun pet non viene caricato da YAML.

## 9. Database e persistenza

Database selezionabili: SQLite, MySQL, MariaDB e PostgreSQL (`POSTGRE`, `POSTGRES` e `POSTGRESQL` sono normalizzati). Se un database remoto fallisce, il plugin tenta il fallback SQLite.

Tabelle previste:

| Tabella | Contenuto | Stato d'uso |
|---|---|---|
| `LastPet` | UUID proprietario, tipo ultimo pet, nome personalizzato | letta/scritta in selezione, rinomina e ripristino |
| `PetLevel` | id, UUID, tipo pet, livello | creata e letta in selezione; gli aggiornamenti runtime non vengono salvati |
| `BoughtPets` | id transazione, UUID, tipo pet | interrogata da `isUnlocked`/`bought`, ma non creata in `AbstractDatabase.load()` e non scritta dal comando buy |

`PetLevel` non dichiara un vincolo univoco su `(owner, petType)`, quindi il modello permette duplicati. Il loader usa il primo risultato.

Le DAO sono ricreate a ogni operazione e le query/scritture sono sincrone sul main thread. Con database remoto questo può produrre lag durante selezione, rename o controlli di possesso.

## 10. Comandi, GUI, permessi ed eventi API

### Comandi

`plugin.yml` registra soltanto `/hpet` con il permesso base `pet.command`. `PetCommand` implementa manualmente `CommandExecutor` e `TabCompleter`.

Sono disponibili nel dispatcher corrente:

- `help`;
- `select <tipo> [player]`;
- `remove [player]`;
- `update [player]`;
- `rename <nome>`;
- `buy <tipo> [player]`;
- `addlevel`, `removelevel`, `setlevel`, `level`.

Le annotazioni `@FCommand` descrivono permessi specifici, ma l'istanza non viene registrata nel command framework: il dispatcher manuale non verifica quei permessi. Di fatto Bukkit controlla soltanto `pet.command`, salvo il controllo esplicito di `pet.rename` e `pet.rename.color`.

La selezione non chiama `PetType.isUnlocked()` né `canSee()`. Quindi `pet.use.<id>` e `pet.see.<id>` non proteggono selezione da comando o GUI nel codice corrente.

`buy` è uno stub: mostra i messaggi WIP/successo ma non controlla saldo, non preleva denaro e non salva `BoughtPets`.

### GUI

`/hpet` senza argomenti apre sempre una GUI 54 slot:

- categorie derivate da `config.yml -> group.*.pets`;
- vista “tutti i pet”;
- paginazione da 45 pet;
- azioni rename, update e remove.

La GUI mostra tutti i tipi caricati e consente di selezionarli senza controlli di abilitazione, visibilità, unlock o permesso. `gui.enable` non viene letto. Le risorse `guis/*.yml` e `legacy_gui.yml` non vengono usate dalla classe GUI corrente.

### API ed eventi

`PetAPI` espone query, selezione, rimozione e ripristino da database. Le collezioni restituite non sono sempre copie difensive: `spawnedPets()` espone direttamente il set interno come `Collection`.

Le classi `PetSelectEvent`, `PetRemoveEvent` e `PetUpdateEvent` esistono, ma non vengono mai emesse. Inoltre tutte ereditano lo stesso `HandlerList` statico definito in `PetEvent`, aspetto da verificare prima di rendere pubblica l'API eventi.

## 11. Configurazione: attiva, parziale o inattiva

| Sezione config | Stato corrente |
|---|---|
| `database.*` | attiva |
| `group.*.pets` | attiva solo per categorie GUI |
| `nametags.enable/format/maxlength/invalidnames` | attiva |
| `fix.yawCalibration` | attiva |
| `enabledPets` | non letta |
| `disabledWorlds` | field presente, mai popolato/usato |
| `gui.enable` | non letta |
| `vanish` | non letta |
| `fix.delay.*` | classi listener presenti, ma non aggiunte a `ModulesHandler` |
| `fix.delay.join` / `joinDatabaseUpdate` | nessun listener join presente |

## 12. Criticità da considerare prima delle modifiche

### Priorità alta: comportamento e access control

1. Selezione senza controllo permessi/unlock, sia da comando sia da GUI.
2. Sottocomandi amministrativi senza verifica dei permessi dichiarati nelle annotazioni.
3. `enabledPets`, `disabledWorlds` e `gui.enable` ignorati.
4. Rimozione non persistente (`LastPet` resta valorizzato).
5. Nessun ripristino/pulizia su join/quit.
6. Ability configurate ma mai costruite/assegnate.
7. Livelli modificati solo in memoria e nametag non aggiornato.

### Priorità alta: stabilità

1. Ordine di caricamento moduli non deterministico.
2. `hasModule()` indica la presenza nella mappa, non che l'hook sia realmente caricato.
3. `BoughtPets` non viene creata nello schema iniziale.
4. Possibile auto-spawn da database anche quando PacketEvents non è disponibile.
5. Workload orfani quando il proprietario sparisce.
6. Operazioni DB remote sincrone sul main thread.

### Priorità media: coerenza prodotto/API

1. Supporto dichiarato per multi-pet/gruppi non presente nel flusso di selezione.
2. Eventi pubblici definiti ma mai emessi.
3. Formati MythicMobs, ModelEngine e ItemsAdder documentati ma non implementati nel loader corrente.
4. Acquisto/Vault non implementato end-to-end.
5. README non allineato su skin interval, placeholder, comandi e API.
6. Fake entity inviate globalmente al mondo senza tracking/viewer lifecycle.

## 13. Punti di modifica consigliati per area

| Obiettivo futuro | File/classi principali |
|---|---|
| filtro pet abilitati e gruppi | `PetTypesHandler`, `PetGui`, `config.yml` |
| permessi/unlock | `PetCommand`, `PetGui`, `AbstractPetType` |
| selezione/rimozione/multi-pet | `PetsHandler`, `PetAPI`, `LastPet` (probabile nuovo schema) |
| join/quit/world lifecycle | nuovo listener, `PetAPI.spawnDatabasePet`, `PetsHandler` |
| rendering o nuovi tipi | `PetTypesHandler`, package `pettypes`, package `userpets`, `fakeentities` |
| movimento | `AbstractUserPet`, package `animations` |
| nametag | `NametagGenerator`, `HandUserPet`, `StackUserPet`, package `nametags` |
| livelli | `AbstractUserPet`, `PetLevel`, `PetCommand`, `NametagGenerator` |
| ability | `AbilitiesHandler`, `AbstractPetType`, package `abilities` |
| acquisto/economia | `PetCommand`, `VaultHook`, `BoughtPets`, `AbstractDatabase` |
| visibilità per viewer | `AbstractFakeEntity` e lifecycle player/tracking |
| API eventi | `PetsHandler`/`PetAPI`, package `api.events` |

## 14. Sequenza prudente per gli interventi

Prima di aggiungere nuove feature conviene stabilizzare le fondamenta in questo ordine:

1. rendere deterministico il lifecycle dei moduli e distinguere moduli presenti da moduli caricati;
2. centralizzare una policy di selezione (`enabled`, mondo, permesso, unlock) usata da comando, GUI e API;
3. definire chiaramente se il prodotto deve supportare uno o più pet per proprietario e adeguare persistenza/API;
4. aggiungere il lifecycle join/quit/world e la pulizia dei workload;
5. correggere persistenza di rimozione e livelli, incluse migrazioni/constraint DB;
6. collegare ability, acquisti e integrazioni soltanto dopo avere definito i relativi modelli;
7. allineare `config.yml`, `pets.yml`, README ed eventi pubblici al comportamento deciso;
8. aggiungere test almeno per parsing tipi, policy di selezione, persistenza e calcolo delle posizioni.

## 15. Limiti della ricognizione

La ricognizione è statica sul sorgente e sulle risorse del repository. Non è stato possibile eseguire `mvn test` perché nell'ambiente corrente non è disponibile il comando Maven e il repository non contiene `mvnw`. Nel progetto non è presente una directory `src/test`, quindi al momento non risultano test automatici versionati da eseguire.
