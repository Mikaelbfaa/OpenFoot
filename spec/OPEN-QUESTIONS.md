# Lacunas e ambiguidades da spec

Comportamento que a spec não determina de forma única. Cada item registra a **resolução adotada** no
código, para que a decisão fique visível em vez de escondida dentro de uma função.

Regra: o motor nunca adivinha em silêncio. Se você encontrar uma lacuna nova, acrescente aqui e abra
uma issue com o rótulo `spec-gap`.

## Seções 3.4 a 3.6

### 1. Divisor do duelo de chute nas temporadas 1 a 4

A seção 3.5 lista `D = 8` para as temporadas 1 a 4 sem dizer a qual família de duelo se aplica, e só
então dá 11 e 10 "a partir da temporada 5".

**Resolução (INFERIDO):** 8.0 para os três duelos antes da temporada 5. Guardado num único campo do `RuleSet`,
então uma correção na spec é uma linha.

### 2. Numeração de temporada

Não está dito se a contagem começa em 0 ou 1.

**Resolução (INFERIDO):** base 1. A compressão começa quando `temporada >= 5`.

### 3. O piso de 0.2 contra os pesos anti-exploit em 3.6b

Lendo literalmente, o piso roda **depois** da atribuição anti-exploit, então 0.10 e 0.05 viram ambos
0.2 e os dois casos ficam indistinguíveis.

**Resolução (INFERIDO):** manter a ordem literal. Existe um teste que afirma que os dois casos são iguais, para
que a suposição fique visível. Evidência a favor: sem o piso, uma defesa com um zagueiro sofreria
**mais** finalizações do que uma com zero, o que não faz sentido.

### 4. O `round` do anti-exploit em 3.6c

A spec escreve `round(wDef x 0.2)` sem dizer a escala.

**Resolução (INFERIDO):** `bfRound` para inteiro, como todo `round` da spec. Para um peso de defesa perto de
1.0 isso dá 0, que então bate no piso de 0.2.

### 5. Onde entra o bônus de marcação no meio-campo

A spec diz que a marcação soma 0 / 0.04 / 0.08, mas não diz se antes ou depois do divisor.

**Resolução (MEDIDO, `LineAggregatesTest`):** somado ao **total** do meio-campo, antes do divisor fixo. É a única leitura que
produz os 0.008 e 0.016 na escala 0 a 10 que a seção 3.12 cita. No caso degenerado de menos de três
meias o bônus não é aplicado.

### 6. Escala do arredondamento do goleiro fora de posição

`round(GK x 0.2)` pode ser inteiro ou decimal.

**Resolução (MEDIDO, `LineAggregatesTest`):** inteiro, na escala 0 a 10. É a única leitura que reproduz o exemplo da seção 5.3: um
jogador de linha com força 70 no gol rende 1.0 contra 7.0 de um goleiro de verdade. Consequência
aceita: força 40 no gol dá legitimamente 0.0, e o piso de 0.2 da resolução de chute cuida disso.

### 7. O que conta como zagueiro

Tanto o anti-exploit de 3.6b quanto o bônus de cabeceio do finalizador falam em "zagueiro" sem dizer
se é posição natural ou slot.

**Resolução (INFERIDO):** faixa de slots 3 a 8, coerente com a regra da seção 5.1 de que o motor agrupa por
slot e nunca por posição natural. As duas leituras só divergem para um jogador improvisado.

### 8. Elegibilidade do finalizador

"Sorteio ponderado entre os escalados (exceto goleiro)" não diz se o banco entra.

**Resolução (INFERIDO):** somente slots 2 a 25, ou seja, apenas quem está em campo. Reservas têm peso base
zero, mas os bônus por característica são aditivos, então sem o filtro um atacante do banco poderia
finalizar.

### 9. Os números de alavanca da seção 3.16 não fecham

A seção 3.16 afirma que 20 pontos de diferença de força no meio-campo levam o duelo de posse de 55%
para "~69%" e o de chance de 50% para "~56%".

O primeiro não reproduz: a fórmula de 3.6a dá **67,07%** com divisor 8 e 63,84% com divisor 11.
O segundo não reproduz de jeito nenhum, porque **o duelo de chance não lê o meio-campo** - ele
compara ataque contra defesa.

Dos outros números de 3.16, três reproduzem exatamente em qualquer escalação: 0.614, 0.55 e 9,81%.
Os dois de conversão, 8,8% e 11,1%, reproduzem só de forma aproximada: o valor medido é 8,71% e
11,09%, e a diferença vem da alavanca anti-goleada da 3.6c, que a 3.16 não leva em conta. Os quatro
restantes - 0.565, 0.50, ~16 chutes e ~12,6 chutes - só reproduzem quando a linha de defesa e a linha
de ataque preenchem exatamente os divisores fixos da 3.4 (5 defensores, 3 atacantes), e mesmo assim os
volumes de chute só fecham depois de também recontar as posses como 47 em vez de 46, que é o item 28.
Num 4-4-2, a formação que a IA mais escolhe, essas duas linhas ficam desiguais e os quatro números não
fecham; ver itens 28 e 30.

**Resolução (MEDIDO, `DuelsTest`, `ShotResolutionTest`, `SanityCheckTest`):** tratar esse parágrafo
como narrativo. Testar apenas os valores exatos. Item aberto na spec.

## Seção 4 - criação do mundo

### 10. A spec não define ordem de sorteio na criação do mundo

A seção 0 diz que o original cria um gerador novo, sem semente, a cada sorteio. Não existe ordem a
imitar, e nenhuma seção descreve a criação do mundo como uma sequência.

**Resolução (INFERIDO):** definimos a nossa. A semente deriva por posição no mundo, nunca por contagem de
sorteios: `raiz -> WORLDGEN -> clube -> jogador`, com a chave do clube vinda do `fileRef`. Dentro de
um jogador os sorteios são sequenciais, na ordem estilo, força, atributos, bônus de característica,
contrato. Isso torna irrelevante a ordem em que os clubes são gerados, que é o que permite gerar em
paralelo sem mudar resultado.

### 11. Três linhas da tabela 4.2 não dão fórmula para Gol

Lateral ofensivo, volante e meia armador listam seis atributos. Falta **Gol** nos três. As outras
quatro linhas dão os sete, e toda linha de jogador de linha usa o mesmo idioma: `Gol = 1+rnd(k)`,
com k igual a 4 no lateral defensivo, 7 no zagueiro e 6 no atacante.

**Resolução (INFERIDO):** `Gol = 1+rnd(4)` nas três linhas omissas.

Vale registrar por quê, porque a leitura oposta parece mais conservadora e não é. Deixar o atributo
em zero também é uma escolha que a spec não faz: zero vem do vetor zerado da implementação, não do
documento. As duas leituras inventam alguma coisa.

O que decide é a seção 3.3. Ela já define o mecanismo para punir quem joga fora da posição: a
divisão pela metade. Deixar `Gol` em zero aplica uma segunda punição, não projetada, e só em três
dos sete arquétipos. Além disso cria uma assimetria arbitrária entre o lateral defensivo, que
recebe `1+rnd(4)`, e o lateral ofensivo, que é a mesma posição e difere apenas no estilo.

O efeito é pequeno: com os pesos do gol (0,60 para Gol) a diferença fica em torno de 0,1 na escala
0 a 10, e só aparece com habilidade individual ligada e um jogador de linha no gol.

Isto é **observável**, e a observação vale mais que o argumento: escale um jogador de linha no gol
com habilidade individual ligada e leia a coluna Gol na tabela de elenco.

### 12. A seção 4.4 descreve dois elencos e não diz qual se aplica

O bloco principal calcula a força de jogadores que já existem, e o parágrafo seguinte monta um
elenco do zero com 3 GOL, 4 LAT, 4 ZAG, 5 MEI, 4 ATA. Não está dito quando cada caminho vale.

**Resolução (INFERIDO):** o caminho principal vale para clubes que trazem elenco no arquivo; o elenco sintético
é para clubes sem elenco. Nesta versão só o primeiro é implementado, e o segundo fica registrado
para quando existir clube sem dados.

### 13. "base/faixa" na 4.4 contra "teto/piso" na FORMAT-SPEC

Os mesmos números (div1 20 e 7, reputação 5 dá 22 e 7) aparecem como base e faixa na SIMULATION-SPEC
e como teto e piso na FORMAT-SPEC.

**Resolução (INFERIDO):** vale a leitura da 4.4, porque a fórmula usa os dois de forma aditiva
(`força = nívelMapeado + base + rnd(3)`), o que não faz sentido para um teto. A FORMAT-SPEC descreve
o mesmo efeito por fora, olhando a faixa de valores que sai.

### 14. Não existe tabela de nível de país

A 4.4 escala a força pelo nível do país do clube (`nívelPaís <= 13` dispara multiplicadores de 0,40
a 0,75). Essa tabela está no código do jogo, não em nenhum arquivo de dados, então está fora do
alcance da regra clean-room.

**Resolução (INFERIDO):** `nível` vira campo do país no conjunto de dados, e o importador o **deriva dos
próprios dados**: o nível do clube mais forte que o país tem.

Esta resolução substitui uma anterior, que derivava o nível do ranking mundial da FIFA por faixas. A
derivação a partir dos dados é melhor por três motivos. Não depende de fonte externa nenhuma, então
não há licença a respeitar nem número inventado de memória. Fica na mesma escala que o nível de
clube, que é com o que a 4.4 compara. E é reproduzível: qualquer pessoa com a mesma instalação chega
na mesma tabela.

A derivação se valida sozinha no conjunto distribuído: os cinco países que ela classifica no topo
(nível 20) são exatamente os cinco que a 4.8 paga mais, uma tabela que a derivação não consulta. A
distribuição cai suavemente de 20 até 11, e só 23 dos 134 países ficam em 13 ou menos, cobrindo 34
dos 703 clubes.

Um país sem nenhum clube não tem como ser avaliado e cai num valor acima do limiar, para que a falta
de dado não enfraqueça um elenco em silêncio.

Continua sendo uma **divergência deliberada**: o original usa uma tabela própria e o CLASSIC não vai
reproduzir esses multiplicadores até que ela seja observada. Como é dado e não lógica, trocar a
tabela depois não mexe em código nenhum.

### 15. O que significa "-4 se > 4" na entrada A da 4.2

A 4.2 diz que na criação do mundo `A` é o nível mapeado do clube, "-4 se > 4". O parêntese admite
mais de uma leitura.

**Resolução (INFERIDO):** literal, `A = nívelMapeado - 4` quando `nívelMapeado > 4`, senão o próprio.

Na prática não há ambiguidade nenhuma: o nível do time vai de 6 a 20 (FORMAT-SPEC, campo `c`) e a
tabela de mapeamento devolve o próprio valor até 15, então o menor nível mapeado possível é 6. A
condição `> 4` é sempre verdadeira para qualquer clube que possa existir, e o ramo alternativo nunca
executa. Toda leitura do parêntese dá o mesmo resultado.

Vale notar que a leitura literal é **não monótona** se o ramo fosse alcançável: mapeado 4 daria
`A = 4` e mapeado 5 daria `A = 1`, um degrau para baixo. Como o ramo é inalcançável com os dados
reais, isso é só uma guarda defensiva. Existe um teste que fixa que níveis 6 a 20 sempre caem na
subtração, para que um conjunto de dados futuro com nível menor falhe alto em vez de produzir o
degrau em silêncio.

### 16. Desconto por temporada de chegada na temporada 1

O valor de mercado da 4.9 desconta por quando o jogador chegou ao clube, mas não diz o que vale para
quem já estava lá quando o mundo foi criado.

**Resolução (INFERIDO):** contam como "mais antigo", sem desconto. Ninguém chegou por transferência antes da
primeira temporada existir.

### 17. De onde vem o talento de um profissional na criação do mundo

O talento (`es`, o campo `hash` do arquivo) existe em todo jogador, mas a única distribuição que a
spec dá está na seção 4.6, que trata da base. A 4.4 só fixa `es = 7 + rnd(4)` para o elenco
sintético, e não diz nada sobre os profissionais que vêm do arquivo.

**Resolução (INFERIDO):** sortear pela mesma distribuição da 4.6, escolhida pela qualidade do clube. É a única
distribuição documentada, e nesta versão o valor é inerte de qualquer forma: todo efeito do talento
é condicionado a ter vindo da base, seja pelo `veio de base` do crescimento semanal, seja pelo
`desenvolvimento de base >= 60` do teto. Quando a base for implementada, este item precisa ser
revisto contra o que os arquivos realmente contêm.

### 18. A tabela de reputação da 4.4 não cobre reputação zero

A 4.4 lista as faixas por reputação de 5 até 1. A reputação vai de 0 a 5 (seção 5.5), então falta
uma linha.

**Resolução (INFERIDO):** reputação 0 usa a mesma faixa de 1, 2 e 3, ou seja base 5 e faixa 1. As três
reputações mais baixas já são indistinguíveis na tabela, então estender a menor para o zero não
inventa comportamento novo, só fecha o buraco. Há um teste que fixa que 0, 1, 2 e 3 dão o mesmo
resultado, para que a suposição fique visível.

Vale notar que este caminho só vale para seleções. Um clube em liga escolhe pela divisão e nunca lê
a reputação aqui.

### 19. A cadeia do lateral na 4.3 não tem padrão

A 4.3 fecha as cadeias do meia e do atacante com um padrão explícito, mas a do lateral termina numa
condição: "1 se Velocidade/Cruzamento; 0 se Desarme/Marcação; senão 1 se Drible/Finalização/Passe/
Armação". Um lateral que não casa com nenhuma das três fica sem estilo. As características de
jogador de linha vão de 4 a 13, e as duas que ficam de fora de todos os testes são Cabeceio e
Resistência, então um lateral com essas duas cai no vazio.

**Resolução (INFERIDO):** 0, defensivo. A última cláusula é condicional e entrega 1, então não casar com ela
significa não ser 1. O meia e o atacante recebem padrão 1 porque a spec diz isso explicitamente para
eles; o lateral não tem essa frase.

### 20. Uma fórmula para vários atributos: um sorteio ou um por atributo

A 4.2 escreve a mesma fórmula para mais de um atributo em dois lugares. Na linha do goleiro,
`Des/Arm/Fin = B+rnd(3)`. Na lista de bônus, `Armação -> Arm e Pas +B+rnd(5)`. Nos dois casos não
está dito se o `rnd` é sorteado uma vez e usado nos dois ou três atributos, ou uma vez por atributo.

**Resolução (INFERIDO):** um sorteio por atributo, nos dois casos.

O argumento é o efeito visível. Com sorteio único, todo goleiro do jogo sairia com desarme,
armação e finalização exatamente iguais entre si, e todo jogador com a característica Armação
teria o mesmo incremento em armação e passe. Isso seria um padrão perceptível na tabela de elenco,
e nada na spec sugere que ele exista. A notação compacta economiza três linhas de tabela, o que
explica a escrita sem implicar sorteio compartilhado.

Há testes que fixam as duas escolhas, então se a observação contradisser, o que muda é um teste
e uma linha.

### 21. A spec nomeia os cinco países que pagam mais, mas não publica os cinco índices

A 4.8 dá uma tabela de salário melhor para clubes de {ALE, FRA, ITA, ING, ESP}. A FORMAT-SPEC
publica o índice numérico de quatro deles (3 Alemanha, 65 Espanha, 72 França, 104 Itália) e não o da
Inglaterra. A tabela completa de 224 países está no arquivo `countries.json`, que não acompanha a
spec.

**Resolução (INFERIDO):** o índice da Inglaterra é **97**, e isto veio dos dados, não de chute.

A convenção de nome de arquivo marca cada clube com o país (`1deagosto_ang`, `barcelona_esp`), então
o sufixo dá o código do país de cada índice. O sufixo `ing` aparece no índice 97. E os índices
publicados pela FORMAT-SPEC estão em ordem alfabética portuguesa (3 Alemanha, 5 Angola, 11 Argentina,
21 Bélgica, 29 Brasil, 65 Espanha, 72 França, 104 Itália, 154 Portugal), o que coloca 97 exatamente
onde Inglaterra pertence, entre França e Itália.

O conjunto é `{3, 65, 72, 97, 104}`. Evidência independente: a derivação de nível de país do item 14,
que não consulta esta lista, classifica exatamente esses cinco no topo.

O campo `majorLeague` do país continua existindo, porque o mesmo conjunto reaparece na 4.5 (bônus
continental de crescimento) e parcialmente na 4.10 (limiares de topMundial), e porque um conjunto de
dados que não venha de uma instalação precisa poder dizer isso por conta própria.

### 22. Onde entram os multiplicadores da 4.9

O bloco da 4.9 lista os multiplicadores (estrela, topMundial, atacante, titular) entre a definição
de `baseNível` e o termo de idade, mas a linha que produz o resultado, `valor = quadrático x
baseNível`, vem depois. O texto não diz sobre o que eles incidem.

A escolha muda o número. Um titular estrela de força 50, 24 anos, clube nível 20:
- multiplicando o `baseNível` antes do termo de idade: `600 x 1,7 = 1020`, mais 176, dá 11,96 M.
- multiplicando o valor pronto: `(600 + 176) x 1,7`, dá 13,19 M.

**Resolução (INFERIDO):** incidem sobre o valor pronto, depois de o `baseNível` estar completo.

A aferição da própria spec (`100^2 x (600+176)`) prova que o termo de idade entra **dentro** do
`baseNível`, então o `baseNível` está fechado antes de qualquer multiplicação. E os descontos por
temporada de chegada, que vêm logo abaixo na mesma lista e claramente incidem sobre o valor, dão o
padrão de leitura para os multiplicadores acima deles.

Como todos são multiplicações, a ordem entre eles não importa; só importa estarem depois da soma.
Há um arredondamento único no fim, então nem a ordem entre eles muda o centavo.

### 23. Atributos individuais são sempre gerados, mesmo com a opção desligada

A FORMAT-SPEC diz, como CONFIRMADO, que o original só gera os sete atributos individuais quando a
opção `habilidadeIndividual` está ligada. Com ela desligada o jogador tem só a força.

**Resolução (INFERIDO):** geramos sempre. Custa sete sorteios e não é observável com a opção desligada, porque
nesse modo o motor lê a força e nunca olha os atributos. Em troca, o jogador é o mesmo jogador
independentemente de como a opção está, o que evita que ligar a opção no meio de uma carreira mude
quem cada um é.

Isto é uma simplificação deliberada, não uma leitura da spec. Se algum dia a opção puder ser trocada
com efeito visível, este item vira uma decisão de verdade.

### 24. Nenhum arquivo diz em qual divisão cada clube joga

O arquivo do time não tem campo de divisão. O arquivo de configuração da liga nacional descreve a
forma da pirâmide (`pais`, `divisao`, `nTimes`, `nRebaixados`) e **não tem lista de times**. Ou seja,
a associação clube-divisão não está em nenhum dado distribuído, e a divisão escolhe a base de força
da 4.4 (20 na primeira divisão contra 1 fora da pirâmide), então errar isso muda todo jogador.

**Resolução (INFERIDO):** ordenar os clubes de cada país por nível, decrescente, e preencher cada divisão na
ordem até o `nTimes` dela.

A evidência é forte e verificável: no conjunto distribuído, os vinte clubes brasileiros de maior
nível são exatamente os vinte que disputaram a Série A de 2022, e os vinte seguintes são exatamente
os da Série B. O corte de nível cai exatamente na fronteira (níveis 19, 18, 17 e 16 somam vinte
clubes), então nem empate houve ali.

Empates são desfeitos pela referência do arquivo. Nas divisões de baixo os níveis se repetem muito
(cinquenta clubes brasileiros no nível 7), e sem critério fixo a mesma base de dados geraria
pirâmides diferentes a cada execução, quebrando toda semente já compartilhada. O critério é
arbitrário e está registrado como tal.

Isto é **observável**: basta abrir o jogo e ver quem está em cada divisão. Uma observação que
contrarie a ordenação por nível derruba esta resolução, e o custo é uma função.

### 25. A distribuição de talento nos arquivos não é a da seção 4.6

A 4.6 dá distribuições de talento por qualidade do clube, com pico em 5 e 6 (25% a 35% cada). O
campo `hash` dos 703 arquivos distribuídos é quase **uniforme**: cada valor de 1 a 10 aparece entre
1400 e 1800 vezes, e o 0 aparece 186 vezes.

**Resolução (INFERIDO):** as duas coisas não estão em conflito, e o item 17 partia de uma premissa errada. As
distribuições da 4.6 descrevem a **geração de um júnior novo em tempo de execução**, não o conteúdo
dos arquivos. Um profissional importado traz o talento dele no arquivo e nada precisa ser sorteado.

O item 17 fica revisto: só há sorteio quando a base passar a gerar jogadores, e aí a distribuição da
4.6 é que vale.

### 26. Nenhum arquivo diz o continente de um país

A 3.3 usa o continente do clube no deságio do Mundial de Clubes, e a 4.9 usa a nacionalidade europeia
num degrau de valor de mercado. Nenhum arquivo distribuído tem campo de continente, e a tabela está
no código do jogo.

**Resolução (INFERIDO):** por enquanto o importador grava um continente que não é a Europa, e registra isso.

O motivo de não inventar a tabela agora é que ela é inerte: o Mundial de Clubes precisa de
competições, que não existem, e o degrau de valor da 4.9 precisa de clube de nível 21 ou mais, que
nenhum arquivo expressa. Escolher "não Europa" garante que a falta de dado não conceda isenção
europeia a ninguém, que é o erro que passaria despercebido.

Quando as competições chegarem, isto deixa de ser inerte e precisa de tabela de verdade.

### 27. Um país sem arquivo de liga deixa todos os seus clubes fora de qualquer divisão

O item 24 resolve **como** ordenar os clubes de um país dentro da pirâmide dele. Não trata do caso
em que não há pirâmide nenhuma, que na instalação distribuída é o caso dominante e não a exceção:
`conf_ligas_nacionais/` traz apenas `BRA.cfg` e `ESP.cfg`, então **533 dos 703 clubes ficam sem
divisão**.

Isso não é um detalhe de calibragem. A divisão entra em três lugares e todos empurram na mesma
direção:

| Onde | Divisão 1 | Sem divisão |
|---|---|---|
| Base de força da 4.4 | 20 | 1 |
| Teto de crescimento da 4.5 | 80 a 100 por reputação | 30 |
| Piso de declínio da 4.5 | 35 | 1 |

O efeito já é visível num mundo gerado. Bayern e Real Madrid têm o mesmo nível 20 nos arquivos, ou
seja, os dados dizem que são equivalentes:

```
realmadrid_esp   div 1     melhor 67  Benzema
liverpool_ing    div nula  melhor 47  Salah
bayern_ale       div nula  melhor 45  Neuer
juventus_it      div nula  melhor 36  Szczesny
milan_it         div nula  melhor 33  Calabria
```

E a parte que ainda não aparece é pior que essa. Quando a evolução semanal da 4.5 entrar, o Neuer
com 45 já está acima do teto de 30 do `div0`, logo nunca cresce, e declina rumo ao piso de 1.
O Benzema cresce rumo a 100 e nunca cai abaixo de 35. Em poucas temporadas todo país sem arquivo de
liga desaba, o que o jogo original claramente não faz.

**Resolução atual (EM ABERTO):** nenhuma. O importador registra uma nota dizendo quantos clubes ficaram sem
divisão, e a nota subestima o problema porque só menciona a base de geração da 4.4.

**Hipótese a testar:** os dois `.cfg` são configurações **sobreponíveis** pelo usuário, colocadas por
cima de uma tabela de ligas embutida no jogo, e não o conjunto completo das ligas. Isso explicaria ao
mesmo tempo por que só dois arquivos são distribuídos e por que o Bayern não é fraco no jogo real.
A tabela embutida ficaria no código, fora do alcance da regra clean-room, exatamente como a tabela de
nível de país do item 14.

**Como decidir:** é observável. Basta abrir o original e ver se a Alemanha tem liga com divisões, e
quantas. Se tiver, o recurso não pode ser `div0`, e a saída provável é derivar uma pirâmide sintética
para país não configurado a partir do nível dos clubes, em vez de jogar todos na faixa mais fraca.

Enquanto isso não for decidido, qualquer aferição estatística feita fora do Brasil e da Espanha mede
este item e não o motor.

### 28. A contagem de tiques da 3.1 não bate com a da 3.16

A 3.1 dá `extra1 = rand(0..2)` e `extra2 = rand(1..5)`, com o primeiro tempo nos minutos
`0..44+extra1` e o segundo em `0..44+extra2`. Isso dá `45+extra1` mais `45+extra2` tiques, ou seja
de 91 a 97, que é exatamente a faixa que a própria 3.1 afirma. A média é `45+1+45+3 = 94`, logo
cada time é possuidor 47 vezes.

A 3.16 fala em cerca de 92 tiques e cerca de 46 posses por time, e os números derivados dela são
aritmética sobre 46, e não sobre 47:

```
chutes do mandante   46 x 0,614 x 0,565 = 15,96   a spec diz "~16 chutes"
chutes do visitante  46 x 0,55  x 0,50  = 12,65   a spec diz "~12,6 chutes"
```

Com 47 o número do visitante seria 12,93, que teria sido escrito 12,9. Ou seja, a 3.16 foi calculada
com 92 enquanto a 3.1 produz 94.

**Resolução (MEDIDO, `SanityCheckTest`):** implementar a 3.1 ao pé da letra, porque a faixa de 91 a 97 que ela declara é
consistente com as fórmulas dela mesma, e as cifras com til da 3.16 não são. A validação deriva a
contagem esperada de chutes da posse **medida**, nunca de um 46 fixo. É o mesmo tratamento que o
item 9 já dá ao parágrafo de alavanca da 3.16.

Isto é **observável**: contar os minutos de uma partida no jogo original resolve.

### 29. A posse exibida não chega aos 55/45 da 3.16

A 3.5 diz que a porcentagem exibida vem de um contador separado de vitórias no duelo de posse.
Contando o vencedor do duelo a cada tique, o mandante fica, em 92 tiques:

```
46 x 0,614 + 46 x (1 - 0,55) = 28,24 + 20,70 = 48,94 de 92 = 53,2 por cento
```

A 3.16 diz cerca de 55/45.

**Resolução (MEDIDO, `SanityCheckTest`):** contar o vencedor do duelo a cada tique, que é a única leitura que a frase da 3.5
admite. A validação verifica uma faixa que contém 53,2 e exclui 50,0 e 60,0, e registra o valor
medido, para que uma correção futura da spec tenha com o que comparar.

A diferença é pequena e pode ser só arredondamento generoso da 3.16, mas registrar é mais barato
que redescobrir.

### 30. Os volumes de chute da 3.16 só aparecem com todas as linhas no divisor

A 3.16 dá `P(chute | posse)` de 0,565 para o mandante e 0,50 para o visitante, e deriva daí os
"~16 chutes" e os "~12,6 chutes". O 0,50 do visitante só sai se `ATAQUE(TB)` e `DEFESA(OPP)` forem
iguais, porque o duelo de chance da 3.6b compara **essas duas linhas** e não um time contra o outro.
Dois times equivalentes não bastam: as duas grandezas comparadas vêm de linhas diferentes. É a mesma
leitura que o item 9 já registrou ao notar que o duelo de chance não lê o meio-campo.

Com os divisores fixos da 3.4 (5 para a defesa, 5 para o meio, 3 para o ataque), a igualdade exige
5 defensores e 3 atacantes. Num 4-4-2, que é a formação que a IA mais escolhe, sobram 4 defensores e
2 atacantes, e com força 50 dos dois lados (nota 4,8 por jogador, já com o multiplicador 0,95 da liga
nacional para reputação 3) as linhas ficam desiguais:

```
DEFESA = 4 x 4,8 / 5 = 3,84      ATAQUE = 2 x 4,8 / 3 = 3,20

visitante  wA = 1 + (3,20 - 3,84)/8 = 0,92   wD = 1,08   P(chute) = 0,92/2,00 = 0,460
mandante   wA = 0,92 + 0,3        = 1,22   wD = 1,08   P(chute) = 1,22/2,30 = 0,530
```

Com 5 defensores e 3 atacantes as duas linhas valem 4,8, a diferença zera e voltam os 0,50 e 0,565
da 3.16. Medido em 20000 partidas com semente fixa, temporada 1, campo normal:

| Grandeza | 4-4-2 | Linhas no divisor | 3.16 |
|---|---|---|---|
| Chutes do mandante | 15,31 | 16,32 | ~16 |
| Chutes do visitante | 11,93 | 12,96 | ~12,6 |
| Gols do mandante | 1,333 | 1,421 | ~1,4 |
| Gols do visitante | 1,324 | 1,435 | ~1,4 |

"Linhas no divisor" é a única escalação de onze jogadores que põe defesa e ataque exatamente sobre os
seus divisores: 5 defensores, 3 atacantes e, por consequência, 2 meias. Ela reproduz a 3.16 ao
centésimo, o que localiza a diferença na escalação e não na montagem do motor. O meio-campo fica
desfalcado e cai para a nota degenerada de 0,01, o que não muda nada aqui porque acontece dos dois
lados e o duelo de posse lê só a diferença.

Note ainda que os "~12,6" da 3.16 são aritmética sobre 46 posses. Sobre as 47 que a 3.1 produz, a
mesma conta dá 12,92, que é o valor medido. O item 28 e este se somam.

O resto da 3.16 bate exatamente nas duas escalações: 0,614 e 0,55 no duelo de posse, e 8,71 por cento
contra 11,09 por cento na conversão, ante os 8,8 e 11,1 da 3.15. Ou seja, o mando invertido está
reproduzido; o que não reproduz é só o volume.

**Resolução (MEDIDO, `SanityCheckTest`):** tratar os volumes de chute da 3.16 como calculados sobre uma escalação com todas as
linhas cheias, e não sobre uma escalação real. A validação afirma as duas coisas: as faixas medidas
do 4-4-2, que é o caso que o jogo produz, e a reprodução exata da 3.16 com as linhas no divisor, que
é a prova de que a diferença vem do defeito 3 da 3.15 (divisores fixos) e não de um erro de
montagem. Nenhuma faixa foi alargada para acomodar a 3.16.

Isto é **observável**: ler a média de chutes de uma temporada IA contra IA no jogo original, junto
com a formação escalada, resolve.

### 31. A partir de qual minuto do tempo se conta o desgaste de 7 em 7 minutos da 3.9

A 3.9 diz que o desgaste acontece "a cada 7 minutos" e dá "~7 descontos por tempo", mas não diz se a
contagem começa no primeiro minuto do tempo ou no sétimo. A seção também afirma que um jogador de 24
anos "perde ~28 de energia por partida completa".

Um jogador de 24 anos cai na faixa `<=25 -> 2`, então perde 2 por desconto. 28 de energia implica 14
descontos na partida inteira, ou seja 7 por tempo, não 6.

Contando os minutos de um tempo de 45 a partir de zero, os descontos caem em 0, 7, 14, 21, 28, 35 e
42, o que dá exatamente 7 descontos. Contando a partir de um, os descontos cairiam em 7, 14, 21, 28,
35 e 42, o que dá 6.

`7 descontos x 2 de custo = 14 energia por tempo x 2 tempos = 28`, batendo com a spec. `6 x 2 = 12 x
2 = 24`, que não bate.

**O que o código faz, ao lado da derivação.** A derivação acima é sobre um tempo regulamentar de 45
minutos. `drainsThisMinute` não conta sobre 45: conta sobre o tempo de verdade, do zero ao último
minuto que o relógio da 3.1 sorteou. Como `matchClock` sorteia o acréscimo do segundo tempo em 1 a 5,
o segundo tempo tem de 46 a 50 minutos, e num tempo de 50 minutos o deslocamento 49 também é múltiplo
de 7. Isso dá um **oitavo** desconto. O primeiro tempo nunca faz o mesmo, porque o acréscimo dele é
de 0 a 2 e ele para em 47 minutos.

Ou seja, a partida mais longa que a 3.1 permite custa 30 de energia a um jogador de 24 anos, e não os
28 que a 3.9 cita; 28 é o custo de uma partida de dois tempos regulamentares. As duas afirmações
convivem, e a derivação continua sendo o que decide a pergunta deste item, que é de qual ponta contar,
não quantos descontos cabem numa partida real. Fixado em `EnergyTest`, teste "the longest legal second
half drains an eighth time".

**Resolução (INFERIDO):** contar os minutos de cada tempo a partir de zero, reiniciando a contagem no
início do segundo tempo. É a única leitura das duas que reproduz os ~28 de energia que a 3.9 cita
para um jogador de 24 anos numa partida de dois tempos regulamentares. Testado em `EnergyTest`.

### 32. Quantos passes de relaxamento a escalação automática de fato executa

A 3.2 descreve a busca de cada slot como **3 passes** (exato -> ignora lado -> ignora lado+papel) e o
item 7 da 3.15 diz que **um** passe de relaxamento é inalcançável por causa do limite do laço. A spec
não diz qual, nem quantos sobram.

Duas leituras competem, e elas não diferem em detalhe: uma cria uma divergência entre CLASSIC e
MODERN, a outra não cria nenhuma.

**Leitura A, adotada.** O inalcançável é o terceiro dos passes de lado e estilo, o que ignora
lado+papel. "Limite do laço" descreve a condição de parada, e um limite errado corta a **última**
iteração; cortar uma do meio seria defeito no corpo do laço, não no limite.

**Leitura B.** A 5.4 chama o laço **externo** de "relaxamento de posição", então "um passe de
relaxamento" pode ser a última posição de cada cascata, e não um dos três passes internos. Nesse caso
os três passes rodam sempre, o defeito é outro, e **não existe divergência de contagem** entre
CLASSIC e MODERN neste ponto.

Três argumentos pesam a favor de A:

1. A 3.2 usa a palavra "passes" **só** para o trio lado/estilo. O laço externo ela chama de "cadeias
   de preferência por posição", nunca de passe.
2. O item 5 da mesma 3.15 escreve "ramo" quando quer dizer ramo: "Há ainda um ramo inalcançável
   (> 10 amarelos)". O vocabulário da lista distingue os dois, então "passe" no item 7 parece
   deliberado.
3. A 5.3 afirma que lado errado **não tem penalidade nenhuma** e é "só preferência na escalação
   automática". Isso seria falso se o passe exato nunca rodasse, porque o lado deixaria de ser
   preferência e viraria letra morta. Logo o corte não é no primeiro passe, e entre os outros dois
   vale o argumento do limite.

**Por que dois passes quase nunca são dois.** Pela tabela do item 34, 19 das 25 células não pedem
lado nenhum. Numa célula central os dois passes alcançáveis do CLASSIC testam exatamente a mesma
condição, o sub-papel, porque o único filtro que o segundo passe larga é o lado e ali não havia lado
a largar. Ou seja, na maior parte da grade o CLASSIC tem **um** filtro distinto, não dois, e nunca
chega a ignorar o sub-papel. É essa colapsagem, e não o número 2 em si, que faz uma célula central
desistir da posição inteira em vez de afrouxar o estilo.

**Pegada.** Não é caso de canto. Levantamento sobre os dados do original: **29,7% dos times**
carregam menos de dois meias defensivos e **14,9%** menos de duas pontas. Nesses times, sob a leitura
A, toda célula de volante (11-13) que não acha volante natural sobrando desiste do meio-campo e desce
a cascata - num time com um volante só, é a segunda célula; sem nenhum, são todas. Como as células de
defesa vêm **depois** das de meio em toda formação, ela encontra a defesa inteira disponível e escala
um zagueiro de volante, empurrando a defesa para improvisos em cadeia. Sob MODERN o mesmo time põe o meia
ofensivo no volante e mantém a defesa inteira. Um 4-4-2 é afetado em quase um terço dos elencos.

**O goleiro reserva vai a campo por essa cascata, numa célula de defesa.** É a consequência mais cara
e foi medida, rodando o motor, depois de eu ter argumentado que ela não podia acontecer. Elenco na
forma ideal da 5.7 (2 goleiros de 62 e 48, 3 laterais ofensivos, 3 zagueiros, 5 meias ofensivos, 3
atacantes), formação 4, CLASSIC:

| Célula | 1 | 22 | 24 | 11 | 13 | 14 | 16 | 2 | 9 | 3 | 5 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Escalado | GOL 62 | ATA 82 | ATA 79 | ZAG 68 | ZAG 67 | MEI 80 | MEI 78 | LAT 66 | LAT 64 | ZAG 65 | **GOL 48** |

As duas células de volante comem dois zagueiros, as de lateral comem dois laterais, a célula 3 leva o
último zagueiro, e a célula 5 fica sem zagueiro nenhum: recusa por sub-papel o lateral, os meias e o
atacante que sobraram, todos ofensivos, e chega ao fim da cadeia, onde está o goleiro reserva, que é
defensivo. **Não é o preenchimento final do item 35**: quando a célula 5 é preenchida sobraram o
atacante de 77, os meias de 76, 75 e 74, o lateral de 63 e o goleiro de 48, então o preenchimento
final teria escalado o atacante de 77, o mais forte deles, e não o jogador mais fraco do elenco. Sob MODERN o mesmo elenco não põe goleiro nenhum na linha. A formação
5, que tem três células de volante, faz o mesmo com um volante natural no elenco, e aí o goleiro cai
na célula 8.

O erro do meu argumento anterior era olhar só a célula 11, onde de fato sempre há zagueiro
disponível, e esquecer que são as próprias células de volante que esvaziam a defesa antes de as
células de defesa serem preenchidas.

**Com que frequência isso acontece.** Contado sobre os dados do original rodando o nosso motor, não
estimado no lápis, porque a resposta útil depende da formação e a IA sorteia doze. Leia o parágrafo
"O que estes números são" mais abaixo antes de citar qualquer número desta seção. Método: importar a instalação local do original (703 clubes, 14672
jogadores), gerar o mundo, e para cada clube montar a escalação em cada uma das onze formações que a
IA sorteia, pesando cada formação pela largura da sua faixa na tabela de sorteio da 3.2. Repetido com
as sementes 1, 2 e 3, porque a força sai da criação do mundo e a força é o que ordena o pool.

Resultado, estável nas três sementes: **1,85% das escalações da IA põem um goleiro em célula de
linha** sob CLASSIC, e **0,00%** sob MODERN, em todas as formações. Como são dois lados por partida,
cerca de 3,7% das partidas têm pelo menos um time assim.

O número por formação, em times de 703, mostra onde o defeito mora: 5-4-1 e 4-4-2 def 35, 3-4-3 cerca
de 40, 3-5-2 34, e **4-4-2 apenas 3** (0,4%). São as formações com mais células de sub-papel
defensivo (três de zagueiro, ou três de volante) que esgotam os zagueiros antes das células de
defesa. O 4-4-2 do exemplo acima é, portanto, o caso raro da formação comum, e não o caso típico.

Duas frequências de apoio, ambas conferidas no mesmo levantamento e ambas **sem condicionar a
formação**: 53 dos 703 times (7,5%) não têm nenhum volante natural, e 156 (22,2%) têm exatamente um.
O número de 7,5% mede isso, e não o caso condicionado que uma versão anterior deste item lhe atribuiu
por engano.

Fixado em `AutoLineupTest`, teste "a defence cell falls to the reserve keeper when everyone left is
offensive".

**O que estes números são, e o que não são.** 1,85% e 0,00% não são fatos sobre o jogo original. São
**previsões da leitura A**, calculadas pelo nosso motor sobre os dados do original: o levantamento
mediu este projeto escalando os elencos importados, não o original escalando os dele. Se a leitura A
estiver errada, os dois números caem junto com ela.

Eles também não são MEDIDO no sentido do CONTRIBUTING. Aquela classe exige citar o teste que prova, e
o utilitário que produziu estes números era descartável e já foi removido; nenhum teste do
repositório os reproduz, e o `./gradlew check` ficaria verde se eles fossem outros. O que está fixado
por teste é o mecanismo, não a frequência: `AutoLineupTest` prova que uma célula de defesa chega ao
fim da cadeia e escala o goleiro reserva, e prova que sob MODERN isso não acontece. A frequência com
que isso acontece nos 703 times é INFERIDO quantificado, e é assim que deve ser lida e citada.

Isto é **observável**: uma temporada de liga com 20 clubes tem 380 partidas e portanto 760
escalações de IA. A leitura A prevê 1,85% delas com goleiro em célula de linha, ou seja **cerca de 14
avistamentos por temporada**; a leitura B prevê **zero**, e não por pouco: sob B o passe inalcançável
é a última posição de cada cascata, que nas cadeias de linha é justamente o goleiro, então nenhuma
célula de linha pode alcançá-lo. Assistir a uma temporada de IA contra IA no original e anotar
qualquer goleiro reserva escalado fora do gol decide entre A e B sem descompilar nada. É o item mais
provável de servir de base para trabalho futuro e o de experimento mais barato.

Vale registrar que essa pegada **enfraquece um argumento usado no item 34**, o de que "um defeito
desse tamanho estaria na 3.15". Está: é o próprio item 7. O item 34 se sustenta pela medição, não por
essa heurística.

**Resolução (INFERIDO):** leitura A. `lineupRelaxationPasses` no `RuleSet`, 2 no CLASSIC e 3 no
MODERN. O par de escalações que a diferença produz está fixado em `AutoLineupTest`, no teste "classic
gives up on the position before it gives up on the style", e o delta novo entre os dois conjuntos de
regras está declarado em `RuleSetsTest`. Se a leitura B for confirmada, os dois valores viram 3 e o
teste inverte.

### 33. Qual posição a cascata tenta depois da posição que a célula pediu

A 5.4 diz que o laço externo é o relaxamento de posição e escreve **uma** cadeia,
GOL -> ZAG -> LAT -> MEI -> ATA, seguida de "etc.". A 3.2 fala em "cadeias de preferência por
posição", no plural. As outras quatro não estão escritas em lugar nenhum.

Duas regras diferentes reproduzem a cadeia escrita **letra por letra**, então isto é escolha e não
leitura:

- **A, adotada.** Posição própria primeiro, depois as outras por **distância** sobre a linha GOL,
  ZAG, LAT, MEI, ATA. Do GOL isso dá exatamente a cadeia da spec.
- **B.** Posição própria primeiro, depois a **ordem escrita** GOL, ZAG, LAT, MEI, ATA pulando a que
  já saiu. Do GOL dá a mesma coisa.

Elas discordam a partir do meio-campo. A dá MEI, LAT, ATA, ZAG, GOL e B, lida à letra, dá MEI, GOL,
ZAG, LAT, ATA; no ataque A dá ATA, MEI, LAT, ZAG, GOL contra ATA, GOL, ZAG, LAT, MEI de B. B começa
pelo GOL nas duas porque a ordem escrita começa pelo GOL, e a regra B é literalmente essa ordem com a
posição já usada removida.

Uma versão anterior deste item enunciou B como MEI, ZAG, LAT, ATA, GOL e ATA, ZAG, LAT, MEI, GOL,
isto é, com o goleiro empurrado para o fim. Isso era a definição de B contradizendo a si mesma: pôr o
goleiro por último é a ponta solta declarada mais abaixo, uma preferência que este projeto adotou
**dentro de A**, e não faz parte de B. B não tem nada a dizer sobre o goleiro além do que a ordem
escrita já diz, e a ordem escrita o põe em primeiro.

Escolhi A por causa do ataque, e com as cadeias corretas o argumento fica mais forte, não mais fraco.
Sob B, uma célula de atacante central sem atacante sobrando tenta o **goleiro** antes do zagueiro, e
as duas saídas são ruins: no CLASSIC o goleiro é recusado pelo sub-papel, porque a célula pede
centroavante e o goleiro é defensivo, e o zagueiro é recusado pelo mesmo motivo, então o time acaba
com um **lateral** de centroavante; no MODERN o terceiro passe ignora o sub-papel já na primeira
posição da cascata, e é o **goleiro reserva** que joga de centroavante. Nos dois casos os meias ficam
no banco, e no MODERN a saída é pior do que o zagueiro que a versão anterior deste item descrevia. A
5.4 é explícita sobre
o que a ordenação produz, o ataque escolhe primeiro e a defesa fica com as sobras, e o improviso que
o jogo mostra no ataque é o meia adiantado, não o lateral nem o goleiro. A regra A também tem
significado próprio: improvise com a posição mais próxima.

Duas pontas soltas, decididas aqui:

- **Empate de distância vai para o lado defensivo.** Um elenco carrega mais defensores que atacantes
  (forma ideal da 5.7: GOL 2, LAT 3, ZAG 3, MEI 5, ATA 3), então a posição mais defensiva é a que tem
  mais chance de ter alguém sobrando.
- **O goleiro vai para o fim de toda cadeia de linha**, em vez do lugar que a distância lhe daria.
  **Isto não tem apoio nenhum na spec** e é preferência declarada. Ela só morde onde o goleiro
  empataria: na cadeia do ZAG (GOL e LAT a distância 1) e na do LAT (GOL e ATA a distância 2). O
  pouco que a sustenta está na 3.3: a tabela de pesos só dá participação ao atributo Gol na célula 1,
  então com a opção de habilidade individual **ligada** um goleiro em célula de linha perde de vez o
  seu melhor atributo. Com a opção desligada a escolha é arbitrária. Note que a célula 1 é a
  primeira de toda formação, então um goleiro em célula de linha nunca é o goleiro que faltou no
  gol: é sempre o reserva. E a preferência não é ociosa: o exemplo medido do item 32 é uma célula de
  zagueiro que chega ao fim desta cadeia e escala o reserva.

Fora a ordem, a cascata não muda o desempenho de ninguém: a 5.3 cobra o mesmo x0,5 de um lateral no
meio e de um goleiro no ataque.

**Resolução (INFERIDO):** `POSITION_CASCADE` em `AutoLineup.kt`, cinco cadeias de cinco posições, a
do GOL igual à da spec. Fixada em quatro testes de `AutoLineupTest`, um por cadeia, cada um montado
para ficar vermelho se aquela cadeia for reordenada: "the keeper cell falls to a centre back, which
is the chain the spec writes", "a centre back cell prefers a spare fullback to the reserve keeper",
"a fullback cell prefers a spare centre back to a spare midfielder" e "a midfield cell tries a
fullback before a forward, and the last cell takes whoever is left". A cadeia do ATA está fixada pelo
teste "a squad short of forwards fills its forward cells with the best midfielders".

### 34. Que lado e que sub-papel cada célula da grade exige

O laço interno da 5.4 compara lado e estilo, mas a tabela da 3.2 não publica um lado nem um sub-papel
por célula. Ela nomeia os pares 2,9 como "Laterais (direito / esquerdo)", 10,17 como "Alas", 18,25
como "Pontas", 11-13 como "Volantes", 14-16 como "Meias ofensivos" e 19-24 como "Atacantes centrais".

Lado: só os três pares de flanco têm lado, e o menor número de cada par é a direita, seguindo a ordem
que a linha dos laterais soletra. Toda célula central aceita os dois lados.

Sub-papel, pela derivação da 4.3: 11-13 pedem o volante, 14-16 o armador, 18 e 25 a ponta, 19-24 o
centroavante, e 10 e 17, que a 3.2 chama de alas e que exigem posição lateral, pedem o lateral
ofensivo. O gol e as seis células de zagueiro pedem o defensivo, que na própria posição é o único que
existe e na cascata serve para puxar um lateral defensivo ou um volante em vez de uma ponta.

As células 2 e 9 não pedem sub-papel nenhum. A tabela as descreve só pelo lado, ao contrário de todos
os outros pares, e a leitura alternativa não sobrevive aos dados: nos 703 times do original,
**1778 dos 2549 laterais são ofensivos**, quase 70%. Exigir o defensivo em 2 e 9 tiraria sete de cada
dez laterais do jogo justamente das células que levam o nome deles, e no CLASSIC os dois passes
alcançáveis nunca chegariam a ignorar isso (item 32); o efeito seria zagueiro de lateral e lateral de
zagueiro, quatro improvisos a x0,5 por time e por partida.

**Resolução (INFERIDO):** `Slot.requiredSide` e `Slot.requiredStyle` em `Slot.kt`, no módulo
`:model`, uma linha por faixa da tabela da 3.2 e ao lado de `Slot.requiredPosition`, que é a
terceira coluna da mesma tabela. As duas colunas moraram um tempo como extensões no `:engine`,
longe da irmã, e foram exatamente as duas que ficaram sem teste; agora estão fixadas célula a
célula em `SlotTest`, junto com a de posição, como detector de mudança.

O lado está fixado em dois testes de `AutoLineupTest`, um por par de flanco, cada um montado para
ficar vermelho tanto se os dois lados forem trocados quanto se a tabela deixar de exigir lado nenhum:
"the fullback cells take the fullback of their own flank, not the stronger one", para as células 2 e
9, e "the wing back and winger cells take the man of their own flank", para as células 10, 17, 18 e
25. Os dois montam um elenco em que o jogador do flanco esquerdo é o mais forte do par, então
qualquer das duas tabelas erradas o escalaria na célula da direita.

O teste "a natural fullback keeps the fullback cell whatever his style" **não fixa lado nenhum**:
ele afirma só que as células 2 e 9 não exigem sub-papel, que é uma linha da tabela de estilo. Uma
versão anterior deste item o citava como prova do lado, e era falso. Até os dois testes acima
existirem nenhum teste do repositório distinguia a tabela de lado, porque todo jogador de fixture era
destro e `Side.LEFT` não aparecia na suíte inteira.

O sub-papel está fixado em sete linhas, uma por faixa da tabela, e elas não estão todas cobertas do
mesmo jeito.

Três já eram cobertas por comportamento antes desta revisão: 1 e 3-8 e 11-13, pelos testes de
cascata, e a escolha de não exigir nada em 2 e 9, pelo "a natural fullback keeps the fullback cell
whatever his style". Trocar qualquer uma das três por `null` deixa vermelhos vários testes de
`AutoLineupTest`.

Três não tinham cobertura nenhuma e ganharam cada uma um teste montado para ficar vermelho se ela for
afrouxada: "a winger cell in a squad with no winger falls to the catch all" (18 e 25 exigem ponta),
"a wing back cell demands the offensive reading of a fullback" (10 e 17 exigem o lateral ofensivo) e
"a centre forward cell refuses a stronger winger" (19-24 exigem o centroavante). Antes deles as três
podiam ser trocadas por `null`, ou a linha das pontas por ofensivo, sem nenhum teste reclamar.

A sétima, 14-16, é caso à parte e vale registrar por honestidade: trocá-la por `null` deixa o
`:engine:test` inteiro verde ainda hoje. Quem a pega é só o detector de mudança de `SlotTest`,
"every pitch cell asks for the sub role its row of section 3 point 2 names". A cobertura existe, mas
vem da tabela fixada célula a célula, e não dos testes de escalação; uma versão anterior deste
parágrafo a atribuía aos testes de cascata, e isso era falso.

### 35. O que acontece com uma célula que esgota a cascata inteira

A 5.4 descreve a busca relaxada e para por aí. Não diz o que fazer quando nenhuma das cinco posições
da cadeia, em nenhum passe, produz um candidato. Isso acontece de verdade, e **só sob CLASSIC**: uma
célula de ponta (18 ou 25) num elenco **sem nenhuma** ponta não casa com ninguém, porque ponta é
sub-papel exclusivo de atacante e as outras posições da cadeia nunca o têm, e com dois passes nada
ignora o sub-papel. Nada ignora porque, das 25 células, 19 não pedem lado: nelas os dois
passes do CLASSIC são o mesmo filtro aplicado duas vezes, e nas seis células de flanco o segundo
passe larga só o lado. Em nenhuma das 25 o CLASSIC larga o sub-papel. São **15 dos 703 times** do original, 2,1%, e só a formação 10 tem célula de
ponta, com 2% dos sorteios da IA. Os 14,9% do item 32 são "menos de duas pontas", que é outra coisa:
com uma ponta só, quem cai aqui é a segunda célula. Sob MODERN o terceiro passe põe um atacante
qualquer nas células 18 e 25 e o preenchimento final não dispara ali.

A frase "não faz nenhuma checagem de legalidade posicional" da 5.4 **não serve de apoio**: ela
descreve a tela manual de escalação, não o preenchedor da IA.

O que sustenta a decisão é a forma do resto do motor. O banco da 5.4 passo 4 é fixo em onze, os
agregados da 3.4 percorrem a lista de escalados sem nenhuma noção de célula vazia, e a 3.16 não
menciona time com menos de onze em campo. Uma IA que deixasse célula vazia produziria linhas
desfalcadas o tempo todo, cairia nos casos degenerados de 0,01 da 3.4 e apareceria na aferição
estatística. Não aparece.

**Resolução (INFERIDO):** a célula que esgota a cascata leva o **primeiro jogador que sobrou no
pool**, isto é, o mais forte disponível, sem nenhuma condição. O time entra com onze sempre que
houver onze disponíveis, e com menos só quando o elenco não tem onze aptos. Fixado em
`AutoLineupTest`, testes "eleven are fielded even when nobody fits anything" e "a midfield cell tries
a fullback before a forward, and the last cell takes whoever is left", este último por afirmar
exatamente qual jogador o preenchimento final escala.

O gatilho descrito acima, a célula de ponta num elenco sem ponta, está fixado em "a winger cell in a
squad with no winger falls to the catch all". Ele existe porque a exigência de ponta em 18 e 25 é
carregada: enquanto essa linha da tabela do item 34 não tinha teste, trocá-la por ofensivo deixava a
suíte verde e levava junto, em silêncio, o gatilho deste item.

### 36. Como o banco fixo da 5.4 passo 4 escolhe quem senta em cada célula modelo

A 5.4 dá o banco como uma lista fixa de células modelo, `1, 1, 2, 4, 4, 12, 15, 15, 20, 20, 23`, e diz
o que cada uma significa (2 goleiros, 1 lateral, 2 zagueiros, 1 volante, 2 meias, 3 atacantes), mas não
diz como uma célula modelo escolhe seu ocupante quando o elenco não tem exatamente o tipo pedido.

**Resolução (INFERIDO):** cada célula do banco passa pela mesma busca relaxada da célula em campo,
`chooseFor`: a cascata de posição do item 33, os passes de lado e estilo do item 34, e o mesmo catch
all do item 35, que senta o jogador mais forte que sobrou quando a cascata inteira falha. O pool é o
mesmo da escalação titular, ordenado uma vez por força e energia, e continua de onde a titular parou,
nunca reordenado nem reiniciado. Uma célula do banco carrega `Slot.UNUSED_SUBSTITUTE` (o menos um da
3.2), nunca o valor da célula modelo, que é só a pergunta feita ao pool e não uma resposta gravada no
jogador.

A alternativa seria uma leitura mais simples, que casasse a célula modelo só pela posição natural e
deixasse a vaga vazia sem casar. Rejeitada por dois motivos: geraria um banco de tamanho variável por
um motivo diferente do que a 3.16 já reconhece (elenco pequeno demais), e o catch all já é o
comportamento que a própria 5.4 exige da escalação titular quando uma célula não acha ninguém, então
usar uma regra diferente para o banco criaria uma segunda leitura sem necessidade.

Consequência aceita: quando o elenco não tem o tipo de jogador que uma célula do banco pede e a
cascata inteira falha, o catch all senta ali o jogador mais forte que sobrou, seja qual for sua
posição, exatamente como faria com uma célula em campo. Isso só é observável com um elenco bem
desequilibrado, porque o catch all só dispara depois de a cascata de cinco posições e os passes de
lado/estilo se esgotarem.

Quando o elenco é pequeno demais para completar as onze células do banco, o preenchimento para em vez
de falhar: uma vez que todo jogador restante já foi usado, a própria busca relaxada não acha mais
ninguém, catch all incluso, e as células seguintes do modelo ficam sem ocupante. Fixado em
`AutoLineupTest`, teste "a squad too small to fill the bench benches fewer rather than failing".

Nenhum teste do primeiro corte deste item discriminava as duas leituras: com `deepSquad` (elenco de
24, três por posição) toda outra célula do banco acha seu tipo natural sob as duas leituras, e o caso
de elenco pequeno demais esgota o pool inteiro, o que também é igual nas duas. A única célula onde as
leituras divergem é a sexta do banco, a modelada em 12 (volante): quando ela é preenchida já não sobra
goleiro, lateral nem zagueiro, e todo meia que resta é ofensivo, então a cascata inteira se esgota e o
catch all senta o atacante de força 86, enquanto a leitura mais simples teria parado no meia de força
76. Fixado em `AutoLineupTest`, teste "the holding midfielder bench place falls to the catch all
forward, not the natural midfielder", que também documenta no próprio texto o que a leitura rejeitada
teria escalado ali.

### 37. De qual fluxo cada lado sorteia sua marcação

A seção 3.12 descreve o sorteio rand(1..100) da marcação de um time, mas não diz se esse sorteio vem
do fluxo do próprio clube ou de um fluxo compartilhado pela partida inteira. A escalação automática da
5.4 não consome nenhuma aleatoriedade, então a marcação é o único sorteio que `assembleMatch` precisa
decidir onde colocar.

**Resolução (INFERIDO):** cada lado sorteia sua marcação do mesmo fluxo forkado a partir do próprio
clube que já decide sua formação, `rng.fork(clubKey(entry.ref))`, a mesma derivação que `generateWorld`
usa para o fluxo do elenco. Isso torna o sorteio de marcação, como o de formação, indiferente a qual
lado do confronto o clube ocupa. `MatchAssemblyTest` não afirma essa propriedade para a marcação, só
para a escalação, porque a escolha é arbitrária: nada na spec impede uma leitura alternativa em que a
marcação venha de um fluxo da partida em vez do fluxo do clube, e as duas leituras são
observacionalmente idênticas para uma única partida com uma única semente. A diferença só apareceria
comparando duas partidas do mesmo clube em posições diferentes do confronto, o que a spec nunca
descreve.

### 38. A partir de qual ponto se conta a fase do minuto na seção 3.8

A seção 3.8 diz "Fase p: minuto < 15 -> 0; < 30 -> 1; senão 2", sem dizer se "minuto" é contado desde
o apito inicial da partida ou desde o início do próprio tempo. A tabela de limiares dá uma linha para
o primeiro tempo e outra para o segundo, cada uma com três fases.

Contando a partir do apito inicial: o primeiro tempo regulamentar tem 45 minutos mais o acréscimo que
a seção 3.1 sorteia em 0-2, então o segundo tempo nunca começa antes do minuto 45. Nesse caso
`minuto < 15` e `minuto < 30` são falsos para todo minuto do segundo tempo, a fase nunca vale 0 nem 1
ali, e a linha do segundo tempo das três tabelas - Amarelo, Vermelho direto, Lesão - colapsa para a
sua última coluna (30, 550, 600 respectivamente antes de qualquer ajuste). Quatro das seis células de
cada tabela nunca disparariam.

**Resolução (INFERIDO):** contar os minutos de cada tempo a partir de zero, reiniciando a contagem no
início do segundo tempo, `MatchClock.intoHalf`, a mesma pergunta e a mesma resposta que o item 31 deu
para o desgaste de energia da seção 3.9. É a única leitura que mantém as seis células de cada uma das
três tabelas alcançáveis. Testado em `DisciplineTest`, teste "every cell of the yellow table is
reachable".

**Leitura alternativa, rejeitada.** Contar a partir do apito inicial, como a seção 3.8 lê à letra.
Essa leitura reduz a linha do segundo tempo de três fases potenciais para uma só, o mesmo defeito de
forma que o item 31 rejeitou para o desgaste de energia, pelo mesmo motivo: um defeito desse tamanho,
que apagaria dois terços de uma tabela inteira sem nenhuma nota na seção 3.15, é mais provável de ser
um jeito impreciso de escrever "contado dentro do tempo" do que um comportamento deliberado do
original.

**Resolução no original (CONFIRMADO). A leitura adotada está certa.** O minuto que escolhe a fase é o
minuto **do tempo em curso, contado a partir de 0**, e a contagem reinicia no início do 2º tempo. É o
mesmo minuto que a 3.9 usa para o desgaste de 7 em 7, o que fecha do mesmo jeito o item 31. As seis
células de cada uma das três tabelas são alcançáveis. A 3.8 e a 3.9 foram corrigidas para dizer isso.

### 39. Quais contadores as sobrescritas do limiar de cartão leem

A seção 3.8 diz que "se já houve > 5 amarelos" o limiar do amarelo dobra, "se já houve >= 2 vermelhos"
ele vira `2 x limiarVermelho`, e "se já houve >= 1 lesão" ele vira `5 x limiarLesão`. A mesma seção
também diz que "segundo amarelo -> expulsão (evento distinto)", ou seja, uma expulsão por segundo
amarelo é ao mesmo tempo um amarelo e uma expulsão. A spec não diz se essa expulsão soma 1 ao contador
de amarelos, ao de vermelhos, aos dois, ou a nenhum, para efeito das três sobrescritas acima.

A metade da pergunta que trata de amarelos já está resolvida por outra regra da mesma seção: a de
suspensões, no parágrafo seguinte, diz explicitamente que "expulsão por 2º amarelo soma 1 amarelo e 1
jogo de gancho" ao registro de suspensão do jogador. Contar a mesma expulsão como um amarelo aqui é a
leitura consistente com essa regra, não uma escolha nova; o registrado neste item é só a metade que
sobra, a dos vermelhos.

**Resolução (INFERIDO):** um segundo amarelo conta 1 no contador de amarelos e 1 no contador de
sendingsOff de `DisciplineCounts`, isto é, para os fins das sobrescritas ele conta como um vermelho
também. `MatchEvent.Booking` é logado junto com `MatchEvent.SendingOff` quando `secondYellow` é
verdadeiro, exatamente para isso: quem soma DisciplineCounts soma ambos os eventos e nunca precisa
saber que a expulsão veio de um segundo amarelo. Isso deixa a leitura simétrica entre amarelo e
vermelho, e é a leitura mais simples de "já houve >= 2 vermelhos" quando o texto da mesma seção não
distingue tipo de expulsão em nenhum outro lugar.

**Leitura alternativa, não adotada.** Só uma expulsão por vermelho direto soma ao contador de
vermelhos que as sobrescritas leem; uma expulsão por segundo amarelo soma apenas ao de amarelos. Essa
leitura é possível porque a seção separa as duas expulsões em toda outra regra que as trata de forma
diferente - a de suspensões dá ao vermelho direto um sorteio de gravidade que o segundo amarelo não
tem - então "vermelhos" no texto das sobrescritas poderia estar restrito ao mesmo sentido estrito.
Rejeitada por não ter apoio direto no texto das sobrescritas, que fala só em "vermelhos" sem qualificar
"direto", ao contrário da regra de suspensões, que qualifica explicitamente onde quer dizer uma coisa e
não a outra.

**Resolução no original (CONFIRMADO). A leitura adotada está ERRADA na metade dos vermelhos.** São
três contadores, e eles são **da partida inteira, não de cada lado**: somam os eventos dos dois times.
A metade dos amarelos está certa - uma expulsão por segundo amarelo soma 1 ao contador de amarelos.
A metade dos vermelhos não: essa expulsão **não soma nada ao contador de vermelhos**. Só a expulsão
por vermelho direto alimenta o contador que a sobrescrita `>= 2 vermelhos` lê, exatamente a "leitura
alternativa, não adotada" registrada acima.

Há ainda um terceiro fato que nenhuma das duas leituras previa: os três contadores são incrementados
**mesmo quando o grupo de risco sorteado está vazio** e nenhum cartão, expulsão ou lesão chega a
acontecer. Ou seja, eles contam **tentativas**, não eventos. Isso importa para a sobrescrita da lesão,
que é a mais violenta das três: ela passa a valer a partir da primeira **tentativa** de lesão, tenha
ela produzido lesão ou não. Efeito prático: os cartões despencam um pouco mais cedo do que a contagem
de eventos sugeriria. A 3.8 foi corrigida nos três pontos.

### 40. Como se escolhe o jogador dentro de um grupo de risco

A seção 3.8 diz "sorteia-se um grupo, depois um jogador aleatório dentro da faixa de slots", sem dizer
se o segundo sorteio é sobre as células da faixa ou sobre os jogadores que de fato as ocupam. As duas
leituras coincidem quando toda célula do grupo está ocupada, mas divergem sempre que a formação em
campo deixa alguma vazia, o que é o caso comum: a maioria das formações usa entre onze e treze das
vinte e cinco células.

Sob uma formação 4-4-2, o grupo 0 cobre as células 10 a 13, quatro células, das quais a formação ocupa
duas (os volantes, 11 e 13); o grupo 5 cobre 19 a 24, seis células, das quais a formação ocupa duas
(os atacantes). Sortear a célula e descartar o evento quando ela está vazia perderia metade dos
sorteios do grupo 0 (duas de quatro células vazias) e dois terços dos do grupo 5 (quatro de seis
células vazias). A seção 3.16 mira "~2-3 amarelos por jogo; um vermelho a cada ~8-12 partidas; uma
lesão a cada ~6-10 partidas por lado"; descartar metade a dois terços dos sorteios de grupos tão
frequentes quanto g0 e g5 empurraria a taxa efetiva de eventos bem abaixo dessas figuras, sem que a
seção 3.15 registre um defeito desse tamanho.

**Resolução (INFERIDO):** sortear uniformemente entre os jogadores que ocupam as células do grupo, não
entre as células. Um grupo sem ninguém em suas células não sorteia ninguém e o evento não acontece,
sem consumir um sorteio da sequência aleatória. Testado em `RiskGroupTest`, teste "the victim is drawn
among the players standing in the group's cells".

**Leitura alternativa, rejeitada.** Sortear uma célula da faixa e, se ela estiver vazia, descartar o
evento (ou sortear de novo até achar uma ocupada). A primeira variante é a que reduziria a taxa de
eventos na aritmética acima; a segunda a preservaria, mas gastaria um número variável de sorteios por
evento sem que a seção 3.8 descreva nenhum laço de repetição em lugar nenhum do seu texto. Nenhuma das
duas tem apoio direto no texto, que fala em sortear "um jogador", não uma célula.

**Resolução no original (CONFIRMADO). A leitura adotada está certa.** A faixa de slots do grupo é
primeiro **filtrada pelos jogadores que estão em campo** e o sorteio é uniforme sobre essa lista
filtrada; uma faixa sem ninguém devolve nada e o minuto passa sem evento. Não há laço de re-sorteio.
A 3.8 foi corrigida para dizer isso explicitamente.

### 41. Quem entra na vaga do goleiro quando não há goleiro no banco

A seção 3.8 diz que o lesionado "sai e é substituído (com regra que impede preencher a vaga do
goleiro com não-goleiro)", mas não diz o que acontece quando o banco não tem goleiro nenhum. A regra
proíbe o preenchimento; ela não diz se a vaga fica vazia, se a proibição cai por falta de
alternativa, ou se algum jogador de linha é promovido ao gol.

A aritmética das duas leituras não é próxima. Pela 3.4, um time sem ninguém na célula 1 joga com o
agregado de goleiro fixado em **0,1**. Pela 5.3, um jogador de linha no gol sofre o x0,5 da nota
inteira **e** tem o agregado reduzido a `round(GK x 0,2)`: um jogador de 70 de força vale 7,0, cai
para 3,5 pelo x0,5 e, arredondando `round(3,5 x 0,2) = round(0,7)`, para **1,0** pelo x0,2 - a própria
5.3 já registra esse exemplo ("rende 1,0 contra 7,0 de um goleiro de 70"). Ou seja, a leitura que
promove um jogador de linha dá ao time um goleiro **dez vezes** melhor que a leitura que deixa a
célula vazia, e as duas produzem partidas visivelmente diferentes sempre que um goleiro se machuca com
o banco já sem goleiro.

**Resolução (INFERIDO):** a vaga fica vazia. O filtro de `chooseReplacement` é aplicado antes da
busca da 5.4 e não depois: quando a célula é a do goleiro, só goleiros são oferecidos, e um banco sem
goleiro oferece uma lista vazia, que não chega nem ao preenchimento final do item 35. O time passa a
jogar com dez e com o `missingKeeperRating` de 0,1 da 3.4. É a leitura literal do texto, que escreve
a regra como uma proibição sem exceção, e é a única que mantém a proibição com algum efeito: se ela
caísse por falta de alternativa, ela nunca mudaria nada, porque só existe para o caso em que não há
goleiro sobrando. Era testado em `SubstitutionTest`, no teste "only a keeper may take the keeper's
cell", removido quando a resolução abaixo substituiu essa leitura; os testes atuais que pinam a
leitura correta são "the cascade fills the keeper's cell with no exception" e "an outfielder in goal
is rated, not the missing keeper figure", ambos em `SubstitutionTest`.

**Leitura alternativa, rejeitada.** A regra proíbe apenas o preenchimento **automático**, e um
treinador humano poria um jogador de linha no gol; o motor faria o mesmo por ele, aplicando o x0,5 e
o x0,2 da 5.3. É o que um time de verdade faz, e a 5.3 se dá o trabalho de descrever exatamente esse
caso, o que sugere que ele acontece em algum lugar do jogo. Rejeitada porque a 5.3 descreve o caso da
escalação manual, onde a tela "não faz nenhuma checagem de legalidade posicional" e o humano pode
escalar onze atacantes; a 3.8 fala da substituição da IA, e ali o texto é uma proibição sem
qualificação. Note que a leitura rejeitada é a mais generosa das duas: adotá-la mais tarde só melhora
o time afetado.

**Resolução no original (CONFIRMADO). A leitura adotada está ERRADA, e a frase da 3.8 que a motivou
descreve a regra ao contrário.** Duas correções:

1. A cascata de posição da 5.4 é aplicada **sem exceção nenhuma para a célula do goleiro**. Ela tenta
   goleiro, depois zagueiro, depois lateral, meia e atacante, e devolve o primeiro do banco que
   servir. Um banco sem goleiro entrega um **zagueiro**, que assume o gol com o x0,5 e o
   `round(GK x 0,2)` da 5.3. A célula do goleiro só fica vazia se o banco inteiro estiver vazio. É a
   "leitura alternativa, rejeitada" acima - a mais generosa das duas, como o próprio item previu.
2. A "regra que impede preencher a vaga do goleiro com não-goleiro" da 3.8 **não existe nessa
   direção**. O que existe é uma trava na reposição de lesão, e ela é o inverso: a troca só é feita
   se quem sai é goleiro **ou** quem entra não é goleiro. Ou seja, o que a regra impede é um
   **goleiro reserva entrar no lugar de um jogador de linha** - se a cascata devolver o goleiro
   reserva para uma célula de linha (o que só acontece com o banco quase esgotado), a substituição é
   cancelada e o lesionado não é reposto, deixando o time com dez. A trava vale **só na reposição de
   lesão**: no sacrifício da expulsão e nas três janelas voluntárias não há trava nenhuma, e um
   goleiro reserva pode acabar em campo como jogador de linha.

A 3.8 foi corrigida nos dois pontos.

**A trava do ponto 2 lê "goleiro" como posição natural, não como célula ocupada (CONFIRMADO).** A
trava fala de "quem sai" e "quem entra". Quem entra é sempre um reserva do banco, e um reserva carrega
`Slot.UNUSED_SUBSTITUTE` - ele não ocupa célula nenhuma até a troca ser efetivada. Logo "o que entra
não é goleiro" só pode se referir à posição natural do reserva, nunca a uma célula que ele ainda não
ocupa; e a mesma palavra "goleiro" na cláusula irmã, "o que sai é goleiro", tem de significar a mesma
coisa - as duas metades de uma regra escrita em uma frase só não trocam de sentido no meio dela. As
outras construções da 3.8 que falam de célula - os seis grupos de risco mais o goleiro, as duas faixas
de sacrifício - são todas escritas como faixas numéricas explícitas de slot; esta regra não é, o que é
o indício textual de que ela não fala de célula.

O caso que distingue as duas leituras é alcançável, e não é exótico: um goleiro nato entra numa célula
de linha através do sacrifício da expulsão ou de uma das três janelas voluntárias, nenhuma das quais
tem trava (ponto 2 acima); ele se machuca ali, jogando fora de posição; a cascata da 5.4 oferece um
segundo goleiro reserva para a célula que ele deixa. Pela leitura de posição natural a troca é
permitida, porque quem sai é goleiro por posição, apesar de estar jogando fora dela; pela leitura de
célula ela seria recusada, porque a célula que ele deixa não é a do goleiro. O banco fixo da 5.4 abre
com duas células de goleiro (`{1,1,...}` no item 4 da 5.4), então esse segundo goleiro reserva existe
rotineiramente num banco cheio.

O caso simétrico - um jogador de linha promovido ao gol pela cascata por falta de goleiro no banco,
lesionado depois, com um goleiro reserva agora disponível - **não é alcançável**. Um jogador de linha
só chega à célula 1 quando o banco não tinha goleiro naquele instante, porque a cascata tenta GOLEIRO
antes de qualquer outra posição; e o banco só encolhe a cada troca, nunca ganha jogador de volta, então
nenhum goleiro pode aparecer nele depois de já ter faltado. As duas leituras concordam nesse caso, e
ele não serve para distingui-las.

### 42. Os pools de minutos estáticos e compartilhados do item 8 da 3.15

O item 8 da 3.15 registra que "os pools de minutos de substituição são estáticos/compartilhados,
re-embaralhados por partida", com a consequência de que "partidas consecutivas sorteiam minutos
correlacionados". É um defeito nomeado do original, e todo defeito nomeado daquela lista foi até aqui
reproduzido sob CLASSIC e, quando muito, corrigido sob MODERN.

**Resolução (INFERIDO):** este motor **não reproduz a metade "estáticos entre partidas" do item 8
sob nenhum dos dois conjuntos de regras** - a outra metade, a de dentro da partida, passou a ser
reproduzida depois, e a nota no fim deste item separa as duas. `matchSubstitutionPlans` embaralha os
pools de novo a cada partida, de um gerador derivado da semente daquela partida, e nada é
compartilhado entre partidas. Isto é uma **divergência deliberada do CLASSIC**, e não uma omissão: é
o único ponto em que este projeto se recusa a copiar um defeito nomeado.

O motivo é que o defeito não é um número errado, é estado global mutável. "Estático/compartilhado"
quer dizer que os pools vivem fora da partida e guardam a ordem em que a partida anterior os deixou;
"correlacionados" é o nome que o item dá ao efeito disso. Reproduzir isso faria o resultado de uma
partida depender de **quais partidas rodaram antes dela**, o que destrói a única propriedade sobre a
qual o projeto inteiro é construído: a de que uma carreira se repete a partir da sua semente e a de
que uma partida pode ser re-simulada sozinha, sem simular tudo o que veio antes. A `simulateMatch` é
explícita quanto a isso, e o `ArchitectureTest` proíbe as construções que permitiriam o
compartilhamento. Uma rodada de 380 partidas rodada em paralelo e a mesma rodada em série dariam
tabelas diferentes.

O custo da divergência é pequeno e mensurável na direção certa: sob o item 8, os minutos de dois
jogos seguidos são correlacionados, mas cada plano isolado continua saindo das mesmas faixas e com as
mesmas probabilidades que a 3.8 publica. Ou seja, a **distribuição** de um plano é a mesma nas duas
leituras; o que se perde é só a correlação entre partidas vizinhas, que nenhuma figura da 3.16 mede e
de que nenhum comportamento observável do jogo depende.

**Leitura alternativa, rejeitada.** Reproduzir o compartilhamento sob CLASSIC, por exemplo com um
pool por carreira reembaralhado a cada partida, e corrigi-lo sob MODERN. Rejeitada pelo parágrafo
acima: seria o primeiro caso em que reproduzir a fidelidade custa a reprodutibilidade, e a
reprodutibilidade é condição de todo o resto, inclusive de conseguir comparar CLASSIC com o original.

**Resolução no original (CONFIRMADO). O item 8 da 3.15 descreve o original com exatidão; a
divergência deliberada continua justificada.** São cinco pools - 19-38, 5-15, 16-35, 36-42 e 43-47 -
criados uma única vez no processo e apenas **re-embaralhados** no começo de cada partida, guardando
entre partidas a ordem em que a anterior os deixou. A distribuição de um plano isolado é a que a 3.8
publica, exatamente como o item previu, então o custo da divergência é só a correlação entre partidas
vizinhas.

Um detalhe que a 3.8 não dizia e que **não** é estado global, e portanto deve ser reproduzido: dentro
de uma partida os dois times tiram do **mesmo** embaralhamento, em posições fixas e distintas - o
mandante fica com os dois primeiros minutos do pool e o visitante com o terceiro e o quarto. Os
minutos de rotina e de "correndo atrás" dos dois lados **nunca coincidem** dentro de uma partida. Um
plano sorteado independentemente por time, como o motor faz hoje, deixa os dois lados colidirem, e a
colisão tem consequência: ver o item 11 da 3.15, acrescentado agora. A 3.8 foi corrigida.

**Nota do reimplementador: metade do item 8 passou a ser reproduzida, e é preciso dizer qual.** O
item tem duas metades independentes, e o motor agora fica de lados opostos das duas.

1. **Dentro da partida: reproduzido.** `matchSubstitutionPlans` sorteia os **dois** planos numa
   chamada só, de um fluxo único (`SUBSTITUTION_PLAN_STREAM`, lido direto, sem mais nenhum fork por
   lado). Os cinco pools são embaralhados no começo da partida - só até a última posição que alguém
   pode ler - e cada lado lê um **bloco fixo e distinto**, o mandante primeiro. Isso reproduz a
   frase confirmada acima: num pool de rotina o mandante fica com o primeiro e o segundo minuto e o
   visitante com o terceiro e o quarto.
2. **Entre partidas: continua não reproduzido**, pelo motivo de sempre, que é o resto deste item: os
   pools estáticos são estado global mutável e fariam o resultado de uma partida depender de quais
   partidas rodaram antes dela. Nada é guardado entre partidas.

**A garantia "os minutos dos dois lados nunca coincidem" vale por pool, e não entre pools.** A frase
do item 8 é absoluta, mas o mecanismo que ela descreve não é: um embaralhamento só, lido em posições
distintas, mata a coincidência **dentro** daquele pool e não sabe nada dos outros quatro. Na prática:

- **"Correndo atrás" contra "correndo atrás"**: impossível. Mesmo pool (19-38), posições distintas.
- **Rotina contra rotina**: impossível também, por dois motivos diferentes. Se os dois lados
  sortearam o mesmo pool, são posições distintas do mesmo embaralhamento; se sortearam pools
  diferentes, as faixas 5-15, 16-35 e 36-42 não se sobrepõem. Os minutos extras de 43-47 são um
  quinto pool, com blocos fixos também, e a faixa não encosta em nenhum pool de rotina.
- **Rotina de um lado contra "correndo atrás" do outro**: **continua possível**, e é a única
  coincidência que sobra. Vêm de embaralhamentos diferentes, e as faixas se sobrepõem: 19-38 contra
  16-35 (em 19-35) e contra 36-42 (em 36-38).

Essa sobrevivente não é um resíduo tolerado, é uma exigência: é o único gatilho que resta para o
item 11 da 3.15, cuja nota está no item 43 aqui e cuja aritmética mostra que o intervalo e duas
janelas de "correndo atrás" no mesmo minuto são vazios. As duas propriedades estão fixadas em
`SubstitutionPlanTest`, uma afirmando a não coincidência por pool e a outra afirmando que a
coincidência entre pools continua acontecendo.

**Detalhes que a spec não decide, resolvidos por inferência (INFERIDO).**

- **Tamanho do bloco de cada lado**: o máximo que aquele lado pode tirar daquele pool - três em
  19-38 (dois fixos mais o da moeda de 69%), dois em cada pool de rotina e dois em 43-47. O bloco do
  visitante começa sempre no fim do bloco do mandante, então uma moeda que recusa deixa a posição
  **sem leitor nenhum** em vez de passá-la ao outro lado. É o que "posições fixas" quer dizer: o
  bloco do visitante não anda quando o mandante recusa um minuto.
- **Os três pools de rotina são embaralhados sempre**, mesmo o que nenhum dos dois lados sorteou,
  porque o original re-embaralha os cinco no começo da partida e só depois olha o que cada lado
  quer. Consequência verificável: o par de planos gasta **32 sorteios** sem colisão nenhuma, e esse
  número não depende de nenhuma moeda.
- **Cada moeda de 43-47 tem a sua posição**: a segunda moeda compra a segunda posição do bloco mesmo
  quando a primeira recusou a primeira. A leitura alternativa - um ponteiro que só anda quando um
  minuto é comprado - só difere quando a primeira moeda recusa e a segunda aceita, cerca de 10% dos
  planos, e foi rejeitada por não ser "posição fixa".
- **O par é sorteado quando qualquer um dos dois lados pode substituir**, e o plano do lado que não
  pode é zerado depois. Pular o sorteio de um lado só moveria os minutos do outro, que é justamente
  o acoplamento que um banco vazio não pode ter. Só a partida em que nenhum dos dois pode
  substituir não sorteia nada - e aí não sobra nada para mover.

### 43. Para qual dos dois times a janela de substituição abre

A 3.8 escreve a resolução do minuto como uma cadeia única: "o primeiro que casar: amarelo -> vermelho
-> lesão -> (se 2º tempo e minuto >= 5) janela de substituição da IA". A cadeia inteira roda sobre o
**time-vítima**, sorteado no início do minuto com `rand(100) > 55`. Lida à letra, a janela abriria só
para o time que aquele sorteio escolheu.

Isso não combina com o parágrafo seguinte da mesma seção, que diz que **cada time sorteia seus
minutos**. Sob a leitura literal, o minuto que um time sorteou para si só dispara quando ele também é
a vítima daquele minuto, ou seja em 44% dos casos para o mandante e 56% para o visitante.

A aritmética: um plano tem em média `2 + 0,69 = 2,69` minutos de "correndo atrás", `2 + 0,79 + 0,49 =
3,28` de rotina, e mais a janela do intervalo, cerca de **7 oportunidades por time por partida**. Sob
a leitura literal sobram ~3,1 para o mandante e ~3,9 para o visitante, antes ainda de descontar os
minutos em que a cadeia parou num cartão ou numa lesão e os minutos em que o placar não pede troca.
Metade de cada plano ficaria morta, e o time da casa trocaria **menos** que o visitante por um efeito
colateral do viés de arbitragem, que a 3.8 não relaciona com substituição em lugar nenhum.

Há ainda um argumento estrutural: a própria 3.8 já tem uma janela de substituição fora da cadeia, a
do intervalo, que nenhum sorteio de vítima poderia governar, porque não há minuto de jogo no
intervalo. Se a janela do intervalo abre para os dois times independentemente, a das outras duas
também abre.

**Resolução (INFERIDO):** a janela abre para **os dois times, independentemente**. A posição da
janela na cadeia é lida como "depois de resolvida a disciplina do minuto", e não como "sobre o
time-vítima". Isso dá duas regras, não uma: as janelas de "correndo atrás" e de rotina, que são
minuto de jogo, só abrem num minuto cuja cadeia disciplinar não produziu cartão nem lesão; a janela
do intervalo não é condicionada ao resultado da cadeia de jeito nenhum. A diferença não é um
capricho: a própria 3.8 restringe a quarta ramificação da cadeia com "se 2º tempo e minuto >= 5", e a
janela do intervalo é pendurada no **primeiro minuto do segundo tempo** (minuto 0), que fica abaixo
desse portão. A janela do intervalo não pode ser aquela quarta ramificação, porque nunca conseguiria
disparar nela; ela é o parágrafo próprio e separado da 3.8 sobre o intervalo, e por isso não herda o
portão de resultado, que só faz sentido para um ramo que compete com cartão e lesão pelo mesmo minuto
de jogo. Testado em `SubstitutionWindowTest`.

**Leitura alternativa, rejeitada.** A janela abre só para o time-vítima do minuto. É a leitura
literal da cadeia e tem a seu favor a economia de sortear a vítima uma vez só. Rejeitada pela
aritmética acima: ela mata metade dos planos, cria uma assimetria mandante/visitante que a seção não
descreve, e não consegue explicar a janela do intervalo, que existe sem vítima nenhuma.

**Resolução no original (CONFIRMADO). A leitura adotada está certa, nas duas metades.** A janela não
tem nada a ver com o time-vítima: quando a cadeia do minuto chega ao quarto ramo, os **dois** times
são examinados, cada um contra o seu próprio plano de minutos e contra o placar. E a janela do
intervalo **não passa pela cadeia**: ela é uma passagem única entre os dois tempos, sem sorteio de
vítima e sem nenhuma condição de disciplina, exatamente como o item argumentou. O portão "2º tempo e
minuto >= 5" também é confirmado, e é inócuo: o menor minuto de qualquer pool é 5.

**Duas coisas que o item não previu, e que mudam o resultado.** Elas estão registradas como os itens
11 e 12 da 3.15:

1. Os dois times não são independentes dentro do minuto. Eles são examinados na mesma passagem, o
   **mandante primeiro**, e se o mandante **efetivamente trocou** a janela do visitante nem chega a
   ser examinada. No intervalo, onde os dois lados são sempre avaliados juntos, isso morde toda vez
   que o mandante trocou. Nos minutos de jogo só morde quando os dois planos batem no mesmo minuto -
   o que no original nunca acontece para rotina e "correndo atrás" (ver item 42), mas acontece num
   motor que sorteia um plano por time.
2. A recusa é por **substituição efetivada**, não por janela aberta: um mandante que abre a janela e
   não acha ninguém para trocar não bloqueia o visitante.

**Nota do reimplementador (achado ao pinar o item 11): a cláusula "ou no intervalo" do item 11 da
3.15 é INALCANÇÁVEL, e o ponto 1 acima está errado nessa metade.** No intervalo os dois lados são
mesmo avaliados juntos, mas eles **nunca podem querer trocar ao mesmo tempo**, então não há janela de
visitante para o mandante engolir ali. A aritmética é da própria 3.8: no intervalo o mandante troca
se perde por >= 1 e o visitante se perde por >= 2, e o déficit de um lado é a sobra do outro. Seja
`d = golsVisitante - golsMandante` o déficit do mandante; o do visitante é `-d`. As duas condições
seriam `d >= 1` e `-d >= 2`, isto é `d >= 1` e `d <= -2` ao mesmo tempo, o que é impossível para
qualquer placar. Não é o caso de ser raro: é vazio.

A mesma conta elimina duas janelas de "correndo atrás" no mesmo minuto, cujos limiares são `d >= 0`
(mandante) e `-d >= 1` (visitante), ou seja `d >= 0` e `d <= -1`. **O minuto de rotina é a única
janela que não pergunta nada ao placar**, então o item 11 só morde quando pelo menos um dos dois
lados está num minuto de rotina, e o outro num minuto de rotina ou de "correndo atrás".

Isso importa para quem for reproduzir o item 8 da 3.15 (ver item 42). Se os dois lados passarem a
tirar seus minutos de um embaralhamento só, em posições fixas, as coincidências **dentro do mesmo
pool** morrem, e com elas o caso rotina-contra-rotina; sobra o caso **rotina de um lado contra
"correndo atrás" do outro**, porque esses vêm de pools diferentes (a janela de "correndo atrás" é
19-38 e os pools de rotina são 5-15, 16-35 e 36-42, com sobreposição em 19-35 e em 36-38). Fixado
assim em `DisciplineChainTest`, teste "a home change swallows the away window in a shared minute",
justamente para o teste não morrer quando os pools passarem a ser sorteados juntos.

### 44. Quem sai no intervalo e num minuto de "correndo atrás"

A 3.8 descreve as três janelas em três frases seguidas: "No intervalo: se perde por >=1 (mandante) /
>=2 (visitante), 50% de chance de **troca aleatória**. Em minuto 'correndo atrás': mandante **troca**
se perde ou empata; visitante só se perde. Em minuto de rotina: **troca por cansaço** - primeiro
não-goleiro com energia < 60". A primeira janela qualifica a troca de aleatória, a terceira a
qualifica de por cansaço, e a do meio não qualifica nada: só diz "troca".

**Resolução (INFERIDO):** no intervalo e no minuto de "correndo atrás" sai um **jogador de linha
aleatório**, e entra o reserva mais adequado à célula que ele deixou; no minuto de rotina sai o
primeiro que a varredura de cansaço achar. O "aleatória" da primeira frase é lido como o padrão das
janelas de placar, e o "troca" pelado da segunda como a mesma coisa dita de forma abreviada na frase
imediatamente seguinte; a terceira frase é a que **sobrescreve** esse padrão, e por isso é a única
que descreve um critério de escolha. O goleiro fica de fora do sorteio: um time não responde a um
placar adverso trocando o goleiro, e a varredura de cansaço da terceira frase também o exclui
explicitamente, então excluí-lo aqui mantém as três janelas coerentes entre si. Testado em
`SubstitutionWindowTest`, testes "a chasing minute changes a drawn outfielder" e "a routine minute
changes the tired man for the reserve his cell suits".

**Leitura alternativa, rejeitada.** O "troca" pelado do minuto de "correndo atrás" herda o critério
da frase seguinte, e não o da anterior, ou seja é também uma troca por cansaço. A favor dela: um time
que está perdendo trocar um jogador ao acaso é comportamento estranho, e herdar o critério de cansaço
tornaria a segunda e a terceira janelas idênticas em tudo menos no gatilho. Rejeitada porque tornar
duas janelas idênticas em tudo menos no gatilho é justamente o que faria a 3.8 não precisar
descrevê-las separadamente, e porque a frase que qualifica a troca de aleatória vem **antes** da
frase pelada, o que faz do "aleatória" o padrão e do "por cansaço" a exceção, e não o contrário.

**Resolução no original (CONFIRMADO). A leitura adotada está certa no essencial e erra no goleiro.**
No intervalo e no minuto de "correndo atrás" sai um jogador sorteado, e entra o reserva mais adequado
à célula que ele deixou; no minuto de rotina sai o primeiro que a varredura de cansaço achar. Mas o
sorteio **não exclui o goleiro do sorteio**: ele sorteia um índice qualquer da escalação em campo e,
**se cair no goleiro, a janela é desperdiçada** - nada acontece naquele minuto e nenhuma nova
tentativa é feita. Cerca de uma em onze janelas de placar morre assim. Excluir o goleiro do sorteio,
como o motor faz hoje, dá ao time ~10% mais trocas por placar do que o original.

O sorteio tem ainda uma segunda condição, essa a favor do time: ele evita tirar quem acabou de entrar,
com uma única re-tentativa se o primeiro índice cair num deles. A condição está quebrada para o
visitante - ver o item 12 da 3.15. A varredura de cansaço, essa sim, pula o goleiro
explicitamente, e também não dá a volta quando começa num índice aleatório depois do minuto 40.

### 45. Em que posição da lista o substituto entra

A 3.8 diz que o substituído "sai" e que o reserva "põe-se no slot vago", sem dizer nada sobre a
**ordem da lista** em que os dois vivem. A ordem não é decorativa: a 3.4 monta cada agregado de linha
pegando os **primeiros N** jogadores da lista cujas células caem na faixa daquela linha, e não os N
melhores. Duas leituras cabem no texto: acrescentar o que entra ao fim da lista, ou colocá-lo no
índice que o que saiu ocupava.

As duas coincidem enquanto a linha não estiver superlotada, isto é enquanto a quantidade de jogadores
nas células daquela linha for menor ou igual ao `take` dela. Divergem assim que passar disso, porque
aí o último da fila é ignorado, e qual jogador é o último depende da leitura.

**A aritmética, sobre uma escalação concreta.** Meio-campo: faixa 10-17, `take` 5, divisor fixo 5. Uma
escalação manual com **seis** meias, na ordem em que a tela a produz - `1, 22, 24, 10, 11, 12, 13, 14,
15, 2, 9` - tem os meias 10, 11, 12, 13, 14 e 15. Todos valem 50 de força, salvo o da célula 15, que
vale 20; o reserva que vai entrar vale 80. Com atributos individuais desligados a nota de cada um é a
força dividida por dez (3.3), e o bônus de marcação entra igual nas duas leituras, então some.

- Antes da troca, contam 10, 11, 12, 13 e 14: `(5,0 x 5) / 5 = 5,0`. O meia da célula 15 é o sexto e
  já é ignorado.
- O meia da célula 11 se machuca. **Acrescentando ao fim**, a lista fica `1, 22, 24, 10, 12, 13, 14,
  15, 2, 9, sub@11`, e os cinco primeiros meias são 10, 12, 13, 14 e **15**:
  `(5,0 + 5,0 + 5,0 + 5,0 + 2,0) / 5 = 4,4`. **O reserva de 80 não entra em conta nenhuma.**
- **Colocando no índice vago**, a lista fica `1, 22, 24, 10, sub@11, 12, 13, 14, 15, 2, 9`, e os cinco
  primeiros meias são 10, o substituto, 12, 13 e 14: `(5,0 + 8,0 + 5,0 + 5,0 + 5,0) / 5 = **5,6**`.

4,4 contra 5,6: 1,2 de diferença em unidades de `B()`, sobre uma alavanca que a 3.16 mede em 2,0 para
levar o duelo de posse de 55% para ~69%. Não é arredondamento.

**Alcance.** Nenhuma das doze formações da 5.1 chega a essa forma: a mais carregada põe cinco
jogadores na defesa (`take` 5), cinco no meio (`take` 5) e três no ataque (`take` 3), nunca mais que o
`take`. Ou seja, com escalação automática a escolha é hoje **inobservável**. Ela só aparece na tela
manual, que a 5.4 descreve como aceitando qualquer forma - "seis zagueiros ou onze atacantes são
aceitos" - e apareceria também no dia em que uma formação nova passasse do `take` de alguma linha.

**Resolução (INFERIDO):** acrescentar ao fim. É o que o array de elenco do original faz: lá a
escalação é uma varredura do elenco, uma substituição troca dois números de slot e não move ninguém no
array, e o índice de um reserva no elenco fica **acima** do dos titulares, então ele é varrido depois
de todos eles. Reproduzir isso é acrescentar ao fim. Os sobreviventes mantêm a ordem que tinham nas
duas leituras, que é a parte que a 3.4 não sobrevive a perder. Está dito na docstring de `substitute`.

**Leitura alternativa, rejeitada.** Colocar o substituto no índice que o substituído ocupava. A favor
dela: ele está exatamente na célula que o outro deixou, então manter a posição mantém a lista em ordem
de formação, e é a única leitura em que uma substituição nunca muda **quais** jogadores uma linha
conta - só as notas deles. Contra: ela descreve uma lista de escalação como estrutura própria, e no
original não existe tal lista; existe o array de elenco, e nele o reserva não tem como aparecer antes
de um titular. Rejeitada por isso, e não por ser pior: no exemplo acima ela é o resultado mais
sensato dos dois, e é ela que um reimplementador que não se importe com fidelidade deveria escolher.

**Resolução no original (CONFIRMADO). A leitura adotada está certa.** Quem sai é removido da
escalação em campo e quem entra é **acrescentado ao fim** dela, herdando o slot da célula vaga. Os
sobreviventes mantêm a ordem relativa. A 3.8 foi corrigida para registrar isso, que deixa de ser
inferência.

### 46. As taxas de cartão, expulsão e lesão da 3.16 não vêm das tabelas da 3.8

É a mesma forma dos itens 28 a 30: a 3.16 e as próprias tabelas da 3.8 discordam, e a validação
registra a discordância em vez de esticar uma faixa para escondê-la.

A 3.16 diz "~2-3 amarelos por jogo; um vermelho a cada ~8-12 partidas; uma lesão a cada ~6-10
partidas por lado". A 3.8 sorteia **um** time-vítima por minuto, não um por lado, então cartões e
lesões são taxas de partida inteira, não de lado. Um tempo de 47 minutos passa cerca de 15 minutos na
fase 0, 15 na fase 1 e 17 na fase 2. O alívio médio de marcação é `0,65 x 30 + 0,30 x 10 + 0,05 x 0 =
22,5`, do sorteio da 3.12.

**Amarelo.** O limiar efetivo de cada fase é o valor da tabela mais os 22,5 do alívio médio: 92,5,
62,5 e 52,5 no primeiro tempo; 67,5, 62,5 e 52,5 no segundo.

```
15/92,5 + 15/62,5 + 17/52,5 + 15/67,5 + 15/62,5 + 17/52,5 = 1,512 por partida
```

As sobrescritas do limiar (defeito 5 da 3.15) só aumentam esse limiar, nunca o diminuem, então só
derrubam essa conta. Medido em `SanityCheckTest`: **1,28375** por partida, abaixo de 1,512 e coerente
com a direção das sobrescritas. A 3.16 pede 2 a 3. Fica abaixo.

**Vermelho.** O limiar de vermelho não sofre nenhuma das sobrescritas da 3.8: elas mexem só no
limiar do amarelo. Sobre a tabela de vermelho direto:

```
15/1200 + 15/900 + 17/800 + 15/800 + 15/700 + 17/550 = 0,122 por partida, uma expulsão a cada 8,2
```

A 3.16 pede 8 a 12, e essa conta bateria. Mas `MatchEvent.SendingOff` não conta só o vermelho
direto: a 3.8 chama o segundo amarelo de "evento distinto", e a documentação de
`MatchEvent.Booking` registra que um segundo amarelo grava os dois eventos, o cartão e a expulsão.
A contagem medida é de expulsões dos dois tipos, não só de vermelho direto, e por isso vem mais alta
que a conta acima: **uma a cada 6,038647342995169 partidas**, medido em `SanityCheckTest`. Isso fica
abaixo do piso de 8 que a própria 3.16 pede. Também fica abaixo, e pelo lado oposto ao que a tabela
de vermelho direto sozinha sugeriria: não é que faltem expulsões, é que a tabela de vermelho direto
nunca foi a conta inteira.

**Lesão.** Sem sobrescrita nenhuma tocando o sorteio de lesão:

```
15/1500 + 15/1000 + 17/800 + 15/800 + 15/600 + 17/600 = 0,118 por partida nos dois lados, uma lesão
a cada 16,9 partidas por lado
```

Medido: **uma a cada 17,248814144027598 partidas por lado**, a menos de 2% da conta acima. A 3.16
pede 6 a 10 por lado. Fica abaixo por quase o dobro.

**A leitura que resolveria dois dos três números, e pioraria o terceiro.** Sortear a cadeia de
disciplina para **cada lado independentemente** a cada minuto, em vez de um único time-vítima por
partida, dá a cada lado o sorteio inteiro que hoje os dois lados dividem entre si: a taxa que hoje é
o **total combinado** dos dois lados passaria a valer para **cada lado sozinho**, sem repartição
nenhuma.

Amarelo, sobre o valor medido e não sobre a previsão pré-sobrescritas: `1,28375 x 2 = 2,5675` por
partida - dentro dos 2 a 3 da 3.16, e não perto da borda. Lesão, sobre o valor medido: hoje o total
combinado é `2 / 17,248814144027598 = 0,11596` por partida; sob a leitura alternativa cada lado
sozinho passaria a ter essa taxa inteira, uma lesão a cada `1 / 0,11596 = 8,6` partidas por lado -
dentro dos 6 a 10 da 3.16. Os dois usam o valor medido, não a previsão anterior às sobrescritas, que
é o que a seção anterior mediu e a validação registra.

O vermelho não segue essa mesma conta, porque a leitura atual não bate com a 3.16 por dois motivos
independentes: o time-vítima divide o sorteio de vermelho direto ao meio, **e** dilui o sorteio de
segundo amarelo, que só conta como par quando as duas cartas caem no mesmo jogador. O vermelho
direto simplesmente dobra: `0,122 x 2 = 0,244` por partida. O segundo amarelo não dobra, escala com
o **quadrado** do volume de cada lado, porque a chance de duas cartas caírem no mesmo jogador é a
chance conjunta de duas cartas, cada uma já proporcional ao volume. Hoje, o excedente medido sobre a
tabela de vermelho direto - `0,165600 - 0,122 = 0,0436` por partida, combinado - é inteiramente
segundo amarelo, e se reparte em cerca de `0,0436 / 2 = 0,0218` por lado, sobre um volume de amarelo
de `1,28375 / 2 = 0,642` por lado. Sob a leitura alternativa esse volume por lado dobra para
`1,28375`, e como o segundo amarelo escala com o quadrado do volume, o excedente por lado escala por
`2^2 = 4`: `0,0218 x 4 = 0,0872` por lado, `0,174` somado nos dois lados. Total de vermelho sob a
leitura alternativa: `0,244 + 0,174 = 0,418` por partida, uma expulsão a cada `1 / 0,418 = 2,4`
partidas - bem fora dos 8 a 12 da própria 3.16.

**A leitura alternativa não é uma correção parcial: ela troca qual dos três números falha, e piora
o pior deles.** Na leitura atual o vermelho já é o número mais próximo de bater: medido, ele é
`8 / 6,038647342995169 = 1,3` vezes mais frequente que o piso da 3.16. Sob a leitura alternativa ele
vira o único que não bate, e passa a `8 / 2,4 = 3,3` vezes mais frequente que esse mesmo piso - o
pior desvio dos seis números desta questão, de longe, e cerca do triplo do desvio que a leitura
atual já tem no mesmo número.

**Resolução (MEDIDO, `SanityCheckTest`):** manter um único time-vítima por minuto, exatamente como a
3.8 descreve, e registrar que os três números da 3.16 não batem com essa leitura, um deles - o
vermelho - por um mecanismo que a tabela da 3.8 sozinha não deixa ver. O time-vítima é o único
mecanismo do jogo parecido com viés de arbitragem que a 3.8 nomeia, e é o que faz o mandante e o
visitante perderem jogadores em proporções diferentes ao longo de uma partida.

**Leitura alternativa, rejeitada.** Sortear a cadeia de disciplina uma vez por lado a cada minuto,
em vez de um único time-vítima. A favor dela: o amarelo e a lesão medidos, dobrados, caem dentro da
faixa da 3.16, o que a leitura atual não consegue em nenhum dos três números. Rejeitada por dois
motivos, um estrutural e um aritmético. O estrutural: a 3.8 nomeia o sorteio de time-vítima, ao pé
da letra - `Time-vitima: rand(100) > 55` - como o único viés de arbitragem do jogo; dois sorteios
independentes, um por lado, não são esse viés, são a ausência dele. O aritmético: a mesma troca que
resolve o amarelo e a lesão piora o vermelho de 1,3 para 3,3 vezes o piso da 3.16, porque o sorteio
de segundo amarelo escala com o quadrado do volume por lado, não com o volume. Trocar dois números
que erram por pouco por um terceiro que passa a errar por um fator de três não é a mesma troca que o
resumo "dois de três" sugere à primeira vista.

Isto é **observável**: contar cartões, expulsões e lesões de uma temporada IA contra IA no jogo
original resolve.

**Resolução no original (CONFIRMADO). A leitura adotada está certa e a seção 3.16 é que estava
errada.** Há **um único time-vítima por minuto**, sorteado com `rand(100) > 55`, e a cadeia inteira -
amarelo, vermelho, lesão, janela - roda sobre ele. Não há sorteio por lado. As três faixas da 3.16
não descrevem esse mecanismo, e os três números medidos pela validação (~1,3 amarelo por partida, uma
expulsão a cada ~6 partidas, uma lesão a cada ~17 por lado) são os corretos. A 3.16 foi corrigida para
eles, com um aviso no topo dizendo que as figuras daquela seção não são derivadas das fórmulas.

Dois ajustes de aritmética, os dois na mesma direção (menos cartões ainda):

1. Os três contadores das sobrescritas contam **tentativas**, não eventos - eles sobem mesmo quando o
   grupo de risco sorteado está vazio (item 39). A sobrescrita da lesão, que é a que derruba os
   cartões, portanto dispara a partir da primeira **tentativa** de lesão, um pouco antes do que a
   contagem de lesões sugere.
2. Os tempos não têm 47 minutos cada. Pela 3.1 o 1º tempo tem `45 + rand(0..2)` minutos, média 46, e o
   2º tem `45 + rand(1..5)`, média 48. A fase 2 do 1º tempo cobre ~16 minutos e a do 2º tempo ~18, e
   não 17 e 17. A conta prevista para o amarelo sai em 1,51 por partida do mesmo jeito.

Continua **observável** no original, e é o teste que fecharia os três números de uma vez.

### 47. Se o sacrifício da expulsão e a reposição da lesão valem no 1º tempo

A 3.8 tem duas frases sobre limite de tempo de substituição, e elas não estão no mesmo lugar. Em
Consequências, a frase da expulsão - "se o slot <= 13 e o time é da IA com substituições disponíveis,
a IA sacrifica um atacante" - e a da lesão - "sai e é substituído" - não mencionam tempo de jogo
nenhum. Só mais abaixo, no parágrafo que introduz os pools de minutos "correndo atrás" e de rotina,
aparece "Substituições da IA: 5 por time, só no 2º tempo (+ janela do intervalo)", seguida por "Cada
time sorteia seus minutos" e pelas três janelas que a IA abre por conta própria.

O motor lê o sacrifício e a reposição como não sujeitos a essa restrição: `sacrificeFor` e a troca
que `injure` faz não olham a metade do relógio, só `canSubstitute` e o teto de cinco por lado.

**Resolução (INFERIDO):** a restrição "só no 2º tempo" vale para as três janelas voluntárias que a
IA sorteia por conta própria, e não para o sacrifício da expulsão nem para a reposição da lesão, que
disparam como consequência mecânica de um evento e não de uma janela. A frase está encaixada no
parágrafo que descreve como esses sorteios funcionam, não no parágrafo de Consequências onde as duas
regras forçadas são descritas; ler a restrição para dentro delas exigiria emprestar uma cláusula de
um parágrafo posterior sobre um mecanismo diferente. Há também um argumento de resultado: um lado
que perde um jogador para uma lesão no primeiro tempo e não pode repô-lo até o intervalo joga um
trecho inteiro do primeiro tempo com dez, uma escalação que o time original nunca produziria, porque
o original resolve a temporada inteira e não tem nenhum instante em que um time jogue diminuído sem
poder repor. Testado em `DisciplineChainTest`, nos casos "a sending off at cell thirteen costs a
forward too" e "an injury is replaced and costs three duration draws", os dois rodados no minuto
vinte do primeiro tempo e os dois terminando com uma substituição registrada no log.

**Leitura alternativa, rejeitada.** A frase "só no 2º tempo" é irrestrita e cobre toda substituição
da IA, forçada ou voluntária; um motor fiel ao CLASSIC recusaria o sacrifício e a reposição no
primeiro tempo, deixando o lado a dez ou a onze deformado até o intervalo. A favor dela: a frase não
tem nenhum qualificador do tipo "nas janelas abaixo", e a leitura mais direta de uma restrição de
tempo colocada perto do número de substituições é que ela vale para todas elas. Rejeitada porque
produziria, no primeiro tempo, um lado preso sem nenhum recurso do jogo diante de uma expulsão ou de
uma lesão, uma consequência que a frase citada não anuncia e que o resto da 3.8 nunca discute - nenhuma
outra regra da seção supõe um time jogando diminuído no primeiro tempo sem poder repor.

**Resolução no original (CONFIRMADO). A leitura adotada está certa.** O sacrifício da expulsão e a
reposição da lesão não consultam a metade do relógio: as únicas condições são o time não ser humano e
ainda ter substituição disponível (mais o `slot <= 13` no caso do sacrifício). A restrição "só no 2º
tempo" vale apenas para as três janelas voluntárias. A 3.8 foi corrigida para separar as duas coisas
explicitamente.

Um detalhe a mais no sacrifício, que a 3.8 não registrava: além de 18-25 e depois 14-17, há um
terceiro passo - **se o expulso é o goleiro** e não há ninguém em 18-25 nem em 14-17, sacrifica-se
qualquer jogador de 2-25.

### 48. Em que ordem se aplicam as duas travas do sorteio das janelas de placar

A 3.8 põe **duas** condições sobre o mesmo índice sorteado pelas janelas de intervalo e de "correndo
atrás", e não diz qual delas roda primeiro. A do goleiro: "se o índice cair no goleiro, a janela é
simplesmente desperdiçada, **sem nova tentativa**". A de quem acabou de entrar: "o sorteio também
evita tirar quem acabou de entrar", com **uma única re-tentativa** (ver o item 44, e o item 12 da
3.15 para o defeito que deixa o visitante sem essa proteção).

As duas ordens não dão o mesmo resultado. Pelo **goleiro primeiro**, uma janela morre no goleiro
mesmo quando a re-tentativa teria achado outro jogador. Pela **re-tentativa primeiro**, um índice
descartado por ser de quem acabou de entrar pode cair no goleiro no segundo sorteio e desperdiçar a
janela ali.

O caso que separa as duas é alcançável e não é exótico: o goleiro em campo pode ser exatamente quem
acabou de entrar, um goleiro reserva que repôs o titular lesionado - a reposição de lesão da 3.8
permite isso sem restrição, porque a trava do item 41 só recusa o caminho inverso. Nesse estado o
índice do goleiro é ao mesmo tempo "goleiro" e "acabou de entrar", e as duas ordens divergem no
mesmo minuto.

**Resolução (INFERIDO):** a **re-tentativa primeiro**, e a trava do goleiro aplicada uma única vez,
sobre o índice com que o sorteio ficou. Três razões.

1. As duas frases da 3.8 falam de coisas diferentes. "O **sorteio** também evita tirar quem acabou
   de entrar" descreve o próprio sorteio, isto é como o índice é produzido; "se o **índice** cair no
   goleiro, a janela é desperdiçada" descreve o julgamento do índice já produzido. Uma trava que dá
   forma ao sorteio roda antes de uma trava que julga o resultado dele.
2. A ordem adotada é a mais **parcimoniosa**, e isto é uma questão de parcimônia e não de
   coerência. Com o goleiro primeiro, a trava dele teria de ser aplicada **duas vezes**, antes e
   depois da re-tentativa: aplicada só ao primeiro índice, a re-tentativa poderia entregar o goleiro
   e a janela **tiraria o goleiro**, o que a 3.8 exclui. Testar duas vezes não é incoerente e não
   gasta sorteio nenhum, então o "sem nova tentativa" da 3.8, que fala de **sorteio** e não de
   teste, não descarta essa leitura - ela apenas aplica em dois pontos uma regra que o texto
   enuncia uma vez. Com a re-tentativa primeiro há exatamente um teste de goleiro, sobre o índice
   final, e nenhum sorteio novo é feito por causa dele.
3. É a ordem que mantém a separação de responsabilidades que o próprio motor já tinha: o sorteio e
   sua única re-tentativa ficam em `scoreWindowTarget`, e o que a janela faz com o homem que ela
   acabou recebendo fica em `runSubstitutionWindow`. Testado em `SubstitutionWindowTest`, testes
   "a keeper who came on is redrawn rather than wasting the window" e "a redraw that lands on the
   keeper wastes the window".

**Leitura alternativa, rejeitada.** O goleiro primeiro: se o primeiro índice é o do goleiro a janela
morre ali, e só um índice sobrevivente é comparado contra a lista de quem acabou de entrar. A favor
dela: é a ordem em que a 3.8 escreve as duas frases, e ela deixa a taxa de janelas desperdiçadas em
exatamente uma em onze, que é a figura que o item 44 registra; a ordem adotada sobe essa taxa um
pouco, para uma em onze mais a chance de a re-tentativa cair no goleiro, isto é `1/11 + (k/11)(1/11)`
com `k` substitutos já em campo, o que dá cerca de 9,9% em vez de 9,1% com um substituto em campo -
uma diferença coberta pelo "cerca de" do item 44. Rejeitada por **parcimônia**, e não por ser
incoerente: ela se sustenta, mas ao custo de duplicar o teste do goleiro, que o texto enuncia uma vez
só, e as duas ordens divergem em exatamente um caso - um índice que é ao mesmo tempo o do goleiro e
o de alguém que acabou de entrar. Nenhuma das duas leituras tem apoio textual direto na ordem; a
adotada tem o argumento 1 a favor e um teste a menos.

### 49. O que conta como "quem acabou de entrar" nas janelas de placar

A 3.8 diz que o sorteio das janelas de placar "evita tirar quem acabou de entrar", com uma única
re-tentativa (itens 44 e 48; o item 12 da 3.15 registra o defeito que tira essa proteção do
visitante). A frase deixa duas coisas em aberto: **quais** entradas contam, e **por quanto tempo** a
proteção vale.

1. Só contam as trocas das próprias janelas voluntárias, ou também o reserva que entrou pela
   reposição de uma lesão e o que entrou pelo sacrifício de uma expulsão?
2. "Acabou de entrar" tem prazo? Quem entrou no minuto 5 do 2º tempo ainda está protegido no 45?

**Resolução (INFERIDO):** conta **toda** entrada, qualquer que seja a causa, e a proteção **não
expira**. A lista é de todo mundo que aquele lado pôs em campo naquela partida; um nome entra nela
quando o jogador pisa no campo e nunca sai dela. Está em `SideState.arrivals`, alimentada por
`substitute`, que é o único caminho do banco para o campo. Testado em `SubstitutionWindowTest`, teste
"a substitute who has just come on is not taken off again".

Três razões. Primeira: uma vez em campo, um substituto não carrega marca nenhuma de por que entrou.
A 3.8 descreve as três entradas - janela, sacrifício de expulsão, reposição de lesão - e todas as
três terminam igual, com o reserva mais adequado à célula vaga acrescentado ao fim da lista (item
45). Separar as três exigiria uma marca que a 3.8 não descreve em lugar nenhum. Segunda: um prazo
exigiria um número, e a 3.8 não publica nenhum aqui, embora publique em toda outra regra de tempo da
seção ("2º tempo e minuto >= 5", "após o minuto 40", "a cada 7 minutos"). Terceira: o efeito é
pequeno e limitado dos dois lados. O teto de cinco trocas por lado deixa a lista com no máximo cinco
nomes entre onze, e a re-tentativa é única, então nem a leitura mais protetora chega perto de
bloquear as janelas de placar.

**Leitura alternativa (1), não adotada: só as entradas pelas próprias janelas contam.** A favor
dela: o parágrafo que enuncia a regra fala das janelas, e é razoável ler a proteção como interna ao
mecanismo que ela descreve. Contra: sob ela, um lado que acabou de repor um lesionado poderia sacar
o reserva no minuto seguinte, que é precisamente o comportamento que a frase existe para evitar, e a
distinção não sobrevive ao fato de as três entradas serem indistinguíveis depois de feitas.

**Leitura alternativa (2), não adotada: a proteção expira depois de alguns minutos.** A favor dela:
"acabou de entrar" é literalmente uma janela de tempo curta, e sem prazo a regra passa a dizer "não
tire ninguém que já foi trocado nesta partida", que é uma afirmação mais forte que a frase. Contra:
não há número nenhum na 3.8 para esse prazo, e inventar um seria escolher a intensidade da regra no
lugar do original. Note que as duas leituras só divergem quando um lado já trocou e a janela sorteia
justamente o substituto, o que o teto de cinco trocas mantém raro.
