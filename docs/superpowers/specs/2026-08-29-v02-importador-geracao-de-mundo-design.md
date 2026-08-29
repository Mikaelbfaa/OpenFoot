# Design da v0.2 - importador e geração de mundo sobre evidência real

Data: 2026-08-29. Status: aprovado em conversa; texto aguardando revisão final.

A v0.1 entregou o motor de partida completo e validado. O importador, a geração de mundo e a CLI
já existem, mas carregam lacunas conhecidas, e a maior delas domina qualquer número medido fora do
Brasil e da Espanha: o item 27 do `spec/OPEN-QUESTIONS.md`. A v0.2 fecha essas lacunas com
evidência de verdade, em vez de apostas.

## O problema central

A instalação distribuída só traz `BRA.cfg` e `ESP.cfg`, então 533 dos 703 clubes importados ficam
sem divisão e caem na faixa de força 1/1 da seção 4.4. Um Real Madrid de nível 20 gera melhor
jogador com 67; um Bayern, com o mesmo nível 20 armazenado, gera 45. Quando a evolução semanal da
seção 4.5 chegar, o efeito piora: teto de crescimento 30 e piso de declínio 1 para todo país sem
arquivo de liga. A hipótese registrada no item 27 é que o jogo original embute uma tabela de ligas
no código, e os dois `.cfg` distribuídos são apenas sobreposições do usuário.

## Decisões tomadas

1. O item 27 se resolve por **decompilação feita pelo time de spec**, não por observação nem por
   pirâmide sintética INFERIDA.
2. A varredura é **completa**: além das tabelas que a v0.2 precisa (itens 14, 26 e 27), verifica as
   apostas INFERIDAS em pé, incluindo o item 30 (divisores fixos ou por contagem), os itens 11, 15,
   17, 19, 20 e 24 da criação de mundo, a origem dos elencos de seleções e o item 21.
3. Além do núcleo, entram na v0.2: **seleções**, **endurecimento de CLI e esquema** e os **campos
   de temporada** dos `.cfg` e `.ces` que hoje são lidos e descartados.
4. Abordagem de execução: **trilhas condicionadas por evidência**. A varredura roda em paralelo com
   o trabalho que nenhum achado pode invalidar; o trabalho dependente de evidência espera a spec
   atualizada.
5. As tabelas recuperadas viram **constantes com `@SpecRef` no `:importer`**, agindo como padrão
   embutido que os arquivos `.cfg` sobrepõem, espelhando a arquitetura do próprio original.
6. O esquema de dados sobe para a **versão 2**, num único movimento coordenado.
7. A v0.2 **quebra compatibilidade de semente** com mundos da v0.1. Antes da 1.0 isso é esperado, e
   o campo `version` do conjunto de dados é o mecanismo que torna a quebra ruidosa.

## A regra clean-room nesta versão

O trabalho tem dois papéis, como o `CONTRIBUTING.md` define. Um agente separado e em quarentena faz
o papel do time de spec: extrai o JAR embutido do `bf22-23.exe`, decompila fora do repositório
(nada disso é commitado nem entra no diretório do projeto) e escreve os achados **diretamente em
`spec/`**, como fatos de comportamento, fórmulas e tabelas, com a classe de evidência CONFIRMADO.
O agente de implementação nunca lê a saída do decompilador; ele lê o diff da spec, que é
exatamente o que a parede permite. Identificadores do código original não entram em lugar nenhum
além do que o estilo atual da spec já carrega.

## Varredura do time de spec

Perguntas, em ordem de prioridade:

1. **Item 27** - a tabela de ligas embutida: quais países têm liga, com quantas divisões, quantos
   times, rebaixados e turnos, e como os `.cfg` interagem com ela.
2. **Item 14** - a tabela de nível de país que a seção 4.4 consulta.
3. **Item 26** - a tabela de continente por país.
4. **Item 30 / experimento E1** - se os divisores de linha da seção 3.4 são fixos (5/5/3) ou seguem
   a contagem de jogadores. É a aposta em aberto mais cara do projeto: se for por contagem, todo
   resultado de partida muda.
5. **Itens 11, 15, 17, 19, 20 e 24** - as resoluções INFERIDAS da criação de mundo.
6. **Seleções** - de onde saem os elencos: `selecoes/` só tem imagens, então ou há listas embutidas
   no código, ou os elencos são derivados dos jogadores do país.
7. **Item 21** - confirmar os cinco índices de país da liga grande, em particular Inglaterra = 97.

Saída: edições em `spec/SIMULATION-SPEC.md`, `spec/FORMAT-SPEC.md` e `spec/OPEN-QUESTIONS.md`,
com cada resolução reclassificada (CONFIRMADO, ou corrigida se a aposta estava errada), mais um
resumo de quais seções mudaram. Uma aposta INFERIDA que a leitura derrubar muda a spec e o teste
que a fixava, nessa ordem, como o projeto já faz.

## Importador e esquema versão 2

**Padrões embutidos.** A tabela de ligas recuperada vira constantes citadas com `@SpecRef` no
`:importer`. Os `.cfg` distribuídos sobrepõem o padrão do seu país. `assignDivisions` mantém o
mecanismo (ordenar por nível, desempatar por `ref`), agora preenchendo pirâmides reais, salvo se o
item 24 CONFIRMADO disser outra coisa.

**Nível de país e continente.** As tabelas reais substituem a derivação pelo clube mais forte e o
marcador "não Europa". A derivação atual sobrevive como reserva para país ausente da tabela, com
nota do importador, como hoje.

**Esquema versão 2**, num único salto:

- `continent` passa a ser obrigatório, sem valor padrão. Hoje o padrão do esquema é Europa e o
  importador grava não-Europa; um conjunto de dados comunitário que omitisse o campo tornaria todo
  país europeu em silêncio. Obrigatório significa falhar alto.
- Entra uma entidade de configuração de liga por país e divisão: número de times, rebaixados,
  turnos e o desempate por pênaltis, vindos dos `.cfg` e da tabela embutida.
- Entra uma entidade de campeonato estadual, vinda dos `.ces` (fórmula de fase final, ida e volta).
- Tudo validado na leitura, como o esquema já faz, e consumido por nada até a v0.3. A regra de
  versão do `WorldDataset` já cobre o caso: arquivo v1 numa build v2 falha com mensagem clara.

**Seleções.** O esquema já tem o campo `nationalTeam` e o `ClubBands` já tem a fórmula por
reputação; falta o produtor. O desenho fino espera o achado 6 da varredura, porque a fonte dos
elencos muda tudo: lista embutida pede leitor, derivação pede gerador.

## Trilha de endurecimento, independente da varredura

- Testes para `parseOptions` e para o despacho de comandos da CLI, que hoje não têm nenhum.
- Mapa de índice de país no `WorldDataset`, no lugar da varredura linear por jogador.
- A nota de importação do item 27 passa a declarar o impacto inteiro: base de geração, teto de
  crescimento e piso de declínio.

## Validação

- **Unidade**: o importador é testado contra streams serializados sintéticos, commitáveis. Nenhum
  arquivo do jogo original entra no repositório, como sempre.
- **Sanidade de mundo**: um teste novo em `:validation` fixa resultados da seção 4.4 com as tabelas
  reais; por exemplo, dois clubes de nível 20 em países configurados distintos geram elencos
  comparáveis.
- **Vetor dourado de geração de mundo**: um conjunto de dados sintético commitado e o resumo do
  mundo de uma semente fixa, para que regressão de geração vire diff, no modelo do
  `MatchGoldenVectorTest`.
- **Ponta a ponta**: importar a instalação real continua sendo verificação local documentada; a CI
  nunca terá os arquivos do jogo.

## Sequenciamento

1. **Fase 1, em paralelo**: dispara a varredura do time de spec; trilha de endurecimento em branch
   próprio.
2. **Fase 2**: revisão dos diffs de spec, atualização dos status no `OPEN-QUESTIONS.md`, e
   fechamento dos detalhes dependentes de evidência (adendo curto a este design se as seleções
   surpreenderem).
3. **Fase 3**: esquema v2, tabelas embutidas, mudanças do importador e produtor de seleções.
4. **Fase 4**: validação nova, atualização de README e roadmap, tag v0.2.

Commits atômicos no padrão do `CONTRIBUTING.md`, `./gradlew check` verde em cada um, PRs por
trilha.

## Riscos e contingências

- **Item 30 vira bomba**: se os divisores seguirem a contagem, é correção de spec que muda
  `LineAggregates` e re-baseia toda a estatística e os vetores dourados. Não está planejado; se
  acontecer, o trabalho para e este design ganha um adendo explícito antes de qualquer código.
- **A hipótese do item 27 cair**: se não houver tabela embutida, a saída vira a pirâmide sintética
  INFERIDA derivada do nível dos clubes, registrada como tal no item 27, e o resto do design fica
  de pé.
- **Decompilação infrutífera**: se a extração ou a decompilação falhar, cai-se para a rota de
  observação (abrir o jogo e olhar as ligas), com o mesmo destino de registro na spec.

## Adendo (2026-08-29, pós-varredura): o que a evidência mudou

A varredura do time de spec fechou os itens 11, 14, 15, 17, 18, 19, 20, 21, 24, 26, 27 e 30, e
quatro achados mudam este design. As decisões abaixo foram aprovadas em conversa.

### 1. Gerador, não tabela

Não existe tabela estática de ligas no original: existe um **gerador de pirâmide** (seção 1.9 da
SIMULATION-SPEC), com elegibilidade por contagem de arquivos (10, ou 16 para ALE/ARG/ING/ITA/FRA),
até 4 divisões por país, tamanhos em degraus 20/18/16/14/12/10, 4 rebaixados no degrau 20 e 2 nos
demais, e os `.cfg` como sobrescritas por par país-divisão. O gerador vira lógica citada com
`@SpecRef` no `:engine` (mundo), porque é ele que monta o mundo; a decisão anterior de "tabela
embutida no `:importer`" fica sem objeto.

### 2. Divisão é propriedade do mundo, não do conjunto de dados

O original atribui clube a divisão **na criação de cada mundo**, por nível decrescente com
desempate sorteado, e quais ligas rodam é escolha de cada carreira. Portanto:

- `ClubEntry.division` **sai do esquema** na versão 2. O conjunto de dados passa a carregar as
  **configurações de liga** vindas dos `.cfg` (país, divisão, times, rebaixados, turnos, desempate
  por pênaltis), que o gerador consulta antes dos padrões embutidos.
- `generateWorld` ganha o conjunto de **ligas ativas** como entrada (o análogo headless das caixas
  de seleção da criação de jogo), com o Brasil como padrão, espelhando o pré-marcado do original. A
  CLI expõe isso como `--leagues` (siglas separadas por vírgula, ou `all`).
- O desempate da ordenação é derivado da semente por clube, pela referência, no padrão do item 10:
  mecanismo fiel, resultado reproduzível.
- País sem liga ativa instancia só os **15 primeiros clubes** por nível e usa o **caminho de
  reputação** da 4.4; a invariância de edição do conjunto de dados passa a valer **entre países**
  (editar um clube pode remontar a pirâmide do próprio país, como no original).

### 3. Correção do caminho de reputação (item 18 estava errado)

O caminho de reputação vale para seleções **e** para clubes de país sem liga ativa; reputação 0 dá
base 1 e faixa 1, e não 5 e 1. `ClubBands` é corrigido e os testes que fixavam a aposta antiga
mudam junto, spec primeiro, como manda o processo.

### 4. Tabela de países real e seleções geradas

A tabela embutida de 224 países (nível e continente, seção 4.4.1) vira constantes citadas no
`:importer`, preenchendo o `CountryEntry`; a derivação pelo clube mais forte sobrevive só como
reserva anotada para índice fora da tabela. Seleções **não são lidas de arquivo nenhum**: são
geradas sob demanda pela 4.12. O leitor de `selecoes/` que este design previa fica sem objeto, e o
campo `nationalTeam` sai do esquema junto com `division`.

### Esquema versão 2, consolidado

Sai: `ClubEntry.division`, `ClubEntry.nationalTeam`. Entra: `WorldDataset.leagues` (configurações
de liga). Muda: `continent` passa a obrigatório. Os campeonatos estaduais (`.ces`) e a geração de
seleções da 4.12 ficam para um plano seguinte, cada um com consumidor claro.
