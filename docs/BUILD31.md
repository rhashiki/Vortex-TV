# Build 31 — Vortex TV nativo no Vortex TV OS (28/10/2026)

## Decisão definitiva de produto
- Vortex TV (streaming) é um APK nativo independente, incluído na imagem Vortex TV OS, sem instalação ou importação manual de M3U pelos pais.
- VortexHome é o launcher: os pacotes não podem ter o mesmo ID. VortexHome = `os.vortex.tv`; VortexTV release = `os.vortex.tv.stream`.
- Nenhuma tela de login, código de pareamento, QR, cadastro ou configuração de fontes no fluxo familiar. Se a configuração OEM existe, o app sincroniza sozinho ao iniciar e novamente após 12h.
- A lista unificada de 33.601 URLs está no Supabase (`vortex_family.playlist_sources`). Transmissões de vídeo não são hospedadas, apenas links e metadados.

## Backend confirmado
- 33.601 URLs distintas em 16 lotes sem lacunas, banco no esquema privado `vortex_family` protegido por RLS e permissões restritas.
- Endpoint paginado `vortex-family-catalog` entrega até 500 registros em M3U por requisição. O cursor é recuperável; importação do app em SQLite por lotes.
- Sem credencial: GET devolveu HTTP 401.
- Com acesso técnico do firmware: GET devolveu HTTP 200 e 500 registros, cursor 500, `has_more=true`.
- O acesso OEM técnico foi gerado no Supabase Vault, sob o nome `vortex_tv_os_oem_catalog_access`; não consta no código-fonte público, no APK público ou na interface do usuário.

## Streaming app (rhashiki/Vortex-TV)
- `NativeCatalogConfig` lê somente quando presente o arquivo OEM `/system/etc/vortex-tv/catalog-access`.
- `MainActivity.onCreate` invoca `syncNativeCatalog(false)` em background.
- `FamilyCatalogSync` utiliza paginação e checkpoint do cursor após cada transação SQLite bem-sucedida, sem apagar dados em interrupções.
- `MainActivity` exibe uma ação secundária para atualizar o catálogo, mas nenhum diálogo de pareamento.
- O player Media3 mantém troca automática entre fontes catalogadas para o mesmo título, sem prometer vídeo quando todas estão fora do ar.
- Package ID de release alterado para `os.vortex.tv.stream`; manifest da activity principal agora usa classe qualificada.
- Versão do app 0.3.3-native-catalog (versionCode 33), minSdk 25 (Android 7.1).

## Vortex TV OS (rhashiki/VORTEX-TV-OS)
- Produto experimental AOSP passou a exigir `VortexHome` e `VortexTV`, com módulo prebuilt separado em `rom/prebuilt/Android.mk`.
- `VortexTV.apk` release e arquivo OEM `catalog-access` são gerados/copied no ambiente de construção e **não** são comitados; `.gitignore` protege ambos.
- `UserApps.list` no launcher passou a exibir explicitamente `os.vortex.tv.stream`, apesar de ser app de sistema; demais apps de fábrica continuam ocultos.
- README do OS e `rom/prebuilt/README.md` registram o contrato da integração.

## Status honesto
- O APK foi compilado em Render e os testes de regressão foram executados. A validação final do deploy dessa revisão deve ser conferida separadamente.
- A ROM Vortex TV OS **ainda não é uma imagem bootável homologada**. Não houve flash nem teste na RK3229. Os arquivos OEM e APK release precisam ser preparados pelo ambiente de build quando a imagem for montada.
- Possíveis credenciais dos URLs não foram colocadas no GitHub. O arquivo de acesso em `/system/etc` exige auditoria de permissões/SELinux no firmware final.
- Os links não foram individualmente validados; o sistema tenta alternativas, mas não garante reprodução quando todas falham.
