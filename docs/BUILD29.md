# Build 29 — Vortex TV 0.3.1 (28/10/2026)

## Evidência de campo
Vídeo enviado (138 s, celular Android, 1080x2400) mostra:
- Big Buck Bunny reproduzindo no player nativo.
- Sprite Fright e Nerve reproduzindo pelo player oficial YouTube.
- TV UFSC e TV Senado em tela preta / sem sinal válido.
- Hero CINDIE TV e muitos pôsteres sem imagens.

## Auditoria de 22 canais
O Supabase fez GET de HLS e leitura limitada do primeiro segmento.
- Passaram inicialmente: TV Brasil, Canal Gov, TV Câmara.
- Falharam: NBR, TV Educação, TV Senado, TV Justiça, TV Cultura pela origem antiga Nextplay, TV Univesp, TV Futura, Rede Vida (timeout), TV Aparecida, Rede Gênesis, TV Novo Tempo, TV UFSC, TV UFRJ, DW (403), France24 (404), NASA TV, Sony Channel (403), N Sports.
- CNN Brasil inicialmente teve falso negativo por GET Range HTTP 206 em playlist variante, que foi corrigido em novo teste.
- Auditadas mais 5 candidatas: TV Cultura (UOL), TV Brasil Internacional, Rede Brasil e CNN Brasil passaram na checagem do primeiro segmento; TV Cultura pelo nxplay devolveu 403.
- Resultado integrado no APK: **7 canais com sinal HLS de servidor testado**, 17 fontes iniciais falhando colocadas em seção de manutenção e CINDIE TV mantido como fonte ainda não homologada.
- Nenhum desses testes prova imagem e áudio na TV Box, nem autorização de redistribuição.

## Capas
Auditados 53 YouTube IDs via i.ytimg.com HEAD: 50 HTTP 200 e 3 HTTP 404 (O Dia da Múmia, Imortais, Spartacus).
- Essas três referências são ocultadas da vitrine, mas não apagadas das fontes locais do usuário.
- Glide tenta miniatura alternativa do YouTube e mantém arte de fundo/identificação quando a imagem falha.
- Cinco logos PNG de canais foram conferidos com HTTP 200 (TV Brasil Internacional, TV Cultura, Rede Brasil, CNN Brasil e TV Câmara).

## Filmes e variedade
- Home prioriza títulos reconhecíveis já cadastrados: Nerve, Deu a Louca nos Bichos, Os Mercenários 2, O Grito, O Nevoeiro e outros.
- Hero deixa de apresentar CINDIE TV como destaque cinematográfico.
- Importação automática de títulos aleatórios do Internet Archive desativada; importação manual segue disponível.
- NetMovies oficial ID: UCK5RRQ3TdwAbbCWrtnXsN3Q.
- Endpoint Supabase: https://nozbyjbtxbgchvkdpnrp.supabase.co/functions/v1/vortex-netmovies-feed
- Feed oficial retornou HTTP 200 e 12 títulos completos recentes, como O Grito 3, Os Condenados, Dia dos Namorados Macabro, A Guerra dos Deuses.
- APK busca o feed ao iniciar e a cada seis horas; conserva até 150 títulos recentes em cache separado dos favoritos e fontes pessoais.
- A reprodução usa sempre o embed YouTube oficial; a publicação de um vídeo no RSS não garante embeddability em todos os aparelhos.

## Arquitetura e segurança
- Vortex TV aplicativo Android, separado do Vortex TV OS.
- Supabase Vortex TV Cloud somente armazena catálogos e diagnósticos, sem retransmissão de mídia.
- Nenhum stream pago foi integrado sem autorização, nem há DRM/auth bypass ou DEX externo.
- URLs quebradas continuam acessíveis em tela de manutenção para testar em redes diferentes; não são promovidas à vitrine.
- Sem GitHub Actions. Build independente no Render.

## Verificações
- Versão Android 0.3.1-curated, versionCode 31, minSdk 25 / Android 7.1.
- Código principal no branch main: https://github.com/rhashiki/Vortex-TV
- APK de teste: https://vortex-tv-android-build.onrender.com/VortexTV-Android-Debug.apk
- Testes Gradle e status do Render devem ser consultados após o deploy que inclui a migração dos 7 canais.

## Pendências reais
1. Instalar no celular e confirmar a nova Home, as capas, o feed NetMovies e reprodução dos canais 7 em uma rede externa.
2. Testar na TV Box RK3229 Ethernet/Android 7.1 (imagem, áudio, D-Pad, consumo de RAM).
3. Integrar diagnóstico periódico de segmentos à lista disponibilizada no APK; o monitoramento de manifestos não equivale a reprodução.
4. Converter build debug em APK release assinado com certificado estável.
