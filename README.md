# qlang-jetbrains

Le plugin JetBrains du langage [qlang](https://github.com/quenti77/qlang) : les fichiers `*.q` colorés, avec
erreurs, complétion, définition, renommage… et surtout le `end` et l'indentation ajoutés **quand on appuie sur Entrée**.

Il s'appuie sur le client LSP **natif** de la plateforme IntelliJ (pas sur LSP4IJ) et sur le plugin TextMate fourni avec
l'IDE. Il vise **PhpStorm et les IDE JetBrains payants récents** (2025.3 et plus, en deux versions du plugin : IntelliJ IDEA, WebStorm, PyCharm
Professional, RubyMine, GoLand, CLion…). Les IDE gratuits (Community) n'ont pas le client LSP natif.

## Ce qu'il fait

| | |
| --- | --- |
| Type de fichier | `*.q`, avec l'icône de qlang |
| Coloration | la grammaire TextMate de qlang (`source.qlang`), embarquée dans le plugin : aucun réglage |
| Serveur | `qlang lsp` : erreurs pendant la frappe, infobulles, complétion, aller à la définition, références, renommage, plan du fichier |
| **Entrée** | après `fun f()`, `struct P`, `impl X`, `enum E`, `if x then`… le plugin demande au serveur `textDocument/onTypeFormatting` (`\n`) et applique la réponse : `end` ajouté, ligne indentée |
| Espace | après `case`, `else`, `elseif`, la ligne se remet à son niveau (`onTypeFormatting` avec `" "`) |
| Reformat Code | `textDocument/formatting`, c'est-à-dire `qlang fmt`, pour les `*.q` |
| Commentaires | `--` et `--( … --)` |

L'IDE (comme LSP4IJ) n'envoie jamais `onTypeFormatting` sur Entrée : sans ce plugin, taper Entrée après `fun salut()`
n'ajoute ni `end` ni indentation. Le plugin s'en charge dans [`QlangOnType.kt`](src/main/kotlin/dev/qlang/jetbrains/QlangOnType.kt).

## Installation

Il faut l'exécutable `qlang` (voir [l'installation de qlang](https://github.com/quenti77/qlang#installation)), version
1.2.1 ou plus pour la complétion des types dans `let x: T` ; `qlang lsp` suffit sinon.

Le plugin n'est pas publié sur le Marketplace. Depuis les sources :

```sh
./gradlew buildPlugin
```

puis, dans l'IDE, **Settings > Plugins > ⚙ > Install Plugin from Disk…** et choisir `build/distributions/qlang-jetbrains-0.1.0.zip`.

## Réglages

**Settings > Languages & Frameworks > qlang** : le chemin de l'exécutable `qlang`. Vide, il est cherché dans le PATH puis
dans `~/.local/bin`, `~/.cargo/bin` et `/usr/local/bin` (un IDE lancé depuis le bureau n'a pas toujours le PATH du
terminal). S'il est introuvable, une notification l'explique et propose d'ouvrir les réglages.

## Développer

```sh
./gradlew runIde      # un IDE de test avec le plugin
./gradlew test        # les essais (voir plus bas)
./gradlew buildPlugin # build/distributions/*.zip
./gradlew verifyPlugin
```

### Deux variantes

L'API LSP de la plateforme a changé de noms en 2026.2 (`LspServer` → `LspClient`…), et la nouvelle n'existe pas avant.
Le plugin est donc compilé en deux variantes (les sources communes sont dans `src/main`, le reste dans `src/lsp-*`) :

| Variante | Version | IDE | Sources | Commande |
|---|---|---|---|---|
| `legacy` (défaut) | 0.1.0 | 2025.3 et 2026.1 (253 à 261) | `src/lsp-legacy` | `./gradlew buildPlugin` |
| `new` | 0.1.1 | 2026.2 et plus (262+) | `src/lsp-new` | `./gradlew -PlspVariant=new buildPlugin` |

La variante `new` a besoin d'un JDK 25 (celui de l'IDE 2026.2) ; `verifyPlugin -PverifyAlso=2026.1,2026.2` vérifie
d'autres versions que celle de compilation.

Par défaut le plugin est construit contre PhpStorm 2025.3 (téléchargé, JDK 21 requis). Pour utiliser un PhpStorm déjà
installé, ce qui évite le téléchargement :

```sh
./gradlew runIde -PplatformLocalPath=$HOME/.local/share/JetBrains/Toolbox/apps/phpstorm
```

### Essais

`./gradlew test` vérifie le type de fichier, la grammaire TextMate et l'exécutable. Les essais avec le vrai serveur
(Entrée après `fun`, `struct`, `impl`, `enum`, `if`, dans un corps, avec un bloc dessous ; espace après `case` et `else`)
ne tournent que si `QLANG_BIN` donne un `qlang` :

```sh
QLANG_BIN=$(which qlang) ./gradlew test
```

## Pas encore fait

- Les commandes `qlang run` / `qlang test` depuis l'IDE (configurations d'exécution) : à ajouter.
- Les fichiers `qlang.toml`.
- Une publication sur le Marketplace JetBrains.

## Licence

MIT, voir [LICENSE](LICENSE).
