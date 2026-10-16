<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="120" alt="">
</p>

<h1 align="center">Bona vortaro</h1>

<p align="center">
  <i>Vortaro de Esperanto por Android, sen interreto</i><br>
  <i>An offline Esperanto dictionary for Android</i>
</p>

<p align="center">
  <a href="https://f-droid.org/packages/io.github.kotchwane.bonavortaro/"><img src="https://f-droid.org/badge/get-it-on-eo.png" height="80" alt="Elŝutu ĝin de F-Droid / Get it on F-Droid"></a>
</p>

<p align="center">
  <a href="#esperanto">Esperanto</a> · <a href="#english">English</a>
</p>

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="200" alt="La serĉo">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="200" alt="Artikolo">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="200" alt="La oficialeco de vorto">
</p>
<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="200" alt="La listo de Oficiala Aldono">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="200" alt="Teni vorton premita">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="200" alt="Foliumi la vortaron, nokte">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width="200" alt="Vorto, kiu mankas en la vortaro">
</p>

---

## Esperanto

**Bona vortaro** estas vortaro de Esperanto por Android. Ĝi enhavas la tutan
[Retan Vortaron](https://reta-vortaro.de/) (ReVo), la liberan vortaron de
Esperanto, kun ĝiaj tradukoj en pli ol 170 lingvojn. Ĉio estas en la telefono:
ĝi funkcias sen interreto, kaj petas neniun permeson de la telefono. Ĝi naskiĝis
el [PReVo](https://github.com/bpeel/prevo), la vortaro de Neil Roberts.

Por la franca, ĝi aldonas la tradukojn, kiujn Reta Vortaro ne havas, el
[Vikivortaro](https://fr.wiktionary.org/).

### Kion ĝi faras

- **Serĉi** en Esperanto kaj en viaj aliaj lingvoj, kun aŭ sen supersignoj
  (*cevalo* aŭ *cxevalo* trovas *ĉevalo*), kaj per ĵokeroj: *\*ologio* trovas
  ĉiujn vortojn, kiuj finiĝas per „ologio“, kaj *?rbo* trovas *arbo* kaj *urbo*.
- **Legi** klarajn artikolojn: difinoj, ekzemploj, rimarkoj, ligiloj inter la
  vortoj, kaj la tradukoj en la lingvoj, kiujn vi elektas, en via ordo, ankaŭ
  tiuj de la ekzemploj: *bonjour* trovas *saluton!* ĉe *saluto*.
- **Scii, kiuj vortoj estas oficialaj**: la etikedoj „Fundamento“ kaj
  „9a Oficiala Aldono“ klarigas sin per unu tuŝo, kaj la listo de ĉiu Aldono
  estas foliumebla.
- **Teni vorton premita** por trovi ĝin en la vortaro, kiel ajn ĝi estas
  skribita: *arbojn* trovas *arbo*, *nomatajn* trovas *nomi*.
- **Foliumi la vortaron** de radiko al radiko, kiel presitan vortaron, aŭ
  malkovri hazardan radikon.
- **Diveni vortojn, kiuj mankas** en la vortaro: la serĉo proponas, el kiuj
  radikoj kaj afiksoj ili eble konsistas (*malsanulejestrano*: malsanulej +
  estr + ano).
- **Pli da francaj tradukoj** el Vikivortaro, kie Reta Vortaro ne havas ilin:
  miloj da pliaj francaj vortoj troveblaj (*potiron* trovas *kukurbo*), kaj
  miloj da tradukoj aldonitaj en la artikoloj, kun punktita substreko. Ili
  aperas nur, se la franca estas inter viaj lingvoj.
- La **lastaj serĉoj**, tagaj kaj noktaj koloroj („papero“ kaj „stelnokto“), la
  grandeco de la teksto, kaj gvidilo en la agordoj.

### Kial „Bona vortaro“?

La nomo omaĝas al [*La bona lingvo*](http://claudepiron.free.fr/livres/bonalingvo.htm)
de Claude Piron. En ĝi li montras, ke Esperanto estas plej klara kaj plej bela,
kiam oni uzas ĝiajn simplajn radikojn kaj ĝian riĉan vortfaradon. *Bona vortaro*
volas esti same: simpla, klara, kaj agrabla por uzi.

### Deveno

*Bona vortaro* baziĝas sur [PReVo](https://github.com/bpeel/prevo) de Neil Roberts,
kies historio estas konservita en ĉi tiu deponejo. Ĝi havas novan interfacon
(Jetpack Compose), novan aspekton kaj novajn funkciojn. La datumoj venas de la
redaktoroj de [Reta Vortaro](https://github.com/revuloj/revo-fonto); la aldonaj
francaj tradukoj, de la kontribuantoj de la franca [Vikivortaro](https://fr.wiktionary.org/),
per la eltiro de [Kaikki](https://kaikki.org/).

La ilo, kiu konvertas Retan Vortaron por la apo, [prevodb](https://github.com/bpeel/prevodb),
ankaŭ de Neil Roberts, troviĝas en `prevodb/`, kun sia historio.

### Konstrui la apon

La deponejo ne enhavas la datumojn de la vortaro: la konstruado kreas ilin el la
fontoj de Reta Vortaro (en `extern/`), per prevodb. Necesas:

- la Android SDK kaj JDK 17 aŭ pli nova (ekzemple tiu de Android Studio);
- C-kompililo, autoconf, automake, libtool, gettext, pkg-config, glib kaj expat.
  - Debian/Ubuntu: `sudo apt install build-essential autoconf automake libtool gettext autopoint pkg-config libglib2.0-dev libexpat1-dev`
  - macOS (Homebrew): `brew install autoconf automake libtool gettext pkgconf glib expat`,
    kaj `export PKG_CONFIG_PATH=$(brew --prefix expat)/lib/pkgconfig`

```bash
git clone --recursive https://github.com/kotchwane/bona-vortaro.git
cd bona-vortaro
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # la testoj
```

La unua konstruado daŭras kelkajn minutojn: ĝi kompilas prevodb kaj konvertas la
tutan vortaron.

La datumoj de Vikivortaro, male, estas en la deponejo
(`app/src/main/assets/vikivortaro/`), por ke ĉiu konstruado estu sama. Por
renovigi ilin el la nuna eltiro de Kaikki (ĉirkaŭ 740 MB, legataj sen konservo):

```bash
python3 scripts/vikivortaro.py https://kaikki.org/dictionary/downloads/fr/fr-extract.jsonl.gz
```

| Dosierujo | Enhavo |
|---|---|
| `app/` | la apo por Android (Kotlin kaj Compose, kun Java-kerno el PReVo) |
| `prevodb/` | la konvertilo de la XML de ReVo al la dosieroj de la apo (C) |
| `extern/revo-fonto`, `extern/voko-grundo` | la fontoj de Reta Vortaro (submoduloj) |
| `app/src/main/assets/vikivortaro/` | la francaj tradukoj el Vikivortaro (CC BY-SA 4.0) |
| `scripts/` | la konstruado de la vortaro, la eltiro el Vikivortaro, la piktogramo |
| `fastlane/` | la tekstoj kaj ekrankopioj por la vendejoj |

### Permesilo

*Bona vortaro* estas libera programaro, laŭ la GNU General Public License,
versio 2 (vidu [`COPYING`](COPYING)), kiel PReVo.

La datumoj de Vikivortaro, verkitaj de ĝiaj kontribuantoj, estas laŭ la permesilo
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). Ili restas en
apartaj dosieroj, en `app/src/main/assets/vikivortaro/`, kies kapoj diras ilian
fonton kaj daton; la apo montras ilian fonton ĉe ĉiu paĝo, kie ili aperas.

---

## English

**Bona vortaro** ("good dictionary") is an Esperanto dictionary for Android.
It contains the whole [Reta Vortaro](https://reta-vortaro.de/) (ReVo), the free
Esperanto dictionary, with its translations into more than 170 languages.
Everything is on the phone: it works without Internet access, and asks
for no permission at all. It grew out of [PReVo](https://github.com/bpeel/prevo),
Neil Roberts' dictionary.

For French, it adds the translations that Reta Vortaro lacks, from the French
[Wiktionary](https://fr.wiktionary.org/) ("Vikivortaro").

### What it does

- **Search** in Esperanto and in your other languages, with or without accents
  (*cevalo* or *cxevalo* finds *ĉevalo*), and with wildcards: *\*ologio* finds
  every word ending in "ologio", and *?rbo* finds *arbo* and *urbo*.
- **Read** clear articles: definitions, examples, remarks, links between words,
  and the translations in the languages you choose, in your order, the
  examples' too: *bonjour* finds *saluton!* under *saluto*.
- **Know which words are official**: the "Fundamento" and "9a Oficiala Aldono"
  badges explain themselves with a tap, and the list of each Official Addition
  can be browsed.
- **Long-press a word** to look it up, whatever its form: *arbojn* finds *arbo*,
  *nomatajn* finds *nomi*.
- **Leaf through the dictionary** from root to root, like a printed one, or
  discover a random root.
- **Guess missing words**: when a word isn't in the dictionary, the search
  suggests the roots and affixes it may be built from (*malsanulejestrano*:
  malsanulej + estr + ano).
- **More French translations** from Wiktionary, where Reta Vortaro has none:
  thousands more French words to search (*potiron* finds *kukurbo*), and
  thousands of translations added in the articles, with a dotted underline.
  They only show if French is among your languages.
- **Recent searches**, day and night colours ("papero" and "stelnokto"), text
  size, and a guide in the settings.

The interface is in Esperanto.

### Why "Bona vortaro"?

The name is a tribute to [*La bona lingvo*](http://claudepiron.free.fr/livres/bonalingvo.htm)
("The good language") by Claude Piron, who shows that Esperanto is at its
clearest and most beautiful when it uses its simple roots and its rich word
building. *Bona vortaro* aims to be the same: simple, clear, and pleasant to use.

### Origins

*Bona vortaro* is based on [PReVo](https://github.com/bpeel/prevo) by Neil Roberts,
whose history is kept in this repository. It has a new interface (Jetpack
Compose), a new look and new features. The data comes from the editors of
[Reta Vortaro](https://github.com/revuloj/revo-fonto); the added French
translations, from the contributors of the French [Wiktionary](https://fr.wiktionary.org/),
through [Kaikki](https://kaikki.org/)'s extract.

The tool that converts Reta Vortaro for the app, [prevodb](https://github.com/bpeel/prevodb),
also by Neil Roberts, lives in `prevodb/`, with its history.

### Building

The repository doesn't contain the dictionary data: the build generates it from
the sources of Reta Vortaro (in `extern/`), with prevodb. You need:

- the Android SDK and JDK 17 or newer (for example the one of Android Studio);
- a C compiler, autoconf, automake, libtool, gettext, pkg-config, glib and expat.
  - Debian/Ubuntu: `sudo apt install build-essential autoconf automake libtool gettext autopoint pkg-config libglib2.0-dev libexpat1-dev`
  - macOS (Homebrew): `brew install autoconf automake libtool gettext pkgconf glib expat`,
    and `export PKG_CONFIG_PATH=$(brew --prefix expat)/lib/pkgconfig`

```bash
git clone --recursive https://github.com/kotchwane/bona-vortaro.git
cd bona-vortaro
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # the tests
```

The first build takes a few minutes: it compiles prevodb and converts the whole
dictionary.

The Wiktionary data, on the other hand, is in the repository
(`app/src/main/assets/vikivortaro/`), so that every build is the same. To refresh
it from Kaikki's current extract (about 740 MB, streamed, not kept):

```bash
python3 scripts/vikivortaro.py https://kaikki.org/dictionary/downloads/fr/fr-extract.jsonl.gz
```

| Folder | What it holds |
|---|---|
| `app/` | the Android app (Kotlin and Compose, with a Java core from PReVo) |
| `prevodb/` | the converter from ReVo's XML to the app's files (C) |
| `extern/revo-fonto`, `extern/voko-grundo` | the sources of Reta Vortaro (submodules) |
| `app/src/main/assets/vikivortaro/` | the French translations from Wiktionary (CC BY-SA 4.0) |
| `scripts/` | the dictionary build, the Wiktionary extraction, the icon |
| `fastlane/` | the store texts and screenshots |

### License

*Bona vortaro* is free software under the GNU General Public License, version 2
(see [`COPYING`](COPYING)), like PReVo.

The Wiktionary data, by its contributors, is under the
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) license. It stays
in separate files, in `app/src/main/assets/vikivortaro/`, whose headers give its
source and date; the app credits it on every page where it appears.
