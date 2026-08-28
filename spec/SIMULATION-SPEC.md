# Brasfoot 22-23 - Especificação comportamental do motor de jogo

Documento clean-room para reimplementação: descreve **o que o sistema faz** (fórmulas, constantes,
probabilidades, fluxo) - não reproduz código do jogo original.

Cada achado é marcado **CONFIRMADO** (lido diretamente da lógica) ou **INFERIDO** (dedução).

> Companion de `FORMAT-SPEC.md`, que cobre os formatos de arquivo. Este documento cobre o
> **comportamento**: simulação de partidas, economia, evolução de jogadores e estrutura de temporada.

---

## 0. Modelo de tempo e aleatoriedade

**Sem datas de calendário.** CONFIRMADO - o jogo não modela dias/meses. O tempo avança em
**rodadas** dentro de uma **temporada numerada** (contador de temporada global). Comparações do tipo
"temporada passada" são feitas como `temporadaAtual - 1`.

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

### 1.3 Geração de tabela de jogos CONFIRMADO
Número de turnos (returnos) por tamanho da liga:
8 times -> 4 turnos, 10 times -> 4, 12 times -> 3, 14 times -> 3, 26/28/30/36 times -> 1,
20 times (com um código de formato específico) -> 1, demais -> 2.
O campo `formula` do `.cfg` de liga nacional é na verdade **`numeroTurnos`** e só age nos casos de
10/12/14 times, reduzindo para 2 ou 3 turnos. (Nos estaduais, `formula` continua sendo o índice de
preset de fase final - classe diferente, significado diferente.)

### 1.4 Sequência de fim de temporada CONFIRMADO
Ordem exata: reconstrução de elencos dos participantes de liga -> reconstrução dos demais times ->
reset de temporada por time -> limpeza de vínculos órfãos -> êxodo de craques de clubes pequenos.

### 1.5 Demissão de técnicos CONFIRMADO
Para cada clube com técnico: calcula-se o **aproveitamento em vitórias da temporada anterior**
`pctVit = vitórias x 100 / jogos`. Sorteia-se `r` uniforme em `1..90`.
**Se `r > pctVit`, o técnico é demitido.** Ou seja, probabilidade de permanência ~ `min(pctVit, 90)/90`.
Um técnico com 45% de vitórias cai em ~50% dos casos; com >=90% é praticamente intocável.
Empates e derrotas não são distinguidos - só a taxa de vitórias importa.
(Técnicos da IA só são efetivamente trocados se o clube também terminou abaixo do 6º lugar.)
O treinador **humano** tem um mecanismo separado e mais rígido - confiança da diretoria, seção 6.7.

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
(2 jogos: 3 variantes, 3 jogos: 5, 5 jogos: 3, 10 jogos: 2), sorteadas por gravidade da
infração narrada (falta dura -> 2; carrinho por trás -> 3 ou 5; agressão/ofensa ao árbitro -> 5 ou 10).

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
   - Mundial: rep < 3 -> x0.55; = 3 -> x0.75; senão, fora do continente 0 -> x0.90.
   - Liga nacional: rep < 3 -> x0.85; = 3 -> x0.95.
   - Copa Nacional / Estadual: se mandante tem rep < 3 e visitante >= 3, **todo o visitante** x0.80.
4. **Retorna `s / 10`.**

**Energia NÃO entra em `B()`.** Nem moral, nem forma, nem capitão.

## 3.4 Agregados de linha

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

### Quem finaliza e quem dá assistência
**Finalizador** - sorteio ponderado entre os escalados (exceto goleiro): slots 2-9 -> peso 1;
slot 10 -> 8; 11-13 -> 4; 14-17 -> 8; 18-25 -> **22**.
Bônus por característica: **Finalização -> +4**; senão **Cabeceio -> +2** (**+2 extra se for zagueiro**).

**Assistência** - só em gols de bola rolando; **20% das vezes não há assistente**.
Pesos: slot 1 -> 1; **2 e 9 (laterais) -> 10**; 3-8 -> 2; **10 -> 10**; 11-13 -> 4; **14-16 -> 20**; 17-25 -> 10.
Bônus (só o primeiro ramo que casar): **Passe -> +10** (+5 se também Armação); senão Armação -> +2
(+2 se a 1ª característica for Drible); senão Drible -> +2 (+2 se a 1ª for Velocidade); senão
Velocidade -> +1 (+2 se lateral); senão Cruzamento -> +5 (+2 se lateral).
**Mais +20 para qualquer lateral quando a marcação do time é "Pesada".**

## 3.7 Tipo de gol

Sorteio `rand(1000)` no momento do gol:

| Faixa | Tipo | Probabilidade |
|---|---|---|
| < 900 | bola rolando | **90,0%** |
| 900-949 | **pênalti** | 5,0% |
| 950-979 | **falta direta** | 3,0% |
| 980-989 | **gol contra** | 1,0% |
| 990-994 | **gol olímpico** | 0,5% |
| >= 995 | bola rolando | 0,5% |

- Pênalti e falta: se o **batedor designado** estiver em campo, ele é creditado no lugar do sorteado.
- Olímpico: se o **cobrador de escanteio** estiver em campo, ele é creditado; senão, se o sorteado for goleiro, vira bola rolando.
- Gol contra: o autor é substituído por um jogador do time **que defende**, com pesos GOL 1, slot 2 -> 5, **slots 3-8 -> 18**, 9 -> 5, 10 -> 1, 11-13 -> 5, 14-25 -> 1. O gol continua contando para o time atacante.
- Assistência só é sorteada para gols de bola rolando.
- **Peculiaridade:** quando o tipo é pênalti e há time humano na partida, o gol **não** é somado ao placar - ele vira o pênalti interativo, que decide. Em IAxIA conta normalmente.

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
<970 -> 3 (7%), <=990 -> 5 (2%), senão 10 (1%). O jogador fica indisponível enquanto
`amarelos >= 3 ou gancho >= 1`; cumprir zera os amarelos (se >=3) ou decrementa o gancho. CONFIRMADO

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

## 3.10 Pênaltis

- **Disputa em IAxIA (não é chute a chute):** `x = rand(2..8)`, `y = rand(2..8)`; se `x >= y` o
  mandante vence por `(x, x-1)`, senão o visitante vence por `(x, x+1)`.
- **Pênalti interativo (partida ao vivo com time humano):** conversão base **70%**; batedor com
  **Finalização** ou "estrela vermelha" +10; "estrela" +5; goleiro com **Defesa Penalty** -10;
  goleiro estrela vermelha -10; goleiro estrela -5.
- **Disputa interativa:** **70% fixos por cobrança, sem nenhum atributo**. Melhor-de-5 e morte súbita;
  ordem dos batedores = posição desc (atacantes primeiro), força desc, energia desc.

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
Posse %, chutes, no alvo (gols + defesas), para fora, desarmes, passes errados e **faltas - cujo
contador existe mas nunca é incrementado (a linha de faltas é sempre 0x0)**.

## 3.14 Notas dos jogadores (pós-partida)

Base por resultado e força:

| Resultado | força <=30 | <=60 | <=90 | >90 |
|---|---|---|---|---|
| Empate | 5,5 | 5,8 | 6,2 | 6,8 |
| Vitória | 6,0 | 6,0 | 6,7 | 7,2 |
| Derrota | 5,0 | 5,2 | 5,5 | 6,0 |

Ajustes: fora de posição -1,5 (-1,5 extra se foi ao gol); **meias (10-17)** com mais posse +0,8
(ou +0,3 com prob. 1/3), +0,3 se volante, **+0,5 se a 1ª característica é Passe ou Armação**; com
menos posse -0,8 (ou -0,3), -0,5 se volante. Gols x+0,9; gols contra x-1,5; amarelos x-0,2;
vermelhos x-0,8; assistências x+0,4; chutes no alvo x+0,3. **Defensivos (1-13)**: venceu a conta de
desarmes +0,6 (ou +0,9 com prob. 1/3), +0,6 extra com prob. 1/3 para zagueiros/volantes; perdeu -0,5.
**Só goleiro**: -0,8 fixo; **+0,2 por chute no alvo sofrido**; +1,2 por pênalti defendido; +0,2/+0,2/+0,3
se o adversário chutou >10/>15/>20; gols sofridos >=5 -> -2,0, >=4 -> -1,5, >=2 -> -1,0, >=1 -> -0,5,
sem sofrer -> +1,0; se não enfrentou nenhum chute no alvo -> -1,5. **Slots 1-13**: jogo sem sofrer gol
+0,5 (+0,5 zagueiros). Estrela +0,4; estrela vermelha +0,6.
Teto 10; depois: < 15 min jogados -> -2,5, < 45 min -> -1,5; piso 2,0; e se jogou < 20 min e ficou em
2,0 -> nota 0 ("sem nota").

## 3.15 Bugs e esquisitices - decidir explicitamente ao reimplementar

1. **Mando invertido na conversão de chutes** (seção 3.6c): mandante converte ~8,8%, visitante ~11,1%; e o peso "para fora" é sobrescrito em toda partida com mando.
2. **Slot 18 não contribui para nenhum agregado** - um 3-4-3 tem o ataque calculado com 2 dos 3 atacantes, dividido por 3.
3. **Divisores fixos** (5/3/5) punem qualquer formação que não tenha exatamente 5 meias / 3 atacantes / 5 defensores.
4. **Peso de assistência inconsistente**: Velocidade vale +1 numa passagem e +2 na outra.
5. **Sobrescritas do limiar de cartão**: após 2 vermelhos vira `2 x limiarVermelho`; após 1 lesão vira `5 x limiarLesão` - ambos derrubam drasticamente os cartões no resto do jogo. Há ainda um ramo inalcançável (`> 10 amarelos`).
6. **Força exibida** usa `round(energia/100 x força)` com divisão inteira -> só mostra a força real com energia exatamente 100; caso contrário **exibe 0**. (Só display; o motor não usa.)
7. Um passe de relaxamento da escalação é inalcançável (limite do laço).
8. Os pools de minutos de substituição são **estáticos/compartilhados**, re-embaralhados por partida -> partidas consecutivas sorteiam minutos correlacionados. CONFIRMADO: os cinco pools (19-38, 5-15, 16-35, 36-42, 43-47) são criados uma vez no processo e apenas re-embaralhados no começo de cada partida; mandante e visitante tiram do **mesmo** embaralhamento, em posições fixas, e por isso os minutos dos dois lados nunca coincidem dentro de uma partida - **mas isso vale por pool, e não entre pools**: a janela de "correndo atrás" (19-38) e os pools de rotina 16-35 e 36-42 são embaralhamentos diferentes e se sobrepõem (em 19-35 e em 36-38), então um minuto de rotina de um lado ainda pode cair sobre um de "correndo atrás" do outro. É exatamente disso que o item 11 abaixo depende; ver o item 42 do `OPEN-QUESTIONS.md`.
9. Um bloco grande de constantes do motor (dez arrays de 3 elementos, ~12 escalares, 2 arrays de contadores) é declarado e **nunca usado** - resquício de um modelo antigo. Não portar.
10. Prorrogação nunca é simulada; empates em mata-mata vão direto para a fórmula abstrata de pênaltis.
11. **A janela de substituição do visitante é engolida pela do mandante.** As duas janelas do mesmo minuto são avaliadas na mesma passagem, o mandante primeiro; se o mandante **efetivamente trocou** naquele minuto, a janela do visitante nem é examinada. Como os minutos dos dois lados saem do mesmo pool sem reposição, isso só morde quando um minuto de "correndo atrás"/rotina do visitante coincide com o do mandante ou no intervalo, onde os dois são avaliados juntos - ali o visitante perde a janela sempre que o mandante trocou. CONFIRMADO
12. **A checagem de "não tire quem acabou de entrar" olha sempre a lista do mandante.** No sorteio aleatório das janelas de placar, o índice do time é comparado contra um valor que ele nunca assume, então a lista consultada é sempre a de substitutos que **entraram pelo mandante**. Efeito: o mandante nunca tira quem acabou de entrar (com uma única re-tentativa), e o visitante não tem proteção nenhuma - pode sacar num minuto o reserva que pôs em campo no minuto anterior. CONFIRMADO

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

**Consequência de design a preservar:** só o **atributo primário** (Gol para goleiros, Des para
zagueiros/laterais defensivos/volantes, Arm para armadores, Fin para atacantes) deriva da força do
próprio jogador. Todos os secundários derivam da **qualidade do clube** - logo são quase
independentes da qualidade individual na criação do mundo.

## 4.3 "Estilo" derivado (`ex`) - usado para elegibilidade de slot

Calculado uma vez a partir de posição + características. GOL e ZAG -> 0.
LAT -> 1 (ofensivo) se Velocidade/Cruzamento; 0 se Desarme/Marcação; senão 1 se Drible/Finalização/Passe/Armação.
MEI -> 1 se Passe/Finalização/Drible/Armação; 0 se Desarme/Marcação; padrão 1.
ATA -> 0 se Desarme/Marcação; **2 (ponta)** se Drible/Velocidade/Cruzamento; senão 1.

## 4.4 Força inicial na criação do mundo

```
clube em liga: div1 -> base=20, faixa=7 ; div2 -> 15,3 ; div3 -> 5,1 ; outra -> 1,1
seleções (por reputação): 5->22,7, 4->15,4, 3->5,1, 2->5,1, 1->5,1
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
Elenco inicial montado com **3 GOL, 4 LAT, 4 ZAG, 5 MEI, 4 ATA**, `força = nívelMapeado - 5 + rnd(8)`,
talento `es = 7 + rnd(4)`, idade `18 + rnd(12)`, contrato 180 dias.

## 4.5 Evolução semanal (todo domingo)

O tique semanal faz, nesta ordem: (1) a cada 4 semanas, re-sorteia o foco de treino dos clubes da IA;
(2) evolução de força de cada profissional + limpa a flag "jogou recentemente"; (3) desenvolvimento
de cada jogador da base; (4) avança o contador de 4 semanas.

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

Acumula `r`; quando o acumulador passa de 1,0 e força < 100 **e < teto**, força += 1.

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

### Distribuição para os 7 atributos
Modo natural (padrão): a taxa vai 100% para um atributo primário, 40% para um secundário e 30% para
um terciário, por posição/estilo (GOL: Gol, LAT def: Des/Arm/Fin, LAT of: Pas/Fin/Des,
ZAG: Des/Pas/Arm, VOL: Des/Arm/Fin, MEI: Arm/Fin/Des, ATA: Fin/Arm/Des).
**Gol e Desarme recebem x1,2** ao serem creditados.
Modo foco de treino (1 semana em 4, ou permanente para clube humano com treino manual): tudo vai para
um único atributo escolhido por posição, com **taxa dobrada**.
No **declínio**, só o atributo **primário** é decrementado - secundários acumulam mas nunca perdem ponto.

## 4.6 Base (juniores)

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

## 4.10 Estrela e topMundial

`topMundial` **implica** `estrela`.

| Sistema | estrela | topMundial |
|---|---|---|
| Força inicial | +9+rnd(3) | idem |
| Crescimento semanal | +0,01 | **+0,02** |
| Valor de mercado | x1,7 (x2 ou x3 na Europa) | x1,6 |
| Salário | bônus `forçax250` | + x1,4 |
| Nota da partida | +0,4 | +0,6 |
| Aposentadoria | idade efetiva -1 | idade efetiva -3 |
| Virar treinador ao aposentar | 1 em 5 | **1 em 2** |

**Como se ganha estrela:** 1% dos juniores gerados; 1 em 200 na promoção; e **ao fim de cada
temporada, o jogador de maior média de notas acima do limiar da competição** ganha a estrela.

**Como se ganha topMundial:** cada temporada em que o jogador figura entre os de elite da competição
incrementa um contador. Vira topMundial quem tem contador >= 2, clube, idade < 35 e:
clube europeu em {ESP, ING} -> contador >= 2; em {ITA, FRA, POR} -> >= 3; clube no Brasil -> >= 4.
Perde a flag automaticamente depois dos 34.

## 4.11 Aposentadoria e virada de temporada

Ordem da virada: (1) jogadores sem clube envelhecem e, passando de 35, são **reciclados** -
viram um novo jogador de `18+rnd(10)` anos com nome novo (piscina infinita de agentes livres);
(2) profissionais envelhecem, zeram registros e passam pelo teste de aposentadoria;
(3) juniores envelhecem e passam pelo teste de promoção; (4) valores e salários são recalculados.

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

## 5.5 Reputação (0-5) - o análogo mais próximo de "moral de clube"

Não existe moral/confiança de time. O que existe é **reputação**, alimentada por um saldo de pontos
de prestígio, e ela entra no motor pelos multiplicadores de competição e pelos pesos anti-goleada.

**Decaimento periódico:** rep 5 -> -6.000 pts (saldo < -90.000 -> cai para 4); rep 4 -> -600
(saldo < -9.000 -> cai para 3); rep 3 -> -50 (saldo < -1.000 -> cai para 2); rep 2 -> -5.
**Promoção:** saldo > 100.000 -> 5, > 10.000 -> 4, > 1.000 -> 3, > 100 -> 2, > 10 -> 1.

**Prêmios por título {campeão, vice}:** liga nacional {500, 90}, copa nacional {300, 50},
estadual {10, 5}, continental 1 {5.000, 1.000}, Mundial {40.000, 1.000}, continental 2 {2.000, 500},
Recopa {500, 0}, regional {50, 0}, Finalíssima {1.000, 500}.
Prêmios acima de 1.000 são multiplicados por **0,6** para clubes fora da Europa; título de liga em
divisão inferior vale fixos 50.

## 5.6 Jogadores designados

| Designação | Como é escolhida | Efeito real |
|---|---|---|
| **Capitão** | maior força, desempate por **maior idade** | **Nenhum** - só exibição |
| **Batedor de falta/pênalti** | força desc, primeiro **titular** com característica Finalização; senão primeiro titular não-goleiro | Creditado como autor em gols de pênalti (5%) e falta (3%), se estiver em campo |
| **Cobrador de escanteio** | só manual | Creditado em gol olímpico (0,5%) |
| **"Falso 9"** | só manual | **Nenhum** |

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
