# Vortex TV — Build 24: Catálogo + guia vertical

Correções implementadas no projeto nativo Android (TV e smartphone):

- Interface de TV ao vivo com lista vertical e numeração; navegação via D-pad no player permanece.
- Canais do guia sem fonte são claramente identificados como `SEM FONTE`; não se promete transmissão de canais pagos sem uma origem reproduzível.
- Pôsteres e thumbnails reais renderizados pelo Glide 4.16 a partir dos URLs HTTPS das fontes, com cache automático.
- Catálogo de filmes por gêneros (Animação, Terror, Romance, LGBT, Documentário e outros) e por origem do conteúdo.
- Integração com a pesquisa oficial do Internet Archive para descobrir mídias com licença Creative Commons informada, com resolução de um arquivo MP4 concreto antes de indexar.
- Os quatro curtas da Blender Foundation continuam pré-carregados, com metadados e miniaturas; busca aberta é assíncrona e pode falhar se o serviço externo estiver indisponível.
- XMLTV reconhece nomes de emissoras para relacionar a programação aos canais do guia.
- As preferências locais, a seleção TV/Smartphone e os canais previamente importados são preservados.

## Ainda não homologado

A busca do Internet Archive depende de disponibilidade externa e suas informações de licenciamento são fornecidas pelos depositantes (é necessária diligência antes de redistribuir). O app não garante que 100% das fontes funcionem. Os canais por assinatura comerciais não possuem streams licenciados integrados nesta build. Não há emulador/TV Box físico neste ambiente; compilação e testes Android devem ser acompanhados no Render e depois no aparelho.

Este é o aplicativo Vortex TV, **não** o Vortex TV OS. Não utilizar GitHub Actions para build/testes.
