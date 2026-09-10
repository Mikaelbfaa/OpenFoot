# Design da v0.3 - a temporada sobre o mundo gerado

Data: 2026-09-09. Status: aprovado em conversa em 2026-09-10, com a varredura do time de spec
disparada; texto aguardando o adendo pós-varredura.

A v0.2 entregou o mundo: base de dados, pirâmide de ligas, elencos e seleções, tudo reproduzível a
partir de uma semente. A v0.1 entregou a partida. O que não existe é o que liga uma partida à
seguinte: rodadas, tabelas, mata-mata, energia que se recupera, cartão que vira suspensão, lesão que
dura dias, jogador que cresce, envelhece e para, clube que sobe e desce. A v0.3 é isso: a temporada
inteira rodando sobre o mundo gerado, e a segunda temporada nascendo da primeira.

## O que a v0.3 é, e o que não é

Uma temporada da IA contra a IA, do sorteio da tabela à virada de ano, reproduzível a partir da
semente, com as competições que a spec fecha. Nenhum clube é humano. Isso não é uma limitação
provisória: é o que torna a v0.3 possível sem a economia, porque a seção 6.0 diz que **dinheiro só
existe para clube humano**. Uma temporada só de IA não tem caixa, folha, bilheteria, diretoria nem
prêmio em dinheiro, e o motor não precisa de nada disso para rodar igual ao original.

Fica de fora, por roteiro e não por falta de spec:

- **Mercado** (1.6 êxodo de craques, 1.7 reconstrução de elenco, 6.5 transferências). É a v0.6. A
  consequência para a v0.3 é que um elenco só muda por promoção da base, aposentadoria e reposição
  gerada, e isso fica registrado como escolha de escopo, não como defeito.
- **Economia e diretoria** (6.x). Sem clube humano, nada disso roda no original.
- **Interface e persistência de carreira**. A v0.3 é uma temporada rodada de ponta a ponta num
  processo, impressa no fim. Uma carreira é uma base, uma semente e a escolha de ligas; salvar e
  retomar chega com a v0.4, quando existir uma tela para retomar.

## O que a spec já fecha

O inventário abaixo é o que pode virar código sem esperar ninguém, e é a maior parte do trabalho.

| Seção | O que dá |
|---|---|
| 1.2 | A tabela de 8 valores e o critério de desempate fixo: pontos, vitórias, saldo, gols pró |
| 1.3 | Número de turnos por tamanho de liga e o `formula` como número de turnos |
| 1.5 | Demissão de técnico da IA: rolagem contra o aproveitamento, só abaixo do 6o lugar |
| 1.8 | Suspensões de tribunal: 2, 3, 5 e 10 jogos |
| 1.9 | A pirâmide, já implementada; a 4a divisão brasileira preenchida pelos estaduais |
| 3.1 | A ordem de uma rodada: escalação, partidas, tabela, suspensões, notas, energia |
| 3.8 | Duração de lesão em dias, suspensão por 3 amarelos e por vermelho, o gancho |
| 3.9 | Recuperação semanal de energia, por idade e por ter jogado, com a tabela da IA |
| 3.10 | Disputa de pênaltis, já implementada |
| 4.5 | Evolução semanal: taxa, modificadores, teto por divisão e país, declínio, foco de treino |
| 4.6 | Base: desenvolvimento semanal, talento, promoção e força na promoção |
| 4.10 | Como se ganha estrela e topMundial; perda depois dos 34 |
| 4.11 | A virada: envelhecimento, aposentadoria, reciclagem de avulsos, revalorização |
| 5.5 | Reputação: saldo de prestígio, decaimento, promoção, prêmios por título |
| 5.7 | Tetos e cotas de elenco da IA que a promoção e a reposição respeitam |
| FORMAT-SPEC, `.ces` | O estadual inteiro: presets, grupos, chaveamento, pernas, pênaltis, rebaixamento e a fila de reserva |

## O que precisa do time de spec antes do código

Estes itens não estão na spec, ou estão em contradição, e nenhum deles pode ser adivinhado em
silêncio. Vão para uma varredura do time de spec, no mesmo protocolo da v0.2: quem lê o original
escreve spec, com classe de evidência, e o código sai da spec.

1. **O modelo de tempo.** A seção 0 diz que não existem datas e que o tempo anda em rodadas. A 3.8
   mede lesão em dias, a 4.7 mede contrato em dias, a 4.5 roda "todo domingo" e a 6.4 cobra salário
   "no dia 2 de cada mês". As duas coisas não podem ser verdade ao mesmo tempo. A pergunta: o que é a
   data de uma rodada, quantos dias há entre rodadas, como uma lesão de N dias vira N rodadas, quando
   o tique semanal dispara em relação às rodadas, e se existe algum efeito do mês fora do dinheiro.
2. **A geração da tabela de jogos.** A 1.3 dá o número de turnos e nada mais. Falta o algoritmo:
   quem joga com quem em cada rodada, como o mando alterna entre os turnos, o "código de formato
   específico" dos 20 times com um turno só, e o que muda com grupos.
3. **O calendário da temporada.** Como as competições se intercalam: estaduais antes, durante ou
   depois da liga; se um clube joga duas vezes na mesma semana; em que rodadas entra a copa; quantas
   rodadas tem uma temporada e o que acontece com um clube sem jogo numa rodada.
4. **As outras catorze competições da 1.1.** A spec só tem, para cada uma, preços de ingresso,
   premiação e prêmio de reputação. Falta tudo o que as monta: quem participa e por que critério,
   quantos, grupos, chaveamento, pernas, mando, gol fora de casa, pênaltis, e o que muda por
   continente. Copa nacional, internacionais 1 e 2, Mundial, Recopa, supercopas, regionais,
   Conference, Finalíssima, Liga das Nações, Eliminatórias, torneios de seleções e amistosos.
5. **Os campos de grupos e mata-mata do `.cfg`.** A 1.9 diz que uma configuração de liga traz
   "grupos, mata-mata", e a FORMAT-SPEC cita `nGrupos` e `melhoresTerceiros`, mas o significado
   nunca foi documentado como o do `.ces` foi. O `LeagueConfigEntry` de hoje descarta esses campos.
6. **Subida e descida entre divisões nacionais.** Quantos sobem, quem, em que ordem a troca
   acontece, o que ocorre com a última divisão, e a regra exata da 4a divisão brasileira preenchida
   pela classificação dos estaduais.
7. **A virada de temporada sem mercado.** A 1.4 dá a ordem com o mercado dentro. Falta saber o que
   sobra quando os passos de mercado não rodam, e o mecanismo da reposição da 4.11 quando o elenco
   fica abaixo de `{2,3,3,6,4}` por posição ou de 16 jogadores.
8. **Três leituras da evolução.** O que marca a flag "jogou recentemente"; como e quando a IA
   sorteia o foco de treino; e o que a 4.5 chama de "desenvolvimento de base >= 60" para um
   profissional.
9. **Estrela e topMundial por desempenho.** Qual é o "limiar da competição" da média de notas que dá
   a estrela, e quem conta como "elite da competição" para o contador de topMundial.
10. **A base na criação do mundo.** A 4.6 descreve o júnior gerado em tempo de execução. Falta saber
    se o mundo nasce com base, com quantos juniores por clube e gerados como.
11. **O tribunal da 1.8.** Quando e com que probabilidade o evento dispara.
12. **Técnico.** Se o técnico tem algum efeito no motor além de existir. Se não tem, a v0.3 modela só
    o que a 1.5 precisa: um nome, o aproveitamento e a demissão.

## Decisões propostas

1. **Competições da v0.3: todas.** O roteiro do README chama a v0.3 de "temporada completa", e é
   isso: as ligas nacionais de todo país com liga ativa, com subida e descida, os estaduais do
   Brasil, e cada uma das outras catorze competições da 1.1 que o original agenda numa temporada,
   nos formatos que a varredura documentar. Uma competição cujo formato a varredura não fechar entra
   em OPEN-QUESTIONS com o que se sabe e é a única que pode ficar para um adendo.
2. **Tudo é IA.** Nenhum clube humano, e portanto nenhuma economia. As tabelas que distinguem humano
   de IA, como a recuperação de energia da 3.9, entram inteiras no `RuleSet` desde já, com o lado
   humano inerte até a v0.5.
3. **A base entra.** A evolução da 4.5 e a virada da 4.11 leem a base o tempo todo, e sem promoção
   os elencos da IA só encolhem de uma temporada para a outra. O custo é a geração de juniores, que
   depende do item 10 da varredura.
4. **O mundo passa a receber o `RuleSet`.** `generateWorld` e a convocação não recebem regras hoje,
   e por isso os itens 64 e 65 de OPEN-QUESTIONS estão reproduzidos sem correção em `MODERN`. A
   temporada precisa de regras de qualquer jeito, então a assinatura muda uma vez, e os dois itens
   ganham o campo de `RuleSet` que a página de defeitos já promete, junto com a escolha do item 69
   (`CLASSIC` rejeita, `MODERN` cai no padrão de 6 times).
5. **Sem arquivo de save.** Uma carreira da v0.3 é reproduzida, não retomada: a linha de comando roda
   N temporadas de uma semente e imprime. O formato de save é decisão da v0.4.

## Arquitetura

### O registro é o log

O motor de partida guarda um log de eventos e lê o placar e as estatísticas dele. A temporada segue
o mesmo princípio: o estado da temporada é o que aconteceu, e tabela, artilharia, suspensões e
classificação para o mata-mata são leituras dele. Isso mantém uma única fonte de verdade e torna
cada leitura testável contra um log escrito à mão.

Pacote novo, `org.openfoot.engine.season`, puro como o resto do `:engine`:

- `Standings`: a tabela de 8 valores da 1.2 e o único comparador, derivados de uma lista de
  resultados.
- `Fixtures`: o algoritmo de turnos da 1.3 (após o item 2 da varredura), com mando por turno.
- `Competition`: liga de pontos corridos; estadual por preset, com grupos, chaveamento por posição,
  pernas e a fila de reserva da FORMAT-SPEC; copa nacional se fechada. Cada uma é uma máquina de
  fases, e o mata-mata reaproveita a resolução de pênaltis da 3.10 que já existe.
- `PlayerRecord`: por jogador e por temporada, o que a 3.8 e a 3.9 acumulam entre partidas: amarelos,
  gancho, dias de lesão, energia, minutos e notas. É este registro que implementa `Availability` e
  `DesignationEnergy`, as duas interfaces que a v0.1 deixou prontas exatamente para isso; nenhum
  caller de `assembleMatch` muda.
- `WeeklyTick`: a 4.5 e a 4.6, com o teto por divisão e país, o declínio e o foco de treino.
- `Turnover`: a 4.11, a promoção da 4.6, a reputação da 5.5, a demissão da 1.5, a subida e descida,
  e a segunda temporada.
- `SeasonState` e `SeasonRunner`: o agregado e a função `playRound(state, rules) -> state`.

### Determinismo

Os domínios de semente já existem em `SeedDomain`: `SEASON`, `FIXTURES`, `WEEK`, `EVOLUTION`,
`END_SEASON`. Cada partida fork por (temporada, competição, rodada, chave da partida), cada tique
semanal por (temporada, semana, clube, jogador), a tabela de jogos por (temporada, competição). Como
na v0.2, a ordem em que as coisas são simuladas não pode mudar nada: uma rodada de 380 partidas dá o
mesmo resultado em série ou em paralelo, e é isso que os testes de determinismo vão afirmar.

### Valores, não mutação

Estado imutável, avançado por funções que devolvem o estado seguinte. Um jogador é identificado pelo
clube e pela posição no elenco, como o `PlayerId` de hoje; uma partida por competição, rodada e par
de chaves. Sem `HashMap`, sem relógio, sem I/O, como o `ArchitectureTest` já exige.

### Interface de linha de comando

`openfoot-cli season --dataset base.json --seed 42 --seasons 2 [--leagues BRA,ESP|all]` roda as
temporadas e imprime, por competição, a tabela final, o campeão, quem subiu e desceu, a artilharia,
e por clube um resumo da virada: aposentados, promovidos, mudança de reputação. A mesma base, a mesma
semente e as mesmas ligas imprimem sempre o mesmo.

## Validação

- **Unidade** por módulo, com geradores roteirizados como hoje: uma tabela lida de um log escrito à
  mão, um chaveamento de 8 conferido contra os pares que a FORMAT-SPEC dá, uma lesão de N dias que
  vira o número certo de rodadas, um jogador de 19 anos num clube nível 20 que ganha exatamente o que
  a 4.5 manda numa semana roteirizada.
- **Sanidade de temporada**, em `:validation`: uma temporada inteira de um mundo sintético, medida.
  A média de gols e de cartões por partida continua dentro das bandas da 3.16; a distribuição de
  pontos de uma liga de 20 é plausível; a taxa de aposentadoria por idade bate a tabela da 4.11; o
  crescimento por temporada por faixa de idade e nível bate a 4.5. Cada banda com o valor medido e a
  seção que prevê, como o `SanityCheckTest` faz.
- **Vetor dourado**: uma temporada do `GoldenWorld` impressa e pinada, no modelo dos três vetores
  que já existem. Uma linha que se mexer é argumentada no commit.
- **Aceitação local** contra a instalação real: duas temporadas do Brasil e da Espanha, sem nada
  commitado.

## Sequenciamento

1. **Fase 0, varredura do time de spec.** Os doze itens acima. Cada resposta entra na spec com
   classe de evidência; o que o original não fecha vira item de OPEN-QUESTIONS com resolução
   INFERIDO declarada. Adendo curto a este design se algo surpreender.
2. **Fase 1, o que não depende da varredura.** `Standings` e o comparador; o registro de jogador com
   suspensões e recuperação de energia; a evolução semanal e o declínio; a reputação; a aposentadoria.
   Tudo testado contra a spec, nada agendado ainda.
3. **Fase 2, o tempo.** Calendário, tabela de jogos, a liga de pontos corridos e o estadual com
   chaveamento; `playRound`; o `RuleSet` chegando ao mundo.
4. **Fase 3, a virada.** Base, promoção, subida e descida, técnicos, a segunda temporada.
5. **Fase 4, o fechamento.** `season` na linha de comando, sanidade de temporada, vetor dourado,
   README, tag v0.3.

Commits atômicos no padrão do `CONTRIBUTING.md`, `./gradlew check` verde em cada um, um branch por
fase.

## Riscos

- **O modelo de tempo.** Se a resposta do item 1 for "existe uma data por rodada e os dias entre
  rodadas variam", o calendário vira dado do mundo e a fase 2 cresce. Se for "dias são contados como
  rodadas", encolhe. Nada da fase 1 depende disso.
- **A copa nacional pode não fechar** numa varredura só. O design já a trata como opcional.
- **Tamanho de teste.** Uma temporada do Brasil com estaduais são algumas centenas de partidas; o
  motor joga vinte mil em sete segundos, então a sanidade de temporada cabe no `check` com folga.
