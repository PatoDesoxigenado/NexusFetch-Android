# 📱 NexusFetch Android

> **Baixe vídeos (até 4K), áudios (MP3 320 kbps), fotos HD e carrosséis do YouTube, Instagram e Twitter/X — tudo 100% local, sem anúncios e sem rastreadores.**  
> **Comprima, converta e recorte mídias direto no celular com aceleração por hardware (Media3) e FFmpeg nativo.**

[![Versão](https://img.shields.io/badge/vers%C3%A3o-1.0.1-E50914.svg)](https://github.com/PatoDesoxigenado/NexusFetch-Android/blob/main/apk/version.json)
[![Licença](https://img.shields.io/badge/licen%C3%A7a-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026--35)-3DDC84.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose%20Material%203-7F52FF.svg)](https://kotlinlang.org/)

### ⬇️ Download Direto (Android)

> 👉 **[Baixar NexusFetch.apk (Versão Recomendada)](https://github.com/PatoDesoxigenado/NexusFetch-Android/raw/main/apk/NexusFetch.apk)**

| Canal | Arquivo | Arquitetura | Download Direto |
| :--- | :--- | :--- | :--- |
| **🚀 Usuário Final (Release)** | `NexusFetch.apk` | `arm64-v8a` (celulares modernos) | [⬇️ **Baixar NexusFetch.apk**](https://github.com/PatoDesoxigenado/NexusFetch-Android/raw/main/apk/NexusFetch.apk) |
| **🧪 Testes / Debug (64-bit)** | `NexusFetch_arm64-v8a-debug.apk` | `arm64-v8a` | [⬇️ **Baixar Debug (arm64-v8a)**](https://github.com/PatoDesoxigenado/NexusFetch-Android/raw/main/apk/debug/NexusFetch_arm64-v8a-debug.apk) |
| **🧪 Testes / Debug (32-bit)** | `NexusFetch_armeabi-v7a-debug.apk` | `armeabi-v7a` | [⬇️ **Baixar Debug (armeabi-v7a)**](https://github.com/PatoDesoxigenado/NexusFetch-Android/raw/main/apk/debug/NexusFetch_armeabi-v7a-debug.apk) |

---

## 👤 Seção 1 — Guia para Usuários

### 📲 Como Instalar no Celular (3 Passos)
1. Toque em **[Baixar NexusFetch.apk](https://github.com/PatoDesoxigenado/NexusFetch-Android/raw/main/apk/NexusFetch.apk)** acima.
2. Abra o arquivo baixado no gerenciador de arquivos ou na barra de notificações do Android.
3. Se o Android solicitar, ative **Permitir desta fonte** e conclua a instalação normalmente.

> 💡 **Dica:** A versão **Release** (`NexusFetch.apk`) e as versões **Debug** (`apk/debug/`) possuem IDs distintos e verificam atualizações automaticamente na pasta `apk/` do repositório com checagem de integridade **SHA-256**.

---

### ✨ Principais Funcionalidades

- **⬇️ Downloader Universal (YouTube, Instagram e Twitter/X):**
  - **YouTube:** Vídeos de 360p até **4K (2160p)**, extração de áudio **MP3 320 kbps**, thumbnails HD e **Playlists** com seleção individual.
  - **Instagram:** Posts públicos, **Reels/Vídeos**, **Fotos originais sem recorte** e **Carrosséis seletivos** (incluindo carrosséis mistos com fotos + vídeos).
  - **Twitter / X:** Vídeos direto em MP4, **GIFs animados** (`.gif`), carrosséis e fotos na resolução original (`?name=orig`).
- **⚡ Compartilhar Direto (Share Sheet / Ação Rápida):**
  - Ao tocar em *Compartilhar* no YouTube, Instagram ou X e escolher **NexusFetch**, um painel rápido analisa o link em cerca de ~1–2 segundos e permite baixar Vídeo, Foto HD, Carrossel, MP3 ou enviar direto ao Recorte sem sair do app atual.
  - Também aceita compartilhamento de vídeos e áudios locais direto da Galeria para o **Compressor**, **Conversor** ou **Recorte**.
- **📦 Compressor Inteligente (Discord & WhatsApp):**
  - Reduz vídeos e áudios em lote para caber nos limites exatos de envio: **10 MB** (Discord Grátis), **16 MB** (WhatsApp Vídeo), **50 MB** (Discord Nitro Basic) e **64 MB** (WhatsApp HD/Documento).
  - Prioriza aceleração por hardware (**Media3 / MediaCodec**) com fallback rápido em **FFmpeg** (`libx264`).
- **🔄 Conversor Multimídia em Lote:**
  - **Vídeo:** MP4, MKV, AVI, MOV e WEBM com **remux instantâneo** (`-c copy`) sempre que compatível na resolução original, ou re-encoding com seleção de resolução (até 4K) e codecs (**H.264**, **HEVC**, **AV1**, **VP9**).
  - **Áudio:** Extração e conversão para **MP3 320 kbps**, WAV, FLAC, M4A, OGG e OPUS.
- **✂️ Recorte Rápido (Trim / Clip):**
  - Recorte trechos exatos de vídeos remotos do YouTube (até 4K) **sem baixar o vídeo inteiro** (`--download-sections`) ou recorte vídeos/áudios locais sem perda de qualidade.
- **📋 Multi-Download, Histórico & Controle de Dados:**
  - Fila persistente em banco local (**Room SQLite**) para analisar e baixar múltiplos links em sequência.
  - Aba de **Histórico** integrada à Galeria/Downloads do Android (`MediaStore`) e proteção para **Modo Apenas Wi-Fi** ou aviso antes de consumir 4G/5G.

---

### 🛡️ Diferenciais & Pontos Positivos

- **100% Open Source & Sem Anúncios:** Zero propagandas, zero paywalls, zero botões enganosos.
- **Privacidade por Concepção (Zero Rastreamento / LGPD):** Não exige login nem envia URLs ou dados pessoais para servidores terceiros; logs locais sanitizam tokens e caminhos privados automaticamente.
- **Processamento 100% Local no Aparelho:** Downloads, compressões, recortes e conversões rodam diretamente no seu smartphone via Media3 e FFmpeg.
- **Motor `yt-dlp` Auto-Atualizável:** Atualiza o extrator automaticamente em segundo plano ou sob demanda em **Mais → Configurações**, mantendo compatibilidade contínua mesmo quando as plataformas mudam.
- **Atualização Automática Verificada (`apk/`):** Periodicamente o app consulta [apk/version.json](apk/version.json) (tanto no canal **Release** quanto nas builds **Debug** por arquitetura) e só libera a instalação após validar o checksum **SHA-256**.

---

## 💻 Seção 2 — Guia para Desenvolvedores

### 🧱 Stack Tecnológica e Arquitetura

| Camada / Área | Tecnologias Utilizadas |
| :--- | :--- |
| **Linguagem & Build** | Kotlin `2.1.0`, Gradle `8.11.1` (Kotlin DSL), Android Gradle Plugin `8.7.3`, JDK `21`, `minSdk 26` / `targetSdk 35` |
| **Interface (UI)** | Jetpack Compose + Material 3 (Edge-to-Edge, Tema Claro/Escuro/Sistema, Dynamic Color), Navigation Compose |
| **Arquitetura & DI** | Clean Architecture (`domain` / `data` / `service` / `ui` / `di`), MVVM + `StateFlow`, Dagger Hilt + KSP |
| **Motores de Mídia** | AndroidX **Media3 Transformer** (`MediaCodec` HW), **youtubedl-android** (`yt-dlp` + **FFmpeg** empacotados) |
| **Rede, Imagens & Dados** | OkHttp 4, Kotlinx Serialization JSON, Coil 3 (`coil-network-okhttp`), Room SQLite, Jetpack DataStore |
| **Qualidade & Testes** | Detekt (`maxIssues: 0`), Android Lint (`abortOnError = true`), JUnit 5, MockK, Turbine, Coroutines Test, GitHub Actions CI |

Estrutura resumida do projeto:
```text
NexusFetch-Android/
├── apk/                         # APKs finais versionados + version.json para o auto-updater
│   ├── NexusFetch.apk           # Build Release assinada (arm64-v8a)
│   ├── version.json             # Manifesto SHA-256 + versão para Release e Debug
│   └── debug/                   # Builds Debug por ABI (NexusFetch_<abi>-debug.apk)
├── app/src/main/java/io/github/patodesoxigenado/nexusfetch/
│   ├── data/                    # Motores (Media3/FFmpeg/yt-dlp), Room, DataStore, clientes HTTP
│   ├── di/                      # Módulos Hilt
│   ├── domain/                  # Modelos puros, erros tipados NexusError e parsers/policies JVM
│   ├── service/                 # Foreground Service (Android 8–15) e JobOrchestrator sequencial
│   └── ui/                      # Telas Compose, ShareSheetActivity, ViewModels e componentes
├── app/src/test/                # Testes unitários JVM (parsers, políticas de atualização, estados)
└── config/detekt/detekt.yml     # Regras estáticas estritas (0 issues permitidas)
```

---

### ⚙️ Pré-requisitos de Ambiente

- **JDK 21** (Temurin / OpenJDK)
- **Android SDK** com `compileSdk 35`, `platform-tools` e `build-tools;35.0.0`
- **Git**

Defina o caminho do SDK no terminal antes de rodar o Gradle (caso não use `local.properties`):

```powershell
# Windows PowerShell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

```bash
# Linux / macOS
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

---

### 🚀 Como Clonar, Testar e Compilar Localmente

```bash
# 1. Clone o repositório
git clone https://github.com/PatoDesoxigenado/NexusFetch-Android.git
cd NexusFetch-Android

# 2. Rode análise estática, Lint e suíte completa de testes unitários
./gradlew detekt lintDebug testDebugUnitTest

# 3. Compile o APK de Debug (Windows: .\gradlew.bat assembleDebug)
./gradlew assembleDebug
```

Os APKs intermediários são gerados em `app/build/outputs/apk/debug/` e `app/build/outputs/apk/release/`.

---

### 🔐 Assinatura Release & Atualização da Pasta `apk/`

Para gerar uma nova versão pronta na pasta raiz `apk/` (já atualizando `apk/NexusFetch.apk`, `apk/debug/NexusFetch_*-debug.apk` e `apk/version.json`):

1. **Incremente a versão** em `app/build.gradle.kts` (`versionCode` e `versionName`).
2. *(Opcional, recomendado para releases oficiais)* Crie um arquivo `keystore.properties` na raiz (ignorado pelo Git) ou exporte as variáveis `NEXUS_KEYSTORE_*`:
   ```properties
   storeFile=C:/caminho/para/sua-keystore.jks
   storePassword=SUA_SENHA
   keyAlias=nexusfetch
   keyPassword=SUA_SENHA
   ```
   > Se `keystore.properties` não estiver presente (como em clones locais rápidos ou builds de PR no CI), o build faz fallback seguro para a assinatura `debug` sem quebrar a compilação.
3. **Execute a verificação completa + empacotamento da pasta `apk/`:**
   ```bash
   ./gradlew detekt lintDebug testDebugUnitTest stageDistributionApks
   ```

#### Como funciona o Auto-Updater via `apk/version.json`
- A task Gradle `stageDistributionApks` calcula o SHA-256 de `apk/NexusFetch.apk` e de cada APK em `apk/debug/`, gravando tudo em `apk/version.json`.
- A cada **6 horas** (respeitando rede Wi-Fi / Modo Apenas Wi-Fi) ou manualmente em **Mais → Configurações → Checar App**, o `GitHubUpdateClient` + `AppUpdatePolicy` lê `apk/version.json` no branch `main`:
  - Em builds **Release**, compara com `release` (`apk/NexusFetch.apk`).
  - Em builds **Debug**, seleciona automaticamente a ABI compatível em `debug` (`arm64-v8a` ou `armeabi-v7a`).
- Havendo `versionCode` ou versão semântica maior que a instalada, o usuário recebe o alerta `AppUpdateDialog` e o download só abre o instalador do Android após confirmação rigorosa do hash **SHA-256**.
