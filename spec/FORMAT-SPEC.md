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

Índice num preset de estrutura de fase final (rótulos do array `best.aq.sN`, e parâmetros em `best.aq.sL[formula]`). `sL[formula] = [nTimes, nGrupos, nClassificados, flagPlayoff, pernas]`:

| # | Rótulo | nTimes | nGrupos | Classificados |
|---|---|---|---|---|
| 0 | 6 times - padrão | 6 | - | 2 |
| 1 | 8 times - 4 classificados | 8 | - | 4 |
| 2 | 10 times - 4 classificados | 10 | - | 4 |
| 3 | 11 times - 4 classificados | 11 | - | 4 |
| 4 | 12 times - 4 classificados | 12 | - | 4 |
| 5 | 12 times - 8 classificados | 12 | - | 8 |
| 6 | 14 times - 8 classificados | 14 | - | 8 |
| 7 | 16 times - 4 grupos - SP 2021 | 16 | 4 | (8) |
| 8 | 16 times - 4 classificados | 16 | - | 4 |
| 9 | 16 times - 8 classificados | 16 | - | 8 |
| 10 | 20 times - 4 grupos | 20 | 4 | (8) |

Nos dados distribuídos, a divisão 1 usa fórmulas variadas (0,1,2,4,7 conforme o estado) e as divisões 2/3/4 usam sempre `formula=0` (pontos corridos simples).

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

### `finaisIdaVolta` (estaduais)

Array de 3 ints (semifinal, quartas?, final - índices 0-2 selecionados na UI +1). Valor por posição: 1 = jogo único, 2 = ida e volta.

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
