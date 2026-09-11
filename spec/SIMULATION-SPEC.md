# Brasfoot 22-23 - Especificação comportamental do motor de jogo

Documento clean-room para reimplementação: descreve **o que o sistema faz** (fórmulas, constantes,
probabilidades, fluxo) - não reproduz código do jogo original.

Cada achado é marcado **CONFIRMADO** (lido diretamente da lógica) ou **INFERIDO** (dedução).

> Companion de `FORMAT-SPEC.md`, que cobre os formatos de arquivo. Este documento cobre o
> **comportamento**: simulação de partidas, economia, evolução de jogadores e estrutura de temporada.

---

## 0. Modelo de tempo e aleatoriedade

**Existe data de calendário real por trás de cada rodada.** CONFIRMADO - uma redação anterior desta
seção dizia que o jogo não modela dias nem meses; isso está errado, e a varredura do item 1 do
adendo v0.3 (ver `docs/superpowers/specs/2026-09-09-v03-temporada-design.md`) corrige o ponto. A
temporada inteira é montada de uma vez, no início, como uma lista corrida de dias entre 1 de janeiro
e 31 de dezembro do ano da temporada (ano-base escolhido na criação da carreira, somado a
`temporadaAtual - 1`); cada dia da lista carrega uma data real e, quando aplicável, as partidas
marcadas para aquele dia em qualquer competição do mundo. O contador de temporada
(`temporadaAtual`) continua um inteiro simples incrementado em 1 a cada virada, independente do ano
de calendário; comparações do tipo "temporada passada" continuam `temporadaAtual - 1`. A data de
início de cada temporada é o primeiro domingo de janeiro do ano correspondente.

**A rodada é um índice dentro da lista de dias, não uma unidade de tempo fixa.** CONFIRMADO -
avançar a rodada significa mover esse índice para o próximo dia da lista que tem ao menos uma
partida marcada, pulando em silêncio por cima de todo dia sem jogo nenhum. Por isso o passo entre
duas rodadas consecutivas **não é constante**: uma semana comum de liga cai de fim de semana em fim
de semana (sete dias), uma rodada de copa no meio da semana encurta o passo, e uma pausa de
data-FIFA ou o intervalo entre temporadas o alarga. Todas as competições do mundo compartilham essa
mesma lista de dias; um dia carrega um tipo de competição associado (por exemplo, o tipo "Nacional"
reúne no mesmo dia as rodadas de liga nacional de todos os países ativos que jogam naquela data), e
é esse compartilhamento, não uma rodada semanal fixa por competição, que faz "rodada" e "semana"
coincidirem no caso comum de uma liga que joga toda semana num único dia fixo. A seção 1.10 detalha
como as competições do calendário brasileiro padrão ocupam essa lista de dias.

**Grandezas medidas em dias (lesão 3.8, contrato 4.7) são datas de expiração absolutas, não
contagens de rodada.** CONFIRMADO - no instante em que uma lesão de N dias é sorteada ou um contrato
de N dias é criado/renovado, o motor lê a data real do dia da rodada em curso, converte para
milissegundos e soma `N dias`; o resultado fica gravado como a data de expiração. Dali em diante, o
jogador fica indisponível (lesão) ou o contrato conta como vencido enquanto essa data de expiração
continuar no futuro em relação à data real da rodada sendo processada - uma comparação de datas, não
um contador de rodadas decrescente, e não há divisão por 7 nem qualquer outra conversão para "número
de rodadas". Quantas rodadas uma lesão de N dias realmente custa depende de quantos dias com partida
daquele clube caem dentro da janela `[data da lesão, data da lesão + N dias)`: pode ser nenhuma
rodada perdida, se não houver jogo marcado na janela, ou várias, se mais de uma competição do clube
cruzar o mesmo intervalo. O "dias restantes de contrato" que a tabela de renovação da 4.7 usa é a
diferença, em dias, entre a data de expiração e a data real da rodada atual, arredondada para baixo
e nunca negativa; renovação manual soma dias diretamente à data de expiração existente.

**O tique semanal (evolução, 4.5) é um relógio de data, não de rodada.** CONFIRMADO - ele dispara em
todo domingo do calendário, existam ou não partidas marcadas naquele domingo, e independente de
quantas rodadas se passaram desde o domingo anterior: a montagem da temporada marca cada domingo do
ano como um evento pendente, e o processamento de uma rodada varre os dias entre o início da
temporada e a rodada em curso disparando, em ordem cronológica, todo evento pendente ainda não
executado. Nenhum domingo é pulado mesmo que várias semanas se passem sem partida nenhuma (uma
pausa longa dispara, em sequência, todos os domingos que ficaram para trás assim que a próxima
rodada com jogo é processada), e nenhum domingo extra é inventado. O contador de "4 semanas" que
decide o foco de treino da 4.5 é um contador de disparos desse próprio tique de domingo (incrementado
a cada chamada), não um segundo campo de data independente; as duas coisas só coincidem com o
calendário real quando a temporada tem exatamente um domingo entre disparos, que é o caso comum. Se
esse contador é reiniciado na virada de temporada não foi determinado por esta varredura - ver item
70 de `OPEN-QUESTIONS.md`.

**"Por rodada" e "por semana" não são sinônimos neste motor.** CONFIRMADO - a recuperação de energia
e o cumprimento de suspensão por cartão (3.1, 3.8, 3.9) correm a cada rodada processada (cada dia com
partida, de qualquer competição do clube), enquanto a evolução semanal (4.5) só corre nos domingos do
calendário, pelo mecanismo de dias pendentes acima. As duas cadências só coincidem quando a
competição de um clube joga exatamente uma rodada por semana, sempre aos domingos; um clube que joga
uma rodada de copa numa quarta-feira recupera energia e cumpre suspensão nessa quarta-feira, mas só
evolui no domingo seguinte.

**Leitura do mês fora do dinheiro.** INFERIDO por ausência - a única leitura do campo de mês
encontrada nesta varredura fica dentro do próprio fluxo de folha de pagamento (6.4): o clube confere
se já pagou naquele mês antes de descontar salário de novo, e o juro de empréstimo (6.4) também lê o
dia 2 do mês. Nenhum efeito do mês fora da seção 6 foi encontrado, mas a varredura não foi exaustiva
sobre esse ponto - ver item 71 de `OPEN-QUESTIONS.md`.

**Aleatoriedade não reprodutível.** CONFIRMADO - a classe do motor mantém um gerador como campo, mas
a classe de partida e dezenas de outros pontos criam **uma nova instância de gerador sem semente a
cada sorteio**. Em nenhum caso há semente. Não existe replay, seed nem determinismo. Uma
reimplementação que queira partidas reproduzíveis precisa introduzir um gerador semeado - isso é uma
melhoria, não uma incompatibilidade.

---

## 1. Estrutura de temporada e competições

### 1.1 Taxonomia de competições CONFIRMADO
16 tipos: Amistoso, Nacional, Copa Nacional, Estadual, Internacional 1, Mundial,
Internacional 2, Seleções, Recopa, Eliminatórias, Regionais, Supercopa, Conference League,
Finalíssima, Liga Nações, Torneio Amistoso.

Escala de prestígio: Municipal, Estadual, Regional, Nacional, Continental, Mundial.

Fases de mata-mata têm tabelas de nomes por tamanho de chave (Pré-Oitavas -> Oitavas -> Quartas ->
Semifinal -> Final), além de ladders de preliminares/qualificatórias para torneios continentais.

### 1.2 Classificação (tabela) CONFIRMADO
Registro por time e por competição = **8 valores**:
`[0] pontos, [1] jogos, [2] vitórias, [3] empates, [4] derrotas, [5] gols pró, [6] gols contra, [7] saldo`
(empates são derivados: jogos - (vitórias + derrotas)).

**Critério de desempate da tabela é FIXO e igual para todos os países**:
`pontos desc -> vitórias desc -> saldo de gols desc -> gols pró desc`.
Existe **um único** comparador de classificação em todo o binário; não é configurável.
-> Isto **corrige** a hipótese anterior de que o campo `desempate` dos configs escolhia critério de tabela.

**Pontuação: vitória vale 3 pontos, empate vale 1, derrota vale 0.** CONFIRMADO - o sistema moderno
de 3 pontos por vitória, não o antigo de 2. Por partida, cada lado sempre soma 1 a jogos e os gols
marcados/sofridos ao agregado; o lado vencedor soma 3 a pontos e 1 a vitórias, o perdedor soma 1 a
derrotas (sem pontos), e um empate soma 1 ponto a cada lado sem mexer em vitórias nem derrotas -
exatamente consistente com a nota da tabela acima de que os empates são derivados.

**O mesmo comparador único vale dentro de um grupo.** CONFIRMADO - uma fase de grupos (liga nacional
com `nGrupos > 0`, estadual com preset de grupos, ou qualquer competição continental/copa com fase de
grupos, seção 1.11/1.14) classifica cada grupo com este mesmo critério fixo, aplicado só entre os
times daquele grupo; não existe um comparador diferente para fase de grupos. Uma tabela geral entre
todos os grupos de uma mesma divisão/competição (usada para o rebaixamento da 4a divisão brasileira,
por exemplo - seção 1.11) usa o critério idêntico sobre o conjunto inteiro.

### 1.3 Geração de tabela de jogos CONFIRMADO
Número de turnos (returnos) por tamanho da liga:
8 times -> 4 turnos, 10 times -> 4, 12 times -> 3, 14 times -> 3, 26/28/30/36 times -> 1,
20 times (com um código de formato específico) -> 1, demais -> 2.
O campo `formula` do `.cfg` de liga nacional é na verdade **`numeroTurnos`** e só age nos casos de
10/12/14 times, reduzindo para 2 ou 3 turnos. (Nos estaduais, `formula` continua sendo o índice de
preset de fase final - classe diferente, significado diferente.)

**O algoritmo de pareamento é o método do círculo clássico ("circle method").** CONFIRMADO - um time
fica fixo numa posição e os `n-1` times restantes giram uma posição a cada rodada; a rodada `r`
empareia a posição `(r+i) mod (n-1)` contra a posição `(r-i) mod (n-1)`, com o time fixo assumindo o
papel do índice que sobraria. Para `n` times (par), isso cobre exatamente `n-1` rodadas de `n/2`
partidas cada, todos os pares uma única vez.

**A ordem dos times antes do círculo é um embaralhamento aleatório completo, não a ordem de
entrada, nível ou nome.** CONFIRMADO - a lista de participantes recebe uma permutação uniforme
sorteada de uma vez no início da geração daquela tabela, antes do método do círculo ser aplicado.
Esse sorteio usa o gerador de números aleatórios padrão da linguagem, e não a função `rand(N)` que o
resto do motor usa - numa reimplementação com gerador semeado próprio isto é um ponto de
aleatoriedade à parte (equivalente a embaralhar a lista), não uma sequência de `rand(N)` a mais para
contar.

**Contagem ímpar de times.** Existe, na mesma rotina, um caminho que preenche a lista com uma "folga"
antes do pareamento quando a contagem é ímpar, mas ele é código morto para os campeonatos do jogo:
toda contagem ímpar plausível (3, 5, 9, 11, 19, 25) tem rota própria com tabela de pares fixa antes
de chegar a esta rotina genérica, e todo tamanho de liga nacional configurável (8, 10, 12, 14, 18,
20, 26, 28, 30, 36 - a lista desta seção e os degraus da 1.9) é par. CONFIRMADO que o caminho existe;
INFERIDO que é inalcançável em qualquer campeonato realmente configurado pelo jogo.

**Mando dentro do turno.** O círculo bruto colocaria o time fixo sempre do mesmo lado do mando em
toda rodada de que participa, o que o deixaria todo mandante ou todo visitante. Por isso, depois de
montada a sequência bruta de rodadas de um turno, as rodadas são **reordenadas** (a sequência final
intercala a 1a, a 4a, a 2a, a 5a e a 3a rodada bruta, generalizando para `n-1` rodadas) e, nas
rodadas que caem em posição ímpar dessa nova ordem, o mando da partida do time fixo é invertido. O
efeito é balancear o mando do time fixo entre casa e fora ao longo do turno, em vez de fixá-lo de um
lado só.

**O segundo turno não é gerado de novo: é o primeiro turno espelhado.** CONFIRMADO - mesmos
confrontos, mesmo agrupamento em rodadas, com mando e visita **trocados em todas as partidas** (não
só na do time fixo). Para ligas de 3 turnos (12, 14 times), o terceiro turno é a repetição literal do
primeiro. Para ligas de 4 turnos (8, 10 times), a sequência é turno1, turno2 (espelhado), turno3 =
repetição do turno1, turno4 = repetição do turno2 - não há um terceiro sorteio nem uma terceira
relação de mando distinta; essas ligas jogam, na prática, o mesmo par ida/volta duas vezes.

**O "código de formato específico" do caso 20 times -> 1 turno** é o mesmo valor de configuração que,
em outro ponto do mesmo mecanismo, marca que a fase de pontos corridos alimenta uma fase eliminatória
de 4 ou 8 times. Não é uma regra de tamanho isolada: é a marca de um formato **liga + mata-mata**
(pontos corridos como classificatória para uma fase final eliminatória), e o turno único é
consequência de a rodada servir só para classificar, não para decidir o título direto. CONFIRMADO
quanto ao mecanismo; qual competição real do jogo usa esse formato é matéria de quem documenta os
formatos das outras competições da 1.1, não desta seção.

**O que muda com grupos (`nGrupos`).** Uma opção separada decide se há jogos **dentro** do grupo:
- **Ligada:** cada grupo recebe sua própria tabela, gerada de forma totalmente independente pelo
  mesmo algoritmo acima (embaralhamento do grupo, círculo, balanceamento de mando, espelhamento para
  o segundo turno se houver). Não há rodada cruzando grupos.
- **Desligada:** em vez do círculo, o confronto sai de uma **tabela de pares fixa e pré-calculada**
  que casa posições específicas de um grupo contra posições específicas de outro (por exemplo, 1o do
  grupo A contra 2o do grupo B), sem sorteio de ordem nenhum. Essas tabelas fixas só existem para
  formatos de competição específicos; uma liga nacional genérica com `nGrupos > 1` e esta opção
  desligada não tem tabela de fallback nesta rotina - se algum campeonato do jogo realmente usa essa
  combinação, e com qual tabela fixa, é lacuna registrada para quem documenta os formatos das outras
  competições da 1.1, não resolvida aqui.

**Exemplo trabalhado: liga de 6 times, 2 turnos.** `T1..T6` já na ordem pós-embaralhamento (a ordem
de entrada é irrelevante, ver acima); `T6` é o time que cai na posição fixa do círculo neste sorteio.

Turno 1:

| Rodada | Partida 1 (mandante-visitante) | Partida 2 | Partida 3 |
|---|---|---|---|
| 1 | T1-T6 | T2-T5 | T3-T4 |
| 2 | T6-T4 | T5-T3 | T1-T2 |
| 3 | T2-T6 | T3-T1 | T4-T5 |
| 4 | T6-T5 | T1-T4 | T2-T3 |
| 5 | T3-T6 | T4-T2 | T5-T1 |

Turno 2 (mesmos confrontos, mando invertido em cada partida):

| Rodada | Partida 1 | Partida 2 | Partida 3 |
|---|---|---|---|
| 6 | T6-T1 | T5-T2 | T4-T3 |
| 7 | T4-T6 | T3-T5 | T2-T1 |
| 8 | T6-T2 | T1-T3 | T5-T4 |
| 9 | T5-T6 | T4-T1 | T3-T2 |
| 10 | T6-T3 | T2-T4 | T1-T5 |

Confere: 15 pares distintos, cada um jogado uma vez por turno (30 partidas no total), e cada time
termina com exatamente 5 jogos em casa e 5 fora somando os dois turnos - inclusive `T6`, o time da
posição fixa, que sem o passo de balanceamento de mando ficaria desequilibrado.

### 1.4 Sequência de fim de temporada CONFIRMADO
Ordem exata: reconstrução de elencos dos participantes de liga -> reconstrução dos demais times ->
reset de temporada por time -> limpeza de vínculos órfãos -> êxodo de craques de clubes pequenos.

**Sem mercado (v0.3): os dois primeiros passos e o último não rodam.** "Reconstrução de elencos dos
participantes de liga" e "reconstrução dos demais times" são a 1.7 (movimentações de mercado pela
IA); "êxodo de craques de clubes pequenos" é a 1.6. Os dois ficam fora até a v0.6, como o design já
registra. Os dois passos do meio **não dependem de mercado nenhum** e continuam valendo:

- **Reset de temporada por time.** Chamado no início da temporada seguinte (não no fecho da
  anterior), roda sobre todo jogador do mundo (profissionais, juniores e técnicos igualmente): só
  resincroniza o vínculo de clube guardado em cache a partir do vínculo real do objeto - é uma
  atualização barata, não um zeramento de estatística de partida (essas já vivem no log de eventos
  da temporada, e leituras futuras simplesmente não olham para trás). É o mesmo passo que zera as
  listas auxiliares de "quem chegou"/"quem saiu" do mundo inteiro antes de começar a computar a
  próxima leva.
- **Limpeza de vínculos órfãos.** O mesmo resync acima é o que corrige qualquer referência de clube
  que ficou desatualizada durante a temporada (por exemplo um jogador cujo clube mudou de
  identidade); não há uma etapa de remoção de objetos separada a reproduzir - "limpeza" aqui é
  literalmente esse resync, não uma coleta de lixo.

**Ordem exata entre esses marcos e os da 4.5/4.10/4.11/5.5, dentro do mesmo fechamento de
temporada:** (1) a reputação (5.5) decai e promove **primeiro**, clube por clube, junto com a
listagem de excedente do mercado (1.7, fora do escopo da v0.3) e o recálculo de batedor/capitão
(5.6) de cada clube da IA; (2) o saldo de prestígio do técnico (achado à parte da 1.5) é atualizado
pela mesma regra da 5.5; (3) **só depois** roda, numa passada à parte sobre as listas do mundo
inteiro (não mais clube por clube), a reciclagem de agentes livres, o teste de aposentadoria e a
promoção da base (4.11); (4) a reconstrução de elenco pela IA (1.7, fora de escopo) roda depois
disso; (5) **por último**, a demissão de técnico desta seção, que por isso já enxerga o elenco
pós-aposentadoria/promoção mas ainda o técnico da temporada que terminou. **A reputação, portanto,
decai e promove ANTES da aposentadoria/promoção da base, não junto com a demissão de técnico** como
uma leitura apressada da ordem poderia sugerir - são três momentos distintos da mesma passada de
fim de temporada. O reset de temporada por time e a limpeza de vínculos órfãos descritos acima
ficam de fora dessa passada de fim de temporada: são INFERIDO como parte da preparação da
temporada **seguinte** (junto com a revalorização de mercado e salário, 4.9/4.8), não confirmados
no mesmo método que os passos 1-5. Ver o item 102 de `OPEN-QUESTIONS.md`.

### 1.5 Demissão de técnicos CONFIRMADO
Para cada clube com técnico: calcula-se o **aproveitamento em vitórias da temporada anterior**
`pctVit = vitórias x 100 / jogos`. Sorteia-se `r` uniforme em `1..90`.
**Se `r > pctVit`, o técnico é demitido.** Ou seja, probabilidade de permanência ~ `min(pctVit, 90)/90`.
Um técnico com 45% de vitórias cai em ~50% dos casos; com >=90% é praticamente intocável.
Empates e derrotas não são distinguidos - só a taxa de vitórias importa.
(Técnicos da IA só são efetivamente trocados se o clube também terminou abaixo do 6º lugar.)
O treinador **humano** tem um mecanismo separado e mais rígido - confiança da diretoria, seção 6.7.
**Este teste é o último passo da passada de fim de temporada** relevante à evolução de elenco -
roda depois da aposentadoria/promoção da base e depois da reconstrução de elenco pela IA (1.7, fora
do escopo da v0.3), lendo o `pctVit` da temporada que acabou de fechar.

**O técnico não influencia a simulação em nada além de existir.** Não há leitura de nenhum campo do
técnico em nenhuma fórmula de duelo, nota, energia, crescimento ou escalação automática; os únicos
lugares em que o objeto do técnico é tocado durante uma partida são dois ganchos de pós-partida que
só atualizam o **próprio** registro de vitórias/jogos do técnico (o `pctVit` que este item consome)
- nunca leem nada de volta para a simulação. A v0.3 pode modelar o técnico exatamente como o design
propõe: um nome, o aproveitamento (derivado dos resultados do clube, não precisa de contador à
parte) e a demissão desta seção. **Achado à parte:** o técnico carrega o próprio saldo de prestígio
e reputação (0-5), decaindo e promovendo pelas mesmas regras da 5.5, mas isso não alimenta nada que
a v0.3 precise - nenhuma fórmula de contratação, demissão ou simulação lê a reputação do técnico;
fica registrado para quando a v0.6 (mercado, contratação de técnico) precisar dele.

### 1.6 Êxodo de craques CONFIRMADO
Um clube é "pequeno" se está fora da Europa com reputação < 5, ou na Europa com reputação < 4.
Todo jogador desse clube com **força > 50, idade < 31 e flag de estrela** tem **74% de chance**
(`rand(100) > 25`) de ser colocado no mercado, mirando **apenas clubes europeus de reputação >= 4**;
o preço é exatamente **100% do valor de mercado**. Há uma segunda tentativa de fallback se a primeira falhar.

### 1.7 Reconstrução de elenco pela IA CONFIRMADO
O número de movimentações de mercado escala com a **posição final na temporada anterior**:
posição <= 1 -> 1 movimento, <= 5 -> 2, <= 10 -> 3, demais -> 4.
O motor executa esse número de passes de entrada e o mesmo número de passes de saída, com cotas
por posição (o primeiro passe usa cotas 4/5/5/10/8 entre os slots de posição).

### 1.8 Suspensões por tribunal (STJD) CONFIRMADO
Além de cartões, existe um evento disciplinar de tribunal com **4 níveis de pena fixos**:
**2, 3, 5 e 10 jogos** de suspensão. Cada nível tem várias redações de notícia
(2 jogos: **6 variantes** - correção desta varredura, a contagem anterior de 3 estava errada -,
3 jogos: 5, 5 jogos: 3, 10 jogos: 2), sorteadas por gravidade da
infração narrada (falta dura -> 2; carrinho por trás -> 3 ou 5; agressão/ofensa ao árbitro -> 5 ou 10).

**Não existe um segundo sorteio, nem uma probabilidade própria de tribunal por rodada ou por
partida.** CONFIRMADO - "STJD" não é um evento separado da disciplina em campo: é a narrativa que o
jogo anexa ao **mesmo sorteio de gravidade do cartão vermelho direto** já descrito na seção 3.8
(`rand(1000)`: <700 -> 1 jogo, <900 -> 2, <970 -> 3, <=990 -> 5, senão 10). Um vermelho direto cujo
sorteio sai em 1 jogo (70% dos vermelhos diretos) nunca gera notícia de tribunal nenhuma; os outros
30% (2, 3, 5 ou 10 jogos) geram, sempre, a redação correspondente ao nível sorteado. Expulsão por
segundo amarelo nunca passa por este sorteio e por isso nunca gera notícia de tribunal - ela soma
só o 1 jogo de gancho fixo que a 3.8 já descreve. O evento dispara **dentro da própria partida**, no
minuto em que a rolagem de vermelho direto da 3.8 roda, e vale para qualquer competição em que um
vermelho direto ocorra - não há filtro de competição nem checagem adicional por rodada.

**O alvo é sempre a vítima do próprio sorteio de vermelho direto que originou a pena** - o mesmo
jogador que a cadeia time-vítima -> grupo de risco -> jogador da seção 3.8 já escolheu para aquele
minuto. O evento de tribunal não sorteia um segundo jogador nem um segundo clube; ele só decora com
uma notícia o resultado que o cartão vermelho já calculou.

**O contador que a pena de tribunal incrementa é o mesmo gancho da 3.8, sem campo separado.**
CONFIRMADO - os "2, 3, 5 ou 10 jogos" são aplicados chamando, em laço, exatamente o mesmo
incremento de um jogo de gancho que um vermelho direto comum de 1 jogo usa uma única vez; uma pena
de tribunal de 5 jogos é o mesmo campo de gancho incrementado 5 vezes seguidas. Não existe overlay,
não existe uma segunda trava de disponibilidade, e a interação com o contador de amarelos é
exatamente a que a 3.8 já descreve para o gancho de cartão vermelho.

### 1.9 Ligas nacionais: pirâmide gerada por país e papel dos `.cfg` CONFIRMADO

**Não existe tabela estática de pirâmides no jogo.** A pirâmide de cada país é **gerada na criação
do mundo** a partir da contagem de arquivos de time, e os `.cfg` de `conf_ligas_nacionais/` são
**sobrescritas por divisão** colocadas por cima desse gerador - exatamente a hipótese do item 27 de
OPEN-QUESTIONS. O mecanismo completo:

**Elegibilidade.** Um país é candidato a ter liga nacional se tem **pelo menos 10 arquivos de time**
válidos; para os cinco países `{ALE 3, ARG 11, ING 97, ITA 104, FRA 72}` o limiar é **16**. (Note:
esta lista de cinco inclui a Argentina e não inclui a Espanha - é uma lista diferente da dos "cinco
grandes" do salário da 4.8.)

**Ativação pelo usuário.** A lista de países candidatos é oferecida na criação do jogo e o usuário
marca quais ligas serão disputadas; o Brasil vem pré-marcado. Um `.cfg` **não cria nem ativa** uma
liga: ele só configura as divisões de um país que já é candidato e foi ativado.

**Instanciação de clubes.** Para um país com liga ativa, **todos** os clubes do país entram no
mundo. Para um país sem liga ativa, só os **15 primeiros** arquivos na ordenação por nível
decrescente entram; os demais nem existem na carreira.

**Divisões.** Para cada país ativo, até **4 divisões**, montadas nesta ordem para div = 1..4:

1. Procura-se um registro de configuração para o par (país, divisão) na união de **todos** os
   `.cfg` carregados.
2. Se existe e o `nTimes` dele cabe nos clubes restantes, a divisão usa a configuração do arquivo
   (times, turnos, grupos, mata-mata, rebaixados; `nRebaixados > 2` com `nTimes <= 10` é
   **grampeado para 2**).
3. Senão, vale o **padrão embutido**: o tamanho é o maior degrau da lista `20, 18, 16, 14, 12, 10`
   que cabe nos clubes restantes, com **4 rebaixados quando o degrau é 20 e 2 nos demais**, pontos
   corridos sem grupos nem mata-mata, e turnos pelo padrão de tamanho da 1.3 (20 times -> 2
   turnos; 10 times -> 4).
4. Com menos de 10 clubes restantes, a divisão não é criada e a pirâmide para ali.

**Atribuição de clube a divisão.** Os clubes do país são ordenados por **nível decrescente**, com
empate desfeito por um **número aleatório sorteado na criação de cada clube** (1..1000) - ou seja, o
desempate muda a cada mundo gerado. Cada divisão toma os primeiros `nTimes` da fila, na ordem. Quem
sobra depois da última divisão fica **sem divisão** (divisão 0), mesmo em país com liga ativa.

**Caso especial do Brasil.** Com os estaduais ligados, a 4a divisão brasileira não é preenchida por
nível: as vagas dela vêm da classificação nos estaduais.

**Por que país sem liga não desaba.** Os clubes de país cuja liga não é disputada usam o caminho de
**reputação** tanto na força de criação da 4.4 quanto no teto de crescimento da 4.5 (reputação
5/4 -> teto 100, 3 -> 70, 2 -> 40, 1 -> 30, 0 -> 20, limitado pelo teto de país), e não a linha
"sem divisão" da tabela de divisões. Um clube grande de país não configurado nasce e evolui pela
reputação 5, não pela base 1. A linha "sem divisão" (teto 30, base 1) só atinge clube de país com
liga **ativa** que sobrou fora das divisões.

### 1.10 Calendário da temporada

**Não existe uma tabela estática "rodada N = competição Y".** CONFIRMADO - a distribuição das
competições pela temporada é **emergente**, não uma tabela fixa lida de configuração. Ela nasce da
combinação de dois mecanismos já descritos na seção 0: (1) a lista corrida de dias do ano, cada um
com um tipo de competição associado e, quando aplicável, as partidas marcadas para aquele dia em
qualquer competição do mundo que use aquele tipo; e (2) cada instância de competição (uma divisão
de liga, o campeonato de um estado, a copa nacional, um torneio continental, etc.) carrega o próprio
contador de rodada e o próprio total de rodadas, calculado a partir do número de participantes e do
número de turnos da própria competição (a tabela de turnos da 1.3). Processar "a próxima rodada" de
um país é iterar sobre toda competição atualmente ativa daquele país e, para cada uma que ainda tem
rodada pendente, deixá-la processar sua própria próxima rodada nessa mesma passada, agrupando no
mesmo dia as partidas de todas as competições cujo turno caiu ali. Quando o contador de rodada de
uma competição ultrapassa o total dela, só aquela competição termina e dispara o que lhe é próprio
(fase de mata-mata seguinte, avaliação de demissão de técnico da 1.5 para liga e estadual, vaga em
torneio continental) - o fim de uma competição não trava as outras.

**Todas as ligas nacionais do mundo compartilham as mesmas datas do tipo "Nacional" no calendário
global** CONFIRMADO - Brasil, Argentina e qualquer outro país com liga ativa jogam a própria rodada
de liga no mesmo dia de calendário; o que muda de país para país é só o conteúdo da rodada (os jogos
de cada liga), não a data.

**Uma competição sem partida nesta rodada de calendário não avança nem cumpre suspensão alguma
para seus clubes nesta rodada** (ver 1.8/3.8 - "cumprir" é por partida efetivamente disputada pelo
clube naquela competição). A recuperação de energia da 3.9 segue a mesma regra, porque é aplicada
como parte do pós-rodada de cada competição (seção 3.1): um clube cuja competição não tem rodada
neste dia simplesmente não passa pelo pós-rodada naquele dia e não recupera energia por ele - mesmo
que outras competições do mesmo país, ou de outros países, estejam processando rodada nesse exato
dia. **A evolução semanal (4.5) é a exceção**: por ser presa à data (todo domingo do calendário,
seção 0) e não a um pós-rodada de competição específica, ela roda para **todo** clube do mundo em
todo domingo, independente de ter jogado, de sua competição ter rodada marcada aquele dia, ou até de
sua competição estar em pausa. INFERIDO a partir da arquitetura confirmada de disparo por data da
4.5 contra o disparo por partida da 3.8/3.9; nenhum teste específico "clube parado evolui mesmo assim"
foi encontrado isolado, mas é a única leitura consistente com os dois mecanismos de disparo
confirmados.

**Não há garantia estrutural de no máximo uma partida por clube por rodada de calendário.** O passo
de avanço de país descrito acima processa, na mesma passada, toda competição ativa daquele país sem
checar se um clube já tem partida marcada em outra competição do mesmo país nesse dia; se dois
calendários de competição do mesmo país colidissem na mesma data, o motor não tem um mecanismo
explícito que o impeça. Se as configurações realmente distribuídas evitam essa colisão na prática
(por exemplo, mantendo copa nacional e estaduais em tipos de dia disjuntos dos da liga) não foi
confirmado nesta varredura - ver item 72 de `OPEN-QUESTIONS.md`.

**Tamanho da temporada e fim de temporada.** O total de rodadas de uma temporada não é uma
constante fixa: é o máximo, entre as competições ativas de um país, do total de rodadas de cada uma
(que por sua vez sai do número de participantes e de turnos, seção 1.3). Existe uma marca de "a
temporada deste país terminou", mas o gatilho exato que a liga não foi fixado com certeza nesta
varredura - ver item 73 de `OPEN-QUESTIONS.md`. Para o Brasil no padrão (liga nacional + estaduais
ligados), isso soma a rodada de liga nacional (20 times -> 2 turnos -> 38 rodadas, pela tabela da
1.3), as rodadas de cada divisão estadual (por preset do `.ces`, ver FORMAT-SPEC) e a copa nacional;
a **ordem exata** em que os dias de tipo "Estadual" e "Nacional" se intercalam ao longo do ano (se os
estaduais terminam antes de a liga nacional começar, ou se as duas se alternam por semana) não foi
determinada com o mesmo grau de confiança que o mecanismo geral acima - a leitura mais provável, por
corresponder ao formato real que a `.ces`/`.cfg` do jogo descreve para o futebol brasileiro
(estaduais concentrados no primeiro terço do ano civil, liga nacional ocupando o restante,
intercalados pela copa nacional e por datas de seleção), é registrada como **INFERIDO** e recebe o
item 74 de `OPEN-QUESTIONS.md` em vez de entrar nesta tabela como fato lido do motor.

### 1.11 Ligas nacionais com grupos e fase final CONFIRMADO

Uma divisão de liga nacional pode ter `nGrupos > 0` (FORMAT-SPEC, campos de `ConfigLigaType`). O
mecanismo é o mesmo motor genérico de fase-de-grupos-mais-mata-mata que a `.ces` já documenta para os
estaduais, reaproveitado literalmente pela mesma classe do motor:

- Os times da divisão são distribuídos em `nGrupos` grupos. Cada time joga contra os times dos
  outros grupos e, quando `jogosDentroGrupo` está ligado (é o caso em todas as entradas distribuídas,
  inclusive a 4a divisão brasileira de 8 grupos), também contra os do próprio grupo - ou seja, um
  campeonato dentro do grupo mais os cruzamentos entre grupos, não um formato "só entre grupos" como
  os presets 7/10 do estadual (que desligam `jogosDentroGrupo`).
- `numeroTimesMataMata` classificados de cada grupo (ou, com `classificaPeloGeral` ligado, os
  primeiros da tabela geral entre todos os grupos, ignorando a divisão por grupo) vão à fase final,
  emparelhados dentro do próprio grupo nas rodadas iniciais quando aplicável, do mesmo jeito que o
  preset 7/10 da `.ces` (1o de cada grupo contra o 2o do grupo seguinte etc.) e depois em mata-mata
  cruzado até a final. `melhoresTerceiros` soma à lista de classificados os melhores 3os colocados
  entre grupos, quando o tamanho da chave final pede um número de vagas que não é múltiplo exato de
  classificados-por-grupo (o mesmo mecanismo do estadual, mas aqui o campo é **configurável pelo
  `.cfg`**, ao contrário do estadual onde o carregador sempre desliga a opção na carga).
- Nenhuma das duas entradas distribuídas (`BRA.cfg`, `ESP.cfg`) ativa `melhoresTerceiros` ou
  `classificaPeloGeral` de fato - o campo existe e é lido, mas nenhum dos dois países o liga; a única
  divisão com grupos de fato é a 4a brasileira (8 grupos,
  `numeroTimesMataMata = 4`, `rebaixadoPeloGrupo = false` - ou seja, mesmo com grupos, quem desce é
  definido pela tabela geral entre os 8 grupos, não posição-a-posição dentro do grupo).

**Os dois formatos sentinela de `numeroTimesMataMata`.** O campo normalmente é uma contagem de
classificados, mas dois valores especiais desviam para um formato dedicado, escrito à mão para a
liga brasileira:

- **`1020` (Série C, divisão 3 do Brasil).** A primeira fase é pontos corridos comum (sem grupos: a
  divisão 3 distribuída tem `nGrupos = 0`) entre os `nTimes` times. Ao fim, os **8** primeiros viram
  uma **segunda fase**: dois grupos de 4 (`nGrupos = 2`), turno e returno completos
  (`doisTurnos = true`), com **1** classificado por grupo indo a uma **final** de dois jogos entre os
  dois vencedores de grupo. Rebaixamento e classificação para a fase final desta segunda etapa usam o
  mesmo critério fixo da 1.2. O `nRebaixados` da divisão (4, no `BRA.cfg`) continua sendo lido da
  tabela geral da **primeira** fase, não da segunda.
- **Fase preliminar de 68 times (excesso de candidatos na 4a divisão brasileira).** Quando o total de
  candidatos à 4a divisão (a fila descrita na 1.12 abaixo) chega a 68 mas a divisão está configurada
  para 64 (o caso do `BRA.cfg` distribuído), os 8 últimos da fila (por nível/critério de fila, não de
  tabela - a divisão ainda não jogou) disputam uma **fase preliminar** de mata-mata de 8 times, ida e
  volta, **antes** da fase de grupos; os 4 vencedores completam as vagas da fase de grupos junto com
  os 60 primeiros da fila. Com 64 candidatos ou menos, a fase preliminar não existe e os 64 entram
  direto nos grupos.

**Campo morto.** `playoffRebaixamento` (int) é serializado e tem getter/setter, mas nenhuma rotina do
motor o lê; quem decide se há playoff de rebaixamento/acesso é a combinação `rebaixadosDireto <
nRebaixados` da 1.12, não este campo. Ver FORMAT-SPEC para a tabela completa dos campos de
`ConfigLigaType`.

### 1.12 Subida e descida CONFIRMADO

**Não existe campo de "número de promovidos" configurável.** O número de promovidos de uma divisão é
sempre igual ao `nRebaixados` da divisão imediatamente acima (para a divisão de topo, zero: nada sobe
para lá). Isso é estrutural: a lista de "quem sobe" de uma divisão é dimensionada, na criação da
temporada, pelo `nRebaixados` da divisão acima dela, não por um campo próprio.

**Swap simples entre duas divisões adjacentes** (o caso comum - nenhuma das quatro divisões
brasileiras usa playoff, ver FORMAT-SPEC):

1. Da divisão de cima, descem os `nRebaixados` últimos da tabela geral (ou, com fase final, na
   **ordem de mérito**: campeão, vice, eliminados das rodadas anteriores do mata-mata em ordem de
   chave, completando pela tabela geral se faltar - o mesmo critério de mérito que a `.ces` já
   documenta para o estadual, reaproveitado pelo mesmo motor).
2. Da divisão de baixo, sobem os primeiros da tabela geral (ou, com fase final, na mesma ordem de
   mérito) em número igual ao `nRebaixados` da divisão de cima.
3. As duas listas trocam de lugar: quem desceu entra na divisão de baixo, quem subiu entra na de
   cima. A troca só ocorre se as duas listas têm o mesmo tamanho, o que é garantido pela construção
   acima sempre que a divisão de baixo consegue formar sua lista de acesso completa.
4. As divisões são processadas de cima para baixo, uma fronteira por vez (1-2, depois 2-3, depois
   3-4, ...), na mesma passada de fim de temporada.

**Playoff de rebaixamento e de acesso (`rebaixadosDireto` e `vagasSobemPeloMataMata`, FORMAT-SPEC).**
Quando `rebaixadosDireto < nRebaixados` de uma divisão, só os `rebaixadosDireto` piores caem direto;
os `nRebaixados - rebaixadosDireto` restantes são decididos por um **playoff de rebaixamento**: os
times na fronteira de baixo desta divisão enfrentam, em confrontos de ida e volta (ou jogo único,
conforme `duasVoltasplayoffReb`) times vindos de logo abaixo na divisão inferior; o perdedor de cada
confronto fica (ou cai para) a divisão de baixo, o vencedor fica (ou permanece) na de cima.
Simetricamente, quando `vagasSobemPeloMataMata > 0` numa divisão, esse número de vagas de acesso à
divisão de cima não vai automaticamente aos primeiros colocados: um grupo de times logo abaixo da
zona de acesso direto desta própria divisão disputa entre si um **playoff de acesso**
(`duasVoltasMataMataSobe`) pelas vagas remanescentes. O exemplo real é a 2a divisão espanhola: das 3
vagas de rebaixamento, 2 são diretas e a 3a vai a playoff contra a 3a divisão; e 1 das vagas de acesso
à 1a divisão é decidida por um playoff interno à própria 2a divisão entre os times abaixo da zona de
acesso direto. CONFIRMADO que o mecanismo existe e é acionado pelos dois campos; a composição exata
dos grupos de candidatos ao playoff (quantas posições da tabela entram na disputa de cada lado) seguiu
a leitura do código mas não foi verificada posição-a-posição contra uma temporada jogada - ver item 80
de OPEN-QUESTIONS.

**Última divisão: troca com a reserva do país.** A divisão mais baixa de cada país não tem divisão
abaixo dela para alimentar sua lista de acesso; em vez disso, troca com a **reserva do país** (os
clubes do país com liga ativa que sobraram fora de todas as divisões, seção 1.9): saem os
`min(nRebaixados, tamanho da reserva)` últimos da tabela geral da última divisão e entram os primeiros
da reserva (fila por nível, com o mesmo desempate aleatório por clube da 1.9); os rebaixados vão para
o fim da fila da reserva e os promovidos saem do início. Mecanismo idêntico ao que a FORMAT-SPEC já
documenta para a troca entre a última divisão estadual e a reserva do estado.

**Caso especial: a 4a divisão brasileira alimentada pelos estaduais.** Quando os estaduais estão
ativos (padrão ligado) e a 4a divisão do Brasil não é preenchida por nível na criação do mundo (seção
1.9), ela também não participa do swap normal de fim de temporada contra a 3a divisão: a 3a divisão
brasileira relega seus times **direto** (o motor força `rebaixadosDireto = nRebaixados` nesse caso
específico, mesmo que o `.cfg` dissesse outra coisa - nenhum playoff de acesso à 3a divisão a partir
da 4a). A 4a divisão, por sua vez, é reconstruída a cada temporada a partir da **fila de campeões e
vice-campeões estaduais**, não por uma tabela geral própria que carrega candidatos de uma temporada
para a seguinte. A fila é montada assim:

1. Os 27 estados são agrupados em 5 níveis de prioridade (lido do código, tabela fixa):
   nível 1 = SP, RJ, MG, RS; nível 2 = PR, SC, BA, PE, CE; nível 3 = GO, AL, PA, MS, MA, RN, PB;
   nível 4 = SE, AC, PI, MT, AM, DF; nível 5 = ES, TO, RO, AP, RR.
2. A fila toma, primeiro, o **campeão** de cada estado dos 5 níveis em ordem (nível 1 ao 5); depois
   volta ao nível 1 e toma o **vice-campeão** de SP/RJ/MG/RS, o 3o colocado dos mesmos quatro, o 2o
   colocado de PR/SC/BA/PE/CE, o 2o colocado de GO/AL/PA/MS/MA/RN/PB, o 2o colocado de
   SE/AC/PI/MT/AM/DF, e segue aprofundando por posição nessa mesma ordem de prioridade (nível 1
   chegando a aprofundar mais posições que os demais) até acumular candidatos suficientes para o
   tamanho configurado da divisão (64, ou 68 quando a fase preliminar da 1.11 é necessária). A posição
   de cada time dentro do seu estado é a classificação final do respectivo estadual (critério fixo da
   1.2). Times que a temporada anterior já deixou "na porta" da 4a divisão (rebaixados da 3a divisão
   quando ela já estava associada aos estaduais) entram no início da fila, à frente dos candidatos
   novos dos estaduais dessa temporada.
3. Se a fila de candidatos dos estaduais não chegar ao tamanho configurado, o motor completa com
   clubes brasileiros sem divisão estadual nenhuma, na ordem em que aparecem no mundo.
4. Faltando ainda candidatos, a lista fica menor que o configurado (o motor não força um tamanho
   artificial). MEDIDO/CONFIRMADO quanto ao mecanismo e à tabela de estados por nível; a leitura não
   cobriu uma temporada completa jogada para confirmar posição-a-posição - ver item 81 de
   OPEN-QUESTIONS.

**A pirâmide não é reconstruída do zero a cada temporada.** CONFIRMADO - a montagem da 1.9 (contagem
de arquivos de time, elegibilidade de país, `.cfg` por divisão) roda uma vez, na criação do mundo. Nas
temporadas seguintes, a composição de cada divisão evolui só pelo swap de sobe-e-desce acima; não há
um nova leitura de nível de clube nem um novo sorteio de desempate por clube a cada ano. A pirâmide de
um país sem liga ativa (fora do escopo de subida e descida) tampouco muda de composição entre
temporadas por este mecanismo.

### 1.13 Copa Nacional CONFIRMADO

Uma Copa Nacional (tipo 2 da taxonomia da 1.1) é criada por país ativo, não por um `.cfg` - não há
arquivo de configuração para ela. Dois formatos coexistem no mesmo código, escolhidos pelo total de
clubes do país e pela opção "novo formato de copa" (`Options.novoFormatoCopa`, **ligada por padrão**):

**Formato padrão** (usado sempre que o país tem menos de 91 clubes, ou tem 91+ mas a opção de novo
formato está desligada; qualquer país com liga ativa e ao menos 8 clubes tem Copa Nacional neste
formato): mata-mata puro de eliminação simples, **do maior tamanho de chave (potência de 2, até 128)
que cabe no total de clubes do país, com mínimo de 8**. Os clubes do país inteiro (todos, não só os
que têm divisão) são ordenados por nível; a metade mais forte e a metade mais fraca de quem entra na
chave são cada uma embaralhada internamente, e depois intercaladas fraco-forte por posição de chave -
ou seja, um chaveamento cabeça-de-chave simples (forte contra fraco), com sorteio dentro de cada
metade. Todas as rodadas são **ida e volta por padrão** (o vetor de 7 posições do país,
`duasVoltasMataMata`, todo `true`); o time mais fraco do confronto (o que veio da metade fraca) manda
a ida e o mais forte manda a volta - a mesma convenção "pior colocado recebe a ida" que a `.ces`
documenta para os estaduais.

**"Novo formato"** (Brasil, quando o país tem 91+ clubes e a opção está ligada - portanto o formato
efetivo de uma carreira nova do Brasil, já que a opção vem ligada e o Brasil tem muito mais de 91
clubes no dataset completo): uma Copa do Brasil em moldes reais.

1. **Doze cabeças de chave fixas** entram só numa rodada posterior: primeiro os clubes brasileiros
   classificados para a fase de grupos e para o mata-mata da Continental 1 América do Sul da
   temporada (se a opção "joga competições internacionais de clubes" estiver ligada), depois - se
   ainda houver vagas nos doze - os dois representantes do país nos playoffs de campeão
   estadual/regional (quando os regionais estão ativos), completando o restante com os melhores
   colocados gerais que ainda não entraram. A lista final de cabeças de chave é **anunciada** (via a
   mesma central de notícias do jogo).
2. Os clubes restantes (excluindo os doze) são divididos em **8 potes de 10**, sorteados
   aleatoriamente dentro de cada pote, e combinados num chaveamento de **80 times** (primeira rodada,
   jogo único, sem ida e volta) numa ordem fixa de cruzamento de potes. **Se a divisão de potes não
   fechar exatamente 8 grupos de 10** (nem todos os clubes cabem), o motor **cai de volta para o
   formato padrão inteiro** (chave de potência de 2 com os cabeças de chave incluídos normalmente) -
   isto é, o novo formato só roda quando o país tem clubes suficientes para preencher os 80 + 12.
   Esta primeira rodada de 80 **nunca vai a pênaltis numa igualdade**: um código dedicado força sempre
   o **segundo time listado** do confronto a avançar quando o placar (que aqui é sempre jogo único,
   então o próprio placar da partida) empata - o mesmo efeito colateral documentado para os estaduais
   com pênaltis desligados, mas aqui **hardcoded**, não controlado por nenhuma opção. Ver item 82 de
   OPEN-QUESTIONS.
3. Os **40 vencedores** dessa primeira rodada entram numa segunda rodada, agora ida e volta, ainda sem
   os cabeças de chave.
4. Os **20 vencedores** da segunda rodada mais os **12 cabeças de chave** formam a rodada de 32,
   ida e volta - é aqui que os grandes clubes entram no torneio.
5. Dali em diante (32 -> 16 -> 8 -> 4 -> 2), cada rodada é ida e volta até a final.

Em ambos os formatos, o critério de desempate de um confronto de ida e volta é: número de jogos
vencidos por perna, depois saldo agregado dos dois jogos (sem regra de gol fora de casa - ver 1.14),
depois disputa de pênaltis (mecânica IAxIA da 3.10: cada lado sorteia um número de 2 a 8, quem
sorteia igual ou maior vence por N a N-1). A Copa Nacional **sempre** agenda a disputa de pênaltis
numa igualdade (não tem o equivalente do campo `desempate` do estadual/liga para desligá-la); a única
exceção é o defeito hardcoded da primeira rodada do novo formato, item 2 acima.

**Premiação e reputação:** ver 6.3 e 5.5 (não repetidos aqui). **O que acontece com menos clubes do
que o formato precisa:** um país com liga ativa e menos de 8 clubes simplesmente não tem Copa Nacional
naquela temporada (o motor só constrói a chave com `>= 8`); entre 8 e 90 (ou 91+ com a opção de novo
formato desligada), sempre o formato padrão.

### 1.14 Competições continentais CONFIRMADO

Cada um dos 6 continentes da 4.4.1 (Europa, América do Sul, África, Ásia, Concacaf, Oceania) tem sua
**Continental 1** (tipo 4 da taxonomia, um objeto por continente). Só Europa e América do Sul têm
também uma **Continental 2** (tipo 6); os outros quatro continentes não têm segunda competição
continental - o que já era visível na 6.3, onde só Europa e América do Sul aparecem com duas tabelas
de premiação de mata-mata. Europa tem ainda um terceiro objeto continental dedicado à Conference
League (tipo 12, ver 1.15) - as duas informações batem com a tabela de prêmios da 5.5, que só lista
"continental 1", "continental 2" e não distingue Conference League com prêmio próprio (ela não
aparece na 5.5; ver item 83 de OPEN-QUESTIONS).

**Qualificação.** Não vem de uma fórmula sobre o nível do país calculada em tempo de execução: cada
gerente continental carrega **duas listas de país fixas, escritas à mão** (uma "principal", que
alimenta a fase de grupos, uma "secundária", que alimenta a fase preliminar), aproximando o ranking
de força real das respectivas confederações na época do jogo. Um país pode aparecer várias vezes
numa lista, e cada aparição é uma vaga - é assim que "país mais forte, mais vagas" é expresso: por
repetição literal de entradas, não por uma fórmula sobre o nível. Uma rotina auxiliar comum a todos os
gerentes resolve cada entrada de país para um clube real, lendo a tabela final da liga nacional
daquele país na temporada anterior (a segunda divisão entra como fallback se a vaga pedir uma posição
mais funda do que a primeira divisão tem). Nas competições europeias e sul-americanas, uma entrada
pode ainda vir marcada para preferir o **campeão da copa nacional** daquele país em vez da posição de
liga, caindo de volta para a liga se o campeão de copa já tiver sido usado; nas quatro competições de
Continental 1 dos demais continentes essa marca não é usada - toda vaga sai só da liga. Além das duas
listas, cada gerente guarda um campo próprio com o **campeão vigente da própria competição**, que
entra com vaga garantida sem consumir a lista de países (na primeira temporada de uma carreira, sem
campeão vigente ainda, a vaga cai de volta num país fixo de preenchimento); Europa e América do Sul
têm ainda **promoção cruzada** entre suas duas competições: o campeão vigente da Continental 2
ganha vaga direta na Continental 1 do mesmo continente (a partir da 2a temporada), e a Conference
League tem o mesmo privilégio de entrada na Continental 2 Europa. Confirmado em detalhe, linha a
linha, para as oito competições:

| Competição | Lista principal (fase de grupos) | Lista secundária (preliminar) | Observações |
|---|---|---|---|
| Continental 1 Europa | 24 entradas: Inglaterra/Espanha/Itália com 4 vagas cada, Alemanha 3, França/Portugal 2 cada, mais 5 países com 1 (alvo 26 com campeão vigente + campeão vigente da Continental 2) | 52 entradas, uma por país | fase preliminar de 5 rodadas (times mais fracos), eliminados de duas rodadas caem para a Continental 2 Europa |
| Continental 2 Europa | 12 entradas (algumas com preferência por campeão de copa nacional), mais vaga garantida do campeão vigente da Conference League | 9 entradas, uma por país | recebe eliminados da Continental 1 Europa (2 rodadas de preliminar e o 3o colocado de cada grupo); playoff cruza 2os colocados de grupo próprios com 3os colocados de grupo da Continental 1 |
| Continental 1 América do Sul (Libertadores) | 10 entradas: Brasil e Argentina 5 vagas cada (com preferência por campeão de copa), os outros 8 países da confederação 2 cada (alvo 28 com campeão + vice-campeão vigentes) | 19 entradas distribuídas pelos mesmos 10 países | fase preliminar em 3 mata-matas sucessivos; opção de "grupos reais" só na 1a temporada |
| Continental 2 América do Sul (Sul-Americana) | Brasil 6 vagas, Argentina 6 vagas (alvo 12) | 8 entradas de 4 vagas cada, uma por país (alvo 32) | recebe os que **não** entraram na Libertadores, mais os 3os colocados de grupo da Libertadores; só o campeão de cada grupo avança (não os 2 primeiros) |
| Continental 1 África | 23 entradas: Tunísia/Argélia/Egito/Marrocos com 2-3 vagas, África do Sul 2, mais dezesseis países com 1 (alvo 32 + campeão vigente) | não existe fase preliminar - entrada direta nos grupos | nenhuma vaga usa preferência por copa nacional |
| Continental 1 Ásia | 23 entradas: China, Arábia Saudita, Japão, Coreia do Sul, Catar, Irã e Emirados com 2-3 vagas cada, mais catorze países com 1 (alvo 40 + campeão vigente) | não existe fase preliminar | 10 grupos de 4; só o campeão de grupo classifica direto, mais uma seleção de "melhores" para completar o mata-mata (critério exato não fechado, ver item 84) |
| Continental 1 Concacaf | 15 entradas: México 4, EUA 3, Costa Rica/Guatemala/Panamá 2 cada, mais dez países com 1 (alvo 24) | não existe fase preliminar | 8 grupos de **3** (único formato de grupo de 3 encontrado); oitavas 1o x 8o, 2o x 7o, 3o x 6o, 4o x 5o |
| Continental 1 Oceania | 7 entradas: Nova Zelândia 2, mais seis países com 1 (alvo exato 8, **sem** vaga extra de campeão vigente - o único dos oito gerentes que nunca lê esse campo) | não existe fase preliminar | 2 grupos de 4; semifinal cruza 1o de um grupo com 2o do outro |

A lista de países da Continental 1 Europa é ajustada uma vez, numa temporada fixa do início da
carreira (a 3a), trocando a vaga de 5 entradas por outro país - o equivalente a uma
re-federação/mudança de coeficiente pontual, não uma atualização contínua por desempenho.

**Sorteio dos grupos.** Europa e América do Sul distribuem os clubes em **potes por força, com
sorteio (embaralhamento) dentro de cada pote** - um sorteio de verdade, não uma tabela fixa. África,
Ásia, Concacaf e Oceania usam, em vez disso, uma **tabela de índices fixa** para formar os grupos - um
"sorteio" determinístico, sempre o mesmo emparelhamento dado o mesmo conjunto ordenado de entrada, sem
consumir nenhum sorteio de verdade.

**Formato comum:** uma fase preliminar de eliminatórias (ida e volta, só na Continental 1/2 Europa e
na Continental 1 América do Sul - as outras cinco não têm fase preliminar, entrada direta nos grupos),
afunilando para uma fase de grupos e desta para um mata-mata ida e volta. **A regra do gol fora de
casa nunca é usada por nenhuma das oito competições continentais nem pela Recopa** - todo confronto de
ida e volta lido nesta varredura passa a marcação de gol fora de casa como desligada, mesmo nas duas
competições sul-americanas: existe, no motor de fase genérico compartilhado, uma lógica que calcula
"gol fora de casa ligado" dinamicamente quando o tipo é Continental 1/2 e o continente é América do
Sul, mas nenhuma das classes de Libertadores ou Sul-Americana passa por esse caminho genérico - as
duas constroem seu próprio mata-mata com a marcação sempre desligada, hardcoded, tornando aquele
trecho do motor genérico código morto para estas competições (pode ser alcançável por algum outro
chamador fora do escopo desta varredura, mas não pelas oito continentais nem pela Recopa). **Correção**
em relação a uma leitura preliminar anterior desta mesma varredura, que havia concluído o oposto por
ter parado na existência do sinalizador dinâmico sem checar se as classes de Libertadores/Sul-Americana
de fato o alcançavam - checagem linha a linha de cada chamada de construção de mata-mata nas quatro
classes européias e sul-americanas confirma o hardcoded `false` em todas. Fora o gol fora de casa, a
resolução de um confronto de ida e volta segue o mesmo procedimento da 1.13 (jogos vencidos por perna,
depois saldo agregado, depois pênaltis pela mecânica IAxIA da 3.10 - nunca o defeito de "avança o
segundo listado", que só existe para Estadual e para o código específico da Copa Nacional da 1.13).

**Recopa** (tipo 8): não é uma competição por continente, e sim **uma final única** entre o campeão da
Continental 1 e o campeão da Continental 2 do **mesmo** continente - por isso só existe onde as duas
competições existem (Europa e América do Sul; nenhuma Recopa existe fora desse par, CONFIRMADO pela
leitura direta dos dois únicos objetos de Recopa do motor). Confronto de **ida e volta**: o campeão da
Continental 2 (o lado "menor") manda a ida, o campeão da Continental 1 manda a volta - a mesma
convenção "pior colocado recebe a ida" usada em toda a Copa Nacional e nos mata-matas continentais.
Sem regra de gol fora de casa; uma igualdade agregada vai à disputa de pênaltis normal (não é tipo 3
Estadual, então nenhum desvio de pênaltis se aplica). Se um dos dois campeões continentais não existir
naquela temporada (por exemplo, se as duas competições de um continente ainda não fecharam), a Recopa
daquele continente não é montada. CONFIRMADO por leitura direta dos dois objetos.

**Menos clubes do que o formato precisa:** as competições sul-americanas e africana/asiática/oceânica
não têm um caminho de fallback gracioso - se o total resolvido não bater exatamente com o alvo da
lista principal e da secundária, a rotina de montagem simplesmente não constrói a competição naquela
temporada (um aviso de diagnóstico é emitido e a montagem para ali). CONFIRMADO para essas seis; para
a Continental 1 Concacaf não foi encontrado o mesmo aviso de diagnóstico, e não ficou claro se existe
algum fallback silencioso alternativo ali - ver item 84.

### 1.15 Mundial, Supercopas, Regionais, Conference League e amistosos de clubes CONFIRMADO

**Mundial de clubes** (tipo 5): **exatamente 6 participantes**, os campeões da Continental 1 de cada
um dos 6 continentes - Europa, América do Sul, África, Ásia, Concacaf e Oceania - da temporada que
acabou de fechar (não da anterior: o motor lê o campeão vigente de cada gerente continental no
momento em que monta o Mundial). Formato de Copa do Mundo de Clubes clássica: os campeões de
**África, Ásia, Concacaf e Oceania** disputam primeiro um mata-mata de **quartas de final** entre si
(jogo único, sorteio de chaveamento entre 3 padrões fixos possíveis); os dois vencedores avançam para
uma **semifinal** de 4, entrando aí de encontro com os campeões da **Europa e América do Sul**, que
começam a competição direto nesta fase (jogo único: "Campeão Libertadores" e "Campeão L. Campeões"
contra os dois vencedores das quartas, rótulos lidos diretamente do motor); os dois vencedores da
semifinal disputam a **final**, e os dois perdedores da semifinal disputam uma **disputa de terceiro
lugar** (a flag de "tem disputa de 3o lugar" está ligada só nesta competição, entre as seis desta
seção). Nenhuma partida é ida e volta e não há regra de gol fora de casa; uma igualdade vai à disputa
de pênaltis normal (mecânica da 3.10), sem nenhum desvio especial como o da 1.13. **Caso especial:**
se o campeão da Continental 1 América do Sul for um clube do **México** (país que pode disputar essa
competição sul-americana no motor), ele é substituído no Mundial pelo melhor colocado não mexicano
ainda encontrado na própria chave sul-americana, procurado da rodada final para trás - o México nunca
representa a América do Sul no Mundial. Se faltar o campeão de qualquer um dos 6 continentes, o
Mundial simplesmente **não é montado** naquela temporada. **Curiosidade sem efeito de implementação:**
o sorteio do cruzamento das quartas de final usa o gerador de números aleatórios puro da linguagem, não
o gerador do próprio jogo - mas a seção 0 já registra que **nenhum** sorteio do original é
reproduzível, então isso não muda nada para a reimplementação (que semeia tudo). Premiação: reputação
campeão +10/vice +5 (bate com a 5.5), dinheiro 40M campeão / 1M vice (bate com a 6.3). CONFIRMADO por
leitura direta da classe do motor.

**Supercopa** (tipo 11, "Supercopa Nacional"): uma competição **por país** (uma instância criada para
todo país com liga nacional carregada, não é opt-in), jogo único, nome exibido "Supercopa " + nome do
país. Os dois participantes são o **campeão da liga nacional** e o **campeão da Copa Nacional**, ambos
**da temporada anterior** (não da que acabou de fechar); **se o campeão da liga também tiver sido
campeão da Copa Nacional**, o segundo participante vira o **vice-campeão da Copa Nacional** em vez do
campeão (para nunca colocar um clube contra ele mesmo). Sem ida e volta; uma igualdade vai à disputa
de pênaltis normal, sem gol fora de casa. Premiação: reputação campeão +2/vice 0 (preenche a lacuna da
5.5 - ver item 83), dinheiro fixo 1M ao vencedor (6.3). Recalculada todo início de temporada, por
país; se o país não tem Copa Nacional ou liga nacional na temporada anterior (nenhum dos dois times
pôde ser determinado), a Supercopa daquele país não é montada naquele ano. CONFIRMADO por leitura
direta da classe do motor.

**Regionais** (tipo 10): **exatamente 4 instâncias fixas, só para o Brasil**, cada uma amarrada a um
grupo fixo de estados brasileiros (lido diretamente do código, por índice de estado da tabela
"Estados brasileiros" da FORMAT-SPEC) e a um nome de torneio real:

| Instância | Estados | Nome |
|---|---|---|
| 0 | RJ, SP | Rio-São Paulo |
| 1 | MG, PR, RS, SC | Sul-Minas |
| 2 | AL, BA, CE, MA, PB, PE, PI, RN, SE | Copa do Nordeste |
| 3 | AC, AM, AP, DF, ES, GO, MS, MT, PA, RO, RR, TO | Copa Verde |

`Options` carrega uma flag geral `jogaRegionais` e 4 flags independentes `jogaRegionaisTodos[0..3]`
(uma por instância acima), todas ligadas por padrão - as duas precisam estar ligadas para a instância
rodar. Montado no **início de temporada** (mesmo passo que oferece o torneio amistoso, abaixo). Cada
instância toma **todos os clubes brasileiros** (qualquer divisão, ou sem divisão) cujo estado está no
seu grupo; se houver **16 ou mais**, os 16 melhores por nível formam **4 grupos de 4** (sorteio por um
padrão de chaveamento fixo), turno e returno completo dentro e entre grupos, com os **2 primeiros de
cada grupo** (8 times) indo a um mata-mata de quartas -> semifinal -> final, **as três rodadas em ida
e volta** (inclusive a final). **Com menos de 16 clubes elegíveis no grupo de estados, aquela
instância simplesmente não é criada** naquela temporada. Premiação: reputação campeão +2/vice +1
(bate com a 5.5), dinheiro por rodada de mata-mata (6.3, `{100k, 100k, 400k}`). CONFIRMADO por leitura
direta da classe do motor.

**Conference League** (tipo 12, só Europa): recebe clubes de duas fontes - uma lista de acesso por
país fixa (dezenas de países europeus, cada entrada podendo exigir passar por rodadas preliminares
extras; os países mais fortes entram com menos rodadas) e, **explicitamente**, os clubes eliminados
nas fases classificatórias da própria temporada da Continental 1 Europa e da Continental 2 Europa - a
mesma "queda" real de Champions/Europa League para a Conference League. Depois de um funil de várias
rodadas preliminares (o total exato de rodadas e o tamanho de cada uma não foi fechado com a mesma
profundidade das demais competições - ver item 84), a fase de grupos é de **32 times em 8 grupos de
4** (4 potes de 8 por força), turno e returno; os 2 primeiros de cada grupo (16 times) vão a um
mata-mata de **quatro rodadas** (oitavas, quartas, semifinal, final), **todas em ida e volta -
inclusive a final** (diferente da Conference League real, cuja final é jogo único em sede neutra; aqui
é ida e volta como qualquer outra rodada). Sem regra de gol fora de casa; disputa de pênaltis normal
numa igualdade agregada. Sem disputa de terceiro lugar. Premiação: reputação campeão +10/vice +8,
ambos os perdedores de semifinal +4 (preenche a lacuna da 5.5 - ver item 83); nenhuma linha própria de
dinheiro foi encontrada na tabela de prêmio fixo por posição usada pelas demais (Mundial, Recopa,
Finalíssima, Liga Nações) - consistente com a 6.3 também não ter uma linha para ela, mas não
descarta um mecanismo de pagamento por rodada de mata-mata ainda não localizado, do mesmo tipo que
paga a Copa Nacional e as competições continentais. Montada todo início de temporada, junto com o
resto do calendário continental europeu. CONFIRMADO por leitura direta da classe do motor, exceto o
detalhe de premiação em dinheiro (INFERIDO por ausência de linha encontrada).

**Torneio Amistoso** (tipo 15): **sempre iniciado por ação humana**, nunca pela IA. No início de
temporada, o jogo pergunta diretamente ("Deseja criar um torneio Amistoso de início de temporada?");
se aceito, uma tela permite escolher os clubes participantes um a um (sem restrição de país ou
divisão), o número de times (padrão 8, ajustável), 1 ou 2 grupos (ou nenhum, virando pontos corridos
puro sem mata-mata), e se o sorteio deve embaralhar a ordem. A fase de grupos é sempre **turno único**
(não turno e returno), e quando existe mata-mata **todas as rodadas são jogo único** (nunca ida e
volta - diferente dos Regionais e da Conference League). Sem prêmio em dinheiro nem reputação.
CONFIRMADO que o gatilho é a pergunta de início de temporada ao jogador humano; não foi confirmado se
a tela impede a criação com poucos times.

**Amistoso** (tipo 0): não é uma competição com chave - é o mecanismo de **convite de amistoso** entre
dois clubes, uma partida avulsa por vez, inserida direto no calendário sem passar pelo motor de
mata-mata/pênaltis usado por todo o resto desta seção. **Sempre iniciado por ação humana**: o clube do
jogador convida um clube de IA (ou aceita/recusa um convite recebido), nunca a IA convidando outra IA
por conta própria. A aceitação depende da diferença de reputação entre convidante e convidado (um
clube de reputação 5 recusa de cara um convite de reputação < 3) e, quando aceita com diferença menor,
consome uma **taxa de amistoso** em dinheiro que escala com a diferença de reputação e com o convite
ser dentro do mesmo país ou entre países diferentes - um mecanismo de negociação por partida, não um
prêmio de competição. Sem prêmio de reputação. CONFIRMADO por leitura direta da classe do motor.

**Amistosos e a v0.3.** Como Torneio Amistoso e Amistoso são sempre iniciados por ação humana, e a
v0.3 é uma temporada só de IA (ver o design da v0.3), nenhuma das duas competições precisa ser
simulada para fechar uma temporada corretamente - a reimplementação pode adiar as duas por completo
sem perder nada observável nas competições que pagam prêmio ou reputação.

---

### 1.16 Competições de seleções CONFIRMADO

Dos 16 tipos da taxonomia da 1.1, quatro cobrem seleção: Seleções (Copa do Mundo e as copas
continentais), Eliminatórias (classificatórias, uma por confederação), Finalíssima e Liga Nações.
No motor original os quatro formam um único agrupamento de tela ("competições de seleção"), ao lado
de Amistoso e Torneio Amistoso quando o competidor é uma seleção em vez de um clube - amistoso de
seleção **não tem tipo próprio**: usa o mesmo Amistoso (tipo 0) ou Torneio Amistoso (tipo 15) do
clube, distinguido só pelo tipo do competidor, e por isso fica fora do ciclo fixo por temporada
descrito abaixo (que cobre exatamente as outras oito competições: Copa do Mundo, as seis
Eliminatórias, as seis copas continentais, a Finalíssima e as duas Ligas de Nações).

**O ciclo por temporada.** Cada uma dessas competições é redesenhada (sorteio de grupos, nova lista
de participantes) em temporadas fixas, contadas a partir do início de uma carreira nova:

| Competição | Confederação/escopo | 1a temporada | Periodicidade |
|---|---|---|---|
| Copa do Mundo | Mundial | 1 | a cada 4 temporadas |
| Eliminatórias (as 6) | uma por confederação | 4 | a cada 4 temporadas, sempre na temporada anterior à Copa do Mundo |
| Eurocopa | Europa | 3 | a cada 4 temporadas |
| Eliminatórias da Eurocopa | Europa | 2 | a cada 4 temporadas, sempre na temporada anterior à Eurocopa |
| Copa América | América do Sul | 3 | a cada 4 temporadas |
| Copa Africana | África | 2 | a cada 4 temporadas |
| Copa Asiática | Ásia | 2 | a cada 4 temporadas |
| Copa Ouro | Concacaf | 2 | a cada 2 temporadas |
| Copa OFC | Oceania | 4 | a cada 4 temporadas |
| Finalíssima | Europa x América do Sul | 4 | a cada 4 temporadas |
| Liga das Nações Europa | Europa | 2 | a cada 2 temporadas |
| Liga das Nações Concacaf | Concacaf | 1 | a cada 2 temporadas |

Não existe Liga das Nações para América do Sul, África, Ásia nem Oceania - só as duas linhas acima.
A Copa América **não tem eliminatórias próprias**: os dez países da CONMEBOL entram direto a cada
edição. As outras quatro copas continentais (Africana, Asiática, Ouro, OFC) também recebem os
países da própria confederação diretamente, sem uma fase eliminatória continental dedicada - a
única eliminatória continental fechada nesta varredura, além das seis de Copa do Mundo, é a da
Eurocopa (abaixo). Times sem seleção jogável (país sem clubes suficientes no mundo gerado) são
excluídos do sorteio de qualquer uma destas competições.

**Copa do Mundo.** Sorteio e formato mudam conforme a edição:

- **Temporada 1** (antes de qualquer Eliminatória ter rodado): 32 vagas preenchidas por sorteio de
  cota por confederação (aproximadamente Europa 13, América do Sul 4, África 5, Ásia 4, Concacaf 3,
  e uma vaga extra sorteada 70%/30% entre Concacaf e Oceania), mais o país-sede da edição, que entra
  sem consumir cota de confederação. Existe uma opção de configuração ("usar grupos reais da Copa")
  que troca esse sorteio pelos 32 países e potes da Copa do Mundo real de 2018, sede Rússia.
- **Temporadas seguintes** (5, 9, 13...): 48 vagas, todas preenchidas pelos classificados das seis
  Eliminatórias daquele ciclo. Os classificados são ordenados por força e divididos em 3 potes de 16
  (melhores, médios, piores), cada pote embaralhado e distribuído 1 por grupo, formando 16 grupos de
  3 times, turno único dentro do grupo. Os dois primeiros de cada grupo (32 times) avançam a um
  mata-mata de eliminação simples, com a ordem do chaveamento calculada para adiar o reencontro de
  times do mesmo grupo.
- **Sede.** Gira por uma lista fixa de 10 países-sede (Catar, EUA/México, Argentina, Espanha,
  Itália, Inglaterra, China, Alemanha, Brasil, Rússia, nesta ordem, repetindo a cada 10 edições = 40
  temporadas); o nome das cidades-sede é só cosmético (3.11).

**Eliminatórias (classificatórias para a Copa do Mundo), uma por confederação:**

| Confederação | Times | Formato | Vagas diretas | Repescagem/desempate |
|---|---|---|---|---|
| Europa | 55 | 10 grupos (5 de 5 e 5 de 6 times), 2 turnos, potes por força | 10 (1os colocados) | 2os colocados + 2 melhores 3os (12 times) em mata-mata de 2 jogos |
| America do Sul | 10 (ou 9, se faltar selecao jogavel) | grupo unico, todos contra todos, 2 turnos ("hexagonal" expandido) | 6 primeiros | 7o colocado vai a repescagem intercontinental |
| Africa | 40 | 8 grupos de 5, 2 turnos | 8 (1os colocados) | nao fechada nesta varredura - ver item 91 de OPEN-QUESTIONS |
| Asia | 40, depois 12, depois 6 | 3 rodadas: 8 grupos de 5 (2 turnos) -> 2 grupos de 6 (2 turnos) -> grupo final de 6 (1 turno) | 2 (1o e 2o do grupo final) | colocados seguintes do grupo final vao a repescagem intercontinental |
| Concacaf | 30, depois 8 | 6 grupos de 5 (1 turno) -> grupo final de 8, "octogonal" (1 turno) | nao fechada com precisao - ver item 91 | resto do octogonal vai a repescagem intercontinental |
| Oceania | 4, depois 8 | grupo de 4 (2 turnos) juntando os times restantes -> 2 grupos de 4 (2 turnos) | nenhuma vaga direta | o melhor colocado vai a repescagem intercontinental |

**Repescagem intercontinental.** Uma competicao a parte ("Eliminatorias - Repescagem"), com 6 times
reunidos das sobras de America do Sul, Asia, Concacaf, Oceania e outra confederacao (o codigo le um
time de cada uma dessas cinco fontes mais um sexto ja pre-classificado; qual confederacao fornece
esse sexto time nao foi identificada com confianca nesta leitura - ver item 90 de OPEN-QUESTIONS);
nao chega a Africa nesta leitura. O formato exato do confronto entre os 6 (rodada unica entre todos,
ou mata-mata) nao foi fechado com confianca total - ver item 92 de OPEN-QUESTIONS. Os vencedores
completam as 48 vagas da Copa do Mundo.

**Eliminatórias da Eurocopa.** Mesmo pool de 55 seleções europeias que a Eliminatória da Copa, mas
sorteio e fase separados: 10 grupos (mesma divisão 5/5/5/5/5/6), 2 turnos. Primeiro **e** segundo
colocados de cada grupo (20 times) classificam direto; os 8 melhores terceiros colocados disputam
um mata-mata de 2 jogos, e os vencedores completam as 24 vagas.

**As seis copas continentais de seleção (Copa do Mundo à parte):**

| Competição | Times | Formato |
|---|---|---|
| Eurocopa | 24 | 6 grupos de 4 (turno único), oitavas de final com os 2 primeiros de cada grupo mais os 4 melhores terceiros |
| Copa América | 10 | 2 grupos de 5 (turno único), 4 melhores de cada grupo às quartas de final |
| Copa Africana | 24 | 6 grupos de 4 (turno único), oitavas com 2 primeiros de cada grupo mais os 4 melhores terceiros |
| Copa Asiática | 24 | 6 grupos de 4 (turno único), oitavas com 2 primeiros de cada grupo mais os 4 melhores terceiros |
| Copa Ouro | 16 | 4 grupos de 4 (turno único), quartas de final com os 2 primeiros de cada grupo |
| Copa OFC | 8 | 2 grupos de 4 (turno único), semifinal com os 2 primeiros de cada grupo |

Fase de mata-mata de todas as seis é eliminação simples, jogo único, sem gol fora de casa (não há
esse conceito no motor - ver 3.15 para a lista de mecânicas ausentes). O prêmio de reputação por
título destas seis competições, se existe algum, não está no catálogo hoje: a tabela de prêmios da
5.5 lista Mundial, continental 1/2, Recopa, regional e Finalíssima, mas nenhuma delas é a copa
continental de seleção nem a Copa do Mundo em si - ver a subseção nova em 4.12 sobre isso.

**Liga das Nações.** Só duas existem, Europa e Concacaf; nenhuma outra confederação tem uma. A
europeia tem 4 divisões (A, B, C, D) com composição de países fixa por divisão (16, 16, 16 e 7
times), cada divisão em 4 grupos (a divisão D em 2 grupos, 4 e 3 times), 2 turnos, sem mata-mata
dentro da própria divisão. Os 4 primeiros-colocados de grupo da divisão A avançam para uma Final a
4 (semifinal e final, mata-mata simples); os últimos colocados de grupo da divisão C disputam um
playoff de rebaixamento para a divisão D (jogo marcado com bandeira de mando definida). Promoção e
rebaixamento entre as demais divisões não foram fechados nesta varredura - ver item 93 de
OPEN-QUESTIONS. A versão Concacaf usa o mesmo motor de fases (grupos, 2 turnos) mas a composição de
divisões e o desenho da fase final não foram conferidos com o mesmo detalhe - ver item 93.

**Finalíssima.** Um jogo único entre o campeão da Eurocopa mais recente e o campeão da Copa América
mais recente, disputado na temporada seguinte à Copa América (temporada 4, 8, 12...). A cidade-sede
é sorteada de duas listas fixas (uma de capitais/cidades europeias, outra sul-americanas),
alternando entre as duas listas a cada edição e nunca sorteando uma cidade do país de um dos dois
finalistas - mas ver 1.16-C abaixo: essa alternância não faz o jogo neutro para o motor de partida.

**C. Regra de mando de campo.** A 3.11 já registra que campo neutro (e os bônus de mando
associados: +0,3 nos dois duelos, +0,1/+0,2 nos pesos de não-gol, e a divisão 44%/56% de
cartões/lesões) é ligado **se e somente se** o tipo da competição é Mundial (Copa do Mundo de
clubes) ou Seleções (Copa do Mundo e as seis copas continentais de seleção) - lido direto da
checagem de tipo no motor de partida. **Eliminatórias, Liga das Nações e Finalíssima não entram
nessa checagem** e, portanto, jogam com mando de campo normal: cada confronto tem um mandante e um
visitante de verdade, com os bônus de posse/chance e o viés de cartão/lesão da 3.11 aplicados. Isso
vale inclusive para a Finalíssima: apesar de a cidade-sede ser sorteada para ser neutra em relação
aos dois países (nenhum dos dois hospeda em seu próprio território), o motor ainda marca um dos dois
lados como mandante e aplica os bônus de mando a ele - a neutralidade da Finalíssima é só de
cidade-sede, não de simulação. Eliminatórias e Liga das Nações, por serem jogos de ida e volta na
maior parte, alternam mando entre as pernas normalmente.

---

## 2. Formato de save (fecha a última lacuna de formato) CONFIRMADO

Salvar um jogo `<nome>` grava **três arquivos** em `sav/`:

| Arquivo | Formato | Conteúdo |
|---|---|---|
| `<nome>.info` | Serialização Java padrão | Um objeto de metadados com 4 campos (nome / técnico / temporada / time). Cabeçalho leve para a tela de carregar sem ler o save inteiro. |
| `<nome>.s22` | **Kryo 4.0.2** | O save real: exatamente **dois** objetos gravados em sequência - o estado global do jogo e um objeto auxiliar. |
| `<nome>.sbck` | Kryo 4.0.2 | **Cópia de backup** do `.s22`, gravada após todo save bem-sucedido. |

**Boa notícia para ferramentas:** o gravador usa **registro desabilitado**, então o Kryo escreve o
**nome completo da classe inline** antes de cada grafo - o `.s22` é **auto-descritivo**, como a
serialização Java. Um leitor da comunidade consegue percorrer o arquivo sem as classes do jogo.
Ressalva: o Kryo **não** grava nomes de campos (grava posicionalmente, na ordem de declaração da
classe), então o leitor ainda precisa do layout de campos de cada classe.

**Carregamento:** lê `.s22`; em **qualquer exceção**, tenta automaticamente o `.sbck`.
Uma reimplementação que importe carreiras existentes deve honrar o mesmo fallback.

**Importante:** tudo que uma carreira acumula - dinheiro, contratos, histórico de transferências,
tabelas, calendário, evolução de jogadores - vive no `.s22`, **não** nos `.ban`.
Os `.ban` são apenas o banco de dados inicial.

---

# 3. SIMULAÇÃO DE PARTIDA

> Esta é a parte central. Toda a matemática abaixo é CONFIRMADA salvo indicação contrária.

**Descoberta estrutural importante:** a classe de "partida" **não** contém o motor de jogo. Ela é um
contêiner (times, escalações, placar, lista de eventos) + um relógio de disciplina/substituições.
Todo o futebol (posse, chances, chutes, gols) acontece numa **classe de motor separada**, com um
método "simular um tick".

## 3.1 Fluxo de uma partida

Ao processar uma rodada: (1) escalação automática para todo time não-humano sem escalação pronta;
(2) para cada partida: define local/público e simula; (3) pós-rodada: grava resultado na tabela,
cumpre suspensões, calcula receita de bilheteria, aplica notas/cartões e **recupera energia**.

Simulação de uma partida:
- Sorteia `TB em {0,1}` = quem começa com a bola (0 = mandante).
- **Se algum dos times é humano, a simulação automática é pulada** - o visualizador ao vivo conduz tick a tick.
- Acréscimos: `extra1 = rand(0..2)`, `extra2 = rand(1..5)`, sorteados uma vez.
- **1º tempo**: minutos `0..44+extra1`. **2º tempo**: minutos `0..44+extra2`. Entre eles, janela de
  substituição da IA.
- Em cada minuto: primeiro a rolagem de disciplina/lesão/substituição, depois **um tick do motor**.
- **Prorrogação nunca é simulada.** Empate em mata-mata vai direto para disputa de pênaltis abstrata (seção 3.10).

Total: **~91 a 97 ticks**, um tick ~ um minuto. (O modo ao vivo usa contagem levemente diferente:
44-48 ticks no 1º tempo e 50 no 2º - logo, desgaste de energia levemente diferente.)

## 3.2 Modelo de escalação: a grade de 25 slots

Cada jogador em campo carrega um **slot 1..25** (posição na grade do campo); banco = 26..36.

| Slot(s) | Significado |
|---|---|
| 1 | Goleiro |
| 2, 9 | Laterais (direito / esquerdo) |
| 3-8 | Zagueiros (6 células) |
| 10, 17 | Alas - **exigem posição = Lateral** |
| 11, 12, 13 | Volantes |
| 14, 15, 16 | Meias ofensivos |
| 18, 25 | Pontas |
| 19-24 | Atacantes centrais |

O **sub-papel** do jogador é **derivado das duas características**, não escolhido:
- GOL, ZAG -> 0.
- LAT -> 1 (ofensivo) se tiver Velocidade, Cruzamento, Drible, Finalização, Passe ou Armação; 0 (defensivo) se Desarme ou Marcação.
- MEI -> 1 (armador) se Passe/Finalização/Drible/Armação; 0 (volante) se Desarme/Marcação; padrão 1.
- ATA -> 0 se Desarme/Marcação; **2 (ponta)** se Drible/Velocidade/Cruzamento; senão 1 (centroavante).

### Escalação automática da IA
- Pool = jogadores não lesionados e não suspensos, ordenados por **força desc, energia desc**
  (energia é só critério de desempate - **a IA não poupa jogadores cansados na escalação**).
- Formação sorteada `rand(1..100)`: 1-2->F1, 3-4->F2, 5-7->F3, **8-38->F4 (31%)**, 39-49->F5, 50-60->F6,
  61-65->F7, 66-72->F8, **73-90->F9 (18%)**, 91-92->F10, 93-100->F11.
- 11 formações definidas como listas de slots (F4 = 4-4-2, F7/F8 = 4-3-3, F9 = 3-5-2, F10 = 3-4-3, etc.).
- Cada slot é preenchido pelo primeiro jogador compatível `(posição, lado, sub-papel)`, relaxando em
  3 passes (exato -> ignora lado -> ignora lado+papel), seguindo cadeias de preferência por posição.
- Banco = 11 jogadores por slots-modelo. **Reservas da IA ficam com slot = -1 até entrarem.**

## 3.3 `B(jogador)` - força efetiva (a função mais importante)

Retorna um número ~0-10. Recalculada a cada uso (sem cache).

- Opção "habilidade individual" **desligada** -> `s = força` (o "F:").
- **Ligada** -> `s` = soma ponderada dos 7 atributos, **com pesos escolhidos pelo SLOT**:

| Slot | Gol | Vel | Téc | Pas | Des | Arm | Fin |
|---|---|---|---|---|---|---|---|
| 1 (GOL) | .60 | .15 | .15 | .10 | - | - | - |
| 3-8 (ZAG) | - | .25 | .10 | .10 | .50 | .05 | - |
| 2, 9 (LAT) | - | .10 | .10 | .30 | .40 | .05 | .05 |
| 11-13 (volante) | - | .15 | .10 | .20 | .40 | .10 | .05 |
| 14-16 (meia of.) | - | .10 | .10 | .25 | .05 | .40 | .10 |
| 10, 17 (ala) | - | .25 | .15 | .25 | .05 | .20 | .10 |
| 19-24 (ata central) | - | .25 | .25 | .05 | - | .05 | .40 |
| 18, 25 (ponta) | - | .25 | .15 | .15 | - | .05 | .40 |

Qualquer outro slot (inclusive -1 e 26-36) -> `s = 0`.

Depois, nesta ordem:
1. **Fora de posição**: `s = round(s x 0.5)` se a posição natural != posição do slot (slot <= 0 conta como fora de posição).
2. `s = max(s, 1)`.
3. **Escala por competição** (multiplica `s`):
   - Seleções: jogador do mesmo país da seleção -> x0.65 se reputação < 3; x0.85 se = 3; x0.95 se = 4.
   - Internacional 1: reputação do clube < 3 -> x0.75; = 3 -> x0.85; senão, país 29 -> x0.90.
   - Mundial: rep < 3 -> x0.55; = 3 -> x0.75; senão, fora do continente 0 -> x0.90
     (continente 0 = Europa; tabela completa de continentes na 4.4.1).
   - Liga nacional: rep < 3 -> x0.85; = 3 -> x0.95.
   - Copa Nacional / Estadual: se mandante tem rep < 3 e visitante >= 3, **todo o visitante** x0.80.
4. **Retorna `s / 10`.**

**Energia NÃO entra em `B()`.** Nem moral, nem forma, nem capitão.

## 3.4 Agregados de linha CONFIRMADO

Os divisores desta tabela são **constantes fixas** (5.0, 3.0, 5.0), nunca a contagem de jogadores
encontrados - dúvida do item 30 de OPEN-QUESTIONS e do experimento E1, fechada por leitura direta
da lógica. O laço soma **no máximo** N jogadores (5/3/5) e divide sempre pela constante; o bônus de
marcação do meio-campo entra na soma **antes** da divisão, como o item 5 já media.

Não existe cache de força de time. A cada tick recalcula-se, **percorrendo a lista de escalados na
ordem da lista e pegando os primeiros N que qualificam** (não os melhores N):

| Agregado | Slots | Pega | Divide por | Caso degenerado |
|---|---|---|---|---|
| **Meio** | 10 <= slot <= 17 | 5 primeiros | **5.0** | < 3 -> `0.01` |
| **Ataque** | **19 <= slot <= 25** | 3 primeiros | **3.0** | 0 -> `0.0` |
| **Defesa** | 2 <= slot <= 9 | 5 primeiros | **5.0** | < 3 -> `0.01` |
| **Goleiro** | slot == 1 | o goleiro | - | nenhum -> `0.1`; fora de posição -> `round(GKx0.2)` |

Consequências enormes para a reimplementação:
- Os divisores são **fixos**: escalar 4 meias em vez de 5 custa 20% da força de meio; 1 atacante dá ataque = `força/3`.
- **O slot 18 fica de fora do agregado de ataque, mas o 25 entra** (faixa 19..25, enquanto pontas são 18 e 25). Quem estiver no slot 18 **não contribui para nada**.
- 4-3-3 é a única formação que preenche o ataque completamente.

## 3.5 O tick do motor

```
tick:
  vencedor = dueloDePosse()
  se vencedor == TB:
      se dueloDeChance() == ataque:
           chutes[TB]++ ; evento = resolverChute()
      senão: 50% "desarme" para o adversário, 50% "passe errado" para TB
  senão: 50% "desarme" para o adversário, 50% "passe errado" para TB
  TB = 1 - TB // a posse SEMPRE alterna
```

`TB` alterna **incondicionalmente** a cada tick - cada time fica "com a bola" em ~metade dos ~92 ticks.
A % de posse exibida vem de um contador separado (vitórias no duelo de posse).

**Primitiva de escolha ponderada:** dados pesos-base `w[]` e multiplicadores `m[]`, calcula
`p[i] = w[i] * m[i]`, sorteia `u ~ U(0, soma p)` e devolve o primeiro índice cuja soma acumulada supere `u`.

**Função de diferença de força:**
```
dif(x, y) = (x - y) / D
D = 8 nas temporadas 1-4
D = 11 a partir da temporada 5 (duelos de posse e de chance)
D = 10 a partir da temporada 5 (duelo de resolução do chute)
```
Ou seja, **a partir da 5ª temporada as diferenças de força são comprimidas em ~27%/20%** - o jogo
fica mais nivelado com o tempo.

## 3.6 Os três duelos - a matemática central

### (a) Duelo de posse
```
a = 1 + dif(MEIO(TB), MEIO(OPP))
b = 1 + dif(MEIO(OPP), MEIO(TB))
se campo não é neutro e TB == mandante: a += 0.3 // MANDO
a = max(a, 0.2) ; b = max(b, 0.2)
pesos-base {55, 45} x {a, b}
```
Forças iguais -> 55% para quem está com a bola; mandante nos seus ticks -> **61,4%**.
O **estilo de jogo** (Equilibrado / Ataque total / Contra-ataque) é **lido e descartado** aqui: **não tem efeito nenhum**.

### (b) Duelo de criação de chance
```
wA = 1 + dif(ATAQUE(TB), DEFESA(OPP)) // ataque vence -> chute
wD = 1 + dif(DEFESA(OPP), ATAQUE(TB)) // defesa vence -> sem chute
se DEFESA == 0: wD = 0.1 ; se ATAQUE == 0: wA = 0.1
se campo não é neutro e TB == mandante: wA += 0.3 // MANDO
se algum time é humano (anti-exploit):
      se o adversário tem 0 zagueiros: wD = 0.10
      se tem 1 zagueiro: wD = 0.05
clamp >= 0.2 ; pesos-base {50, 50} x {wA, wD}
```

### (c) Resolução do chute - onde os gols nascem
```
finalizador = sorteioDeFinalizador(TB)
sh = B(finalizador) (0.1 se nenhum) ; gk = GOLEIRO(OPP)
wDef = 1 + dif10(gk, sh) // peso de "defendido"
wFora = 1 + dif10(DEFESA(OPP), ATAQUE(TB)) // peso de "para fora"

se algum time é humano:
      0 zagueiros no adversário: wDef = round(wDef x 0.2)
      1 zagueiro: wDef = round(wDef x 0.4)

// pesos-base dependem de quantos gols TB JÁ fez nesta partida:
base = {5.5, 35.55, 15.0} // padrão
gols >= 3 -> {4.5, 40.55, 15.0}
gols >= 5 -> {3.0, 40.55, 15.0}
gols >= 6 -> {0.5, 40.55, 15.0}
gols >= 2 e (rep(OPP) - rep(TB)) >= 2 -> {3.0, 40.55, 15.0} // aplicado por último

// ajuste de mando (só se o campo NÃO for neutro):
TB == mandante: wDef += 0.1 ; wFora = wDef + 0.1
TB == visitante: wDef -= 0.1 ; wFora = wDef - 0.1

clamp >= 0.2 ; pesos base x {1.0, wDef, wFora}
  0 -> GOL 1 -> DEFENDIDO 2 -> PARA FORA
```

**Conversão base** (multiplicadores = 1): `5.5 / 56.05 = **9,81%** por chute`.
Com 3 gols já feitos: 7,5%; com 5: 5,1%; com 6: **0,9%** (trava anti-goleada explícita).

**Nota sobre o mando (parece bug original):** o ajuste **aumenta** os dois pesos de "não-gol" do
**mandante** e os **diminui** para o visitante - ou seja, o chute do mandante converte **pior**
(~8,8%) e o do visitante **melhor** (~11,1%). Além disso `wFora` é sobrescrito a partir de `wDef`,
destruindo o termo defesa-vs-ataque em toda partida com mando. Na prática o maior volume de chutes
do mandante é quase exatamente cancelado pela conversão menor.

### Quem finaliza e quem dá assistência CONFIRMADO

**Finalizador** - sorteio ponderado entre os escalados, **excluindo o ocupante do slot 1 e todo
jogador cuja posição natural é goleiro** (as duas condições são exigidas, então um goleiro escalado
na linha também fica de fora): slots 2-9 -> peso 1; slot 10 -> 8; 11-13 -> 4; 14-17 -> 8;
18-25 -> **22**. Bônus por característica: **Finalização -> +4**; senão **Cabeceio -> +2**
(**+2 extra se for zagueiro**). Se o sorteio não devolver ninguém, cai para o **último** jogador da
lista de escalados. O finalizador é sorteado **uma vez por chute**, antes da resolução, e é o mesmo
jogador que depois é levado ao sorteio de tipo de gol (seção 3.7).

**Assistência** - só em gols de bola rolando; **19% das vezes não há assistente** (a moeda é
`rand(100) > 80`, ou seja 81..99). Elegível é **qualquer escalado com slot >= 1 menos o próprio
finalizador** - inclusive o goleiro, que tem peso 1.
Pesos: slot 1 -> 1; **2 e 9 (laterais) -> 10**; 3-8 -> 2; **10 -> 10**; 11-13 -> 4; **14-16 -> 20**; 17-25 -> 10.
Bônus (só o primeiro ramo que casar): **Passe -> +10** (+5 se também Armação); senão Armação -> +2
(+2 se a **1ª** característica for Drible); senão Drible -> +2 (+2 se a **1ª** for Velocidade); senão
Velocidade -> **+1 na conta do total e +2 na caminhada do sorteio** (+2 se lateral, nas duas);
senão Cruzamento -> +5 (+2 se lateral).
**Mais +20 para qualquer lateral quando a marcação do time é "Pesada"** (marcação = 1; 0 é Leve e
2 é Muito pesada).

**A inconsistência da Velocidade (item 4 da 3.15) é CONFIRMADA e é assimétrica**: o total sorteável
é somado com **+1** e a caminhada que escolhe o vencedor é somada com **+2**. Como a caminhada
acumula mais peso do que o total, o alvo do sorteio é alcançado mais cedo: cada jogador com
Velocidade (e nenhuma das características anteriores) rouba assistências dos que vêm **depois** dele
na lista de escalados, e ninguém no fim da lista chega a ser sorteado quando há Velocidade suficiente
antes. Não há terceira passagem: as duas passagens são a do total e a da escolha.

## 3.7 Tipo de gol CONFIRMADO

Sorteio `rand(1000)` no momento do gol, **depois** de o chute já ter sido resolvido como gol e com o
finalizador já sorteado (seção 3.6c):

| Faixa | Tipo | Probabilidade |
|---|---|---|
| < 900 | bola rolando | **90,0%** |
| 900-949 | **pênalti** | 5,0% |
| 950-979 | **falta direta** | 3,0% |
| 980-989 | **gol contra** | 1,0% |
| 990-994 | **gol olímpico** | 0,5% |
| >= 995 | bola rolando | 0,5% |

Na ordem em que o original as aplica:

1. **Assistência** - sorteada **só** quando o tipo é bola rolando, e **antes** dos remendos dos itens
   2 e 3; um gol que só vira bola rolando por causa deles nunca tem assistente.
2. **Olímpico**: se o **cobrador de escanteio** estiver em campo, ele é creditado. Senão o gol
   continua olímpico e fica com o finalizador sorteado - o ramo "se o sorteado for goleiro, vira bola
   rolando" **é inalcançável**, porque o sorteio de finalizador já exclui todo goleiro de posição
   natural. Como o cobrador de escanteio nunca é preenchido pela IA (seção 5.6), na prática **todo
   gol olímpico de time de IA é creditado ao finalizador**.
3. **Gol contra**: o autor exibido é substituído por um jogador do time **que defende**, com pesos
   GOL 1, slot 2 -> 5, **slots 3-8 -> 18**, 9 -> 5, 10 -> 1, 11-13 -> 5, 14-25 -> 1. O gol continua
   contando para o time atacante. Se o time que defende não devolver ninguém, o tipo vira bola
   rolando.
4. **Pênalti e falta**: se o **batedor designado** estiver em campo, ele é creditado no lugar do
   finalizador sorteado.

**A quem o gol é creditado.** O redirecionamento dos itens 2 e 4 troca o autor **do evento** - é ele
que aparece no relato da partida e é ele que recebe o gol na **estatística de temporada**. A
contagem de gols **da partida**, que é a que alimenta a nota da seção 3.14, continua indo para o
**finalizador sorteado**. Num gol de pênalti, de falta ou olímpico redirecionado, portanto, o
batedor designado aparece como autor e o finalizador sorteado é quem ganha o `+0,9` de nota. Ver o
item 57 do `OPEN-QUESTIONS.md`.

**No gol contra o atacante também marca.** O item 3 troca o autor do evento pelo defensor e dá a ele
o contador de gol contra (`-1,5` de nota), mas o **finalizador sorteado do time atacante ganha um gol
na contagem da partida assim mesmo** (`+0,9` de nota), sem aparecer em lugar nenhum do relato. Só a
estatística de temporada fica correta: gol contra não credita gol a ninguém nela.

**O gol conta duas vezes para o autor da partida** em bola rolando, falta e olímpico, e **uma só vez**
em pênalti (IAxIA) e gol contra. Consequência: um gol de bola rolando vale **+1,8** de nota, não
+0,9. Ver o item 13 da 3.15 e o item 51 do `OPEN-QUESTIONS.md`.

**Peculiaridade do pênalti:** quando o tipo é pênalti e **algum dos dois times** é humano (não
importa de quem é o gol), o gol **não** é somado ao placar - ele é entregue ao visualizador como
pênalti interativo, que decide (seção 3.10). A condição do visualizador é a mesma - ele trata como ao
vivo exatamente a partida que tem time humano -, então nenhum gol se perde: ou o pênalti interativo o
confirma, ou o próprio visualizador o soma. Em IAxIA conta normalmente.

## 3.8 Disciplina, lesões e substituições CONFIRMADO

Roda **uma vez por minuto, antes do tick de jogo**.

**Time-vítima:** `rand(100) > 55` -> **mandante** (44%), senão **visitante** (56%). É o único
mecanismo parecido com "viés de arbitragem" do jogo. CONFIRMADO

**Fase** `p`: minuto < 15 -> 0; < 30 -> 1; senão 2, com o **minuto contado dentro do próprio tempo,
a partir de 0** - a contagem reinicia no início do 2º tempo, exatamente como o desgaste de energia
da 3.9. As seis células de cada tabela abaixo são portanto alcançáveis. CONFIRMADO

Eventos disparam com `rand(N) == 1` (prob. `1/N`):

| Evento | 1º tempo (p=0,1,2) | 2º tempo (p=0,1,2) |
|---|---|---|
| **Amarelo** | 70, 40, 30 | 45, 40, 30 |
| **Vermelho direto** | 1200, 900, 800 | 800, 700, 550 |
| **Lesão** | 1500, 1000, 800 | 800, 600, 600 |

Depois: `limiarAmarelo += {Leve: 30, Pesada: 10, Muito pesada: 0}` conforme a marcação da vítima
(qualquer valor de marcação fora de 0-2 cai no 30). CONFIRMADO
Modificadores (os últimos sobrescrevem): se já houve > 5 amarelos -> limiar x2; se já houve >= 2
vermelhos -> limiar do amarelo passa a `2 x limiarVermelho`; se já houve >= 1 lesão -> passa a
`5 x limiarLesão`. **Na prática, depois da primeira lesão da partida a taxa de cartões despenca.**
CONFIRMADO

**Os três contadores que essas sobrescritas leem são da partida inteira, não de cada lado**: contam
amarelos, expulsões por vermelho direto e lesões dos dois times somados. Eles são incrementados
**mesmo quando o grupo de risco sorteado não tem ninguém** e nenhum evento chega a acontecer.
Uma expulsão por segundo amarelo soma **1 ao contador de amarelos e nada ao de vermelhos** - só o
vermelho direto alimenta o contador que a sobrescrita `>= 2 vermelhos` lê. CONFIRMADO

Resolução, o primeiro que casar: amarelo -> vermelho -> lesão -> (se 2º tempo e minuto >= 5) janela de substituição da IA. CONFIRMADO

**Grupos de risco** (sorteia-se um grupo, depois um jogador aleatório **entre os que ocupam** as
células da faixa - a faixa é filtrada pelos jogadores em campo e o sorteio é uniforme sobre eles;
se nenhuma célula da faixa está ocupada, nada acontece naquele minuto):
g0 = 10-13, g1 = 14-17, g2 = 3-8, g3 = 2-3, g4 = 8-9, g5 = 19-24, g6 = goleiro (slot 1). CONFIRMADO

| Evento | Distribuição |
|---|---|
| Amarelo `rand(100)` | <25 -> g0 (25%); <40 -> g1 (15%); <65 -> g2 (25%); <73 -> g3 (8%); <82 -> g4 (9%); <85 -> goleiro (3%); senão g5 (15%) |
| Vermelho `rand(200)` | ==0 -> goleiro (0,5%); <80 -> g0 (39,5%); <110 -> g1 (15%); <160 -> g2 (25%); <170 -> g3 (5%); <190 -> g4 (10%); senão g5 (5%) |
| Lesão `rand(500)` | ==0 -> goleiro (0,2%); <150 -> g0 (29,8%); <250 -> g1 (20%); <320 -> g2 (14%); <360 -> g3 (8%); <420 -> g4 (12%); senão g5 (16%) |

As três distribuições acima são CONFIRMADO.

Segundo amarelo -> expulsão (evento distinto). CONFIRMADO

**Consequências.** Expulso: sai de campo (dos dois lados, humano ou não); se o slot <= 13 e o time é
da IA com substituições disponíveis, a IA **sacrifica um atacante** e põe o reserva mais adequado ao
slot vago. O sacrificado é alguém de 18-25; se não houver, alguém de 14-17; e, **só quando o expulso
é o goleiro**, se ainda não houver, qualquer um de 2-25.
Lesionado: sai e é substituído. Nas duas, o reserva é escolhido pela cascata
da 5.4 para o slot que ficou vago, e nenhuma das duas olha a metade do relógio - **valem também no
1º tempo**, ao contrário das três janelas voluntárias abaixo. CONFIRMADO

**A restrição de goleiro é o inverso do que se poderia esperar.** Na reposição de uma lesão, a troca
só é feita se o que sai é goleiro **ou** o que entra não é goleiro; ou seja, o que a regra impede é
um **goleiro reserva entrar no lugar de um jogador de linha**, e não o contrário. A cascata de
posição da 5.4 é aplicada sem exceção para o gol: quando o goleiro sai e **não há goleiro no banco**,
a cascata desce para a posição seguinte e **um zagueiro (depois lateral, meia, atacante) assume o
gol**, com o x0,5 e o `round(GK x 0,2)` da 5.3. A célula do goleiro nunca fica vazia por falta de
goleiro reserva. CONFIRMADO

**Duração da lesão (em dias)** - **único ponto em que a energia realimenta o resultado**:
```
base = 0 ; energia < 10 -> base = 5 ; senão energia < 50 -> base = 1
x = rand(0..13) ; y = 5 + rand(0..19)
idade <=20 -> x (descarta o termo de energia)
<=25 -> base+x+1 ; <=30 -> base+x+2 ; <=35 -> base+x+3
<=45 -> base+x+y ; senão base+x+10+y
gravidade rand(100): ==1 -> +70 ; <4 -> +40 ; <10 -> +20
idade >= 35 -> perde permanentemente 5 de força (só vira 1 se o resultado ficar NEGATIVO;
              uma força que cai exatamente em 0 fica em 0)
```
CONFIRMADO, inclusive a ordem: a perda de força é aplicada **antes** do sorteio de gravidade, e a
gravidade é sempre sorteada. Duração 0 (possível para idade <= 20 quando `x` sai 0) **não registra
lesão nenhuma** - o jogador sai de campo e volta a ficar disponível na rodada seguinte.

**Suspensões** (pós-rodada): amarelo soma 1 ao registro; expulsão por 2º amarelo soma 1 amarelo **e**
1 jogo de gancho; vermelho direto sorteia `rand(1000)`: <700 -> 1 jogo (70%), <900 -> 2 (20%),
<970 -> 3 (7%), <=990 -> 5 (2%), senão 10 (1%) - é o mesmo sorteio que, nos quatro últimos casos,
gera a notícia de tribunal da seção 1.8; os "jogos" de pena ali são este mesmo gancho, incrementado
em laço uma vez por jogo de pena, sem contador separado. O jogador fica indisponível enquanto
`amarelos >= 3 ou gancho >= 1`; cumprir zera os amarelos (se >=3) ou decrementa o gancho. CONFIRMADO

**Quando os dois estão ativos ao mesmo tempo, um não é cumprido antes do outro terminar - o teste
é sequencial a cada partida do clube.** CONFIRMADO - o registro por jogador guarda os dois contadores
lado a lado (amarelos acumulados e jogos de gancho) e o teste que roda ao fim de cada partida do
clube do jogador nessa competição verifica primeiro `amarelos >= 3`: se verdadeiro, **zera os
amarelos e para ali**, sem tocar no gancho naquela mesma partida; só quando `amarelos < 3` é que o
gancho é decrementado em 1. Ou seja, um jogador com 3+ amarelos e gancho >= 1 ao mesmo tempo cumpre
primeiro a suspensão por cartões amarelos numa partida, e só a partir da partida seguinte o gancho
passa a descontar, um jogo por partida disputada pelo clube naquela competição - os dois nunca são
consumidos na mesma partida. Este teste roda por partida efetivamente disputada pelo clube em cada
competição, não por rodada de calendário: um clube sem partida marcada numa rodada não avança nem
zera suspensão alguma naquela rodada, em nenhuma competição.

**Substituições da IA:** 5 por time; as três janelas **voluntárias** abaixo só abrem no 2º tempo
(+ janela do intervalo); times humanos nunca são substituídos automaticamente. O sacrifício da
expulsão e a reposição da lesão descritos em Consequências **não** têm essa restrição. Os dois times
sorteiam seus minutos do mesmo pool embaralhado, sem reposição entre eles: CONFIRMADO
- **Minutos "correndo atrás"**: 2 por time (+1 com 69% de chance), sorteados sem reposição em **19-38**.
- **Minutos de rotina**: escolhe-se um pool com `rand(100)`: >90 (9%) -> **5-15**; >50 (40%) -> **16-35**; senão (51%) -> **36-42**. Dois minutos por time desse pool, +1 de **43-47** com 79% e +1 de 43-47 com 49%.

No intervalo: se perde por >=1 (mandante) / >=2 (visitante), **49%** de chance de troca aleatória.
Em minuto "correndo atrás": mandante troca se perde ou empata; visitante só se perde.
Em minuto de rotina: troca por cansaço - primeiro não-goleiro com **energia < 60** (após o minuto 40
do 2º tempo o limiar sobe para **90** e a varredura começa num índice aleatório e **não dá a volta**,
podendo terminar sem achar ninguém). CONFIRMADO

**As duas janelas de placar (intervalo e "correndo atrás") sorteiam um índice qualquer da escalação
em campo**, não um jogador de linha: se o índice cair no goleiro, a janela é simplesmente
desperdiçada, sem nova tentativa. O sorteio também evita tirar quem acabou de entrar - com o defeito
descrito no item 12 da 3.15. Quem sai é sempre esse jogador sorteado; quem entra é o reserva mais
adequado à célula que ele deixou. CONFIRMADO

**A janela abre para os dois times, e não para o time-vítima do minuto.** As janelas de "correndo
atrás" e de rotina são o quarto ramo da cadeia de resolução, e por isso só abrem num minuto cuja
cadeia não produziu cartão nem lesão; a **janela do intervalo não passa pela cadeia** - ela roda uma
única vez entre os dois tempos, sem sorteio de vítima e sem nenhuma condição de disciplina. Os dois
times são avaliados no mesmo instante, o mandante primeiro (ver o item 11 da 3.15). CONFIRMADO

**Onde o substituto entra na lista.** O que sai é removido da escalação em campo e o que entra é
**acrescentado ao fim** da lista, herdando o slot da célula vaga. Isso importa para os agregados da
3.4, que tomam os **primeiros N** da lista e não os N melhores. CONFIRMADO

## 3.9 Energia

Inteiro 0-100, inicial 100.
- **Desgaste em jogo**: a cada 7 minutos, cada jogador em campo perde por idade: <=20 -> 1, <=25 -> 2,
  <=31 -> 3, <=36 -> 4, senão 5. **O goleiro é isento no 1º tempo.** ~7 descontos por tempo -> um
  jogador de 24 anos perde ~28 de energia por partida completa; um de 37, ~70. CONFIRMADO - o
  desconto cai nos minutos múltiplos de 7 **contados dentro do próprio tempo a partir de 0**
  (0, 7, 14, 21, 28, 35, 42 e, quando o acréscimo do 2º tempo é 5, também o 49): 7 descontos por
  tempo, 8 no 2º tempo em ~1 partida a cada 5.
- **Recuperação semanal** (pós-rodada):
  - jogou, clube **humano**: <=20 -> +13, <=25 -> +24, <=31 -> +37, <=36 -> +40, senão +30.
  - jogou, clube **da IA**: <=20 -> +20, <=25 -> +30, <=31 -> +50, <=36 -> +52, senão +42.
  - não jogou: <20 -> +30, <26 -> +30, <33 -> +35, <45 -> +35, senão +30.
  -> **clubes humanos recuperam bem menos** (inclinação deliberada de dificuldade).
- Jogadores da base voltam a 100 a cada rodada.

**A energia não influencia nenhuma probabilidade em campo.** Ela afeta: gatilho de substituição da
IA, gravidade da lesão, desempate na ordenação da escalação e o número de força exibido.

**Não existe moral, forma nem confiança em lugar nenhum do motor.**

## 3.10 Pênaltis CONFIRMADO

- **Disputa em IAxIA (não é chute a chute):** `x = rand(2..8)`, `y = rand(2..8)`; se `x >= y` o
  mandante vence por `(x, x-1)`, senão o visitante vence por `(x, x+1)`. **Os dois lados do placar
  saem de `x` nos dois ramos** - `y` só serve para a comparação, inclusive no ramo do visitante - e a
  diferença é **sempre de um gol**. Como o empate favorece o mandante, o **mandante vence
  28/49 = 57,1%** das disputas. O placar do vencedor **nunca passa de 8**: no ramo do mandante
  `x <= 8`, e no ramo do visitante a própria condição `x < y` obriga `x <= 7`, logo `x + 1 <= 8`.
  Faixas observáveis: **vencedor 2 a 8, perdedor 1 a 7**. Uma redação anterior desta seção dizia que
  o vencedor visitante podia chegar a 9; era um deslize de prosa, a fórmula é que está certa - ver o
  item 59 do `OPEN-QUESTIONS.md`.
- **Pênalti interativo (a via do gol de tipo pênalti em partida com time humano, seção 3.7):**
  conversão base **70%**; batedor com **Finalização** ou "estrela vermelha" +10; "estrela" +5;
  goleiro com **Defesa Penalty** -10; goleiro estrela vermelha -10; goleiro estrela -5. A moeda é
  `rand(1..100) <= limiar`. **Esta via existe e é alcançada** em toda partida do time humano em que o
  sorteio da 3.7 devolver pênalti - inclusive quando o pênalti é do adversário.
  - **Convertido:** conta como chute e chute no alvo do time, soma o gol ao placar e dá **um** gol ao
    batedor na contagem da partida.
  - **Perdido:** conta como chute; um sorteio `rand(7)` escolhe o desfecho. A partição de alvo é de
    **duas vias, não três**: **2 dos 7 contam como chute para fora e os outros 5 como chute no
    alvo**. Cruzando com essa partição, e independente dela, **3 dos 7 creditam o goleiro com um
    pênalti defendido** (+1,2 na nota dele, seção 3.14) - e esses 3 estão todos **dentro** dos 5 no
    alvo. Sobram, portanto, **2 desfechos que contam no alvo sem creditar defesa nenhuma** (a bola
    que bate na trave e o batedor que escorrega). O batedor ganha um contador de **pênalti
    perdido**, que a nota lê errado (item 15 da 3.15). CONFIRMADO
- **Disputa interativa:** **70% fixos por cobrança, sem nenhum atributo**. Melhor-de-5 e morte súbita.
  A ordem dos batedores de um time de IA sai de uma **regra de um lado só**: se o primeiro comparado
  tem posição maior ele vai na frente, mas se tem posição menor **não vai atrás** - a comparação cai
  para força desc e depois energia desc. O resultado não é "posição desc, força desc, energia desc",
  e sim uma lista de força desc com atacantes empurrados para a frente de forma dependente da ordem
  de partida. Time humano escolhe a ordem na mão. Esgotada a lista, ela **recomeça do primeiro**.

## 3.11 Mando de campo, público, árbitro, clima

- **Campo neutro** se e só se a competição é Mundial ou Seleções - aí tudo abaixo é desligado.
- Efeitos do mando: **+0,3** no duelo de posse e **+0,3** no duelo de chance (só nos ticks em que o
  mandante tem a bola); **+0,1/+0,2** nos pesos de não-gol do mandante e **-0,1/-0,2** nos do
  visitante (invertido, ver seção 3.6c); e a divisão **44%/56%** de cartões/lesões contra o visitante.
- **Público**: calculado por partida (capacidade dividida em 4 setores, com multiplicadores de preço,
  reputação, divisão, competição e clássico) e usado **somente para receita de bilheteria**. Não afeta o jogo.
- **Qualidade do gramado** (Excelente / Muito Boa / Ruim / Precária) serve **apenas para escolher a
  imagem de fundo**. Zero efeito.
- **Não existe árbitro, clima, viagem nem bônus de clássico dentro do jogo.**

## 3.12 Táticas

Quatro valores por time: `[formação, estilo, marcação, lado do ataque]`.
- **Estilo** (Equilibrado / Ataque total / Contra-ataque): lido e descartado. **Nenhum efeito.**
- **Marcação** (Leve / Pesada / Muito pesada): (a) soma 0 / 0,04 / 0,08 à **soma** do meio-campo
  (~ +0,008/+0,016 na média - desprezível); (b) soma 30 / 10 / 0 ao limiar do cartão amarelo, ou seja
  **marcação mais forte gera bem mais cartões**; (c) "Pesada" dá **+20 de peso de assistência** a todo lateral.
- **Lado do ataque** (Pelo meio / Pelas laterais): **nunca é lido. Nenhum efeito.**
- Táticas da IA: estilo `rand(1..100)` -> 1-70 Equilibrado, 71-80 Ataque total, 81-100 Contra-ataque;
  marcação -> 1-5 Muito pesada, 6-70 Leve, 71-100 Pesada; lado -> 1-70 meio, 71-100 laterais.
- Designados: **batedor de falta** (usado em gols de pênalti e falta), **cobrador de escanteio**
  (gol olímpico), **capitão** (sem efeito encontrado) e **"falso 9"** (**nunca lido - recurso morto**).

## 3.13 Estatísticas produzidas
Posse %, chutes, no alvo, para fora, desarmes, passes errados e **faltas - cujo contador existe mas
nunca é incrementado (a linha de faltas é sempre 0x0)**.

O contador de **no alvo** é "gols + defesas" apenas no jogo corrido. O pênalti interativo da 3.10
acrescenta uma exceção: dos 7 desfechos de uma cobrança perdida, **5 sobem o contador de no alvo** e
só 3 desses creditam uma defesa, de modo que 2 desfechos entram como "no alvo" sem gol e sem defesa.
Os outros 2 sobem o contador de para fora. CONFIRMADO

## 3.14 Notas dos jogadores (pós-partida) CONFIRMADO

**Quem recebe nota:** os **titulares dos dois times** e **todo reserva que entrou em campo**. Quem
ficou no banco a partida inteira não recebe nota nenhuma - não recebe 0, simplesmente não passa pelo
cálculo.

**Slot usado no cálculo:** o slot do jogador; se ele for `<= 0`, adota-se um slot padrão pela
posição natural - **GOL 1, LAT 2, ZAG 7, MEI 15, ATA 23** - e esse valor fica **gravado** no jogador.

Base por resultado e força:

| Resultado | força <=30 | <=60 | <=90 | >90 |
|---|---|---|---|---|
| Empate | 5,5 | 5,8 | 6,2 | 6,8 |
| Vitória | 6,0 | 6,0 | 6,7 | 7,2 |
| Derrota | 5,0 | 5,2 | 5,5 | 6,0 |

Os ajustes, **nesta ordem** (a ordem importa por causa do teto, do piso e do remendo do passo 9):

1. **Fora de posição** (slot `<= 0` também conta como fora de posição): **-1,5**; e **-1,5 extra se o
   slot é o 1**, isto é, se o deslocado foi para o gol.
2. **Meias (slots 10-17)**, comparando o contador de posse dos dois times: com **mais** posse +0,8
   (ou +0,3 com prob. 1/3), **+0,3 se o estilo derivado é 0**, **+0,5 se a 1ª característica é Passe
   ou Armação** (só a primeira); com **menos** posse -0,8 (ou -0,3 com prob. 1/3), **-0,5 se o estilo
   derivado é 0**.

   O termo que a redação antiga chamava de "volante" **não é uma faixa de slot** - não é 11-13. É o
   **estilo derivado da 4.3** (`ex`) valendo **0**, testado sozinho, depois de a faixa 10-17 já ter
   sido aplicada. CONFIRMADO. Duas consequências que a leitura por slot não dá:
   - um **meia ofensivo de estilo defensivo** (Desarme ou Marcação nas características) escalado no
     slot 14, 15 ou 16 **recebe** o termo;
   - nos **slots 10 e 17 (alas)**, que exigem posição Lateral, quem recebe o termo é o **lateral de
     estilo defensivo** - não há volante nenhum ali. O teste é sobre o estilo, e não sobre a
     posição, então ele alcança laterais também.

   Ver o item 60 do `OPEN-QUESTIONS.md`.
3. **Eventos do jogador:** gols da partida **x+0,9**; gols contra **x-1,5**; **se perdeu algum
   pênalti interativo, -1,2 x (gols contra)** - o termo existe, mas multiplica o contador errado e só
   morde quem fez gol contra e perdeu pênalti na mesma partida (item 15 da 3.15); amarelos x-0,2;
   vermelhos x-0,8; assistências x+0,4.
4. **Defensivos (slots 1-13)**, comparando o contador de desarmes: **venceu** +0,6 (ou +0,9 com
   prob. 1/3), mais +0,6 com prob. 1/3 se o slot é **2-9**, mais +0,6 com prob. 1/3 se o slot é
   **11-13**; **perdeu** -0,5, mais **-0,6 com prob. 1/4 se o slot é 3-8**, mais **-0,6 com prob. 1/4
   se o slot é 11-13**. (Os dois termos negativos faltavam na spec; note que a faixa premiada é 2-9 -
   laterais inclusive - e a punida é 3-8, só zagueiros.)
5. **x+0,3 por chute que o goleiro adversário defendeu.** Não é "chute no alvo": o contador do
   jogador só sobe no ramo "defendido" da resolução de chute, então **gol não conta aqui** e chute
   para fora também não. Ver o item 52 do `OPEN-QUESTIONS.md`.
6. **Só o slot 1 (goleiro):** -0,8 fixo; **+0,2 por chute no alvo sofrido**; **+1,2 por pênalti
   defendido** (só pela via interativa da 3.10 - e ela é alcançável); **+0,2 se o adversário deu mais
   de 10 chutes no total** - os ramos "> 15" (+0,2) e "> 20" (+0,3) estão numa cadeia de senão depois
   do "> 10" e são **inalcançáveis**; gols sofridos >= 5 -> -2,0, >= 4 -> -1,5, >= 2 -> -1,0, >= 1 ->
   -0,5, sem sofrer -> +1,0; **se não sofreu nenhum chute no alvo -> -1,5**.

   **Os dois testes leem contadores diferentes**, e a troca de palavra da frase é real. CONFIRMADO:
   - o **+0,2 por chute** e o **-1,5 de "nenhum chute"** leem o contador de **chutes no alvo** do
     adversário (gols + defendidos, 3.13);
   - o degrau **"> 10"** lê o contador de **chutes totais** do adversário (no alvo + para fora).

   A diferença é enorme na prática: pela 3.16 um lado dá ~16 chutes totais e bem menos no alvo, então
   sob o contador total **quase todo goleiro leva o +0,2 do degrau**, enquanto sob o de no alvo quase
   nenhum levaria. Ver o item 61 do `OPEN-QUESTIONS.md`.
7. **Slots 1-13:** partida **sem sofrer gol** +0,5, mais +0,5 se o slot é **2-9**, mais +0,5 com
   prob. 1/3 se o slot é **11-13**; **sofrendo 2 ou mais gols, -0,1 por gol sofrido** (sofrer
   exatamente 1 não custa nada aqui); e, para os slots **2-13**, **-0,4 com prob. 1/3,
   incondicionalmente** - um imposto que todo defensor e todo volante paga em 1/3 das partidas.
8. Estrela **+0,4**; estrela vermelha **+0,6**. **Os dois termos são cumulativos**: são duas somas
   independentes, e não uma cadeia de senão. Quem carrega as duas marcas soma **+1,0**. CONFIRMADO.
   Como a 4.10 faz a estrela vermelha implicar a estrela **na criação do mundo e na leitura do
   `.ban`**, quase todo jogador de estrela vermelha soma +1,0 - mas a **promoção a estrela vermelha
   durante a carreira não liga a estrela comum**, e esse jogador soma só +0,6 (item 18 da 3.15). Ver
   o item 62 do `OPEN-QUESTIONS.md`.
9. **Teto 10**; e, logo depois, **se a nota ficou negativa ela vira 1,0** (não 0, e não o piso 2,0 -
   este passo é anterior ao piso e ao desconto de minutos).
10. **Minutos jogados**: < 15 -> **-2,5**; senão < 45 -> **-1,5**.
11. **Piso 2,0**; e se jogou < 20 min **e** a nota parou em 2,0 -> nota **0** ("sem nota").

**Minutos jogados não são medidos.** Valem **90** por padrão e são **sobrescritos pelo último evento
da partida em que o jogador aparece**, seja qual for o tipo do evento:
- como **protagonista** do evento (autor creditado de um gol, cartão, saída em substituição):
  `minuto` no 1º tempo, `48 + minuto` no 2º;
- como **coadjuvante** (assistente, goleiro que pegou o pênalti interativo, entrada em substituição):
  `98 - minuto` no 1º tempo, `50 - minuto` no 2º.

Ou seja, **quem marca no 1º tempo é tratado como quem jogou só até ali**: um gol no minuto 20 do 1º
tempo custa -1,5 ao autor, e um gol antes do minuto 15 custa -2,5. Simetricamente, **quem dá
assistência tarde no 2º tempo** é tratado como quem entrou tarde: assistir depois do minuto 35 do 2º
tempo custa -2,5. Ver o item 53 do `OPEN-QUESTIONS.md`.

## 3.15 Bugs e esquisitices - decidir explicitamente ao reimplementar

1. **Mando invertido na conversão de chutes** (seção 3.6c): mandante converte ~8,8%, visitante ~11,1%; e o peso "para fora" é sobrescrito em toda partida com mando.
2. **Slot 18 não contribui para nenhum agregado** - um 3-4-3 tem o ataque calculado com 2 dos 3 atacantes, dividido por 3.
3. **Divisores fixos** (5/3/5) punem qualquer formação que não tenha exatamente 5 meias / 3 atacantes / 5 defensores.
4. **Peso de assistência inconsistente**: Velocidade vale +1 na passagem que soma o total e +2 na
   passagem que caminha até o sorteado. CONFIRMADO. Não é só cosmético: a caminhada acumula mais peso
   do que o total, então quem tem Velocidade puxa assistências dos jogadores que vêm depois dele na
   lista de escalados, e o fim da lista pode nunca ser alcançado. Ver a seção 3.6.
5. **Sobrescritas do limiar de cartão**: após 2 vermelhos vira `2 x limiarVermelho`; após 1 lesão vira `5 x limiarLesão` - ambos derrubam drasticamente os cartões no resto do jogo. Há ainda um ramo inalcançável (`> 10 amarelos`).
6. **Força exibida** usa `round(energia/100 x força)` com divisão inteira -> só mostra a força real com energia exatamente 100; caso contrário **exibe 0**. (Só display; o motor não usa.)
7. Um passe de relaxamento da escalação é inalcançável (limite do laço).
8. Os pools de minutos de substituição são **estáticos/compartilhados**, re-embaralhados por partida -> partidas consecutivas sorteiam minutos correlacionados. CONFIRMADO: os cinco pools (19-38, 5-15, 16-35, 36-42, 43-47) são criados uma vez no processo e apenas re-embaralhados no começo de cada partida; mandante e visitante tiram do **mesmo** embaralhamento, em posições fixas, e por isso os minutos dos dois lados nunca coincidem dentro de uma partida - **mas isso vale por pool, e não entre pools**: a janela de "correndo atrás" (19-38) e os pools de rotina 16-35 e 36-42 são embaralhamentos diferentes e se sobrepõem (em 19-35 e em 36-38), então um minuto de rotina de um lado ainda pode cair sobre um de "correndo atrás" do outro. É exatamente disso que o item 11 abaixo depende; ver o item 42 do `OPEN-QUESTIONS.md`.
9. Um bloco grande de constantes do motor (dez arrays de 3 elementos, ~12 escalares, 2 arrays de contadores) é declarado e **nunca usado** - resquício de um modelo antigo. Não portar.
10. Prorrogação nunca é simulada; empates em mata-mata vão direto para a fórmula abstrata de pênaltis.
11. **A janela de substituição do visitante é engolida pela do mandante.** As duas janelas do mesmo minuto são avaliadas na mesma passagem, o mandante primeiro; se o mandante **efetivamente trocou** naquele minuto, a janela do visitante nem é examinada. Como os minutos dos dois lados saem do mesmo pool sem reposição, isso só morde quando um minuto de "correndo atrás"/rotina do visitante coincide com o do mandante ou no intervalo, onde os dois são avaliados juntos - ali o visitante perde a janela sempre que o mandante trocou. CONFIRMADO
12. **A checagem de "não tire quem acabou de entrar" olha sempre a lista do mandante.** No sorteio aleatório das janelas de placar, o índice do time é comparado contra um valor que ele nunca assume, então a lista consultada é sempre a de substitutos que **entraram pelo mandante**. Efeito: o mandante nunca tira quem acabou de entrar (com uma única re-tentativa), e o visitante não tem proteção nenhuma - pode sacar num minuto o reserva que pôs em campo no minuto anterior. CONFIRMADO

13. **Gol de bola rolando, falta e olímpico contam duas vezes para o autor da partida.** O contador
    de gols da partida do finalizador sorteado é incrementado uma vez no início do sorteio de tipo
    (para todo tipo que não seja pênalti nem gol contra) e outra vez ao somar o gol ao placar (para
    todo tipo, quando o gol de fato entra). Efeito visível: um gol de bola rolando vale **+1,8** de
    nota e não +0,9; pênalti em IAxIA e gol contra valem +0,9. A estatística de temporada não é
    afetada - ela é montada a partir dos eventos. CONFIRMADO; ver o item 51 do `OPEN-QUESTIONS.md`.
14. **Minutos jogados são o minuto do último evento do jogador**, e não o tempo em campo (seção
    3.14). Como gols e cartões também são eventos, marcar cedo no 1º tempo aplica ao autor o desconto
    de "entrou faltando pouco", e assistir tarde no 2º tempo aplica -2,5 ao assistente. CONFIRMADO.
15. **O termo de pênalti perdido da nota multiplica o contador errado**: ele é ligado pelo contador de
    pênaltis perdidos, mas o valor multiplicado por -1,2 é o de **gols contra**. Quem perdeu pênalti e
    não fez gol contra perde 0,0; quem fez as duas coisas é punido duas vezes pelo gol contra.
    CONFIRMADO.
16. **Dois ramos inalcançáveis na nota do goleiro** (">15" e ">20" **chutes totais** sofridos, seção 3.14) e um
    no sorteio de tipo de gol (o olímpico que viraria bola rolando por o sorteado ser goleiro, seção
    3.7). Não portar nenhum dos três.
17. **Existe uma segunda tabela de tipo de gol, mais generosa com faltas (80% bola rolando, 5%
    pênalti, 13% falta), acoplada a um sorteio de cartão para o time que cometeu o pênalti. Nada a
    chama** - é código morto de outra versão do motor. Não portar.

18. **A estrela vermelha só implica a estrela comum na entrada, não na promoção.** A 4.10 diz que a
    estrela vermelha implica a estrela, e isso vale onde a marca chega de fora: ao montar o mundo e ao
    ler um jogador do `.ban`, ligar a estrela comum força a marca ligada quando a vermelha já está.
    Mas a **promoção a estrela vermelha ao fim de temporada** (4.10) liga só a marca vermelha e não
    toca na comum. Efeito na nota (passo 8 da 3.14, cujos dois termos são cumulativos): um jogador
    promovido a estrela vermelha que nunca tinha ganhado a estrela comum recebe **+0,6**, e não +1,0 -
    fica valendo *menos* por partida do que um jogador equivalente vindo do arquivo. CONFIRMADO.

## 3.16 Sanity check (o que uma reimplementação fiel deve produzir)

> **Aviso.** As faixas desta seção **não foram derivadas das fórmulas das seções 3.1 a 3.8**. Onde
> uma figura daqui discorda de uma constante lida na lógica, a constante é que vale, e a figura daqui
> foi corrigida. Ver os itens 28 a 30 e 46 do `OPEN-QUESTIONS.md`.

Dois times equivalentes, não-humanos, campo normal, temporada 1:
- **~94 ticks** em média (91 a 97, pela 3.1: `45 + rand(0..2)` mais `45 + rand(1..5)`); cada time é o
  atacante nominal ~47 vezes. CONFIRMADO
- Mandante: P(vencer duelo de posse) ~ 0,614; P(chute | posse) ~ 0,565 -> ~ **16 chutes** a ~8,8% -> **~ 1,4 gol**.
- Visitante: 0,55 e 0,50 -> ~ **12,6 chutes** a ~11,1% -> **~ 1,4 gol**.
- **Posse exibida ~ 53/47** para o mandante, e não 55/45: a posse exibida é a fração de tiques em que
  cada lado venceu o duelo, e o atacante nominal alterna a cada tique, então ela é a média dos dois
  duelos - `(0,614 + (1 - 0,55)) / 2 = 0,532`. CONFIRMADO
- **~1,3 amarelo por jogo** (partida inteira, os dois lados somados); **uma expulsão a cada ~6
  partidas** contando vermelho direto e segundo amarelo juntos; **uma lesão a cada ~17 partidas por
  lado**. As figuras antigas - 2 a 3 amarelos, um vermelho a cada 8-12, uma lesão a cada 6-10 por
  lado - não saem das tabelas da 3.8 sob nenhuma leitura; ver o item 46 do `OPEN-QUESTIONS.md`.

**O que este par de times NÃO mede.** Todos os números acima são de dois times **sem banco de
reservas**, que é como as figuras desta seção foram levantadas. Um time sem banco nunca substitui
ninguém, então **nenhuma** das quatro janelas de substituição da 3.8 chega a abrir e nenhum número
desta seção se mexe quando as regras de substituição mudam. Os dois times também são todos de uma
idade só, o que deixa fora de alcance a duração de lesão 0 da 3.8 (só sorteável até 20 anos) e
quase toda a tabela de desgaste da 3.9. Uma reimplementação que só confira esta seção não terá
verificado nada disso: precisa de um segundo par de times, com banco e com idades variadas, medindo
substituições por partida por lado, com que frequência um lado gasta as cinco, e a divisão dessas
substituições entre as janelas.

Uma terceira cegueira **não** se resolve com banco nem com idade, e é a única que o segundo par
também não fecha: a formação 4 ocupa os **sete** grupos de risco da 3.8 no apito inicial, então a
tentativa que sorteia um **grupo vazio** - que ainda assim sobe os três contadores das sobrescritas,
sem nada chegar ao log - só acontece depois de alguém ter saído de campo. É rara demais (umas poucas
vezes em vinte mil partidas) para qualquer média enxergar, e só um teste de **sorteio roteirizado**
cobre esse caminho. Ver o item 46 do `OPEN-QUESTIONS.md`.

**Alavanca dominante do modelo:** 20 pontos de força de diferença no meio-campo (~ 2,0 em unidades
de `B()`) levam o duelo de posse de 55% para ~69% e o de chance de 50% para ~56% - cerca de **40% de
variação no volume de chutes**.

---

# 4. MODELO DE JOGADOR

## 4.1 Os 7 atributos individuais - identificados

Índice -> rótulo na interface: **0 Goleiro (Gol), 1 Velocidade (Vel), 2 Técnica (Tec), 3 Passe (Pas)
, 4 Desarme (Des), 5 Armação (Arm), 6 Finalização (Fin)**. Todos 0-100, teto 100.
Ordem das colunas na tabela de elenco: Gol, Des, Arm, Fin, Vel, Tec, Pas.

## 4.2 Geração dos atributos

Recebe dois inteiros: **A** (semente de qualidade do clube) e **B** (faixa por divisão/reputação);
`C = floor(A/3)`; `F` = força já calculada; `rnd(k)` = inteiro uniforme em `[0, k-1]`.

Na criação do mundo: `A` = nível mapeado do clube (-4 se > 4) e `B` = 7/3/1 por divisão
(ou 7/4/1/1/1 por reputação). Na **promoção da base**: `A = max(força-5, 5)` - por isso jogadores
promovidos da base saem com atributos secundários bem melhores que os gerados na criação do mundo.

| Posição | Fórmulas |
|---|---|
| **Goleiro** | Gol = `F + rnd(2)`; Vel = `A+rnd(7)`; Tec = `A+rnd(4)`; Pas = `A+rnd(4)`; Des/Arm/Fin = `B+rnd(3)` |
| **Lateral def.** (ex=0) | Des = `round(Fx0.8)+rnd(6)`; Fin = `B+rnd(4)`; Pas = `A+rnd(3)`; Tec = `A+rnd(7)`; Arm = `B+rnd(5)`; Vel = `A+B+rnd(6)`; Gol = `1+rnd(4)` |
| **Lateral of.** (ex=1) | Arm = `round(Fx0.5)+rnd(5)`; Fin = `A+B+rnd(4)`; Pas = `A+C+rnd(3)`; Tec = `A+C+rnd(7)`; Des = `A+rnd(4)`; Vel = `A+B+rnd(4)` |
| **Zagueiro** | Des = `round(Fx0.9)+rnd(2)`; Gol = `1+rnd(7)`; Vel = `A+B+rnd(4)`; Tec = `A+B+rnd(7)`; Pas = `A+B+rnd(3)`; Fin = `B+rnd(6)`; Arm = `A+rnd(5)` |
| **Volante** (ex=0) | Des = `round(Fx0.7)+rnd(6)`; Fin = `A+rnd(4)`; Pas = `A+rnd(3)`; Tec = `A+rnd(7)`; Arm = `A+rnd(5)`; Vel = `A+B+rnd(6)` |
| **Meia armador** (ex=1) | Arm = `F+rnd(2)`; Fin = `A+C+rnd(4)`; Pas = `A+B+rnd(3)`; Tec = `A+C+rnd(7)`; Des = `A+rnd(4)`; Vel = `A+C+rnd(4)` |
| **Atacante** | Fin = `round(Fx0.8)+rnd(2)`; Gol = `1+rnd(6)`; Vel = `A+C+rnd(4)`; Tec = `A+C+rnd(7)`; Pas = `A+B+rnd(3)`; Des = `B+rnd(6)`; Arm = `B+A+rnd(5)` |

Bônus por característica (aplica se **qualquer** das duas casar - logo, característica repetida
não dobra): Armação -> Arm e Pas `+B+rnd(5)`; Cabeceio -> Fin `+2+rnd(3)`; Cruzamento -> Pas `+2+rnd(3)`;
Desarme -> Des `+B+rnd(3)`; Drible -> Tec `+B+rnd(3)`; Finalização -> Fin `+B+rnd(3)`;
Marcação -> Des `+B+rnd(5)`; Passe -> Pas `+B+rnd(2)`; Resistência -> Des `+3+rnd(3)`;
Velocidade -> Vel `+A+rnd(3)`. (Atacantes recebem **A** em vez de B em Armação e Passe.)

**Bônus de goleiro** CONFIRMADO - a lista acima é só de linha. O goleiro tem bônus próprio, e nele
**c1 e c2 são testadas separadamente** (não vale o "qualquer das duas"): c1 em {Colocação, Saída
Gol} -> Tec `+2+rnd(5)`; c2 em {Colocação, Saída Gol} -> Tec `+rnd(2)`; c1 = Reflexo -> Vel
`+2+rnd(5)`; c2 = Reflexo -> Vel `+rnd(2)`; c1 = Defesa Penalty -> Gol `+1+rnd(3)`;
c2 = Defesa Penalty -> Gol `+rnd(2)`. Um goleiro com Colocação e Saída Gol soma os dois termos de
Tec.

**O gerador lê estilo e características como estão gravados no momento da chamada** CONFIRMADO.
As linhas de lateral e meia escolhem a fórmula pelo `ex` da 4.3 já guardado no jogador, e os bônus
leem c1 e c2 já guardadas. Quem chama o gerador antes de gravar estilo e características (o
complemento sintético da 4.12) recebe as fórmulas do estilo 0 e o bônus do índice 0 - ver o passo 5
dos jogadores avulsos na 4.12 e o item 65 de OPEN-QUESTIONS.

**Consequência de design a preservar:** só o **atributo primário** (Gol para goleiros, Des para
zagueiros/laterais defensivos/volantes, Arm para armadores, Fin para atacantes) deriva da força do
próprio jogador. Todos os secundários derivam da **qualidade do clube** - logo são quase
independentes da qualidade individual na criação do mundo.

## 4.3 "Estilo" derivado (`ex`) - usado para elegibilidade de slot

Calculado uma vez a partir de posição + características. GOL e ZAG -> 0. As cadeias exatas, com a
ordem dos testes e qual das duas características (c1, c2) cada teste lê, são CONFIRMADO:

- **LAT**: c1 em {Velocidade, Cruzamento} -> 1; c1 em {Desarme, Marcação} -> 0;
  c2 = Velocidade -> 1; c2 em {Desarme, Marcação} -> 0;
  c1 em {Drible, Finalização, Passe, Armação} -> 1; **senão 0** (defensivo).
  Dois defeitos de alcance nessa cadeia: **Cruzamento como segunda característica não é testado**
  (o teste que deveria ler c2 = Cruzamento relê c1), e a última cláusula lê **só c1** - um lateral
  com Drible/Finalização/Passe/Armação apenas na segunda característica cai no padrão 0.
- **MEI**: c1 em {Passe, Finalização, Drible, Armação} -> 1; c1 em {Desarme, Marcação} -> 0;
  os mesmos dois testes sobre c2; **padrão 1** (ofensivo).
- **ATA**: **só c1 é lida**: c1 em {Desarme, Marcação} -> 0;
  c1 em {Drible, Velocidade, Cruzamento} -> **2 (ponta)**; senão 1. Um atacante com essas
  características apenas em c2 é centroavante.

## 4.4 Força inicial na criação do mundo

```
clube de país com liga ativa: div1 -> base=20, faixa=7 ; div2 -> 15,3 ; div3 -> 5,1 ; outra -> 1,1
seleções E clubes de país sem liga ativa (por reputação): 5->22,7, 4->15,4, 3->5,1, 2->5,1, 1->5,1, 0->1,1
nível mapeado: <=15 -> o próprio ; 16->17 17->18 18->19 19->21 20->25 21->26 ... 25->30
força = nívelMapeado + base + rnd(3)
titular -> +8 + rnd(2)
estrela/top -> +9 + rnd(3)
base (júnior) -> -23 (se ficar < 5 -> 10)
escala por país (nível do país do CLUBE):
   nívelPaís <= 13: nívelClube <=5 -> x0.40 ; <10 -> x0.65 ; senão x0.75
   senão, se nívelClube < 10: <3 -> x0.50 ; <5 -> x0.60 ; senão x0.70
teto 100 ; contrato = 210 + rnd(30) dias
```
**Não existe elenco sintético de clube** CONFIRMADO. O parágrafo que aqui descrevia um "elenco
inicial" de 3 GOL, 4 LAT, 4 ZAG, 5 MEI e 4 ATA com `força = nívelMapeado - 5 + rnd(8)`, talento
`es = 7 + rnd(4)`, idade `18 + rnd(12)` e contrato de 180 dias é o **gerador de jogadores avulsos
da seleção**, e só a convocação da 4.12 o chama. Um clube que chega sem elenco não recebe elenco
nenhum. A descrição completa, sorteio a sorteio, está na 4.12 ("Jogadores avulsos"); fecha o item
12 de OPEN-QUESTIONS.

### 4.4.1 Tabela de países: continente e nível CONFIRMADO

A tabela de países é **embutida no jogo** (não vem de arquivo de dados) e carrega, por país, o
código de 3 letras, o **continente** e o **nível do país** - o `nívelPaís` que a escala por país da
4.4 consulta, e o continente que a 3.3 (deságio do Mundial de Clubes), a 4.5 (teto de país) e a 4.9
(nacionalidade europeia) consultam. Fecha os itens 14 e 26 de OPEN-QUESTIONS.

Continentes: `0 = Europa, 1 = América do Sul, 2 = África, 3 = Ásia, 4 = Concacaf (América do
Norte/Central e Caribe), 5 = Oceania` - os nomes são estes, na tabela de rótulos do próprio jogo.
Três entradas reservadas (135, 204, 207) têm continente `-1` e não pertencem a confederação
nenhuma. O "continente 0" da regra do Mundial de Clubes da 3.3 é, portanto, **a Europa**.

Níveis vão de 11 a 20. **Sete** países estão no nível 20: ALE, ARG, BRA, ESP, FRA, ING, ITA - e não
só os cinco grandes do salário da 4.8; a derivação por dados do item 14 acertava a forma da tabela,
mas o topo real tem sete. No nível 19: BEL, CRO, HOL, MEX, POR, URU.

| Indice | Sigla | Cont | Nivel | Indice | Sigla | Cont | Nivel | Indice | Sigla | Cont | Nivel | Indice | Sigla | Cont | Nivel |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 0 | AFG | 3 | 14 | 56 | DOM | 4 | 13 | 112 | LAO | 3 | 13 | 168 | SVG | 4 | 13 |
| 1 | AFS | 2 | 16 | 57 | EGI | 2 | 17 | 113 | LES | 2 | 13 | 169 | SEN | 2 | 17 |
| 2 | ALB | 0 | 15 | 58 | ELS | 4 | 16 | 114 | LET | 0 | 15 | 170 | SLE | 2 | 14 |
| 3 | ALE | 0 | 20 | 59 | EMI | 3 | 17 | 115 | LBN | 3 | 15 | 171 | SER | 0 | 17 |
| 4 | AND | 0 | 13 | 60 | EQU | 1 | 16 | 116 | LIB | 2 | 15 | 172 | SEY | 2 | 12 |
| 5 | AGO | 2 | 15 | 61 | ERI | 2 | 12 | 117 | LRI | 2 | 13 | 173 | SIN | 3 | 13 |
| 6 | AIA | 4 | 12 | 62 | ESC | 0 | 16 | 118 | LIE | 0 | 13 | 174 | SIR | 3 | 16 |
| 7 | ATG | 4 | 13 | 63 | ELQ | 0 | 17 | 119 | LIT | 0 | 15 | 175 | SOM | 2 | 12 |
| 8 | CUR | 4 | 13 | 64 | ESV | 0 | 16 | 120 | LUX | 0 | 13 | 176 | SRI | 3 | 12 |
| 9 | ARS | 3 | 17 | 65 | ESP | 0 | 20 | 121 | MAC | 3 | 13 | 177 | ESS | 2 | 13 |
| 10 | ALG | 2 | 18 | 66 | EST | 0 | 15 | 122 | MCD | 0 | 14 | 178 | SUD | 2 | 13 |
| 11 | ARG | 1 | 20 | 67 | ETI | 2 | 13 | 123 | MAD | 2 | 13 | 179 | SUE | 0 | 18 |
| 12 | ARM | 0 | 14 | 68 | EUA | 4 | 18 | 124 | MAL | 3 | 13 | 180 | SUI | 0 | 18 |
| 13 | ARU | 4 | 12 | 69 | FIJ | 5 | 12 | 125 | MWI | 2 | 14 | 181 | SUR | 4 | 13 |
| 14 | AUS | 3 | 17 | 70 | FIN | 0 | 16 | 126 | MLD | 3 | 13 | 182 | TAD | 3 | 14 |
| 15 | AUT | 0 | 17 | 71 | FIL | 3 | 14 | 127 | MLI | 2 | 16 | 183 | TAI | 3 | 14 |
| 16 | AZE | 0 | 14 | 72 | FRA | 0 | 20 | 128 | MTA | 0 | 13 | 184 | TTI | 5 | 14 |
| 17 | BAH | 4 | 13 | 73 | GAB | 2 | 15 | 129 | MAR | 2 | 17 | 185 | TAW | 3 | 13 |
| 18 | BHR | 3 | 14 | 74 | GAM | 2 | 13 | 130 | MAU | 2 | 13 | 186 | TAN | 2 | 13 |
| 19 | BAN | 3 | 13 | 75 | GAN | 2 | 17 | 131 | MEX | 4 | 19 | 187 | TGO | 2 | 14 |
| 20 | BAR | 4 | 13 | 76 | GEO | 0 | 14 | 132 | MIA | 3 | 13 | 188 | TON | 5 | 12 |
| 21 | BEL | 0 | 19 | 77 | GRA | 4 | 13 | 133 | MOC | 2 | 14 | 189 | TRT | 4 | 15 |
| 22 | BLZ | 4 | 13 | 78 | GRE | 0 | 16 | 134 | MOL | 0 | 14 | 190 | TUN | 2 | 17 |
| 23 | BEN | 2 | 14 | 79 | GUA | 4 | 14 | 135 | MNC | -1 | 12 | 191 | TCM | 3 | 14 |
| 24 | BER | 4 | 13 | 80 | GUN | 4 | 13 | 136 | MGL | 3 | 13 | 192 | TUR | 0 | 18 |
| 25 | BIE | 0 | 15 | 81 | GUI | 2 | 16 | 137 | NAM | 2 | 14 | 193 | UCR | 0 | 17 |
| 26 | BOL | 1 | 16 | 82 | GNB | 2 | 14 | 138 | NEP | 3 | 13 | 194 | UGA | 2 | 15 |
| 27 | BOS | 0 | 16 | 83 | GNE | 2 | 13 | 139 | NIC | 4 | 14 | 195 | URU | 1 | 19 |
| 28 | BOT | 2 | 13 | 84 | HAI | 4 | 14 | 140 | NIR | 2 | 14 | 196 | UZB | 3 | 15 |
| 29 | BRA | 1 | 20 | 85 | HOL | 0 | 19 | 141 | NIG | 2 | 18 | 197 | VAN | 5 | 13 |
| 30 | BRU | 3 | 12 | 86 | HON | 4 | 16 | 142 | NOR | 0 | 17 | 198 | VEN | 1 | 16 |
| 31 | BUL | 0 | 16 | 87 | HKG | 3 | 14 | 143 | NOZ | 5 | 16 | 199 | VIE | 3 | 15 |
| 32 | BKF | 2 | 15 | 88 | HUN | 0 | 17 | 144 | OMA | 3 | 15 | 200 | ZAM | 2 | 15 |
| 33 | BUR | 2 | 13 | 89 | IEM | 3 | 13 | 145 | PGA | 0 | 17 | 201 | ZIM | 2 | 14 |
| 34 | BUT | 3 | 12 | 90 | ICA | 4 | 12 | 146 | PAL | 3 | 15 | 202 | ICM | 2 | 12 |
| 35 | CAV | 2 | 15 | 91 | ICO | 5 | 12 | 147 | PAN | 4 | 16 | 203 | MIC | 5 | 13 |
| 36 | CAM | 2 | 17 | 92 | IFA | 0 | 14 | 148 | PNG | 5 | 13 | 204 | IMA | -1 | 12 |
| 37 | CMJ | 3 | 13 | 93 | ISA | 5 | 14 | 149 | PAQ | 3 | 13 | 205 | IMR | 2 | 12 |
| 38 | CAN | 4 | 15 | 94 | IVB | 4 | 12 | 150 | PAR | 1 | 17 | 206 | NAU | 5 | 13 |
| 39 | CAT | 3 | 16 | 95 | IND | 3 | 14 | 151 | PER | 1 | 17 | 207 | PLU | -1 | 12 |
| 40 | CAZ | 0 | 14 | 96 | IDO | 3 | 14 | 152 | POL | 0 | 18 | 208 | KIR | 5 | 13 |
| 41 | CHA | 2 | 12 | 97 | ING | 0 | 20 | 153 | PRI | 4 | 13 | 209 | SUS | 2 | 13 |
| 42 | CHI | 1 | 18 | 98 | IRA | 3 | 18 | 154 | POR | 0 | 19 | 210 | TUV | 5 | 13 |
| 43 | CHN | 3 | 17 | 99 | IRQ | 3 | 16 | 155 | QUI | 3 | 15 | 211 | IVA | 4 | 12 |
| 44 | CPR | 0 | 15 | 100 | IRL | 0 | 16 | 156 | RCA | 2 | 13 | 212 | MST | 4 | 13 |
| 45 | TML | 3 | 12 | 101 | IRN | 0 | 17 | 157 | RDG | 2 | 16 | 213 | ITC | 4 | 12 |
| 46 | COL | 1 | 18 | 102 | ISL | 0 | 16 | 158 | RDO | 4 | 13 | 214 | SME | 5 | 12 |
| 47 | CNG | 2 | 16 | 103 | ISR | 0 | 12 | 159 | RTC | 0 | 17 | 215 | NCA | 5 | 13 |
| 48 | CRN | 3 | 14 | 104 | ITA | 0 | 20 | 160 | ROM | 0 | 17 | 216 | GIB | 0 | 13 |
| 49 | CRS | 3 | 17 | 105 | MON | 0 | 16 | 161 | RUA | 2 | 13 | 217 | GDA | 4 | 13 |
| 50 | COM | 2 | 17 | 106 | JAM | 4 | 16 | 162 | RUS | 0 | 18 | 218 | GMA | 3 | 12 |
| 51 | CSR | 4 | 17 | 107 | JAP | 3 | 18 | 163 | SAM | 5 | 12 | 219 | MTI | 4 | 13 |
| 52 | CRO | 0 | 19 | 108 | JOR | 3 | 15 | 164 | SAN | 0 | 12 | 220 | GFR | 4 | 13 |
| 53 | CUB | 4 | 14 | 109 | QUE | 2 | 14 | 165 | STL | 4 | 13 | 221 | BON | 4 | 11 |
| 54 | DIN | 0 | 18 | 110 | KOS | 0 | 14 | 166 | SCN | 4 | 14 | 222 | SMF | 4 | 11 |
| 55 | DJI | 2 | 12 | 111 | KUW | 3 | 14 | 167 | STP | 2 | 12 | 223 | SMH | 4 | 11 |

**A lista dos cinco grandes é uma constante à parte** e vale `{3, 65, 72, 97, 104}` = {ALE, ESP,
FRA, ING, ITA} - o índice da Inglaterra é **97**, como o item 21 de OPEN-QUESTIONS inferiu. É essa
lista que a 4.8 (salário) e o teto de país da 4.5 consultam. Não confundir com a lista de limiar de
liga da 1.9, que troca a Espanha pela Argentina.

### 4.4.2 Sorteio de características por posição CONFIRMADO

Onde a spec diz "características sorteadas por posição" (juniores da 4.6, jogadores avulsos da
4.12 e as telas de edição manual de jogador), o sorteio é **um único `rnd(n)` sobre uma tabela de
pares (c1, c2) da posição**, com n = número de linhas da tabela. As duas características **não**
são sorteadas separadamente: saem juntas de uma linha, na ordem em que a linha as lista (a
primeira é c1, a segunda é c2). Nenhuma linha repete a mesma característica nas duas casas; uma
linha repetida na tabela é a forma de dar peso maior àquele par. Índices como na FORMAT-SPEC
(0 Colocação, 1 Defesa Penalty, 2 Reflexo, 3 Saída Gol, 4 Armação, 5 Cabeceio, 6 Cruzamento,
7 Desarme, 8 Drible, 9 Finalização, 10 Marcação, 11 Passe, 12 Resistência, 13 Velocidade).

| Posição | n | Linhas (c1, c2), na ordem da tabela |
|---|---|---|
| GOL | 6 | (0,3) (0,1) (2,0) (1,2) (3,1) (0,2) |
| LAT | 7 | (6,10) (6,13) (10,11) (10,13) (10,6) (10,9) (6,11) |
| ZAG | 12 | (7,10) (7,12) (7,5) (10,13) (7,13) (7,10) (7,5) (7,13) (7,12) (7,9) (7,10) (5,12) |
| MEI | 19 | (4,11) (4,9) (9,11) (11,9) (4,8) (4,13) (7,10) (7,11) (7,5) (7,13) (10,13) (10,11) (9,4) (10,12) (4,11) (8,11) (7,9) (11,13) (7,11) |
| ATA | 12 | (9,5) (13,9) (9,5) (8,9) (9,13) (9,5) (9,8) (5,13) (8,11) (9,11) (9,12) (13,8) |

Lido junto com a 4.3, o sorteio fixa a proporção de estilos dos jogadores gerados: um lateral
sai ofensivo (`ex = 1`) em 3 das 7 linhas (as de c1 = Cruzamento) e defensivo nas outras 4; um
meia sai volante (`ex = 0`) em 9 das 19 linhas (as de c1 em {Desarme, Marcação}) e armador nas
outras 10; um atacante sai ponta (`ex = 2`) em 4 das 12 linhas (c1 em {Drible, Velocidade}) e
centroavante nas outras 8. Nenhuma linha de ATA começa por Desarme ou Marcação, logo um atacante
gerado nunca tem `ex = 0`.

## 4.5 Evolução semanal (todo domingo) CONFIRMADO

O tique semanal faz, nesta ordem: (1) a cada 4 semanas, re-sorteia o foco de treino de **todo**
clube do mundo (não só os da IA - o time humano com treino manual permanente é um caminho à parte,
abaixo); (2) evolução de força de cada profissional + limpa a flag "jogou recentemente"; (3)
desenvolvimento de cada jogador da base; (4) avança o contador de 4 semanas.

**Os passos 1 e 4 só rodam com a opção "atributos individuais" ligada.** O contador de 4 semanas e o
re-sorteio do foco inteiro dependem dela: com a opção desligada, o foco nunca é sorteado e o
contador nunca avança (fica parado onde nasceu), e o passo 2 usa sempre a distribuição natural
(abaixo). Isso não muda o crescimento da força em si (a 4.5 principal roda sempre): só decide se os
7 atributos individuais evoluem em modo natural ou em modo foco. O motor sem essa opção nesta versão
não perde nada rodando sempre em modo natural.

**A flag "jogou recentemente" marca presença na súmula, não minutos jogados.** Ela é ligada no
momento em que um jogador é **relacionado para a partida** (colocado em qualquer um dos slots da
escalação, titular ou banco, em qualquer competição) - a mesma rotina que monta a súmula, chamada
tanto pela escalação automática quanto pela substituição manual. Um reserva relacionado que nunca
entra em campo ainda ganha o bônus de +0,04 na evolução da semana. A flag só é desligada de volta
quando o passo 2 a consome (limpa incondicionalmente após aplicar o bônus, esteja ele ligado ou
não), então ela vale "foi relacionado ao menos uma vez desde o último tique semanal".

### Crescimento (idade < 32)
Taxa semanal `r` pela tabela nível-do-clube x idade:

| Nível do clube | | | | |
|---|---|---|---|---|
| >= 19 | <20: **0,16** | <23: 0,12 | <29: 0,10 | senão 0,08 |
| 15-18 | <18: **0,12** | <21: 0,10 | <29: 0,08 | senão 0,06 |
| 11-14 | <18: **0,10** | <21: 0,08 | <29: 0,06 | senão 0,04 |
| <= 10 | <18: **0,08** | <21: 0,06 | <29: 0,04 | senão 0,02 |

Modificadores aditivos: **jogou recentemente +0,04** (o termo de minutagem);
força 30-40 -> -0,02, 41-50 -> -0,03, 51-70 -> -0,04, 71-100 -> -0,05;
se veio de base: `fd < 50` -> -0,05, `fd < 70` -> -0,02, depois `es >= 9` -> +0,07 senão `es >= 7` -> +0,05;
topMundial +0,02 senão estrela +0,01;
continente do clube: África/Ásia -0,02, Oceania -0,04, América do N/C (exceto México) -0,03,
América do Sul -0,02, Europa {ALE, FRA, ITA, ESP, ING} **+0,01**, {POR, HOL, BEL} -0,01, resto da Europa -0,02
(os ajustes negativos só valem quando `r > 0,06`).

Acumula `r`; quando o acumulador passa de 1,0 e força < 100 **e < teto**, força += 1. **O
acumulador nunca é zerado pela virada de temporada** - ele só perde 1,0 quando cruza o limiar e
credita o ponto de força; entre temporadas ele simplesmente continua de onde parou.

**Clube sem divisão (caminho de reputação da 1.9, e seleções).** A tabela `tetoTabela[divisão]` só
é lida se o clube pertence a uma liga; um clube fora de liga nenhuma - pelo caminho de reputação da
1.9, e isso inclui a seleção, que também nunca tem divisão - usa exatamente a linha "seleções" acima
(`rep5->100 rep4->100 rep3->70 rep2->40 rep1->30 senão 20`), e não uma tabela à parte. O mesmo vale
para o **piso do declínio**: um clube sem divisão mapeia reputação `>=4 -> piso 35 (como div1)`,
`==3 -> piso 25 (como div2)`, `<=2 -> piso 10 (como div3)` - a tabela de piso por divisão da seção
"Declínio" abaixo é lida com esse mapeamento de reputação em vez da divisão real.

**O teto** (mesma lógica para força e para cada atributo):
```
clube em liga: tetoTabela[divisão][reputação]
   div0: 30 30 30 30 30 30
   div1: 80 85 90 95 100 100
   div2: 50 60 60 65 80 80
   div3: 40 40 40 45 70 70
   div4: 25 30 30 30 50 60
   div5: 30 30 30 30 30 30
seleções: rep5->100 rep4->100 rep3->70 rep2->40 rep1->30 senão 20
tetoPaís: Europa ALE/FRA/ITA/ESP/ING->95, POR/HOL->90, BEL/RUS->80, resto->70
          A.Sul BRA/ARG->90, URU/COL/CHI->80, PER/PAR->70, resto->60
          África ARG/MAR/EGI->75, NGA/SEN/TUN->70, resto->60
          Ásia JAP/COR->75, IRA/ARA/EAU/CHN->70, resto->60
          A.N/C MEX->80, EUA->70, CRC->65, resto->55
          Oceania NZL->60, resto->45
teto = min(teto, tetoPaís)
bônus de talento (só se desenvolvimento de base >= 60):
   es=7 -> +5+rnd(5), es=8 -> +15+rnd(5), es=9 -> +25+rnd(5), es=10 -> +30+rnd(5) ; teto final 100
```
**O teto é reavaliado toda semana** - mudar para um clube maior destrava crescimento imediatamente.
Esta é a alavanca central de progressão.

**"Desenvolvimento de base >= 60" lê o campo `fd` guardado no próprio jogador**, o mesmo contador
0-100 que a seção 4.6 desenvolve semana a semana enquanto ele é júnior. O campo não é apagado na
promoção - segue morando no profissional, e é por isso que o bônus de teto (e o bônus de +0,05/+0,07
do crescimento, abaixo) continuam lendo `fd` depois que o jogador já virou profissional, sem
precisar de nenhuma flag "veio de base" à parte: o teste é literalmente `fd > 0` (crescimento) ou
`fd >= 60` (teto). **INFERIDO:** o formato de arquivo (`FORMAT-SPEC.md`) não lista `fd` entre os
campos do `.ban`, e nada na criação do mundo o inicializa para um profissional importado - só a
rotina que calcula o `fd` inicial, chamada quando um júnior é gerado em tempo de execução (4.6). Um
profissional que veio de arquivo (nunca foi júnior no motor) deve, portanto, entrar com `fd = 0`, e
os dois bônus acima ficam inertes para ele a vida inteira, exatamente como para um jogador cujo
`fd` já foi zerado por algum motivo. Ver o item 100 de `OPEN-QUESTIONS.md`.

### Declínio (idade >= 32)
```
d = idade - 31 ; se nível do clube >= 20 -> d -= 2
taxa = 0,7,d (força 1-50) | 1,0,d (51-70) | 1,2,d (71-100)
semanal = taxa / 50
piso = 35 (div1) | 25 (div2) | 10 (div3) | 1 (demais)
acumula; se acumulador > 1,0 e força > piso -> força -= 1
```
Ou seja: **um jogador de 32-33 anos num clube de nível >= 20 não declina.** Um de 38 com força 80 num
clube modesto perde ~8-9 pontos por temporada. Além disso, **toda lesão a partir dos 35 custa -5 de força**.

### Distribuição para os 7 atributos CONFIRMADO
**As sete habilidades têm acumuladores próprios**, um double por atributo (não o mesmo acumulador da
força), com o mesmo teto e a mesma condição de crédito (acumulador > 1,0 e valor atual abaixo do
teto da semana) descritos acima para a força.

Modo natural (padrão): a taxa vai 100% para um atributo primário, 40% para um secundário e 30% para
um terciário, por posição/estilo (GOL: Gol, LAT def: Des/Arm/Fin, LAT of: Pas/Fin/Des,
ZAG: Des/Pas/Arm, VOL: Des/Arm/Fin, MEI: Arm/Fin/Des, ATA: Fin/Arm/Des).
**Gol e Desarme recebem x1,2** no momento de creditar ao acumulador (não no momento de aplicar o
ponto), e só esses dois - o bônus não se aplica a secundário nem a terciário de nenhuma posição
além do próprio Gol/Des quando algum deles cai num desses papéis.

**Modo foco de treino (1 semana em 4, ou permanente para clube humano com treino manual): defeito a
reproduzir.** O foco é sorteado **por clube**, uma vez a cada quatro semanas, para cada uma das 5
categorias de posição (GOL, LAT, ZAG, MEI, ATA): um valor `1 + rnd(3)`, isto é, **1, 2 ou 3**. Na
semana de foco, esse valor é usado **diretamente como índice do vetor de sete atributos** (0 Gol,
1 Vel, 2 Tec, 3 Pas, 4 Des, 5 Arm, 6 Fin) - não como "1a/2a/3a posição no trio primário/secundário/
terciário" da tabela acima. Como o sorteio nunca produz 0, 4, 5 ou 6, **o foco de treino do original
só pode cair em Velocidade (1), Técnica (2) ou Passe (3), nunca no atributo primário real da
posição** (nunca Gol, Des, Arm ou Fin), com taxa dobrada só nesse atributo sorteado. Um goleiro em
semana de foco, por exemplo, nunca desenvolve Gol por essa via - só Vel, Tec ou Pas, conforme o que
saiu para a categoria GOL do clube. Isso é diferente do que a redação anterior desta seção supunha
("escolhido por posição" lido como "entre os três da tabela acima"); a reimplementação `CLASSIC`
deve reproduzir a leitura direta do índice 1-3, e uma correção para `MODERN` (mapear o valor
sorteado para a posição correta dentro do trio da posição) é um campo de `RuleSet`.

No **declínio**, só o atributo **primário** é decrementado - secundários acumulam mas nunca perdem ponto.

## 4.6 Base (juniores) CONFIRMADO

**O mundo não nasce com juniores gerados por sorteio nenhum.** O gerador em tempo de execução
descrito nesta seção só roda durante a partida (reposição de vaga na base e regeneração de um
descartado, abaixo); não há chamada dele na criação do mundo. Todo júnior que um clube tem ao
nascer o mundo vem do próprio arquivo `.ban` - campo `m`/`getJuniores` do FORMAT-SPEC, que a maioria
dos arquivos distribuídos traz **vazio** e alguns trazem com cerca de 15 jogadores de 15-20 anos.
Fecha a primeira pergunta do item 10 do design da v0.3: um clube importado sem juniores no arquivo
simplesmente começa sem base, exatamente como um clube sem elenco profissional simplesmente começa
sem elenco (4.4). **INFERIDO:** como o campo `fd` não existe no formato de arquivo (só `hash`/`es`
existe), um júnior importado do arquivo deve nascer com `fd = 0` e desenvolvê-lo inteiramente pelo
passo semanal abaixo, sem o salto de "`fd` inicial" que só o gerador em tempo de execução aplica.

**O gerador em tempo de execução é o mesmo em toda situação: não existe um gerador de júnior e
outro de reposição para a 4.11 - é a mesma rotina nos dois papéis.** Ele roda em três gatilhos,
sempre associado a um clube real (nunca à seleção, que não tem caminho da 4.4 nenhum):

1. **Refil do teto de 20.** Sempre que uma promoção (abaixo) tira um júnior da base e o total de
   juniores do clube cai abaixo de 20, entra **um** júnior novo, de posição sorteada (rolagem
   <=10 GOL, <=30 LAT, <=50 ZAG, <=80 MEI, senão ATA), com todos os sorteios abaixo.
2. **Descarte por talento insuficiente.** Quando um júnior de 20 anos não passa no teste de
   promoção e o clube é da IA, ele é regenerado **no mesmo objeto** - os campos são todos
   redesenhados pelos sorteios abaixo (idade volta a `16+rnd(4)`, talento é sorteado de novo, etc.),
   sem passar por um objeto novo nem por uma fila; na prática é indistinguível de "descartado e
   substituído por um totalmente novo".
3. **Reposição de elenco profissional na virada** (4.11) - o mesmo gerador cria o substituto, que
   nesse caso é imediatamente promovido no mesmo passo; ver 4.11 abaixo.

**Cada júnior gerado, sorteio a sorteio:** nacionalidade = a do clube; posição (sorteada ou dada
pelo chamador); talento `es` pela tabela abaixo; se a rolagem de talento saiu exatamente 1 (o mesmo
1% que também dá estrela), **e o clube é de nível >= 18**, há 1/6 de chance por país específico de
nascer estrangeiro: um clube brasileiro sorteia entre ARG, CHN, URU, PAR, HAI ou (1/6) qualquer país
do mundo; um clube dos cinco grandes europeus (ALE, POR, HOL, ITA - FRA aparece só como resultado
fixo do sexto ramo, nunca sorteado par com os outros quatro) sorteia entre esses quatro mais um
quinto país fixo, SIR (Síria, nível 16, fora da Europa) - uma lista que não bate com "clube
europeu grande" e é reproduzida como está, sem tentar corrigi-la. Um júnior estrangeiro por esse
caminho recebe **sempre `es = 7+rnd(4)`**, sobrescrevendo o talento já sorteado. Nome pelo gerador
da 4.12 (país do júnior). Idade `16+rnd(4)`; lado `rnd(2)`; status 0 (reserva); características
sorteadas da tabela da posição (4.4.2); depois valor de mercado e salário recalculados.

Desenvolvimento semanal `fd` (0-100):
```
base: idade <=17 -> 0,500, 18 -> 0,375, 19 -> 0,350, 20 -> 0,125
bônus de talento: es <=3 -> +0,03, <=6 -> +0,04, <=8 -> +0,07, =9 -> +0,10, =10 -> +0,11
```
`fd` inicial = `{16->15, 17->35, 18->55, 19->70, 20->75}[idade] + 1 + rnd(5) + es`, limitado a [1, 95].

**Talento `es` (1-10)** - é o campo `hash` do `.ban`! Distribuição por qualidade do clube:

| rolagem 1-100 | clube nível >=19 ou rep >3 | nível >=15 | demais |
|---|---|---|---|
| 1 | es=1 (2%) | es=1 (2%) | es=1 (4%) |
| ... | 2:3% 4:5% 5:15% 6:35% 7:20% 8:10% 9:8% 10:2% | 2:3% 4:5% 5:20% 6:35% 7:25% 8:5% 9:3% 10:2% | 2:4% 3:7% 4:10% 5:25% 6:25% 7:20% 8:3% 9:1% 10:1% |

Rolagem exatamente 1 -> também vira **estrela** (1%). Idade `16+rnd(4)`; posição por rolagem
(<=10 GOL, <=30 LAT, <=50 ZAG, <=80 MEI, senão ATA); características sorteadas de tabelas por posição.
Júnior estrangeiro (possível em clubes nível >=18) **sempre recebe es = 7+rnd(4)**.

O **talento exibido ao treinador é uma estimativa ruidosa**: rolagem <=15 -> mostra `es` exato;
<=60 -> `es - 1`; senão `es + 1` (limitado a 1-10), renderizado como `es/2` estrelas.

**Promoção** (avaliada na virada de temporada, só a partir dos 20 anos):
```
mínimoEstrelas = {1,4,5,6,6,6}[reputação do clube]
cotaPorPosição = {GOL:3, LAT:5, ZAG:5, MEI:8, ATA:6}
promove se es >= mínimo E contagem na posição < cota E elenco < 32
senão, se o clube é da IA -> o júnior é DESCARTADO e regenerado do zero
```
**Força na promoção:**
```
bônus = liga ? {div0:5, div1:22, div2:17, div3:14, >=div4:7} : {rep5:20, rep4:15, rep3:12, rep2:7, senão 5}
se continente do clube == Europa -> bônus += 5
bônus += rnd(5)
força = es + round(bônus x min(fd,100)/100)
se es >= 9 -> força += rnd(10)
```
Um júnior de talento 10 num grande clube europeu é promovido com ~36 de força e cresce rápido;
um de talento 3 num clube pequeno é promovido com ~10.

**Onde o promovido entra no elenco, e se é titular.** A promoção tira o jogador da lista de
juniores do clube e o acrescenta ao **fim** da lista de profissionais - nunca inserido no meio,
nunca ordenado por força; é por isso que a pool da convocação (4.12) lê "promovidos da base entram
no fim, na ordem da promoção". O `status` (titular por atributo, ver 5.6) **não é tocado pela
promoção** - o júnior chega ao elenco profissional com o mesmo `status = 0` (reserva) com que
nasceu como júnior; a promoção nunca marca ninguém titular por atributo. O contrato do promovido
é fixado em 180 dias (4.7), a temporada de chegada é a atual, e o acumulador de crescimento da
força (o `r` da seção "Crescimento" acima) começa zerado.

**Estrela na promoção.** Um júnior que já tinha a marca de estrela (o 1% da geração) só a mantém
com **1/3 de chance** na promoção - nos outros 2/3 ela é desligada. Um júnior sem estrela tem
**1/200 de chance** de ganhá-la na promoção (o número que a 4.10 já cita). O caso defensivo de um
júnior já `topMundial` ao ser promovido (na prática não deveria acontecer, mas o código o cobre)
liga a estrela e desliga o `topMundial`.

## 4.7 Contratos

| Evento | Prazo |
|---|---|
| Criação do mundo | 210 + rnd(30) dias |
| Promoção da base / elenco inicial | 180 dias |
| Transferência definitiva | 180 dias |
| Empréstimo | 365 dias |
| Humano assume um clube | todo o elenco vai a 180 dias |
| Renovação manual | +180 / +365 / +730 / +1095, **somados ao fim atual** |
| Renovação automática (opção) | 180 dias a partir de hoje |

**Negociação de renovação:**
```
descontoPct = {1, 3, 5, 12}[índice do prazo] // usado quando falta pouco contrato
prêmioPct = {10, 12, 15, 5}[índice do prazo] // usado nos demais casos
exigência = (diasRestantes < 60) ? salário - salárioxdesconto/100
                                 : salário + salárioxprêmio/100
aceita se salárioOferecido >= exigência ; e o salário precisa ser < 25% do valor de mercado
```
Inversão importante: com **mais de 60 dias** restando o jogador exige **aumento** (até +15%);
nos **últimos 60 dias** ele aceita **redução** (até -12% no contrato de 3 anos).

**Vencimento:** o contrato simplesmente expira; o jogador **não é liberado automaticamente**.
Um jogador com contrato vencido **não pode ser escalado por clube humano** (clubes da IA não sofrem
essa trava). Com a opção de renovação automática ligada, ele ganha 180 dias em silêncio.

## 4.8 Salário

```
base = 350
clube em {ALE, FRA, ITA, ING, ESP}: div1->750 div2->550 div3->500 div4/5->450 senão 350
demais países: div1->600 div2->500 div3->450 div4/5->400 senão 350
nível do clube > 20 -> base += 50
ajuste por posição: GOL -70, LAT -30, ZAG -40, ATA -50, MEI 0
base = round(0,5 x base)
núcleo = força x 2 x base
salário = (idade < 32) ? núcleo + bônusEstrela
                       : núcleo - (idade-32)x300 + bônusEstrela
bônusEstrela = (estrela ou topMundial) ? força x 250 : 0
piso 500 ; topMundial -> x1,4 ; júnior -> x0,1
opção "salário mensal" ligada -> x4
```
Ou seja, a fórmula produz um valor **semanal** e o quadruplica no modo mensal (que é o padrão).

## 4.9 Valor de mercado

```
quadrático = (força x 2)^2
baseNível = nívelClube >=21 -> 750, >=20 -> 600, >=18 -> 500, >=12 -> 400, senão 366
estrela: nível >=22 e nacionalidade europeia -> x3 ; nível >=21 e europeia -> x2 ; senão x1,7
topMundial -> x1,6 ; atacante -> x1,3 ; titular -> x1,2
termo de idade (idade mínima 16):
   <20 -> (32-idade)x27, <=25 -> (32-idade)x22, <32 -> (32-idade)x15
   <34 -> (34-idade)x10, >=34 -> -(idade-34)x50
baseNível += termoIdade ; se <= 0 -> 60
valor = quadrático x baseNível
desconto por temporada de chegada:
   júnior -> x0,03 x es
   chegou nesta temp. -> x0,18 (limitado ao preço pedido registrado, se houver)
   temporada passada -> x0,35
   duas atrás -> x0,65
   mais antigo -> sem desconto
júnior com es = 10 -> x1,5
```
**O valor cresce quadraticamente com a força** - é de longe o termo dominante.
Aferição: força 50, 24 anos, clube nível 20 -> `100^2 x (600+176) = 7,76 M`.

## 4.10 Estrela e topMundial CONFIRMADO

`topMundial` **implica** `estrela` - mas **só na entrada**: ao criar o mundo e ao ler o `.ban`, ligar
`estrela` num jogador que já tem `topMundial` força `estrela` ligada. A **promoção a `topMundial`
durante o jogo não liga `estrela`** (item 18 da 3.15), o que muda o passo 8 da 3.14 para esse
jogador.

| Sistema | estrela | topMundial |
|---|---|---|
| Força inicial | +9+rnd(3) | idem |
| Crescimento semanal | +0,01 | **+0,02** |
| Valor de mercado | x1,7 (x2 ou x3 na Europa) | x1,6 |
| Salário | bônus `forçax250` | + x1,4 |
| Nota da partida | +0,4 | +0,6 |
| Aposentadoria | idade efetiva -1 | idade efetiva -3 |
| Virar treinador ao aposentar | 1 em 5 | **1 em 2** |

**Como se ganha estrela por desempenho, exatamente.** O prêmio roda **por competição**, não uma vez
para o mundo inteiro: cada entrada da lista de fases/divisões de uma competição (a primeira entrada é
o topo do formato; para uma competição sem divisões essa lista tem só uma entrada, a competição
inteira) é avaliada assim que essa entrada termina. O motor acumula, por jogador que recebeu ao menos
uma nota ali (3.14), a soma e a contagem das notas, e ordena todos por **média de nota decrescente**
(desempate por número de partidas jogadas decrescente - a ordenação é a mesma usada para montar uma
seleção do campeonato por posição, achado colateral sem efeito de jogo conhecido). Em seguida
percorre essa lista ordenada e escolhe o **primeiro** cuja **contagem de partidas** bate um piso
mínimo, recalculado a cada vez a partir do número de times e do avanço do calendário da própria
competição-entrada. **O "limiar da competição" é um piso de partidas jogadas, não um piso de nota**:
um jogador de média modesta pode levar o prêmio se for o melhor entre os que já jogaram o suficiente,
e não existe nota mínima abaixo da qual ninguém ganha. Só esse único jogador - o primeiro da lista
ordenada que bate o piso - é elegível pela entrada; se nem o melhor da lista bate o piso ainda
(cedo na temporada, antes de metade do calendário, o piso costuma valer zero partidas, então isso só
falha se ninguém jogou nada), ninguém ganha a estrela por essa entrada. A estrela em si só é
concedida quando a entrada avaliada é o **topo** do formato da competição (a primeira da lista) -
entradas de divisões inferiores da mesma competição são avaliadas (e alimentam a seleção do
campeonato), mas não dão estrela. A fórmula exata do piso de partidas fica registrada no item 104 de
`OPEN-QUESTIONS.md`, porque a leitura confirmou a **natureza** do teste (contagem de partidas, não
nota) mas não isolou com confiança total os três campos que compõem o cálculo.

**Como se ganha topMundial, exatamente.** O mesmo jogador que ganha a estrela do topo do formato de
uma competição (acima) é quem tem seu contador de "temporada de elite" incrementado nessa mesma
avaliação; o eventual jogador de elite de uma divisão inferior da mesma competição não incrementa
contador nenhum, ainda que seja calculado. Como o incremento roda **por competição**, um jogador que
é o melhor do topo em mais de uma competição na mesma temporada (por exemplo liga nacional e copa
nacional do mesmo país) tem o contador incrementado **uma vez por competição**, não uma vez por
temporada. Vira topMundial quem, no momento em que o contador cresce, ainda não é topMundial, tem
clube, idade < 35, e cai numa de seis combinações de país e contador - **nenhum outro país produz
topMundial por esta via, por maior que o contador cresça**: clube na Espanha ou na Inglaterra com
contador >= 2; na Itália, na França ou em Portugal com contador >= 3; no Brasil com contador >= 4 (o
único caso fora da Europa). Perde a flag automaticamente depois dos 34 - teste de idade avaliado à
parte, no mesmo fechamento de temporada que a 4.11.

**Quando os dois rodam, em relação à virada.** O prêmio por desempenho (estrela e o incremento do
contador de topMundial) é avaliado **por competição, assim que a última entrada dela termina** - um
gatilho de fim-de-competição, não o fechamento de temporada da 4.11/5.5/1.5. Numa temporada em que
liga nacional, copa e outras competições terminam em datas diferentes, cada uma credita seu prêmio de
desempenho no próprio instante em que fecha; toda competição de uma temporada já fechou - e já
creditou os dois prêmios - antes de a sequência de virada (reputação, aposentadoria, reposição,
promoção) começar a rodar sobre essa mesma temporada. Se uma competição de seleções entra nessa
mesma lista de entradas avaliadas é a pergunta do item 95 de `OPEN-QUESTIONS.md`, fora do escopo desta
varredura de clube e jogador.

## 4.11 Aposentadoria e virada de temporada CONFIRMADO

Ordem da virada: (1) jogadores sem clube envelhecem e, passando de 35, são **reciclados** -
viram um novo jogador de `18+rnd(10)` anos com nome novo (piscina infinita de agentes livres);
(2) profissionais envelhecem, zeram registros e passam pelo teste de aposentadoria;
(3) juniores envelhecem e passam pelo teste de promoção; (4) valores e salários são recalculados.
Essa é a mesma sequência que o motor original executa numa única passada, por lista (todos os
sem-clube, depois todos os profissionais, depois todos os juniores) - nunca clube por clube. "Zeram registros" no passo 2 é leve: só ressincroniza o vínculo de clube em cache
(a mesma resincronização da 1.4) e limpa listas auxiliares de temporada; as estatísticas de
partida propriamente ditas vivem no log de eventos e não precisam de zeramento algum. Os passos 2 e
3 do original também limpam a marca "temporada de chegada" nova sempre que o nome do jogador estiver
vazio ou for o marcador de teste do editor - irrelevante para um mundo gerado pela seed.

**Reciclagem de agente livre (passo 1).** Só o campo idade avança normalmente até passar de 35;
nesse ponto o objeto é reaproveitado no lugar (não é removido e recriado): idade volta a
`18+rnd(10)`, nome novo pelo gerador da 4.12 (país do próprio jogador), e as estatísticas de
carreira acumuladas (gols, jogos) são zeradas. A piscina de agentes livres nunca é podada por
tamanho - só por essa reciclagem individual quando um deles envelhece demais -, e ela é a mesma
piscina de avulsos e de reciclados da 4.12 (país, nome, idade e carreira são os únicos campos que
essa reciclagem toca; força, talento e atributos ficam como estavam).

**Teste de aposentadoria** (pulado para emprestados; só acima de 32):
```
idadeEfetiva = idade - (estrela ? 1 : 0) - (topMundial ? 3 : 0) - (goleiro ? 3 : 0)
rolagem 1..100:
 <32 -> nunca, 32 -> 1%, <=34 -> 10%, 35 -> 45%, 36 -> 70%, <=38 -> 85%
 39 -> 95%, 40 -> 97%, <=42 -> 98%, <=48 -> 99%, >48 -> sempre
```
**Assimetria crucial:** se o clube é da **IA**, a aposentadoria acontece. Se o clube é **humano**,
**nada acontece** - o treinador só recebe a notícia de que o jogador manifestou vontade de parar.
Elencos humanos nunca perdem jogador por aposentadoria contra a vontade do treinador.

Ao aposentar: sai do clube, entra no hall da fama se teve carreira relevante (limiares de gols por
temporada), o clube gera reposição se ficar abaixo de `{2,3,3,6,4}` por posição ou com menos de 16
jogadores, e há chance de **virar treinador** (1/25 a 1/125 conforme divisão; 1/5 se estrela; 1/2 se topMundial).

**Chance de virar treinador, exata por divisão:** `{div0: 1/120, div1: 1/25, div2: 1/50, div3: 1/60,
div4: 1/100, div5 (ou clube sem divisão): 1/125}`; a marca de estrela sobrescreve para `1/5`, e a de
topMundial (testada depois, então prevalece) para `1/2`. Só clubes de liga sorteiam essa
chance - um jogador de clube sem divisão nunca vira treinador ao aposentar. O sorteio roda **antes**
de o jogador sair do clube, então lê a divisão do clube que ele está deixando.

**Como a reposição é gerada: não existe um gerador de "jogador de reposição" à parte.** O motor
sorteia um júnior **pelo mesmo gerador da 4.6** (mesma tabela de talento, mesma idade `16+rnd(4)`,
mesmo sorteio de características), na posição que ficou carente, e o promove **no mesmo instante**
pela mesma rotina de promoção da 4.6 - só que esse júnior acabou de nascer, então `fd = 0`, e o
termo de bônus da força na promoção (`bônus x min(fd,100)/100`) some por inteiro: a reposição entra
com **força = talento (`es`)**, mais `rnd(10)` se `es >= 9`, sem nenhum dos bônus de divisão,
reputação, continente ou desenvolvimento que um júnior "de verdade" acumula amadurecendo na base.
Uma reposição é, portanto, tipicamente um jogador fraco (força de um dígito a pouco mais de dez) que
só cresce depois, pela evolução semanal normal. Ela entra **titular por atributo apenas com a mesma
chance de 1/200** que qualquer promoção tem (e 1/3 se por acaso já nascesse estrela, o que não
acontece aqui: a reposição não passa pela rolagem de estrela de 1% da geração, porque o teste do
gerador só faz o sorteio adicional de país estrangeiro quando a rolagem de talento sai 1, e mesmo
nesse caso a marca de estrela em si só é ligada pelo ramo de promoção, não pela geração). O teste
`{2,3,3,6,4}`/16 jogadores que decide **se** gerar é avaliado **uma vez por saída** (aposentadoria
ou outra remoção), sobre o elenco já sem o jogador que está saindo - não uma varredura única ao
fim da virada. Ver o item 103 de `OPEN-QUESTIONS.md`.

**Contrato na reposição:** 180 dias, como qualquer promoção da base (4.7). **Contrato dos
profissionais que ficam:** a virada de temporada **não renova nem mexe em contrato nenhum** dos
jogadores que continuam no clube - nenhum passo do envelhecimento, da aposentadoria ou da promoção
chama a rotina de contrato para um jogador que não está sendo criado ou promovido agora. Um clube da
IA carrega contratos vencidos indefinidamente entre temporadas sem nenhum efeito prático, porque
4.7 já diz que só o clube humano é barrado de escalar um jogador vencido.

## 4.12 Seleções: criação do time e convocação CONFIRMADO

**Não existe elenco de seleção em arquivo nenhum** - a pasta `selecoes/` da instalação só tem
imagens (escudos e camisas). A seleção é um objeto de time criado **sob demanda** quando uma
competição precisa dela, e o elenco é **convocado** dos jogadores que já existem no mundo.

**O time.** Criado com o nome e o nível do país (tabela da 4.4.1), cores de camisa embutidas na
mesma tabela de países, e **reputação derivada do nível do país**:
`>= 20 -> 5 ; 19 -> 4 ; 17-18 -> 3 ; 15-16 -> 2 ; senão 1`.
O objeto nasce **sem técnico e sem elenco** (os dois chegam na convocação), marcado como seleção,
sem estádio e sem arquivo de origem. É essa reputação que a escala de competição da
3.3 lê em jogo de seleção, e o teste da 3.3 é por **nacionalidade**, não por pertencer ao elenco:
um jogador cuja nacionalidade é o país do mandante é escalado pela reputação do mandante; senão,
se a nacionalidade é o país do visitante, pela reputação do visitante; senão fica sem escala. Como
o pool da convocação é filtrado pela nacionalidade, **todo convocado recebe a escala da própria
seleção**. A seleção nunca é dona de jogador: cada convocado continua sendo do seu clube, e os
avulsos não têm clube; por isso nenhum caminho da 4.4 roda com uma seleção no papel de clube (a
seleção não carrega jogadores de arquivo nem promove base).

**Quando acontece.** A convocação da IA roda quando uma competição de seleções precisa do time; a
do time humano roda pela tela de convocação. As duas chamam a mesma rotina, que começa
**esvaziando o elenco anterior** e, se o time não é humano e está sem técnico (ou se a competição
pedir a troca), escolhe o técnico (abaixo).

**A convocação** monta a lista assim:

1. **Complemento sintético**, antes de montar o pool. O jogo gera **20 jogadores avulsos** da
   nacionalidade se dois testes falharem: (a) **15 de linha e 2 goleiros** contando só a lista
   mundial de jogadores (os que vieram de arquivo ou subiram da base; a piscina de avulsos não
   conta, nem mesmo um avulso que já foi contratado por clube), e (b) **16 de linha e 2
   goleiros** contando a lista mundial mais a piscina de avulsos. O teste (a) é uma **marca
   guardada no país**, calculada na criação do mundo e recalculada só quando o humano abre a tela
   de convocação; o teste (b) é calculado na hora. A tela do humano aplica só o teste (b). A
   geração acontece a cada convocação em que os testes falham; como uma leva de 20 já satisfaz
   (b) sozinha (17 de linha e 3 goleiros), na prática cada país gera **uma leva só**. Os gerados
   entram na piscina de avulsos e **persistem**: a piscina nunca é podada, um avulso contratado
   por clube continua nela (e é enumerado por ela, não pela lista mundial), e o que passa dos 35
   anos é reciclado como diz a 4.11. Como cada um é gerado: "Jogadores avulsos", abaixo.
2. **Pool**: primeiro, na ordem da lista mundial de jogadores, os que têm a nacionalidade do país
   **e têm clube**; depois, na ordem de criação, todos os da piscina de avulsos com a
   nacionalidade, com ou sem clube. A lista mundial está na ordem em que os clubes foram
   carregados e, dentro de cada clube, na ordem do elenco no arquivo; promovidos da base entram
   no fim, na ordem da promoção; aposentados saem dela.
3. **Ordenação**: **força guardada** decrescente (a da 4.4/4.5, não a efetiva da 3.3: energia,
   slot, escala de competição e atributos individuais não entram); empate favorece o jogador
   **estrela** (a comum da 4.10; topMundial não é lido, exceto por implicar estrela na entrada);
   empate completo mantém a ordem do pool, porque a ordenação é **estável**. Ver item 67 de
   OPEN-QUESTIONS sobre essa ordem no motor.
4. **Cotas**, percorrendo o pool ordenado uma única vez; cada jogador entra se a cota da sua
   célula ainda tem vaga, senão é **pulado** (a vaga fica aberta e só o passo 5 pode preenchê-la).
   Células, com D = lado 0 (direita) e E = lado 1 (esquerda), estilo pela 4.3:
   `GOL 3 (sem lado) ; LAT 2D + 2E ; ZAG 2D + 2E ; MEI armador (ex = 1) 2D + 3E ;
   MEI volante (ex = 0) 2D + 1E ; ATA 2D + 2E`
   (3 + 4 + 4 + 5 + 3 + 4 = 23). O estilo do atacante **não é lido**: centroavante (ex = 1) e
   ponta (ex = 2) disputam a mesma cota de ATA.
5. **Preenchimento final**, executado **só se o pool inteiro tem menos de 23 jogadores**: a
   condição lê o tamanho do pool, não o da lista montada. Primeiro, para cada célula na ordem
   GOL, LAT, ZAG, MEI armador, ATA, MEI volante, se a cota da célula não foi esgotada, entram
   **todos** os jogadores do pool daquela posição que ainda não estão na lista, ignorando lado e
   estilo e sem limite de quantidade; depois entram todos os que sobraram, na ordem do pool. O
   resultado líquido é que **o pool inteiro é convocado**. O passo não completa até 23 nem até a
   cota: com pool de 20 (só os avulsos) a lista fica com 20. Corolário: com 23 ou mais no pool
   mas alguma célula sem candidato (um país sem lateral de lado 1, por exemplo), a lista fica
   **com menos de 23** e ninguém a completa. Ver item 64 de OPEN-QUESTIONS.
6. **Ordem final e designações**: a lista é ordenada por posição crescente (GOL, LAT, ZAG, MEI,
   ATA), depois estilo crescente (volante antes de armador; centroavante antes de ponta), depois
   força decrescente, depois estrela na frente, e vira o elenco da seleção. Em seguida cobrador
   de escanteio e "falso 9" são **zerados**, e batedor de falta/pênalti e capitão são
   **recalculados** pelas regras da 5.6 sobre os convocados: o batedor pela ordem força desc,
   energia desc (primeiro titular por atributo cuja 1ª característica é Finalização, senão
   primeiro titular de linha, senão primeiro jogador de linha), o capitão por força desc, idade
   desc. As duas rotinas reordenam o elenco no lugar e a do capitão roda por último, logo o
   elenco guardado fica em **força desc, idade desc** (estável sobre a ordem anterior). O
   "titular" é o atributo de dado que o convocado traz do clube; os avulsos nascem com status 0.

**Jogadores avulsos (o complemento sintético).** São criados na ordem **3 GOL, 4 LAT, 4 ZAG,
5 MEI, 4 ATA** (posição por posição, na ordem da tabela de posições), e cada jogador é construído
nesta sequência, com estes sorteios:

1. **Nacionalidade** = o país da seleção, e **nome** gerado por país (mecanismo abaixo). O jogador
   é acrescentado à piscina de avulsos já aqui, antes de qualquer outro campo.
2. **Posição** da vez.
3. **Força** = `nívelMapeado(nívelPaís) - 5 + rnd(8)`, com o nível do país passado pela **mesma
   tabela de nível mapeado da 4.4** (<= 15 -> o próprio; 16 -> 17; 17 -> 18; 18 -> 19; 19 -> 21;
   20 -> 25; 21 a 25 -> 26 a 30; acima de 25 daria 0, mas a 4.4.1 vai só até 20). Sem escala por
   país e sem teto: o intervalo é `[nívelMapeado - 5, nívelMapeado + 2]`, ou seja 20 a 27 para um
   país de nível 20 e 6 a 13 para um de nível 11.
4. Marca de júnior desligada; **temporada de chegada = temporada atual**; um campo auxiliar de
   média, sem leitor conhecido, zerado; **contrato de 180 dias** a partir da data da rodada atual.
5. **Atributos individuais** (só com a opção ligada), pelo gerador da 4.2 com
   `A = nívelMapeado(nívelPaís)`, menos 4 se maior que 4, e `B = 7 se nívelPaís >= 20 ; 4 se 19 ;
   senão 1`, isto é, as faixas da linha de reputação da 4.2 lidas pelo nível em vez da reputação,
   o que dá no mesmo pela tabela de reputação acima. **Defeito de ordem a reproduzir**: o gerador
   roda **antes** de estilo e características serem gravados, e lê os dois como estão no jogador
   recém-criado, ou seja `ex = 0` e c1 = c2 = índice 0. Logo laterais e meias avulsos recebem
   **sempre as fórmulas defensivas** (lateral def. e volante) da 4.2, mesmo os que no passo 8 saem
   ofensivos; goleiros avulsos recebem sempre o bônus de Colocação da 4.2 (Tec `+2+rnd(5)` por c1
   e `+rnd(2)` por c2); nenhum jogador de linha avulso recebe bônus de característica, porque o
   índice 0 não é característica de linha. Ver item 65 de OPEN-QUESTIONS.
6. **Características**: um sorteio de par pela tabela da posição na 4.4.2.
7. **Talento** `es = 7 + rnd(4)`; **idade** `18 + rnd(12)`; **lado** `rnd(2)` (0 = direita,
   1 = esquerda, como na FORMAT-SPEC); **status** = 0 (reserva por atributo).
8. **Estilo** da 4.3, calculado agora a partir das características do passo 6.
9. **Valor de mercado** (4.9) e **salário** (4.8), calculados sem clube. No salário, base 350 sem
   ajuste de país, divisão ou nível (só o ajuste por posição e o resto da fórmula: núcleo, idade,
   piso 500, x4 mensal). No valor, o nível do clube ausente vale **10**, logo `baseNível = 366`;
   sem x1,2 de titular (status 0); x1,3 se atacante; e como a temporada de chegada é a atual entra
   o desconto **x0,18** de "chegou nesta temporada", sem teto de preço pedido porque não há nenhum.
10. Estrela, topMundial e as demais marcas ficam **desligadas**; nenhum registro de carreira.

**Nome gerado por país.** O gerador de nomes é **dado embutido do jogo**, não do formato de dados:
dentro do executável há **dois arquivos de texto por país**, um de primeiros nomes e um de
sobrenomes, cada um com uma entrada por linha e nomeado pela **sigla de 3 letras** da tabela da
4.4.1. São 221 arquivos em cada pasta (uma sigla sem país correspondente; quatro países sem
arquivo: ESS, BON, SMF, SMH). A maioria dos arquivos tem entre 50 e 100 entradas; o total é da
ordem de 32 mil primeiros nomes e 27 mil sobrenomes, e o Brasil é a exceção, com cerca de 1.700
primeiros nomes e 700 sobrenomes. Uma entrada pode ter mais de uma palavra ("João Carlos",
"Carlos de Jesus"). O mecanismo:

1. Ao carregar, linhas vazias, com ponto ou com dígito são descartadas, e a lista fica em cache
   por país. A primeira linha de cada arquivo repete a segunda com uma marca de codificação na
   frente, e o índice 0 **nunca é sorteado** (0 vira 1).
2. Primeiro nome: índice `rnd(n)`; se a lista tem 1000 ou mais entradas (só o Brasil), com 50% o
   índice é ressorteado como `rnd(500)`, o que favorece o começo do arquivo.
3. Pelo número de palavras da entrada sorteada: **uma palavra** -> anexa um sobrenome (índice
   `rnd(m)`, 0 vira 1) se a lista de sobrenomes tem mais de 2 entradas e o sobrenome difere do
   nome; **duas palavras** -> com 50%, e só se a entrada tem até 12 caracteres, anexa um sobrenome
   de até 6 caracteres (mesmo sorteio; se o sorteado é maior, fica sem); **três ou mais** -> fica
   como está.
4. Se o país não tem arquivo, vale uma **lista global de reserva** de cerca de 37 mil nomes (outro
   arquivo embutido), lida por janelas contíguas `[início, início + tamanho)` escolhidas pelo
   continente e por grupos de países, com índice `início + rnd(tamanho)`.

O mesmo gerador nomeia os juniores da 4.6, os avulsos reciclados da 4.11 e qualquer jogador de
arquivo com nome vazio ou com o nome reservado "TESTE". O projeto **não copia essas listas**; ver
item 63 de OPEN-QUESTIONS.

**Técnico da seleção**: escolhido na convocação, se o time não é humano e está sem técnico (ou se
a competição pedir a troca), entre os técnicos **desempregados e não humanos** da lista mundial de
técnicos, na ordem dessa lista: primeiro o da mesma nacionalidade com reputação 5, depois 4;
depois qualquer nacionalidade com reputação 5, depois 4; depois compatriota de reputação 3,
depois qualquer um de reputação 3. Reputação abaixo de 3 nunca é escolhida, e sem candidato a
seleção fica sem técnico. **Não existe piscina de técnicos desempregados na criação do mundo**:
o mundo nasce só com o técnico de cada clube, lido do arquivo do clube, todos empregados. A
piscina se forma durante o jogo, com as demissões da 1.5 e com os jogadores que se aposentam e
viram técnicos (4.11), que entram desempregados com a nacionalidade do jogador e a reputação e a
divisão do último clube. Na primeira temporada, portanto, toda seleção da IA joga sem técnico.
Ver item 66 de OPEN-QUESTIONS.

### Efeitos da convocação no mundo

**Quando a convocação roda.** A 1.16 mostra que cada competição de seleção é redesenhada uma vez
por edição (sorteio de grupos, nova lista de países), não partida a partida. A convocação segue o
mesmo ritmo: o time da IA é esvaziado e reconvocado quando a competição precisa dele pela primeira
vez naquela edição, não antes de cada jogo individual - reconvocar a cada rodada seria refazer o
sorteio de artilheiro/capitão/cobrador da etapa 6 do algoritmo acima sem motivo, e o ciclo fixo por
temporada da 1.16 já dá um ponto natural único por edição para isso acontecer. Esta granularidade é
**INFERIDO**: a leitura direta confirma que a criação do time e a convocação são preguiçosas ("sob
demanda", como já registrado acima) e que o redesenho de cada competição é por edição, mas o ponto
exato em que a rotina de convocação é chamada dentro dessa janela não foi isolado nesta varredura -
ver item 94 de OPEN-QUESTIONS.

**O jogador convocado é o mesmo objeto do clube.** A seção acima já registra que "a seleção nunca é
dona de jogador" - o convocado continua sendo o jogador do elenco do clube, só referenciado pela
lista da seleção. Não existe um registro de carreira separado para partidas de seleção: as notas da
3.14, o desgaste de energia da 3.9, os cartões e lesões da 3.8 de uma partida de seleção são escritos
no mesmo registro do jogador que qualquer partida de clube usa, porque é o mesmo objeto - não há
sincronização de volta para o clube porque nunca houve cópia. Uma lesão ou suspensão por cartão
sofrida numa partida de seleção **aparece no jogador quando ele volta ao clube**, do mesmo jeito que
uma lesão de clube apareceria numa convocação seguinte.

**Se o jogador fica indisponível para o clube durante a convocação** (se as datas de clube e de
seleção podem colidir, e o que acontece se colidirem) é uma pergunta de calendário e de rodada, fora
desta varredura - a 1.10 é quem descreve que rodadas cada competição ocupa. Esta varredura só
confirma o que o objeto do jogador guarda; se há exclusividade de escalação entre clube e seleção na
mesma rodada é do outro time de spec.

**Os avulsos da convocação nunca viram jogador de clube.** A 6.5 já registra que "jogador sem clube
nunca é contratável" - essa regra é geral, e os avulsos sintéticos que o passo 1 da convocação gera
não são exceção: nascem sem clube, permanecem nele até serem reciclados pela 4.11 aos 35 anos (ou
continuam como avulsos indefinidamente), e não existe rotina de contratação de avulso por clube.
O único destino de um avulso além de reciclagem é continuar sendo convocado, potencialmente por
muitas temporadas seguidas.

**Nada disto alimenta o contador de elite/topMundial nem o hall da fama por um caminho fechado nesta
varredura.** A 3.14 calcula nota para qualquer partida, então a média de notas de um jogador
tecnicamente inclui partidas de seleção; mas se o contador de "elite da competição" da 4.10 conta
partida de seleção, e se o hall da fama da 4.11 (limiares de gols por temporada) soma gols feitos
pela seleção, não foi confirmado nem descartado nesta varredura - a definição de "elite da
competição" em geral é escopo do time de spec de evolução de jogador (ver o item 9 da lista de
"o que precisa do time de spec" do design da v0.3); a pergunta estreita e específica de seleção fica
registrada como item 95 de OPEN-QUESTIONS.

**A reputação de país não muda.** Ao contrário da reputação de clube (5.5), que é um saldo de
prestígio atualizado por título e por decaimento periódico, o nível de país da 4.4.1 - e portanto a
reputação de seleção que dele deriva - é lido uma única vez, na carga da tabela de países embutida
no jogo, e nunca é escrito de volta por resultado de partida, título de copa ou qualquer evento de
jogo. Vencer uma Copa do Mundo ou uma copa continental não muda o nível do país nem a reputação da
seleção nas edições seguintes. A tabela de prêmios de reputação da 5.5 (que lista liga nacional,
copa nacional, estadual, continental 1/2, Mundial, Recopa, regional, Finalíssima) é inteiramente
sobre reputação de **clube**; nenhuma das oito competições de seleção da 1.16 concede prestígio a
país nenhum, porque não existe saldo de prestígio de país para conceder.

---

# 5. TIME, FORMAÇÕES E TÁTICA

## 5.1 Catálogo de formações

A formação é armazenada como **lista de slots**, não como "4-4-2". Nomes de menu e slots:

| ID | Nome | Slots | Agrupamento D/M/A do motor |
|---|---|---|---|
| 1 | 5-4-1 | 1,20,11,13,14,16,2,9,6,4,8 | 5/4/1 |
| 2 | 5-3-2 | 1,22,24,12,14,16,2,9,6,4,8 | 5/3/2 |
| 3 | 4-5-1 | 1,23,11,13,15,2,9,6,8,10,17 | 4/5/1 |
| 4 | 4-4-2 | 1,22,24,11,13,14,16,2,9,3,5 | 4/4/2 |
| 5 | 4-4-2 def | 1,19,21,11,12,13,15,2,9,6,8 | 4/4/2 |
| 6 | 4-4-2 ofensivo | 1,22,24,12,14,15,16,2,9,6,8 | 4/4/2 |
| 7 | 4-3-3 | 1,22,23,24,12,14,16,2,9,6,8 | 4/3/3 |
| 8 | 4-3-3 def | 1,19,20,21,11,13,15,2,9,6,8 | 4/3/3 |
| 9 | 3-5-2 | 1,22,24,11,13,15,4,6,8,10,17 | 3/5/2 |
| 10 | 3-4-3 | 1,18,25,23,11,13,4,6,8,10,17 | 3/4/**2** (!) |
| 11 | 4-2-3-1 | 1,23,14,16,15,13,11,2,9,6,8 | 4/5/1 |
| 12 | 4-2-3-1 Alas | 1,20,10,17,15,13,11,2,9,6,8 | 4/5/1 |

Atenção: o 3-4-3 usa o slot 18, que **não conta em nenhum agregado** -> na prática o ataque é calculado com
2 dos 3 atacantes, dividido por 3. A IA **nunca escolhe a formação 12**.

**Grupos do motor (por faixa de slot, nunca pela posição natural):**
Goleiro = 1, Defesa = 2-9, Meio = 10-17, Ataque = 19-25, slots 0 e 18 = nenhum grupo, Banco = 26-36.

Como os divisores são fixos (5/5/3): cada um dos 5 primeiros defensores vale `força/50`, cada um dos
5 primeiros meias vale `força/50`, e cada um dos **3 primeiros atacantes vale `força/30`** - um
atacante pesa **1,67x** um defensor dentro do seu próprio agregado. Jogadores além do 5º/5º/3º são
desperdício puro.

## 5.2 O vetor de tática - 4 posições

`[0]` formação (0-12), `[1]` **postura** (0 Equilibrado / 1 Ataque total / 2 Contra-ataque),
`[2]` **marcação** (0 Leve / 1 Pesada / 2 Muito pesada), `[3]` **lado do ataque** (0 meio / 1 laterais).

**Três dos quatro botões são inertes:**
- `[0]` formação: escrito pela IA e pela interface, **nunca lido pelo motor** (que só olha os slots atribuídos).
- `[1]` postura: **lida e jogada fora**.
- `[3]` lado do ataque: **nunca lido**.
- `[2]` marcação: **o único com efeito real** - bônus desprezível de meio-campo (+0,008/+0,016 numa
  escala 0-10), **+30/+10/+0 no divisor de cartão** (marcação leve é bem mais segura) e o **+20 de
  peso de assistência para laterais** quando é "Pesada".

## 5.3 Fora de posição e lado

- **Fora de posição** se e somente se posição natural != posição exigida pelo slot -> **x0,5 na nota inteira** do jogador. Penalidade fixa, independente de quão distante é a improvisação.
- **Não-goleiro no gol**: sofre o x0,5 **e** o agregado de goleiro vira `round(GKx0,2)`. Exemplo: um jogador de linha com 70 de força no gol rende 1,0 contra 7,0 de um goleiro de 70 - colapso de ~86%.
- **Lado errado (direita/esquerda) NÃO tem penalidade nenhuma de força.** Só é preferência na escalação automática.
- Estilo (volante x armador x ponta) também não tem penalidade de força - só preferência de escalação.

## 5.4 Escalação automática

1. Filtra lesionados e suspensos (e, para clube humano, contratos vencidos).
2. **Ordena por força desc, energia desc** - e nada mais.
3. Preenche os 11 slots **na ordem da lista da formação**, com busca relaxada:
   laço externo = relaxamento de posição (cascata GOL->ZAG->LAT->MEI->ATA etc.), laço interno = lado e estilo.
4. Banco fixo: `{1,1,2,4,4,12,15,15,20,20,23}` = 2 goleiros, 1 lateral, 2 zagueiros, 1 volante, 2 meias, 3 atacantes.

**Consequência importante de ordenação:** as listas de formação colocam o goleiro primeiro, depois os
**atacantes**, depois os meias e por último os defensores. Como os candidatos são consumidos do mais
forte para o mais fraco, **os atacantes da IA escolhem primeiro e os zagueiros ficam com as sobras.**
Reproduza essa ordem ou os times da IA sairão visivelmente diferentes.

A tela manual expõe todos os 25 slots + 11 do banco, exige exatamente 11 em campo e **não faz nenhuma
checagem de legalidade posicional** - seis zagueiros ou onze atacantes são aceitos.

## 5.5 Reputação (0-5) - o análogo mais próximo de "moral de clube" CONFIRMADO

Não existe moral/confiança de time. O que existe é **reputação**, alimentada por um saldo de pontos
de prestígio, e ela entra no motor pelos multiplicadores de competição e pelos pesos anti-goleada.

**Decaimento periódico:** rep 5 -> -6.000 pts (saldo < -90.000 -> cai para 4); rep 4 -> -600
(saldo < -9.000 -> cai para 3); rep 3 -> -50 (saldo < -1.000 -> cai para 2), **e essa etapa e a
seguinte só rodam para clube de liga** (um clube fora de liga nenhuma, caminho de reputação da 1.9,
nunca decai por esta regra); rep 2 -> -5 (saldo < -1.000 -> permanece 2). **A reputação nunca cai
abaixo de 2 por decaimento** - não existe transição para reputação 1 ou 0 nesta rotina; reputação 1
só é alcançada pela promoção abaixo (ou já nasce assim na criação do mundo).
**Promoção:** saldo > 100.000 -> 5, > 10.000 -> 4, > 1.000 -> 3, > 100 -> 2, > 10 -> 1 (a reputação
só sobe por aqui, nunca desce).

**Quando o decaimento e a promoção rodam: uma vez por temporada, na virada, junto com o
envelhecimento e a aposentadoria dos clubes da IA (4.11)** - não mensal, não por rodada. É o mesmo
fechamento de temporada que aciona o teste de demissão de técnico (1.5) e a reconstrução de
calendário da temporada seguinte; roda clube por clube, uma chamada por clube, dentro do mesmo laço
que decide aposentadoria e reposição. **O saldo de prestígio não é zerado nem tocado por nenhum
outro evento** além do decaimento periódico e dos prêmios de título abaixo - sem efeito de
transferência, de goleada sofrida ou de resultado de partida isolado.

**Prêmios por título {campeão, vice}:** liga nacional {500, 90}, copa nacional {300, 50},
estadual {10, 5}, continental 1 {5.000, 1.000}, Mundial {40.000, 1.000}, continental 2 {2.000, 500},
Recopa {500, 0}, regional {50, 0}, Finalíssima {1.000, 500}.
Prêmios acima de 1.000 são multiplicados por **0,6**, mas não simplesmente "fora da Europa": a
condição exata lida é **clube sem liga nacional ativa** (o mesmo caminho de reputação da 1.9) **e**
continente diferente de Europa **e** de América do Sul - ou seja, o desconto só bate em clubes de
reputação (sem divisão) da África, Ásia, Concacaf ou Oceania. Um clube europeu ou sul-americano nunca
tem desconto, esteja ou não numa liga; um clube de outro continente que tem liga ativa também não -
só quem está ao mesmo tempo fora de liga e fora desses dois continentes perde 40% do prêmio. Título
de liga em divisão inferior vale fixos 50.

**Quando os prêmios são creditados: por competição, no momento em que a colocação final do clube
nela é registrada** (campeão ou vice) - não em lote na virada de temporada. Cada competição credita
o prêmio assim que fecha, o que normalmente cai perto do fim da temporada mas é um gatilho lógico
separado do decaimento/promoção acima; uma temporada com competições que terminam em datas
diferentes credita os prêmios em momentos diferentes, todos antes do decaimento/promoção da mesma
virada. **Achado à parte:** o técnico do clube recebe o mesmo prêmio de título, sinal de que carrega
o próprio saldo de reputação em paralelo (ver 1.5) - sem relevância para o saldo do clube em si.

## 5.6 Jogadores designados CONFIRMADO

As quatro designações são **guardadas no time**, não derivadas na hora da partida.

| Designação | Como é escolhida | Efeito real |
|---|---|---|
| **Capitão** | do **elenco inteiro**: maior força, desempate por **maior idade** | **Nenhum** - só exibição |
| **Batedor de falta/pênalti** | do **elenco inteiro**, ordenado por força desc e energia desc: o primeiro com **status de titular** cuja **1ª** característica é Finalização; senão o primeiro com status de titular que não seja goleiro de posição; senão o primeiro não-goleiro, ignorando o status | Creditado como autor **do evento** em gols de pênalti (5%) e falta (3%), se estiver em campo |
| **Cobrador de escanteio** | **só manual** | Creditado como autor do evento em gol olímpico (0,5%), se estiver em campo |
| **"Falso 9"** | **só manual** | **Nenhum** |

Detalhes que mudam a reimplementação:

- **O batedor não sai da escalação, sai do elenco.** O pool é o elenco profissional inteiro, e
  "titular" é o **atributo de dado** do jogador (`status == 1`, ver `FORMAT-SPEC.md`), não "estar no
  time da partida". Um titular por atributo que ficou no banco continua sendo o batedor designado -
  ele só não é creditado porque o sorteio de tipo de gol exige que ele esteja em campo (seção 3.7).
- **A característica exigida é só a primeira.** Quem tem Finalização como 2ª característica não é
  preferido; cai no ramo seguinte.
- **Quando é recalculado:** na criação do mundo e a cada mudança de elenco. Não é recalculado por
  partida. Existe ainda um caminho que recalcularia a designação a partir de uma lista dada (a
  escalação, por exemplo) só quando o designado não estivesse nela, mas **nada o chama** - é código
  morto, e a spec não deve portá-lo.
- **A designação guardada só é apagada quando o jogador deixa o clube.** Lesão, suspensão e ficar
  fora da escalação não a invalidam.
- **Seleções zeram cobrador de escanteio e "falso 9"** a cada convocação, e recalculam batedor e
  capitão **com as mesmas regras desta tabela, sobre a lista de convocados** (passo 6 da 4.12).
  O "titular" continua sendo o atributo de dado que o convocado traz do clube; os avulsos
  sintéticos nascem com status 0, então só caem no terceiro ramo do batedor. Como **a IA nunca
  preenche o cobrador de escanteio**, na prática só o time humano tem um - ver o item 2 da
  seção 3.7.

## 5.7 Constantes de gestão de elenco da IA

- Elenco profissional: teto **35** (promoção bloqueada em 32). Base: teto **20**.
- **Forma ideal (compra se abaixo):** GOL 2, LAT 3, ZAG 3, MEI 5, ATA 3.
- **Excedente (lista para venda se acima):** GOL 2, LAT 3, ZAG 3, MEI 5, ATA 4 - com rolagem de 50% por
  posição; listado com força < 42 ainda é dispensado de vez com 30% de chance.
- **Limites de contratação:** força mínima `{1,40,30,20,5}[divisão]` ou `{1,10,20,40,50,55}[reputação]`;
  força máxima `{20,30,45,85,100,100}[reputação]`.
- **Cotas por posição que bloqueiam contratação:** GOL > 3, LAT > 5, ZAG > 5, MEI > 10, ATA > 5.

---

# 6. ECONOMIA

## 6.0 O FATO ESTRUTURAL MAIS IMPORTANTE DE TODO O JOGO

> **Dinheiro só é simulado para clubes com treinador humano.** CONFIRMADO.

- **Transferências** só movem caixa quando o clube daquele lado é humano. Uma transferência IA->IA é **completamente neutra em dinheiro** para os dois lados.
- **Bilheteria** só é creditada se o mandante é humano. **Premiação** só é paga a clube humano.
- **Salários, juros e obras** só são processados na lista de clubes humanos.
- **Nenhum caminho de decisão da IA lê o saldo de caixa antes de comprar.** Clubes da IA não têm orçamento, não têm folha salarial e não podem falir.
- O único momento em que o caixa da IA é consultado é quando **você** compra: a contraproposta do vendedor confere se **o seu** clube tem como pagar.

Ou seja: a economia é uma **camada de gestão de recursos para um jogador só**, aparafusada sobre uma
simulação de mundo determinística - **não é um sistema econômico fechado**. Uma reimplementação
precisa reproduzir isso, ou o balanceamento muda completamente. (Se você quiser economia para todos,
saiba que vai precisar escrever do zero a lógica de orçamento da IA, que simplesmente não existe.)

## 6.1 Caixa inicial e aporte de temporada

| Divisão | Caixa inicial | Aporte anual (categoria 6) |
|---|---|---|
| 0 (sem pirâmide) | 3.500.000 | 3.500.000 |
| 1 | 15.000.000 | 6.000.000 |
| 2 | 12.000.000 | 4.500.000 |
| 3 | 10.000.000 | 2.500.000 |
| 4 | 3.500.000 | 2.000.000 |

**Atenção:** assumir um novo clube **zera o caixa para o valor inicial e apaga o livro-caixa e a dívida**.
Quando estaduais estão ligados, clubes **fora do Brasil** recebem ainda um aporte de **3,2 x folha
salarial** no início da temporada (clubes brasileiros compensam via bilheteria de estadual).
**Não existe patrocínio nomeado nem cota de TV** - o aporte acima é tudo.

## 6.2 Bilheteria - a principal receita recorrente

Calculada em toda partida, creditada **só ao mandante humano**, e nunca em Mundial (5) nem Seleções (7).

**Setores** (capacidade limitada a [1.000, 120.000]):
Geral = 15%, Arquibancada ~ 75,1% (o resto), Cadeira = 9%, Camarote = 0,9%.

**Preços recomendados** (4 números por linha, um por setor):

| Competição | Indexado por | Linhas |
|---|---|---|
| Liga nacional | divisão 0-4 | `{3,12,15,30}`, `{10,15,25,80}`, `{7,13,20,70}`, `{5,12,17,40}`, `{3,12,15,30}` |
| Copa nacional | reputação 0-5 | `{3,12,15,30}`x3, `{7,13,20,70}`, `{10,15,25,80}`x2 |
| Estadual | reputação 0-5 | `{3,5,12,20}`, `{3,12,15,30}`x2, `{5,12,20,50}`, `{10,15,25,70}`x2 |
| Continental 1 | Europa / resto | `{30,45,65,200}`, `{20,35,55,150}` |
| Continental 2 / Supercopa | Europa / resto | `{20,25,45,150}`, `{20,25,40,120}` |
| Amistoso | Europa / resto | `{5,15,20,30}`, `{3,12,15,25}` |
| Demais | - | `{10,25,35,50}` |

**Público por setor:**
```
1) BASE por reputação do mandante:
   rep0 {200,500,50,0}, rep1 {1000,5000,1200,20}, rep2 {2000,10000,1500,50}
   rep3 {4000,20000,2500,300}, rep4 {4500,30000,3500,400}, rep5 {5000,40000,5500,500}
2) escala por competição: estadual/regional x0,7, amistoso x0,4
3) bônus proporcional à capacidade: k = 0,30
   +0,15 fase de grupos/mata-mata, +0,30 continental 1, +0,30 seleções/eliminatórias
   +0,15 continental 2 / supercopas -> público += round(capacidadeSetor x k)
4) qualidade do visitante: difRep = repVisitante - repMandante
   m = {0, 0,05, 0,10, 0,15, 0,20, 0,25}[|difRep|]
   difRep > 0 -> x(1+m), difRep < 0 -> x(1-m)
5) torcida: x (apoioDaTorcida / 100) // medidor do treinador, 80 se não houver
6) ruído por divisão (somado)
7) elasticidade de preço (só com preços customizados):
   dif = preçoRecomendado - seuPreço ; público += round(0,03 x público) x dif
   -> cada unidade ABAIXO do recomendado dá ~3% de público; cada unidade ACIMA tira ~3%
8) limita cada setor à capacidade
RECEITA = soma público[i] x preço[i]
9) multiplicador final: seleções x5 (fase de Copa do Mundo) ou x3 ; eliminatórias x2
```
Limites manuais de preço: Geral 1-200, Arquibancada 1-300, Cadeira 1-500, Camarote 1-1000.

**Leitura estratégica:** a elasticidade é linear sobre a diferença absoluta, então satura rápido.
Clube de reputação baixa maximiza receita cobrando **10-20 unidades abaixo do recomendado** nos dois
setores grandes; clube de reputação alta já bate no teto de capacidade e deve cobrar **acima**.

## 6.3 Premiação

**Classificação final de liga** (top 10), `[divisão][posição]`:

| Div | 1º | 2º | 3º | 4º | 5º | 6º |
|---|---|---|---|---|---|---|
| 1 | 5.000.000 | 3.500.000 | 2.000.000 | 1.500.000 | 1.000.000 | 500.000 |
| 2 | 2.500.000 | 2.000.000 | 1.000.000 | 500.000 | 250.000 | 100.000 |
| 3 | 1.500.000 | 1.000.000 | 500.000 | 300.000 | 150.000 | 75.000 |
| 4 | 750.000 | 500.000 | 300.000 | 200.000 | 100.000 | 50.000 |

**Estadual** (1ª fase): `{700.000, 500.000, 300.000, 100.000}` para o 1º ao 4º.

**Mata-mata - pago ao vencedor de cada confronto:**
Copa nacional `{1M, 2M, 4M, 5M, 7M, 15M, 20M, 25M}` (alinhado à direita, terminando sempre na última
entrada), Continental 1 Europa `{2M, 5M, 7M, 25M, 30M}`, Continental 1 América do Sul
`{2M, 3,5M, 5M, 20M, 40M}`, Continental 1 África/Ásia/Concacaf `{0,5M, 1,5M, 2M, 4M, 40M}`,
Continental 1 Oceania `{0,2M, 0,5M, 1M, 2M, 20M}`, Mundial `{2M, 5M, 7M}`,
Continental 2 Europa `{0,5M, 0,7M, 2M, 2,5M, 7M}`, Continental 2 A.Sul `{0,5M, 1M, 1,5M, 5M, 5M}`,
Supercopas fixas 1M, Regional `{100k, 100k, 400k}`.

## 6.4 Despesas e ciclo financeiro

| Despesa | Valor | Frequência |
|---|---|---|
| Salários | folha inteira (profissional + base) | **dia 2 de cada mês** (padrão) ou **todo domingo** |
| Compra de jogador | valor negociado | na transferência |
| Multa rescisória | 12-30% da venda | **só em venda passiva** |
| Juros de empréstimo | 3% da dívida | mensal (dia 2) |
| Obras no estádio | orçamento | à vista, uma vez |

**Não existe** custo de comissão técnica, custo de categoria de base, manutenção de estádio nem viagem.

Detalhe importante: o salário só é debitado de clubes que **tenham ao menos uma partida marcada no mês
corrente**. Mês sem jogo = folha zero.

**Por turno (dia):** vencimento de empréstimos; **50% de chance** de rodar a varredura de venda passiva;
conclusão de obras; ofertas recebidas (1% compra, 50% empréstimo); renovação automática.
**Por temporada:** zera livro-caixa -> decai prestígio -> poda de elenco -> paga aporte -> envelhecimento,
aposentadorias, base -> **recalcula valor e salário de todo mundo** -> janela de transferências da IA ->
mercado de treinadores.

## 6.5 Mercado de transferências

### Interesse do clube comprador
Só considera contratar se: elenco < **35**; `força >= mínimo` (`{1,40,30,20,5}[divisão]` para clubes de
liga, `{1,10,20,40,50,55}[reputação]` para os demais); `força <= máximo` (`{20,30,45,85,100,100}[reputação]`);
e a posição não estiver saturada (`{3,5,5,10,5}`). **A saturação é ignorada quando o vendedor é humano.**

Lendo mínimo+máximo juntos, sai a "barreira de classe" do jogo: reputação 0 só pega força 1-20;
rep 1 -> 10-30; rep 2 -> 20-45; rep 3 -> 40-85; rep 4/5 -> 50/55-100.

### Decisão do JOGADOR - só reputação/continente; **salário e minutagem nunca são consultados**
- Clube comprador **na Europa** -> **sempre aceita**.
- Senão, se ele joga **na Europa em clube de reputação >= 4**: comprador rep >= 4 ou divisão 1 -> aceita;
  comprador rep = 3 -> aceita **exigindo salário x 2**; senão recusa.
- Senão: vendedor rep >= 3 e comprador rep >= 3 -> aceita; vendedor rep = 3 e comprador rep em {1,2} -> aceita;
  vendedor rep <= 2 -> aceita; senão recusa.

### Preço pedido pela IA vendedora (multiplicadores sobre o valor de mercado)

| Profundidade no elenco | GOL | LAT | ZAG | MEI | ATA |
|---|---|---|---|---|---|
| Sobra, jogador bom e jovem | x1,50 | x1,20 | x1,20 | x1,20 | x1,50 |
| Sobra, fraco (<30) ou velho (>35) | x0,85 | x0,80 | x0,80 | x0,90 | x0,90 |
| Escasso (2 ... mínimo-1) | x2,00 | x2,00 | x2,00 | x2,00 | x2,50 |
| Único na posição | x2,50 | x2,50 | x2,50 | x2,50 | x3,00 |

**Em negócio IA->IA esse markup não é aplicado - o preço é exatamente o valor de mercado.**

### Janela de transferências da IA (uma vez por temporada, 5 fases)
1. Clubes de liga vendem: rodadas = 1 (posição <=1), 2 (<=5), 3 (<=10), senão 4 - cada uma com um passe agressivo e um de rotina.
2. Clubes fora de liga vendem (mesma rotina).
3. Todos preenchem buracos abaixo de `{2,3,3,5,3,2}`, comprando por **exatamente o valor de mercado**;
   se ninguém servir, **o clube cria um jogador do nada**.
4. Manutenção de seleções.
5. Êxodo de craques (seção 1.6).

### Ofertas que chegam ao clube humano
A cada turno: **1%** de chance de uma oferta de compra por um jogador aleatório; senão **50%** de chance
de um pedido de empréstimo. **A oferta é sempre `valor de mercado x 1,3`**, sem barganha.

### Vender passivamente (jogador listado)
Margem aceita: **10%**, ou 15% com 19% de probabilidade. Ou seja, **listar não rende mais que ~110-115%
do valor**. E é **o único caminho de venda em oito que cobra multa rescisória**:

| Dias restantes de contrato | Multa |
|---|---|
| <= 30 | 12% |
| <= 60 | 20% |
| <= 90 | 22% |
| <= 180 | 25% |
| > 180 | 30% |

Não existem luvas nem taxa de agente em nenhum outro lugar.
**Consequência:** oferecer manualmente ao mercado (130%, sem multa) é **sempre** melhor que listar (<=115%, com multa).

### Leilões (opção `verLeiloes`, desligada por padrão)
Após rodada de liga, estadual ou regional, gera `rand(0..3)` lotes. Vendedor = clube da IA com >= 21
jogadores e sobra posicional; o jogador não pode estar emprestado nem ser estrela.
Lance mínimo = **50% do valor de mercado**; cada clube humano dá **um único lance**, em ordem de lista.
```
r = rand(0..99) ; d = 0,45 ; se r > 60 -> d = 0,62
TETO = round(lanceMínimo x 2,5) // = 1,25 x valor de mercado
LANCE_IA = round(lanceMínimo x d) + melhorLanceHumano + lanceMínimo
se melhorLanceHumano < lanceMínimo -> IA leva (ou lote anulado se não houver IA interessada)
senão se melhorLanceHumano < TETO e r > 50 e existe IA -> IA cobre e leva
senão -> o humano de maior lance leva pelo próprio lance
```
Ou seja: **você ganha um leilão com certeza pagando >= 125% do valor de mercado**; abaixo disso é
cara-ou-coroa a cada vez. Leilão não paga multa rescisória.

### Empréstimos
Duração **366 dias** (contrato ajustado para 365). **Taxa zero** e **sem divisão de salário - quem toma
emprestado paga 100%**. Limites: **4** entrando, **10** saindo/listados. Retorno automático no
vencimento; se o clube de origem é humano e está com 35, o retorno é **adiado indefinidamente**.
Comprar o emprestado custa **exatamente 100% do valor de mercado**.

### Não existe mercado de agentes livres
Jogador sem clube **nunca é contratável**. Contrato vencido **não libera** o jogador - ele fica no
elenco, mas **inelegível para escalação**. Essa inelegibilidade é a única punição por deixar vencer.

## 6.6 Dívida e falência

Empréstimo com a diretoria em blocos fixos de **500.000**; juros recalculados como `floor(dívida x 3/100)`
e cobrados mensalmente -> **3% ao mês ~ 42,6% ao ano**, sem prazo de quitação.
Tetos de dívida por divisão: `{1M, 5M, 3M, 2M, 1,5M}` (divisões 0-4).

**Não existe falência, venda forçada nem empréstimo automático.** Caixa negativo é legal e apenas:
(1) bloqueia toda compra; (2) **derruba a confiança da diretoria a cada partida**; (3) muda o motivo da
demissão para "crise financeira".

## 6.7 Diretoria, torcida e demissão do treinador humano

Dois medidores 0-100: **confiança da diretoria** (demite) e **apoio da torcida** (multiplica o público).
Ao assumir: confiança 95, torcida 85. No início de cada temporada ambos ganham **+50** (teto 100).

A posição na tabela vira uma faixa 1-5 (1 = líder, 5 = zona de rebaixamento). Partida de liga nacional:

| Resultado | Mandante (faixas 1->5) | Visitante |
|---|---|---|
| Vitória | +5, +4, +1, +1, 0 | +6, +5, +2, +2, +1 |
| Empate | +2, +1, -1, -1, -3 | +2, +1, 0, -1, -2 |
| Derrota | -2, -2, -3, -5, -7 | -1, -1, -2, -4, -5 |

**E, além disso, em toda partida: caixa < 0 -> -10 (liga nacional) ou -5 (demais competições);
torcida < 20 -> -3.**

Torcida por partida: vitória +4 (casa) / +3 (fora), com +5/+4 extras se a diferença for >= 3 gols;
empate +1 / -1; derrota -4 / -5, com -5/-7 extras em goleada sofrida.

**Demissão: confiança < 10.** Motivo: "crise financeira" se caixa < 0; senão "pressão da torcida" ou
"maus resultados".

> **Aritmética da espiral da morte:** partindo de 100 de confiança, jogar no vermelho custa 10 por
> partida de liga **além** do efeito do resultado. Dez jogos de liga no vermelho demitem você **mesmo
> vencendo todos** em faixa 3 (+1 -10 por jogo). **Disciplina de caixa é uma trava mais dura que resultado.**

## 6.8 Estádio

Ampliação por setor, com tetos por temporada (temporada 1: 18.000 / 80.000 / 9.000 / 700;
temporada >= 2: 20.000 / 100.000 / 10.000 / 800 - os dois níveis seguintes existem nos dados mas são
**inalcançáveis**). Capacidade total limitada a 120.000.

**Custo por assento**, conforme o tamanho **final** do setor:

| Setor | Faixas (<=) | Custo/assento |
|---|---|---|
| Geral | 1.000 / 2.500 / 3.500 / 10.000 / 18.000 / acima | 80 / 160 / 240 / 500 / 700 / 700 |
| Arquibancada | 5.000 / 15.000 / 30.000 / 60.000 / 80.000 / acima | 120 / 380 / 640 / 700 / 1.400 / 1.400 |
| Cadeira | 1.000 / 2.000 / 3.000 / 5.000 / 9.000 / acima | 300 / 600 / 750 / 800 / 1.200 / 1.200 |
| Camarote | 100 / 200 / 500 / 600 / 700 / acima | 1.500 / 3.500 / 4.000 / 6.000 / 6.400 / 6.400 |

`custo = soma (custoPorAssento x assentosAdicionados) + 100.000` (taxa fixa de mobilização), **pago à vista**.
Prazo: < 1.000 assentos -> 15 dias, < 10.000 -> 20, < 30.000 -> 30, senão 40.

**Economia da obra:** camarote custa 1.500-6.400 por assento e vende a 30-200 por jogo; geral custa
80-700 e vende a 3-30. Com ~20-25 jogos em casa por temporada, camarote se paga em 1-2 temporadas com
reputação alta e praticamente nunca com reputação baixa. **Ampliar só é racional quando os setores já
estão batendo no teto de capacidade.**

## 6.9 Defeitos conhecidos da economia

1. Nível de escalada do leilão (x0,82) é **código morto** - só 0,45 e 0,62 acontecem.
2. Níveis 3 e 4 de ampliação de estádio são **inalcançáveis** (ordem das comparações de temporada).
3. Com vários clubes humanos, a apuração do leilão compara cada lance com o **primeiro** lance, não com
   o maior corrente - o vencedor pode ser um lance menor.
4. A interface fala em limite de 30/32 jogadores; o código aplica **35**.
5. **Valor e salário só são recalculados uma vez por temporada** - um jogador que ganhe 15 de força
   durante o ano mantém preço defasado até a virada (explorável nos dois sentidos).
6. Clubes da IA são **imunes a dinheiro** - o mercado nunca seca nem infla.
7. A multa rescisória incide em **exatamente 1 dos 8 caminhos de venda**, tornando a listagem passiva
   estritamente pior que a oferta manual.
8. Renovação de **3 anos custa só +5%**, enquanto a de 2 anos custa +15% - contrato longo é sempre o
   melhor negócio; e deixar o contrato cair abaixo de 60 dias transforma todo aumento em **redução**.

---
