# Journal des changements

## 0.1.1

Même plugin, construit pour PhpStorm et les IDE **2026.2 et plus** (builds 262+) avec la nouvelle API LSP de la
plateforme (`LspClient`, `LspIntegrationProvider`…), sans aucun usage d'API dépréciée. La 0.1.0 reste la version
des IDE 2025.3 et 2026.1 (builds 253 à 261), dont l'API LSP est l'ancienne. Aucun changement de comportement.

## 0.1.0

Première version, pour les IDE 2025.3 et 2026.1 (builds 253 à 261).

- Type de fichier `qlang` pour `*.q`, avec l'icône du langage.
- Coloration par la grammaire TextMate de qlang (`source.qlang`), embarquée : rien à régler.
- Serveur de langage : `qlang lsp` est lancé pour les fichiers `*.q` avec le client LSP natif de la plateforme
  (erreurs, infobulles, complétion, aller à la définition, références, renommage, plan du fichier).
- Réglage **Settings > Languages & Frameworks > qlang** pour le chemin de l'exécutable ; sinon `qlang` est cherché
  dans le PATH, puis dans `~/.local/bin`, `~/.cargo/bin` et `/usr/local/bin`. Un message clair s'affiche s'il est introuvable.
- **Mise en forme sur Entrée** : l'IDE n'envoie jamais `textDocument/onTypeFormatting`, le plugin le fait. Après
  `fun`, `struct`, `impl`, `enum`, `if … then`… le `end` et l'indentation sont ajoutés.
- Espace après `case`, `else` ou `elseif` : la ligne se remet à son niveau.
- *Reformat Code* (`textDocument/formatting`, c'est-à-dire `qlang fmt`) pour les fichiers `*.q`.
- Commentaires (`--` et `--( … --)`) avec *Comment with Line/Block Comment*.
- Corrigé avant publication : chemin de recherche dans le `PATH`, API de formatage et sélecteur de fichier non dépréciés.
