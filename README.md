# Prisma Jam

Puzzle casual para Android: deslize os blocos de gelatina até a porta da mesma cor antes do tempo acabar.
Kotlin + Jetpack Compose, monetizado com AdMob, publicado só na Play Store.

## O que tem

- Fases infinitas geradas pelo número da fase, todas com solução garantida (montadas de trás pra frente e conferidas por um solver).
- Dificuldade crescente: tabuleiro de 5x5 a 8x8, 2 a 7 cores, pedras (fase 8+), gelo (fase 12+) e blocos com seta (fase 18+).
- Relógio que só começa no primeiro toque; poderes Congelar e Martelo (moedas ou vídeo); +30 s por vídeo.
- Moedas, temas, missões diárias, baú diário, recordes, sequência de vitórias, tutorial na fase 1.
- Visual de gelatina com brilho, portas neon, partículas, sons, música e vibração.
- Anúncios: banner adaptativo, intersticial entre fases, premiado; consentimento UMP (GDPR/LGPD).
- 8 idiomas: en, pt, es, fr, de, tr, id, hi.

## APK de teste sem PC

Cada `push` na `main` roda o GitHub Actions, que gera o APK com anúncios de teste e publica em
**Releases → "teste"** (`PrismaJam-teste.apk`). É só baixar no celular e instalar.

## Antes de publicar

Configure em *Settings → Secrets and variables → Actions*:

| Secret | Para quê |
|---|---|
| `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Assinar o AAB de produção |
| `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`, `ADMOB_REWARDED_ID` | IDs reais do AdMob |

Ícones e arte da loja: `python3 tools/make_art.py .` (saída em `store/` e nos `mipmap-*`).
