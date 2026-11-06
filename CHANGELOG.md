# Journal des changements

## 0.1.0

Première version.

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
