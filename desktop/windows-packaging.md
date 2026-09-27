# Packaging Windows

- GUID produit WiX (stable, généré une fois pour toutes les versions) : `381c7875-31dc-41ec-a229-5a40fcf556f0`
- Le keystore/certificat n'est pas requis : l'installateur est non signé (SmartScreen affichera un avertissement « éditeur inconnu », il faut cliquer « Exécuter quand même »)
- jpackage `--type exe` nécessite WiX 3.x (candle.exe + light.exe accessibles dans le PATH)
- Sur la CI, WiX est extrait depuis wix314-binaries.zip dans `wix314-binaries/`
