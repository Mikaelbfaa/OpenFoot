# Brasfoot 22-23 - Especificação dos formatos de arquivo

Spec da comunidade para os formatos de dados do Brasfoot 2022-23 (jogo abandonado; formato documentado para interoperabilidade e reimplementação clean-room).

**Como este documento foi produzido:** os arquivos de dados usam serialização Java padrão, que é auto-descritiva (nomes de classes/campos ficam no próprio arquivo). A semântica foi confirmada por análise estatística de todos os 703 times distribuídos, por validação contra fatos reais (idades de jogadores em ago/2022, capacidades de estádios, nacionalidades de técnicos) e pelos nomes de getters/tabelas presentes no jogo. Nenhum código do jogo original está reproduzido aqui - apenas fatos sobre o formato e comportamento observável.

## Contêiner

Todos os formatos (`.ban`, `.cfg`, `.ces`, `.bcf`) são **streams de serialização Java** (magic `AC ED 00 05`). Qualquer parser genérico de serialização Java lê esses arquivos - em Python, `javaobj-py3` (ver `ban2json.py`).

| Arquivo | Classe raiz | Conteúdo |
|---|---|---|
| `teams/*.ban` | `e.t` (serialVersionUID=16) | Um time + elenco |
| `conf_ligas_nacionais/*.cfg` | `est.ArrayLigaType` | Formato das divisões de uma liga nacional |
| `conf_estadual/*.ces` | `est.ArrayLigaEType` | Formato de um campeonato estadual |
| `options.bcf` | `est.Options` | Opções do jogo |

O loader do jogo só aceita um `.ban` se o campo `vid == 185` (número mágico de versão do formato). Ferramentas que escrevem `.ban` precisam gravar `vid=185` e reproduzir exatamente os descritores de classe (`e.t`, `e.g`, ambas com `serialVersionUID=16`).

## Time - classe `e.t`

Nomes de campo curtos são da ofuscação; a semântica abaixo é **confirmada** (getters não-ofuscados no binário + validação estatística), exceto onde marcado.

| Campo | Tipo | Significado | Getter original |
|---|---|---|---|
| `e` | String | Nome do time | `getNome` |
| `d` | String | Referência de arquivo (nome-base do .ban e das imagens em `img/`) | `getFileRef` |
| `a` | int | País (índice na tabela de países, ver `countries.json`) | `getPais` |
| `b` | int | Estado brasileiro (índice 0-26, tabela abaixo); **só válido quando país=29 (Brasil)** - times estrangeiros carregam lixo aqui (255 ou sobras) | `getEstado` |
| `c` | int | Nível/força do time, escala 6-20 | `getNivel` |
| `n` | int | Reputação, 0-5 | `getReputacao` |
| `f` | String | Nome do estádio | `getEstadio` |
| `g` | int | Capacidade do estádio | `getCapacidade` |
| `h` | String | Nome do técnico | `getTecnico` |
| `i` | int | Nacionalidade do técnico (tabela de países) | `getTecNac` |
| `cor1`/`cor2` | String | Cores do uniforme, hex `#rrggbb` | `getCor1/2` |
| `o` | int | Cor-base do uniforme (índice 0-18 em paleta interna - mapear via editor/UI) | `getCorBase` |
| `l` | ArrayList | Elenco principal (objetos `e.g`) | `getJogadores` |
| `m` | ArrayList | Juniores/base (objetos `e.g`; vazio na maioria; quando presente, ~15 jogadores de 15-20 anos com `tid=0`) | `getJuniores` |
| `id` | int | ID numérico do clube (0 em muitos times menores; não é único no dataset) | `getId` |
| `valid` | boolean | Sempre `true` nos arquivos distribuídos | `isValid` |
| `vid` | int | **Número mágico: precisa ser 185** ou o jogo ignora o arquivo | `getVid` |
| `aid`, `sid`, `tid` | int | Sempre 0 nos arquivos; nunca lidos pelo código do jogo (reservados/runtime) | - |

## Jogador - classe `e.g`

| Campo | Tipo | Significado | Getter original |
|---|---|---|---|
| `a` | String | Nome | `getNome` |
| `d` | int | Idade. Três registros estão fora do plausível: dois jogadores com 0 e um com 56 | `getIdade` |
| `c` | int | Nacionalidade (tabela de países) | `getPais` |
| `e` | int | Posição: 0=Goleiro, 1=Lateral, 2=Zagueiro, 3=Meia, 4=Atacante (siglas G/L/Z/M/A) | `getPosicao` |
| `i` | int | Lado: 0=Direita, 1=Esquerda | `getLado` |
| `g` | int | Característica 1 (tabela abaixo) | `getCr1` |
| `h` | int | Característica 2 (tabela abaixo) | `getCr2` |
| `f` | int | Status: 1=titular, 0=reserva. **Nem sempre 11 por time**: 651 dos 703 times têm exatamente 11, e os outros 52 vão de 8 a 15 | `getStatus` |
| `b` | boolean | "Estrela" - flag de craque; 16 jogadores nos elencos principais e 4 na base, 20 no total | `isEstrela` |
| `j` | boolean | "Top mundial" - sempre `false` nos arquivos distribuídos | `isTopMundial` |
| `tid` | int | ID único do jogador (0 nos juniores). **Nunca lido pelo código do jogo** - aparentemente só usado por ferramentas/editor | `getTid` |
| `hash` | int | **TALENTO / POTENCIAL, 1-10** (0 ocorre nos dados). Campo mais importante depois da posição: controla a taxa de crescimento semanal, o bônus de teto de evolução, a força na promoção da base e o valor de mercado do júnior. Se o jogador é estrela e talento > 8, é forçado a 10. Ver `SIMULATION-SPEC.md` seção 4. | `getHash` |
| `aid`, `sid` | int | Sempre 0; nunca lidos | - |

**Não existe "força" individual armazenada por jogador.** A força é **gerada em runtime** a partir do time. Ao carregar/criar um jogo, `best.F.fI()` (objeto de jogador em runtime, distinto do `e.g` armazenado) calcula a força assim:

1. Base vem da **divisão** (se o time joga liga) ou da **reputação** do time (0-5). Cada faixa define um teto e um piso (ex.: divisão 1 -> teto 20; reputação 5 -> teto 22, piso 7).
2. Soma-se o `nivel` do time (com uma tabela de bônus para níveis 16-25) + `Random(0..2)`.
3. Titulares (`status==1`) ganham +8 +`Random(0..1)`; jogadores marcados como estrela ganham +9 +`Random(0..2)`.
4. Ajuste por país (federações mais fracas, `ac.fn(pais) <= 13`, recebem multiplicadores 0.4-0.75).
5. Resultado é limitado a 100 e gravado com `ad(forca)`.

Ou seja: **posição/lado/características do `e.g` não afetam a força total** - elas afetam o comportamento em campo. A força vem de nível + divisão/reputação + status + aleatoriedade.

### `habilidadeIndividual` (opção em `est.Options`) - CONFIRMADO

Quando **ligada**, logo após calcular a força, `best.F.j(n, n2)` gera **7 sub-atributos individuais** por jogador (`eA`-`eG`) a partir da força base, ajustados pela posição - e a UI **esconde o número "F:" (força geral)**, mostrando só o restante (ver `components.ao`). Quando **desligada**, o jogador tem apenas a força única, exibida como "F:". Em resumo: é o modo "atributos detalhados por jogador" vs. o modo clássico de força única. (Os rótulos "F:" = Força e "E:" = Energia/estado vêm de `F.fi()` e `F.fp()`.)

### Características (índices de `g`/`h`)

Goleiros usam 0-3; jogadores de linha usam 4-13.

| # | Nome | # | Nome |
|---|---|---|---|
| 0 | Colocação | 7 | Desarme |
| 1 | Defesa Penalty | 8 | Drible |
| 2 | Reflexo | 9 | Finalização |
| 3 | Saída Gol | 10 | Marcação |
| 4 | Armação | 11 | Passe |
| 5 | Cabeceio | 12 | Resistência |
| 6 | Cruzamento | 13 | Velocidade |

### Estados brasileiros (campo `b` do time, 0-based)

0 Acreano, 1 Alagoano, 2 Amazonense, 3 Amapaense, 4 Baiano, 5 Cearense, 6 Brasiliense, 7 Capixaba, 8 Goiano, 9 Maranhense, 10 Mineiro, 11 Sul-matogrossense, 12 Matogrossense, 13 Paraense, 14 Paraibano, 15 Pernambucano, 16 Piauiense, 17 Paranaense, 18 Carioca, 19 Potiguar, 20 Rondonense, 21 Roraimense, 22 Gaúcho, 23 Catarinense, 24 Sergipano, 25 Paulista, 26 Tocantinense

### Países

Tabela completa de 224 países em `countries.json` (índice -> nome). Exemplos confirmados: 3=Alemanha, 5=Angola, 11=Argentina, 21=Bélgica, 29=Brasil, 65=Espanha, 72=França, 104=Itália, 154=Portugal. As bandeiras correspondentes ficam em `aflags/<id>.png` dentro do JAR do jogo.

## Configurações de liga - `est.*` (não ofuscadas)

As classes `est.ConfigLigaType`, `est.ConfigEstadualType` e `est.Options` **não são ofuscadas**: os nomes de campos serializados já são auto-explicativos (`nTimes`, `nRebaixados`, `doisTurnos`, `divisao`, `pais`, `formula`, `desempate`, `nGrupos`, `melhoresTerceiros`, etc.). Use `ban2json.py --raw` ou qualquer parser de serialização Java para inspecioná-las. `versaoArquivo=22` nos `.cfg` distribuídos.

### Semântica dos `.cfg` de liga nacional: sobrescrita por divisão - CONFIRMADO

Os dois `.cfg` distribuídos (BRA, ESP) **não são o conjunto completo das ligas**: são sobrescritas
colocadas por cima de um gerador de pirâmide embutido no jogo (mecanismo completo na seção 1.9 da
SIMULATION-SPEC). Regras de carga:

- Na criação do mundo, **todos** os `.cfg` de `conf_ligas_nacionais/` são lidos e suas entradas
  `ConfigLigaType` concatenadas numa lista única. **O nome do arquivo é irrelevante** para a carga -
  cada entrada carrega o próprio `pais` e a própria `divisao`; um arquivo pode, em tese, configurar
  vários países.
- A consulta é por par `(pais, divisao)`: vence a **primeira** entrada da lista que casa. Sem
  entrada para o par - ou com `nTimes` maior que os clubes restantes do país - vale o **padrão
  embutido** daquela divisão (tamanho em degraus 20/18/16/14/12/10, 4 rebaixados no degrau 20 e 2
  nos demais, pontos corridos, turnos pelo padrão de tamanho).
- `nRebaixados > 2` com `nTimes <= 10` é grampeado para 2 na carga.
- Um `.cfg` **não cria liga**: só configura divisões de um país que já é elegível (>= 10 arquivos
  de time; >= 16 para ALE/ARG/ING/ITA/FRA) e que o usuário ativou na criação do jogo. Também **não
  lista times**: a atribuição de clube a divisão é sempre por nível decrescente (seção 1.9 da
  SIMULATION-SPEC).
- O editor de ligas do jogo grava um arquivo **por país**, nomeado `<SIGLA>.cfg` (sigla de 3 letras
  da tabela de países), substituindo na lista global todas as entradas daquele país.

### `formula` (campeonatos estaduais - `ConfigEstadualType`) - CONFIRMADO

Índice num preset de estrutura do campeonato. O preset é uma linha de cinco números:
`[nTimes, nGrupos, classificados, doisTurnos, nRebaixados]`. Tudo o que o jogo precisa saber sobre o
tamanho e a forma do campeonato vem dessa linha; o `.ces` só guarda o índice.

| # | Rótulo na interface | nTimes | nGrupos | Classificados | doisTurnos | nRebaixados |
|---|---|---|---|---|---|---|
| 0 | 6 times - padrão | 6 | 0 | 2 | sim | 2 |
| 1 | 8 times - 4 classificados | 8 | 0 | 4 | sim | 2 |
| 2 | 10 times - 4 classificados | 10 | 0 | 4 | não | 2 |
| 3 | 11 times - 4 classificados | 11 | 0 | 4 | não | 2 |
| 4 | 12 times - 4 classificados | 12 | 0 | 4 | não | 2 |
| 5 | 12 times - 8 classificados | 12 | 0 | 8 | não | 2 |
| 6 | 14 times - 8 classificados | 14 | 0 | 8 | não | 2 |
| 7 | 16 times - 4 grupos - SP 2021 | 16 | 4 | 2 por grupo (8) | não | 2 |
| 8 | 16 times - 4 classificados | 16 | 0 | 4 | não | 2 |
| 9 | 16 times - 8 classificados | 16 | 0 | 8 | não | 2 |
| 10 | 20 times - 4 grupos | 20 | 4 | 2 por grupo (8) | não | 4 |

"Classificados" é quantos times saem da primeira fase para o mata-mata; nos presets com grupos é por
grupo. "doisTurnos" diz se a primeira fase tem dois turnos (só nos formatos de 6 e 8 times) ou um.
"nRebaixados" é o número de rebaixados **efetivamente usado pelo jogo** (ver seção `.ces` abaixo: o
campo `nRebaixados` do arquivo é ignorado).

Nos dados distribuídos, a divisão 1 usa fórmulas variadas (0,1,2,4,7 conforme o estado) e as divisões
2/3/4 usam sempre `formula=0`.

> **Correção:** versões anteriores desta spec descreviam a quarta e a quinta coluna do preset como
> "flag de playoff" e "pernas". Não são: a quarta é o número de turnos da primeira fase e a quinta é
> o número de rebaixados. As pernas do mata-mata vêm de `finaisIdaVolta`.

### `desempate` - CONFIRMADO (corrigido)

**Mesmo significado nos dois formatos** (`.ces` e `.cfg`): é a checkbox **"Desempate por penalties"**,
e a polaridade é **invertida** em relação ao que parece intuitivo:

- **`0` = disputa de pênaltis LIGADA** (checkbox marcada). Todos os 25 estaduais e a Espanha usam 0.
- **`1` = disputa de pênaltis DESLIGADA**. O Brasil usa 1 - o Brasileirão é pontos corridos sem final, então o campo é irrelevante lá.

O valor é consumido só em confronto de mata-mata (fase 3), decidindo se o empate vai aos pênaltis.

> **Correção:** versões anteriores desta spec diziam que este campo escolhia o critério de desempate
> da tabela. **Não escolhe.** O critério de classificação é **fixo e único em todo o binário**:
> `pontos desc -> vitórias desc -> saldo desc -> gols pró desc` (critério brasileiro, aplicado a todos os países).

### `formula` nas LIGAS NACIONAIS != `formula` nos ESTADUAIS - CONFIRMADO

Nos `.cfg` de liga nacional, `formula` é na verdade o **número de turnos** (`numeroTurnos`) e só age em
ligas pequenas. Turnos padrão por tamanho: 8 times -> 4, 10 -> 4, 12 -> 3, 14 -> 3,
26/28/30/36 -> 1, demais -> 2; `formula` igual a 2 ou 3 reduz o padrão nos casos de 10/12/14 times.
(ESP divisão 3: 10 times, `formula=4` -> 4 turnos.) Nos `.ces` estaduais, `formula` continua sendo o
índice de preset de fase final da tabela acima.

### `finaisIdaVolta` (estaduais) - CONFIRMADO (corrigido)

Array de exatamente 3 ints. A posição é a **rodada do mata-mata, contada da primeira rodada disputada
até a final**, e qual rodada cada posição representa depende de quantos times o preset manda ao
mata-mata: com 2 classificados só a posição 0 conta (final); com 4, posição 0 = semifinal e 1 = final;
com 8, posição 0 = quartas, 1 = semifinal e 2 = final. Valor `2` = ida e volta; **qualquer outro
valor** (o `1` gravado pelo editor, ou o `0` que aparece no RJ) = jogo único. As posições além da
final não são lidas. Detalhes na seção seguinte.

> **Correção:** versões anteriores desta spec davam a ordem "semifinal, quartas?, final". A ordem é
> crescente por rodada, e a rodada de cada posição muda com o tamanho do mata-mata.

## Campeonatos estaduais - `.ces`

Esta seção descreve o formato com precisão suficiente para escrever um leitor sem ver os arquivos.
Tudo aqui é **CONFIRMADO**: a estrutura e os valores foram lidos dos 25 arquivos distribuídos (marcado
"lido dos arquivos distribuídos" onde essa é a única fonte) e as regras de carga e uso foram lidas da
lógica do jogo.

### Contêiner e classes

- Um `.ces` é um stream de serialização Java (magic `AC ED 00 05`), sem cabeçalho próprio e **sem
  campo de versão**: diferente do `.cfg`, não existe `versaoArquivo` no estadual.
- A raiz é um único objeto `est.ArrayLigaEType` (`serialVersionUID=1`) com **um só campo
  serializado**, chamado `a`, do tipo `java.util.ArrayList`. Cada elemento da lista é um
  `est.ConfigEstadualType`.
- `est.ConfigEstadualType` (`serialVersionUID=1`) tem **seis campos serializados**, na ordem em que a
  serialização padrão os grava (primitivos em ordem alfabética, depois os campos de objeto):
  `desempate` (int), `divisao` (int), `formula` (int), `id` (int), `nRebaixados` (int),
  `finaisIdaVolta` (int[]). Nenhuma das duas classes tem escrita customizada: é serialização de
  campos pura, e qualquer parser genérico de serialização Java lê o arquivo inteiro.
- **Uma entrada é uma divisão de um estado.** Lido dos arquivos distribuídos: os 25 arquivos têm
  **exatamente 4 entradas cada**, divisões 1 a 4 do mesmo estado, nessa ordem, e por isso todos têm o
  mesmo tamanho (432 bytes). Isso é consequência do editor, que sempre grava as quatro divisões (ver
  "Editor" abaixo), não uma exigência do leitor: o jogo aceita qualquer número de entradas.
- Campos que **não existem** no `.ces`, apesar de existirem no `.cfg`: `nTimes`, `nGrupos`,
  `melhoresTerceiros`, `doisTurnos`, `nPromovidos`, `pais`, `versaoArquivo`. Tamanho, grupos e turnos
  são derivados do preset de `formula`; "melhores terceiros" e promoção não são configuráveis no
  estadual.

### Campos da entrada

| Campo | Tipo | Significado | Faixa que o editor grava | Observado nos 25 arquivos |
|---|---|---|---|---|
| `id` | int | Estado, índice 0-26 da tabela "Estados brasileiros" (o mesmo índice do campo `b` do time) | 0..26; fora disso o editor grava -1 | sempre igual ao estado do nome do arquivo |
| `divisao` | int | Número da divisão dentro do estado | qualquer valor até 4 (o editor não rejeita 0 nem negativos); o jogo só consulta 1..4 | 1, 2, 3, 4 (uma de cada por arquivo) |
| `formula` | int | Índice do preset da tabela de `formula` acima | 0..10; valor 11 ou maior vira 0 | divisão 1: 0, 1, 2, 4 ou 7; divisões 2-4: sempre 0 |
| `nRebaixados` | int | Número de rebaixados **declarado**. **O jogo nunca lê este campo**: o número de rebaixados usado vem da quinta coluna do preset | 1..4 (outro valor é ignorado e fica o anterior; padrão 2) | sempre 2 |
| `desempate` | int | 0 = disputa de pênaltis ligada; 1 = desligada (ver seção `desempate`) | 0 ou 1 | sempre 0 |
| `finaisIdaVolta` | int[3] | Pernas de cada rodada do mata-mata, da primeira rodada à final; 2 = ida e volta, qualquer outro valor = jogo único | cada posição 1 ou 2; o editor só regrava as posições visíveis para o preset | `[2,2,2]` em 97 entradas; SP div 1 `[1,1,2]`; SC div 1 `[2,2,1]`; RJ div 1 `[2,2,0]` |

Valores padrão de uma entrada nova (o que o editor cria para uma divisão sem entrada): `formula=0`,
`nRebaixados=2`, `desempate=0`, `finaisIdaVolta=[2,2,2]`, `id` e `divisao` da posição no editor.

**O leitor do jogo não valida nada na carga.** As faixas acima são impostas pelo editor ao gravar;
a desserialização escreve os campos diretamente, então um arquivo editado à mão com `formula` fora
de 0..10 chega inteiro à criação do mundo e faz o jogo indexar fora da tabela de presets (falha).
Um leitor de reimplementação deve rejeitar `formula` fora de 0..10 e tratar `id` fora de 0..26 ou
`divisao` fora de 1..4 como entrada inerte (nunca casa numa consulta).

### Mapa arquivo -> estado

O **nome do arquivo é irrelevante para a carga**: cada entrada carrega o próprio estado em `id`. O
nome só importa para o editor, que grava um arquivo por estado chamado `<SIGLA>.ces`, com a sigla
tirada desta tabela (índice 0-26, na mesma ordem da tabela "Estados brasileiros"):

AC AL AM AP BA CE DF ES GO MA MG MS MT PA PB PE PI PR RJ RN RO RR RS SC SE SP TO

Lido dos arquivos distribuídos: existem 25 arquivos, e o `id` de todas as entradas de cada um é o
índice da sigla do nome. **Faltam `PI.ces` (16) e `SE.ces` (24)**; esses dois estados caem no formato
padrão (ver carga).

### Dados distribuídos por estado (lido dos arquivos distribuídos)

Em todos os 25 arquivos, as entradas das **divisões 2, 3 e 4 são idênticas**: `formula=0`,
`nRebaixados=2`, `desempate=0`, `finaisIdaVolta=[2,2,2]`. A divisão 1 varia:

| Arquivo | `id` | `formula` | Times | Mata-mata | `finaisIdaVolta` | Pernas resultantes |
|---|---|---|---|---|---|---|
| AC | 0 | 1 | 8 | 4 | 2,2,2 | semi ida e volta, final ida e volta |
| AL | 1 | 1 | 8 | 4 | 2,2,2 | idem |
| AM | 2 | 1 | 8 | 4 | 2,2,2 | idem |
| AP | 3 | 1 | 8 | 4 | 2,2,2 | idem |
| BA | 4 | 2 | 10 | 4 | 2,2,2 | idem |
| CE | 5 | 2 | 10 | 4 | 2,2,2 | idem |
| DF | 6 | 1 | 8 | 4 | 2,2,2 | idem |
| ES | 7 | 1 | 8 | 4 | 2,2,2 | idem |
| GO | 8 | 4 | 12 | 4 | 2,2,2 | idem |
| MA | 9 | 1 | 8 | 4 | 2,2,2 | idem |
| MG | 10 | 4 | 12 | 4 | 2,2,2 | idem |
| MS | 11 | 1 | 8 | 4 | 2,2,2 | idem |
| MT | 12 | 2 | 10 | 4 | 2,2,2 | idem |
| PA | 13 | 1 | 8 | 4 | 2,2,2 | idem |
| PB | 14 | 0 | 6 | 2 | 2,2,2 | final ida e volta |
| PE | 15 | 2 | 10 | 4 | 2,2,2 | semi ida e volta, final ida e volta |
| PR | 17 | 4 | 12 | 4 | 2,2,2 | idem |
| RJ | 18 | 4 | 12 | 4 | 2,2,0 | idem (a posição 2 não é lida) |
| RN | 19 | 1 | 8 | 4 | 2,2,2 | idem |
| RO | 20 | 1 | 8 | 4 | 2,2,2 | idem |
| RR | 21 | 1 | 8 | 4 | 2,2,2 | idem |
| RS | 22 | 4 | 12 | 4 | 2,2,2 | idem |
| SC | 23 | 4 | 12 | 4 | 2,2,1 | semi ida e volta, final jogo único |
| SP | 25 | 7 | 16 em 4 grupos | 8 | 1,1,2 | quartas jogo único, semi jogo único, final ida e volta |
| TO | 26 | 1 | 8 | 4 | 2,2,2 | semi ida e volta, final ida e volta |

`desempate=0` e `nRebaixados=2` em todas as 100 entradas.

### Carga na criação do mundo - CONFIRMADO

1. **Quando.** Os estaduais só existem se o país do jogo é o Brasil (29) e a opção `jogaEstadual`
   de `est.Options` (padrão ligada) está ligada. São montados depois das ligas nacionais do Brasil.
2. **Leitura.** Todos os arquivos de `conf_estadual/` cuja extensão (sem distinguir maiúsculas) é
   `.ces` são lidos, na ordem em que o sistema operacional lista o diretório, e as entradas de todos
   eles são **concatenadas numa lista global única**, como no `.cfg`. Se um arquivo falha ao
   desserializar, a leitura **para nesse arquivo**: as entradas já lidas ficam, os arquivos ainda não
   lidos são pulados. Não há validação de faixa na leitura.
3. **Quais estados ganham campeonato.** Contam-se os times brasileiros (país 29) por estado
   (índice 0..26; time com estado fora dessa faixa é ignorado). Um estado ganha campeonato se tem
   **6 ou mais times**. Não existe lista de estados no arquivo: um `.ces` **não cria campeonato**,
   só configura o de um estado que já é elegível.
4. **Fila de times do estado.** Os times do estado são ordenados por **nível decrescente**, com
   empate desfeito pelo mesmo número aleatório por clube usado na montagem das ligas nacionais
   (seção 1.9 da SIMULATION-SPEC). Seja `T` o total de times do estado.
5. **Divisões.** Para `divisao` = 1, 2, 3, 4, nessa ordem, **enquanto restarem 6 ou mais times na
   fila**:
   - Consulta-se na lista global a **primeira** entrada com `id` igual ao estado e `divisao` igual
     ao número da divisão. Entradas duplicadas para o mesmo par são ignoradas a partir da segunda;
     entradas com outros pares nunca são lidas.
   - **Formato padrão** se não há entrada, **ou** se o `nTimes` do preset da entrada é maior que
     `T` (o total do estado, **não** o que resta na fila). O padrão é o preset 0 com as opções
     iniciais: 6 times, sem grupos, 2 classificados, dois turnos, 2 rebaixados, **final ida e volta
     e pênaltis ligados**. Nesse caso `finaisIdaVolta` e `desempate` da entrada são ignorados mesmo
     que ela exista.
   - Senão, o formato vem do preset (`nTimes`, `nGrupos`, classificados, `doisTurnos`,
     `nRebaixados`) e da entrada (`finaisIdaVolta`, `desempate`). Nos presets 7 e 10 a primeira
     fase é só de jogos entre grupos diferentes.
   - A divisão toma os primeiros `nTimes` da fila e os remove. Como a checagem de tamanho é contra
     `T` e não contra o que resta, uma entrada de divisão 2 ou mais cujo preset pede mais times do
     que restam na fila não é tratada pelo original (ver item 69 de OPEN-QUESTIONS); com os
     arquivos distribuídos isso não ocorre, porque as divisões 2-4 pedem 6.
6. **Grupos reais de SP.** Se a divisão é a 1, o estado é SP (25), o preset é o 7 e a opção
   `usaGrupoPadraoEstadual` de `est.Options` (checkbox "Usar grupos reais se possível", padrão
   ligada) está ligada, o jogo procura entre os times do estado, **pela referência de arquivo do
   time** (campo `d` do `.ban`), esta lista fixa de 16, nesta ordem: corinthians_bra, santoandre_sp,
   interlimeirasp_bra, botafogosp_bra, saopaulo_bra, ferroviaria_sp, pontepreta_bra, saobento_bra,
   bragantino_bra, palmeiras, ituano_sp, novorinzontino_sp, miirassol_sp, santos, guaranisp_bra,
   saocaetano_bra (as duas grafias erradas são as do jogo e dos arquivos de time distribuídos). Se
   **todos os 16** são encontrados, são eles que formam a divisão 1, independentemente do nível, e
   os grupos são preenchidos **em sequência** na ordem da lista (4 consecutivos por grupo: A, B, C,
   D). Se faltar qualquer um, vale a regra geral (16 melhores da fila) e os grupos são distribuídos
   **em rodízio**: o k-ésimo time da fila vai para o grupo `k mod 4`.
7. **Sobras.** Quem sobra depois da última divisão criada fica na **reserva do estado** (sem divisão
   estadual), na ordem da fila. A reserva alimenta a promoção para a última divisão ao fim da
   temporada.
8. **Consequência com os dados distribuídos.** Nenhum estado tem times suficientes para uma segunda
   divisão (o maior é SP, com 19: 16 na divisão 1 e 3 na reserva), então **as 75 entradas de
   divisões 2-4 nunca são consultadas**. 18 estados têm 6 ou mais times (AL, AM, BA, CE, GO, MA, MG,
   MT, PA, PB, PE, PR, RJ, RN, RS, SC, SE, SP) e cada um ganha só a divisão 1. SE (sem arquivo) e
   AM (arquivo pede 8, o estado tem 6) caem no formato padrão de 6 times. Os nove estados sem times
   suficientes (AC, AP, DF, ES, MS, PI, RO, RR, TO) não têm campeonato, embora oito deles tenham
   arquivo.

### Editor - CONFIRMADO

O editor do jogo trabalha sobre a mesma lista global. Ao abrir um estado ele reconstrói a lista a
partir dos arquivos, filtra as entradas daquele `id` e, para cada divisão 1-4, fica com a **última**
entrada que casa (o carregador fica com a primeira: uma duplicata só é observável se as duas
divergirem). Divisões sem entrada ganham uma entrada nova com os valores padrão, adicionada à lista
global. O painel de cada divisão expõe apenas o preset (`formula`), a checkbox de pênaltis
(`desempate`) e as caixas de pernas visíveis para o preset (só a final; semifinal e final; ou quartas,
semifinal e final, sendo que os presets 7 e 10 mostram as três). `nRebaixados` não tem controle
nenhum, o que explica ser 2 em todas as entradas. "Salvar" grava **todas** as entradas da lista
global com aquele `id` em `<SIGLA>.ces` - daí os quatro registros por arquivo.

### O que a temporada faz com cada campo - CONFIRMADO

**Preset sem grupos (formulas 0-6, 8, 9).** Primeira fase em pontos corridos entre os `nTimes`
times, em dois turnos quando o preset diz `doisTurnos` (6 e 8 times) e em turno único nos demais,
classificação pelo critério fixo da seção `desempate`. Os `classificados` primeiros vão ao
mata-mata, emparelhados pela posição: com 2, a final é 1o x 2o; com 4, semifinais 1o x 4o e 2o x 3o;
com 8, quartas 2o x 7o, 4o x 5o, 1o x 8o e 3o x 6o, nessa ordem de chave. Nos jogos únicos o melhor
colocado é o mandante; na rodada de ida e volta o pior colocado recebe a ida e o melhor colocado
recebe a volta. Os vencedores seguem na ordem da chave (vencedor do 1o confronto x vencedor do 2o,
e assim por diante) até a final. A regra do gol fora de casa **não** se aplica aos estaduais.

**Preset com grupos (formulas 7 e 10).** Os 16 (ou 20) times formam 4 grupos de 4 (ou 5). Na
primeira fase cada time joga **uma vez contra cada time dos outros grupos** e nunca contra o próprio
grupo: um calendário fixo de 12 rodadas de 8 jogos (preset 7) ou 15 rodadas de 10 jogos (preset 10).
Classifica-se por grupo, com o critério fixo, e os 2 primeiros de cada grupo vão às quartas de final
**dentro do próprio grupo** (1o do grupo x 2o do grupo, para os grupos A, B, C, D nessa ordem);
depois as semifinais entre vencedores de A e B e de C e D, e a final. Em jogo único o 1o do grupo é
mandante; em ida e volta ele recebe a volta. Uma tabela geral com todos os times continua sendo
mantida ao lado dos grupos, e é ela que decide o rebaixamento.

**Melhores terceiros.** Não é configurável nem ativo em estadual: o carregador deixa a opção
desligada. Só existe nas ligas do `.cfg`.

**Pernas (`finaisIdaVolta`).** O jogo converte o array em sete booleanos: as posições 0-2 valem
"ida e volta" quando o inteiro correspondente é exatamente 2, e as posições 3-6 são sempre "ida e
volta". A rodada `r` do mata-mata (0 = primeira rodada disputada) usa a posição `r`. Como nenhum
preset estadual manda mais de 8 times ao mata-mata, só as posições 0-2 têm efeito, e num preset de 4
classificados só a 0 e a 1.

**Rebaixados e promovidos (`nRebaixados` do preset).** Ao fim da temporada, os rebaixados de uma
divisão são os `nRebaixados` **últimos da tabela geral** da primeira fase (também nos presets com
grupos: não é por grupo). Os promovidos da divisão de baixo são os `nRebaixados` da divisão de cima
tirados da sua **lista de mérito**: campeão, vice, depois os eliminados das rodadas anteriores do
mata-mata em ordem de chave, completando pela tabela geral se faltar. A troca entre duas divisões
adjacentes só acontece se as duas listas têm o mesmo tamanho (com os presets distribuídos, sempre
têm). A última divisão troca com a reserva do estado: saem os `min(nRebaixados, tamanho da reserva)`
últimos e entram os primeiros da reserva; os rebaixados vão para o **fim** da reserva e os promovidos
saem do **início** (fila). O campo `nRebaixados` do arquivo não participa de nada disso.

**Pênaltis (`desempate`).** Só age no mata-mata. Um confronto está empatado quando, contando cada
perna como um jogo, nenhum lado venceu mais jogos que o outro e o placar agregado é igual (sem gol
fora de casa). Com `desempate=0`, o jogo decisivo (o jogo único, ou a volta) vai a uma disputa de
pênaltis sorteada no fim da partida: cada lado sorteia um número de 2 a 8; se o do mandante é maior ou
igual ao do visitante, o mandante vence a disputa por `N x N-1`, onde `N` é o número sorteado pelo
mandante; senão o visitante vence por `N+1 x N`, com o mesmo `N`. Com `desempate=1` não há disputa, e **avança o time listado em segundo
no confronto**: em ida e volta é quem recebe a volta (o melhor colocado, ou o 1o do grupo); em jogo
único é o **visitante**, ou seja, o pior colocado. A intenção do original era claramente "vantagem
do melhor colocado", mas a comparação que decide isso confronta um valor com ele mesmo e cai sempre
no segundo listado; reproduza o efeito, não a intenção, no conjunto de regras `CLASSIC`.

## O que ainda falta identificar

- Paleta indexada por `o` (`corBase`) do time (0-18). **Provavelmente irrelevante**: o ícone de camisa
  é derivado das cores hex `cor1`/`cor2` por faixas de matiz (HSB), e o único leitor de `corBase` não
  é consumido em lugar nenhum - trate como legado.

Tudo o mais foi decodificado. **O formato de save (`sav/`) está documentado** em
`SIMULATION-SPEC.md` seção 2 (Kryo 4.0.2 auto-descritivo + backup `.sbck` + cabeçalho `.info`).

## Comportamento do jogo (motor de simulação, economia, evolução)

Ver o documento companheiro **`SIMULATION-SPEC.md`**: matemática de gols, agregados de força por
linha, disciplina/lesões, energia, evolução e declínio de jogadores, talento, contratos, salários,
valor de mercado, formações, táticas e estrutura de temporada.

## Ferramentas

- `ban2json.py` - converte um `.ban` (ou a pasta `teams/` inteira) para JSON legível. `--raw` mostra os campos originais sem decodificação.
- `countries.json` - tabela país-ID -> nome.
- `teams.json` - dump completo dos 703 times distribuídos (gerado com `ban2json.py`).
