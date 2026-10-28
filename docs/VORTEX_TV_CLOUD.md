# Vortex TV Cloud — backend dedicado, sem retransmissão

**Estado em 28/10/2026: projeto criado, banco implantado e função publicada.**

- Organização: Vortex (plano Free)
- Projeto: Vortex TV Cloud, região sa-east-1
- Ref: `nozbyjbtxbgchvkdpnrp`
- API somente leitura: https://nozbyjbtxbgchvkdpnrp.supabase.co/functions/v1/vortex-catalog
- Migração: `20261028_vortex_tv_cloud.sql`
- Função: `supabase/functions/vortex-catalog/index.ts`
- Aplicativo: Android 7.1+ / Media3, config em `app/src/main/assets/cloud_config.json`

## Funcionamento
O APK chama **GET** ao endpoint, sem login. O backend consulta apenas fontes
`enabled=true`, `rights_status=authorized` e
`validation_status=playback_ok`. A função entrega JSON v1:

```json
{"version":1,"updated_at":"2026-10-28T00:00:00Z","items":[]}
```

Este resultado vazio é esperado até que existam fontes homologadas.
O aplicativo mantém cache separado das fontes que a família importou
manualmente, e atualiza o catálogo Cloud ao abrir (máximo a cada 6 horas
após sucesso) ou manualmente pelo menu Fontes.
O player faz requisições **diretas** ao provedor autorizado, não ao Supabase.

## Segurança
- Tabelas com RLS ativado; nenhuma política aberta para `anon` ou
  `authenticated`, e privilégios de tabela revogados.
- A função responde publicamente a GET, não aceita escrita ou parâmetros
  que permitam consultar tabelas arbitrárias.
- A Edge Function usa `SUPABASE_SECRET_KEYS` (chave default) somente em
  ambiente de servidor, com fallback para chave service_role legado.
- **Nunca adicionar links privados, tokens, credenciais, DRM bypass ou
  dados sensíveis** ao feed público, mesmo que o uso principal seja doméstico.
- HTTP OK e manifesto válido não implicam `playback_ok`: só homologar
  após confirmar imagem e áudio no dispositivo alvo.
- Nenhum APK de terceiros ou DEX remoto é carregado.
- Não há proxy, hospedagem ou retransmissão de vídeos.

## Pendências
1. Verificar resposta HTTP da Edge Function publicamente. A implantação está
   ativa, mas este ambiente de execução pode não conseguir consultar a URL
   externamente devido às restrições de rede.
2. Rodar build Android via Render para o novo endpoint e verificar APK.
3. Homologar fontes autorizadas com testes em uma RK3229 Android 7.1 real;
   só depois habilitar entradas no banco.
4. Acrescentar um indexador agendado de fontes públicas que faça verificação
   de HTTP/manifesto sem elevar automaticamente para `playback_ok`.
5. Verificar os avisos de segurança e desempenho após popular o banco.
   Avisos de RLS sem policy e índices não usados são esperados neste
   banco privado e inicialmente vazio.

## Estado das versões
As compilações do APK de teste não são homologações em campo. O frontend
continua distinguindo Vortex TV (app) do projeto Vortex TV OS (firmware).

**Política permanente:** nenhum GitHub Actions; Gradle/Render e testes
independentes.
