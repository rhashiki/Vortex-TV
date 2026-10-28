# Vortex TV Cloud — integração sem retransmissão

## Estado 28/10/2026
A organização Supabase **Vortex** está no plano Free, e a criação do projeto
"Vortex TV Cloud" (sa-east-1) foi cotada em USD 0/mês, mas **bloqueada**
pelo limite de 2 projetos gratuitos ativos no titular. Não altere ou pause
os projetos Vortex Cafe sem aprovação explícita. Não existe backend Supabase
implantado para o Vortex TV nesta entrega.

## Funciona antes do Supabase
O Android carrega o arquivo HTTPS em \`app/src/main/assets/cloud_config.json\`.
A URL atual aponta para \`catalog/cloud/catalog.json\` deste repositório. O
arquivo inicia vazio por segurança: nenhuma fonte não testada é apresentada
como validada. O aplicativo guarda o catálogo remoto separadamente das
listas locais/favoritos e sincroniza automaticamente (intervalo de 6 horas)
ou através de **Fontes → Sincronizar catálogo Vortex Cloud**.

Contrato JSON:
\`\`\`json
{
  "version": 1,
  "updated_at": "2026-10-28T00:00:00Z",
  "items": [
    {
      "name": "Exemplo de canal já homologado",
      "url": "https://provedor-autorizado.example/live.m3u8",
      "group": "Abertos",
      "format": "hls",
      "status": "playback_ok",
      "rights_status": "authorized"
    }
  ]
}
\`\`\`
O exemplo acima é ilustrativo, NÃO corresponde a uma fonte real. Apenas
\`playback_ok\` + \`authorized\` podem entrar na biblioteca remota.
HTTP 200 e manifesto válido NÃO equivalem à reprodução confirmada.

## Quando houver projeto dedicado no Supabase
1. Criar um projeto exclusivamente para Vortex TV Cloud, região sa-east-1.
2. Executar \`supabase/migrations/20261028_vortex_tv_cloud.sql\` no projeto novo.
3. Publicar \`supabase/functions/vortex-catalog/index.ts\` como Edge Function
   \`vortex-catalog\` com JWT verification desativada (somente GET público;
   não há escrita anônima nas tabelas).
4. Cadastrar mídias e fontes autorizadas. Só marcar \`playback_ok\` após
   testar vídeo e áudio no Android 7.1 / RK3229. Um verificador HTTP/manifesto
   pode elevar o máximo para \`manifest_ok\`, nunca para \`playback_ok\`.
5. Testar \`https://<project-ref>.supabase.co/functions/v1/vortex-catalog\`
   e confirmar JSON v1, nenhuma origem privada e nenhuma URL sem autorização.
6. Trocar apenas \`catalog_url\` no \`cloud_config.json\` e compilar nova
   versão do app. Não colocar \`service_role\` ou chaves secretas no APK.
7. Revisar Security Advisor/RLS antes de habilitar o endpoint.

## Limitações e segurança
- O Supabase distribui **metadados e URLs**, NÃO vídeo. O Media3 acessa as
  fontes diretamente, sem proxy, transcodificação ou armazenamento de vídeo.
- O endpoint é público (sem login) por decisão do produto para uma TV Box;
  **não inserir URLs privadas ou tokens**. Para dados privados, será preciso
  autenticação ou mecanismo de acesso diferente.
- O feed de referência permanece vazio até haver testes de reprodução e
  direitos de uso. Não garante ESPN, AXN, Universal ou qualquer canal pago.
- Nunca usar GitHub Actions. Tests/compilação em ambiente independente.
