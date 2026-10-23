# Prisma Jam — contexto do projeto

Puzzle de deslizar blocos de gelatina até a porta da mesma cor (estilo Color Block Jam), contra o relógio.
Android, só Play Store. Kotlin + Jetpack Compose (desenho em Canvas), sem engine.
Dono: Eldo (EldoFarias). Comunicação em português. Monetização: AdMob (banner, intersticial, premiado).

## Como compilar e rodar

```bash
./gradlew installDebug        # pacote com.farias.prismajam.debug
adb logcat -s PrismaJam AndroidRuntime VibratorManagerService
```

- Debug usa sempre IDs de **teste** do AdMob; release lê os IDs reais de variáveis/secrets (`ADMOB_*`).
- `applicationId` = `com.farias.prismajam` (não mudar depois de publicar).
- Cada push na `main` gera o APK em Releases → `teste` (`.github/workflows/android.yml`). Erros de compilação
  aparecem como anotações do job.
- `.github/workflows/screenshots.yml` (manual): prints no emulador em vários tamanhos → Releases → `telas`.
- Atalhos só no debug: `adb shell am start -n com.farias.prismajam.debug/com.farias.prismajam.MainActivity --es screen game --ei level 12`
  (`home`, `chest`, `game`, `daily`, `map`, `themes`, `stats`; `--ez landscape true`; `--ez shatter true` estilhaça blocos em loop).

## Regras do jogo

- Arrasta-se um bloco (polyominó) célula a célula; ele sai do tabuleiro quando empurrado contra uma porta da sua cor
  que cubra toda a largura dele naquele lado.
- Pedras (fase 8+) são fixas. Gelo (fase 12+): o número diminui a cada bloco que sai; com 0 o bloco solta.
  Setas (fase 18+): bloco só anda na horizontal ou vertical.
- Relógio começa no primeiro toque. Poderes: Congelar (10 s) e Martelo (quebra um bloco). +30 s por vídeo uma vez por fase.

## Arquitetura

| Pasta | O que tem |
|---|---|
| `game/Jam.kt` | Regras puras (sem Android): tabuleiro, formas, portas, movimento, saída, gelo, `JamSolver`. Testável em JVM. |
| `game/Levels.kt` | Fases geradas pelo número (determinístico). Montadas de trás pra frente → sempre têm solução; o solver confere. |
| `game/GameViewModel.kt` | Estado da partida para a UI: arrastar, animações, tempo, poderes, vitória/derrota, missões. |
| `data/Progress.kt` | Tudo que persiste: fase, moedas, temas, recordes, sequência, missões, baú. |
| `data/Missions.kt` | 3 missões diárias determinísticas por data. |
| `ui/JamPainter.kt` | Desenho dos blocos de gelatina, portas, gelo, pedras (paths em cache). |
| `ui/` | Splash, Home, Game, Themes, Stats, Overlays (pausa/vitória/tempo esgotado), Rewards. |
| `ads/AdsManager.kt` | AdMob + consentimento UMP. |
| `audio/` | `SoundManager`, `MusicPlayer`, `Haptics` (vibração com atributos de mídia/jogo — necessário no MIUI). |
| `tools/` | `strings.py` (traduções), `make_art.py` (ícones e arte da loja), `JamTest.kt` (teste das fases na JVM). |

## Convenções

- Textos em 8 idiomas (`values` en, `pt`, `es`, `fr`, `de`, `tr`, `in`, `hi`): editar `tools/strings.py` e rodar
  `python3 tools/strings.py app/src/main/res`.
- Gradientes radiais com raio 0 derrubam o app: proteger tamanhos < 1 px.
- Telas se adaptam: compacto e deitado; em tablet o app inteiro é desenhado maior (`Scaled` no MainActivity).
- Tabuleiro inclinado em 3D (`graphicsLayer rotationX`): toques chegam no espaço do tabuleiro; efeitos na tela usam `vm.screenAt()`. `android:appCategory="game"` mantém retrato no Android 16.

## Pendências

1. Testar no celular e ajustar dificuldade/tempo das fases.
2. Publicação: política de privacidade, textos/prints da loja, keystore + secrets `KEYSTORE_*`, IDs reais do AdMob.
