# Vortex TV — aplicativo de streaming, UI/UX 01

**Este repositório contém CÓDIGO-FONTE, não contém APK compilado e não representa a versão Beta funcional.** Não confundir com **Vortex TV OS**, que é o firmware da TV Box.

## O que foi escrito

- Android nativo em **Java**, `minSdk 25` (Android 7.1), incluindo lançamento em Android TV e celulares.
- Interface de navegação familiar: menu superior, banner principal, fileiras horizontais de pôsteres, categorias, busca e favoritos.
- Suporte de foco por D-pad; o toque também funciona.
- Importação de arquivo M3U via Storage Access Framework, playlist HTTPS (até 4 MB), vídeos diretos HTTPS e guia local XMLTV (até 20 MB).
- Persistência local da biblioteca e favoritos, sem login, ads ou backend.
- Player interno Media3/ExoPlayer 1.11.1 com suporte previsto a HLS/DASH/progressivo.
- Sem carregamento de DEX de terceiros, DRM bypass, catálogos de origem desconhecida ou scripts de extração de conteúdo protegido.
- Prévia navegável **separada** em `preview/index.html` (títulos ilustrativos apenas).

## Atualização 28/10 — pré-compilação nativa

Este projeto agora contém 23 fontes públicas candidatas e 4 curtas como dados iniciais, **não testados em reprodução**. A escolha TV/Smartphone acontece na primeira abertura e pode ser modificada em Fontes. O D-pad em TV ao vivo alterna canais por ↑/↓ e abre o guia por ←/→. Os ícones oficiais substituem os placeholders. Para status fiel consulte `BUILD_STATUS_2026-10-28.md`.

## Importante sobre reprodução

Nenhuma transmissão foi testada num dispositivo Android real. Os links importados são fontes **fornecidas pelo usuário**, e a disponibilidade e os direitos de uso dependem do provedor. O aplicativo inclui fontes públicas candidatas, mas não declara reprodução verificada. O player aceita HTTPS; URLs HTTP sem TLS são deliberadamente bloqueadas neste código-base.

## Como compilar no computador com Android Studio / SDK

Requisitos: **JDK 17**, Android SDK Platform **35**, Android SDK Build Tools, **Gradle 8.9** (ou Android Studio compatível) e acesso aos repositórios Maven/Google para dependências.

1. Abra **esta pasta** (`VortexTV_App_Source_UI01`) no Android Studio.
2. Aguarde a sincronização Gradle; escolha JDK 17.
3. No terminal do projeto, execute `gradle :app:assembleDebug` (o ZIP não contém Gradle Wrapper).
4. O arquivo esperado, **após compilar**, é `app/build/outputs/apk/debug/app-debug.apk`.
5. Instale em dispositivo Android via `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
6. Importe arquivo M3U ou fonte HTTPS que você tem autorização para usar e teste D-pad, áudio, vídeo, internet, disponibilidade do EPG e desempenho.

### Por que não há um APK aqui?

O ambiente onde os arquivos foram preparados não dispõe de Android SDK, `aapt2`, `d8`, Gradle ou acesso de rede para instalar essas ferramentas. Portanto não houve compilação, assinatura, instalação ou teste físico. **Não criar ou renomear arquivos como `.apk` sem uma build real.**

## Testes efetuados

- `M3uPlaylistParser.java`: `javac` e teste Java — passou.
- `XmltvGuideParser.java`: `javac`, parse XMLTV e rejeição de entidades externas — passou.
- Cinco testes Python do kit de integração — passaram.
- Estruturas `AndroidManifest.xml` e `styles.xml` — XML válido.
- Prévia web: renderização de 32 cartões ilustrativos, capturas em formato desktop e celular.
- **Ainda não feito:** compilação Android, teste em emulador, instalação ARMv7 e reprodução real de stream.

## Relação com o catálogo de engenharia reversa

A planilha das URLs de terceiros é uma evidência forense independente. Importá-las cegamente na aplicação faria o Vortex TV conter propaganda, APIs auxiliares, fontes obsoletas e possivelmente endereços sem autorização. Esses dados devem continuar num inventário separado, com adaptadores implementados caso a caso após verificação de direito de acesso, estabilidade, funcionamento e finalidade.

## Próximas entregas para atingir a Beta exigida

1. Compilar e assinar o APK em ambiente Android com SDK.
2. Verificar listas e fontes que podem ser legitimamente integradas, e mapear metadados/EPG com testes de respostas reais.
3. Adicionar imagens de catálogo com licenças, cache leve, categorias mais robustas e detalhamento de temporadas/episódios.
4. Testar playback real em Android 7.1 ARM 32 bits (TV Box) e smartphone: HLS, DASH, D-pad, foco, codecs, fallback e instabilidade de rede.
5. Corrigir problemas encontrados e publicar **somente quando comprovadamente funcional** em um destino persistente, por exemplo GitHub Releases.

_Observação_: visual inspirado em padrões de navegação comuns de serviços de streaming; não utiliza logotipos, artes ou ativos da Netflix.
