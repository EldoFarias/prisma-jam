# Prisma Jam — contexto do projeto

Puzzle de deslizar blocos de gelatina até a porta da mesma cor (estilo Color Block Jam), contra o relógio.
Android, só Play Store. Kotlin + Jetpack Compose (desenho em Canvas), sem engine.
Dono: Eldo (EldoFarias). Comunicação em português. Monetização: AdMob (banner, intersticial, premiado).

## Como compilar e rodar

```bash
./gradlew installDebug        # pacote com.farias.prismajam.debug
adb logcat -s PrismaJam AndroidRuntime VibratorManagerService
```

- Debug usa sempre IDs de **teste** do AdMob; release lê os IDs reais de variáveis/secrets (`ADMOB_*`) e
  **falha** se faltar algum ou se for ID de teste.
- `applicationId` = `com.farias.prismajam` (não mudar depois de publicar).
- Cada push na `main` gera o APK em Releases → `teste` (`.github/workflows/android.yml`). Erros de compilação
  aparecem como anotações do job.
- `.github/workflows/screenshots.yml` (manual): prints no emulador em vários tamanhos → Releases → `telas`.
- Atalhos só no debug: `adb shell am start -n com.farias.prismajam.debug/com.farias.prismajam.MainActivity --es screen game --ei level 12`
  (`home`, `chest`, `game`, `daily`, `map`, `themes`, `stats`; `--ez landscape true`; `--ez shatter true` estilhaça blocos em loop).

## Regras do jogo

- Arrasta-se um bloco (polyominó) célula a célula; ele sai do tabuleiro quando empurrado contra uma porta da sua cor
  que cubra toda a largura dele naquele lado, e só se **cada quadrado** tiver caminho livre até a borda.
- Fases infinitas e determinísticas (`JamLevels.level(n)`). A cada 10 fases uma "difícil" (coroa no mapa): só 1 bloco
  livre no início e muitas rodadas; a seguinte é mais leve. Fases normais: no máximo 2–4 blocos livres no início.
  O gerador monta de trás pra frente e depois "aperta" (blocos que fecham a saída dos livres), sempre conferindo com o solver.
- Desafio diário: `JamLevels.daily(dia)`, igual para todos no dia; estrelas 1–3 por tempo restante.
- Mecânicas (a partir da fase, `JamLevels.*_FROM`): pedra 8, gelo 12 (número cai a cada saída), setas 18 (só um eixo),
  duas camadas 25 (`inner`: a porta da cor de fora só tira a casca), chave/cadeado 35 (`key`/`lock`), portas com tempo 45
  (`Gate.timed`, ciclo em `GameViewModel.GATE_*`; o solver ignora o tempo). Na 1ª vez aparece um aviso (`Mechanic`).
- Relógio começa no primeiro toque. Poderes: Dica (`JamSolver.hint`), Congelar (10 s), Martelo e Tesoura (`JamBoard.cut`).
  +30 s por vídeo uma vez por fase.
- Mapa (`ui/MapScreen.kt`): mundos de 20 fases com as cores dos temas, baú a cada 5 fases, coroa nas fases difíceis.

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
- Tabuleiro reto visto de cima (o Eldo não gostou da inclinação), largura da tela menos 32 dp por lado; blocos têm espessura 3D. Toques chegam no espaço do tabuleiro; efeitos na tela usam `vm.screenAt()`. `android:appCategory="game"` mantém retrato no Android 16.

## Forma de trabalho

- Não tirar prints no emulador: só publicar o APK (push na `main`); o Eldo testa no celular e manda prints.

## Pendências

1. Testar no celular e ajustar dificuldade/tempo das fases.
2. Publicação (roteiro em `store/PUBLICAR.md`): textos da loja prontos (`store/listings.py`), ícone/arte novos
   (`tools/make_art.py`), política em `docs/privacy.html` (o Eldo precisa ativar o GitHub Pages: main /docs).
   Falta, por último: keystore + secrets `KEYSTORE_*`, IDs reais do AdMob e prints da loja.
