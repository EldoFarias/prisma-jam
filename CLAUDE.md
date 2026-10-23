# Prisma Blocks — contexto do projeto

Jogo de blocos 8x8 (estilo Block Blast) para Android, só Play Store. Kotlin + Jetpack Compose, sem engine.
Dono: Eldo (EldoFarias). Comunicação em português. Monetização: AdMob (banner, intersticial, premiado).

## Como compilar e rodar

```bash
./gradlew installDebug        # instala no celular conectado (pacote com.farias.prismajam.debug)
adb logcat -s PrismaBlocks AndroidRuntime Vibrator VibratorManagerService
```

- Debug usa sempre IDs de **teste** do AdMob; release lê os IDs reais de variáveis/secrets (`ADMOB_*`).
- `applicationId` = `com.farias.prismajam` (não mudar depois de publicar).
- A cada push na `main`, o GitHub Actions (`.github/workflows/android.yml`) gera o APK de teste em Releases → `teste`.
  Erros de compilação aparecem como anotações do job.
- `.github/workflows/screenshots.yml` (manual): abre o app no emulador em 4 tamanhos e publica os prints em Releases → `telas`.
- Atalhos só no debug: `adb shell am start -n com.farias.prismajam.debug/com.farias.prismajam.MainActivity --es screen game`
  (`home`, `chest`, `game`, `adventure_game`, `map`, `themes`, `stats`; `--ez landscape true`).

## Arquitetura

| Pasta | O que tem |
|---|---|
| `game/GameEngine.kt` | Regras puras (sem Android): tabuleiro, peças, linhas, combo, gemas, desfazer, bomba, salvar/restaurar. Testável em JVM. |
| `game/Shapes.kt` | 39 formatos de peça com pesos. |
| `game/Adventure.kt` | Fases geradas pelo número (gemas/linhas/pontos), estrelas por jogadas. Calibrado por simulação (`tools/AdventureBench.kt`). |
| `game/GameViewModel.kt` | Estado da partida para a UI, modos CLASSIC/DAILY/ADVENTURE, poderes, efeitos, missões. |
| `data/Progress.kt` | Tudo que persiste (SharedPreferences): moedas, temas, recordes, sequência, missões, baú, fases. |
| `data/Missions.kt` | 3 missões diárias determinísticas por data. |
| `ui/` | Telas Compose: Splash, Home, Game, Adventure (mapa), Themes, Stats, Overlays (pausa/fim/vitória), Rewards (missões/baú). |
| `ui/GemPainter.kt`, `Fx.kt`, `Background.kt` | Desenho dos blocos, partículas/textos flutuantes, fundo aurora. |
| `ads/AdsManager.kt` | AdMob + consentimento UMP; intersticial a cada 2 partidas e ≥90 s. |
| `audio/` | `SoundManager` (SoundPool), `MusicPlayer` (loop), `Haptics` (vibração). |
| `notify/DailyReminder.kt` | Lembrete diário 7h30 via WorkManager. |
| `tools/` | Scripts que geram ícone, sons, música e traduções (`translations.py`), testes da lógica. |

Telas se adaptam: compacto (altura < 660 dp), tablet (escala 1.3 nos controles) e layout deitado (largura > altura).
`android:appCategory="game"` mantém a orientação retrato em tablets no Android 16.

## Convenções

- Textos em 8 idiomas: `values` (en), `pt`, `es`, `fr`, `de`, `tr`, `in`, `hi`. Ao criar string nova, adicionar em en/pt/es
  e nos dicionários de `tools/translations.py`, depois rodar `python3 tools/translations.py app/src/main/res`.
- Gradientes radiais com raio 0 derrubam o app: sempre proteger tamanhos < 1 px antes de desenhar.

## Pendências

1. **Vibração não funciona no celular do Eldo** (vibração ligada no jogo). Já trocamos `performHapticFeedback` por
   `Vibrator`/`VibratorManager` direto (`audio/Haptics.kt`) com permissão `VIBRATE`, e ainda assim não vibra.
   Hipóteses para investigar com o celular no cabo:
   - Vibração sem atributos é tratada como "toque" e bloqueada pela intensidade de vibração ao toque da ROM (MIUI/HyperOS).
     Testar `vibrate(effect, VibrationAttributes.createForUsage(USAGE_MEDIA))` (API 33+) ou
     `vibrate(effect, AudioAttributes USAGE_GAME)` (API 26–32).
   - Modo silencioso / Não perturbe bloqueando vibrações de mídia.
   - Conferir no logcat (`VibratorManagerService`) se a vibração chega e por que é ignorada.
   - Confirmar que `Progress.vibrationOn` está `true` e que `vm.haptic` está sendo chamado.
2. Preparar publicação: política de privacidade (GitHub Pages), textos/prints da loja em 8 idiomas,
   keystore de upload + secrets `KEYSTORE_*`, IDs reais do AdMob nos secrets.
3. Fase 2: login com Google Play Games (salvamento na nuvem, ranking, conquistas).
