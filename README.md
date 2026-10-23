# Prisma Blocks

Jogo de quebra-cabeça de blocos 8x8 para Android. Feito em Kotlin com Jetpack Compose e monetizado com AdMob.

## O que já tem

- Tabuleiro 8x8 com 39 formatos de peça e 3 peças por rodada. O jogo sempre garante que existe uma jogada possível quando gera novas peças.
- Pontuação com combos e bônus por limpar o tabuleiro inteiro. O recorde fica salvo no aparelho.
- A partida é salva automaticamente. O jogador pode fechar o app no ônibus e continuar depois.
- Visual neon/vidro: blocos em estilo gema, fundo com aurora animada, prévia das linhas que serão limpas, partículas, textos de elogio, tremida de tela, vibração e sons próprios.
- Anúncios:
  - banner adaptativo;
  - intersticial a cada 2 partidas, com intervalo mínimo de 90 segundos;
  - vídeo premiado para continuar a partida, uma vez por partida;
  - tela de consentimento (UMP) para GDPR e LGPD.
- Idiomas: português, inglês e espanhol.

## Compilar sem PC (GitHub Actions)

Cada `push` na branch `main` faz o seguinte:

1. Gera o **APK de teste**, que usa anúncios de teste do Google.
2. Publica esse APK em **Releases → "APK de teste"**. É só baixar no celular e instalar.
3. Gera o **AAB de produção** para a Play Store, desde que os secrets de assinatura estejam configurados.

### Secrets do repositório

Configure em *Settings → Secrets and variables → Actions*.

| Secret | Para quê |
|---|---|
| `KEYSTORE_BASE64` | chave de upload (.jks) em base64 |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | dados da chave |
| `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`, `ADMOB_REWARDED_ID` | seus IDs reais do AdMob (opcional; sem eles, a versão de produção usa os IDs de teste) |

## Antes de publicar

- [ ] Criar o app no AdMob e configurar os 4 IDs reais nos secrets.
- [ ] Gerar a chave de upload e ativar a assinatura de apps do Google Play (Play App Signing).
- [ ] Publicar a política de privacidade (GitHub Pages) e informar o link na Play Console.
- [ ] Usar os materiais da loja em `store/`: ícone 512x512 e gráfico de destaque 1024x500.
- [ ] Conferir se o nome "Prisma Blocks" está livre na Play Store.

> O `applicationId` é `com.farias.prismajam`. Ele não pode mudar depois da primeira publicação.
