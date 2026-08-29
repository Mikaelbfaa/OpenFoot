# Defeitos conhecidos do original

O conjunto de regras `CLASSIC` reproduz o Brasfoot 22-23 **defeito por defeito**, de propósito.
Isso não é descuido: é a única forma de comparar a saída estatística deste motor com a do jogo
original e provar que ele está certo.

O conjunto `MODERN` corrige o que estava claramente quebrado.

**Se você encontrou um destes jogando, não é bug deste projeto.** Troque o conjunto de regras ou
comente na issue correspondente.

## Já implementados

### Slot 18 não conta para nada

O agregado de ataque lê os slots 19 a 25, mas as pontas ficam nos slots 18 e 25. Quem joga no slot
18 não contribui para nenhuma linha. Na prática o 3-4-3, a única formação que usa esse slot, ataca
com dois dos seus três atacantes, e ainda divide por três.

Spec: seção 3.4. Em `MODERN` a faixa de ataque vira 18 a 25.

### Mando de campo invertido na conversão de chutes

O ajuste de mando **aumenta** os dois pesos de não-gol do mandante e os **diminui** para o
visitante. O mandante converte pior, cerca de 8,8% por chute, contra 11,1% do visitante. O maior
volume de chutes do mandante quase cancela a diferença, o que provavelmente é por isso que ninguém
percebeu.

Spec: seção 3.6c. Em `MODERN` o sinal é corrigido.

### O peso de "para fora" é sobrescrito

No mesmo trecho, o peso de "para fora" é recalculado a partir do peso de "defendido", jogando fora
a comparação entre defesa e ataque. Em toda partida com mando, a qualidade da defesa adversária
deixa de influenciar se o chute vai para fora.

Spec: seção 3.6c. Em `MODERN` o peso original é preservado. Há um par de testes de caracterização
que prova os dois comportamentos.

### Zero e um zagueiro dão no mesmo

A regra anti-exploit contra escalações quebradas atribui pesos 0,10 e 0,05, mas o piso de 0,2 roda
depois e engole os dois. Uma defesa sem zagueiro nenhum e uma com um zagueiro sofrem exatamente a
mesma coisa.

Spec: seção 3.6b, com a resolução registrada em `spec/OPEN-QUESTIONS.md`. Mantido como está: sem o
piso, um zagueiro sofreria **mais** que nenhum, o que é pior.

### Depois da primeira lesão, a taxa de cartões despenca

O limiar de amarelo é sobrescrito ao longo da partida, e cada sobrescrita **apaga** a anterior em
vez de se acumular com ela. Depois de duas expulsões ele passa a `2 x limiarVermelho`; depois de
**uma única lesão**, passa a `5 x limiarLesão`, que é uma ordem de grandeza maior que qualquer
limiar de amarelo da tabela. Na prática, a primeira lesão de uma partida quase encerra os cartões
dela, para os dois times ao mesmo tempo.

Spec: seção 3.8, defeito 5 da seção 3.15. Em `MODERN` as duas sobrescritas são desligadas colocando
o gatilho de cada uma fora de alcance.

### A janela de substituição do visitante é engolida pela do mandante

As duas janelas de um mesmo minuto são avaliadas juntas, o mandante primeiro, e se o mandante
**efetivamente trocou** alguém a janela do visitante nem chega a ser examinada. Na prática o
visitante troca menos que o mandante ao longo de uma temporada, sem que nenhuma regra diga isso.

Só morde quando um minuto vale para os dois lados ao mesmo tempo. O intervalo, que o item 11 cita,
não serve: a 3.8 pede ao mandante um placar de 1 gol atrás e ao visitante 2, e o déficit de um lado
é a sobra do outro, então as duas condições nunca valem na mesma partida. A mesma conta elimina duas
janelas de "correndo atrás" no mesmo minuto. O que sobra é um minuto de **rotina** de um lado
coincidindo com um de **"correndo atrás"** do outro: esses dois vêm de pools diferentes - 19-38
contra 16-35 e 36-42 - e por isso continuam podendo cair no mesmo minuto, mesmo agora que os dois
lados tiram seus minutos dos mesmos embaralhamentos. A aritmética está no item 43 do
`spec/OPEN-QUESTIONS.md`.

Spec: seção 3.8, defeito 11 da seção 3.15. Em `MODERN` as duas janelas de um mesmo minuto rodam.

### O visitante saca quem acabou de entrar

O sorteio das janelas de placar evita tirar quem entrou há pouco, com uma única re-tentativa, mas a
checagem consulta **sempre a lista de quem entrou pelo mandante**, seja qual for o time da janela.
Efeito: o mandante nunca tira quem acabou de entrar e o visitante não tem proteção nenhuma - pode
sacar num minuto o reserva que pôs em campo no minuto anterior.

Spec: seção 3.8, defeito 12 da seção 3.15. Em `MODERN` cada lado consulta a própria lista.

### Os dois times tiram seus minutos do mesmo embaralhamento

Os cinco pools de minutos de substituição (19-38 para "correndo atrás", 5-15, 16-35 e 36-42 para
rotina, 43-47 para os extras) são embaralhados uma vez por partida, e os **dois** times leem blocos
fixos e distintos do mesmo embaralhamento, o mandante primeiro. Não é um plano por time: é um
sorteio só, para a partida inteira.

Efeito: dentro de uma partida os dois lados nunca marcam o mesmo minuto **dentro do mesmo pool**.
Isso mata a coincidência de "correndo atrás" contra "correndo atrás" e a de rotina contra rotina
(que, quando os pools sorteados são diferentes, já estava morta porque as faixas não se sobrepõem).
O que **continua** possível é um minuto de rotina de um lado cair sobre um de "correndo atrás" do
outro, porque esses vêm de embaralhamentos diferentes - e é justamente disso que o defeito acima,
a janela engolida, vive.

Só a metade de dentro da partida é reproduzida. A outra metade do mesmo item 8, os pools serem
estáticos **entre** partidas, continua fora; ver a seção seguinte.

Spec: seção 3.8, defeito 8 da seção 3.15. Mantido nos dois conjuntos de regras: não é um número
errado, é como o original sorteia.

### A IA nunca é punida por defesa quebrada

As regras anti-exploit acima só valem quando há clube humano na partida.

Spec: seções 3.6b e 3.6c. Mantido.

### Um gol de bola rolando, de falta ou olímpico conta duas vezes

O contador de gols **da partida** do finalizador sorteado sobe uma vez logo depois do sorteio de tipo,
para todo tipo que não seja pênalti nem gol contra, e sobe de novo quando o gol é somado ao placar.
Bola rolando, falta e olímpico contam duas vezes; pênalti em IAxIA e gol contra contam uma. Como é
esse contador que a nota da seção 3.14 lê, um gol de bola rolando vale **+1,8** de nota e não +0,9.

A artilharia da temporada não é afetada: ela é montada a partir dos eventos, e o evento continua
sendo um só.

Spec: seção 3.7, defeito 13 da seção 3.15, item 51 de `spec/OPEN-QUESTIONS.md`. Mantido nos dois
conjuntos de regras: a contagem dobrada é o comportamento do original, e deduplicar mudaria a
distribuição de notas de todo jogo de ataque.

### O autor do evento e o dono do gol da partida são dois créditos diferentes

Num gol de pênalti, falta ou olímpico em que o designado da seção 5.6 está em campo, ele aparece como
autor no relato e na artilharia da temporada, mas o **+0,9 de nota fica com o finalizador sorteado**.
Num gol contra, o autor exibido vira um jogador do time que defende e leva o -1,5, enquanto o
finalizador sorteado do time atacante ganha um gol na contagem da partida que **não aparece em lugar
nenhum**.

O motor guarda os dois lados no mesmo evento, `author` e `scorer`, justamente porque eles podem
divergir; unificar os dois moveria 0,9 de nota entre dois jogadores em cerca de 8,5% dos gols.

Spec: seção 3.7, item 57 de `spec/OPEN-QUESTIONS.md`. Mantido nos dois conjuntos de regras.

### Gol de pênalti em partida com time humano não é somado direto

Quando o sorteio da seção 3.7 devolve pênalti e **qualquer um dos dois times** é humano, o gol não vai
para o placar: ele é entregue ao pênalti interativo da seção 3.10, que decide. Nada se perde, porque a
condição do visualizador é a mesma, e o pênalti convertido ali vale **+0,9** de nota para o batedor, e
não +1,8, porque quem soma é o visualizador e ele incrementa uma vez só.

Spec: seções 3.7 e 3.10, item 51 de `spec/OPEN-QUESTIONS.md`. Mantido nos dois conjuntos de regras.

## Nunca reproduzido, em nenhum conjunto de regras

### Os dois caminhos mortos do sorteio de tipo de gol

A seção 3.7 tem um ramo que transformaria um gol olímpico em gol de bola rolando quando o jogador
sorteado fosse goleiro. Ele é **inalcançável**: o sorteio de finalizador da seção 3.6 já exclui todo
goleiro de posição natural, então nunca há um goleiro para o ramo testar. O item 16 da seção 3.15
manda não portar, e ele não foi portado.

O item 17 da mesma seção registra uma **segunda tabela de tipo de gol**, mais generosa com faltas
(80% bola rolando, 5% pênalti, 13% falta) e acoplada a um sorteio de cartão para o time que cometeu o
pênalti. Nada no original a chama: é código morto de outra versão do motor. Ela também não foi
portada, e por isso não existe campo nenhum de `RuleSet` que pudesse ligá-la.

Spec: seção 3.7, itens 16 e 17 da seção 3.15.

### Pools de minutos de substituição estáticos entre partidas

O item 8 da seção 3.15 registra que os pools de minutos de substituição do original são
estáticos e compartilhados, reembaralhados por partida, de modo que partidas consecutivas sorteiam
minutos correlacionados. É um defeito nomeado como qualquer outro desta página, mas ao contrário de
todos os outros ele não sai reproduzido sob `CLASSIC` nem corrigido sob `MODERN`:
`matchSubstitutionPlans` embaralha os pools de novo a cada partida, de um gerador derivado só da
semente daquela partida, e nada é guardado entre partidas.

Só esta metade do item 8 fica de fora. A outra, os dois times lendo blocos fixos do mesmo
embaralhamento **dentro** da partida, é reproduzida e tem seção própria acima.

É uma divergência deliberada, não uma omissão. O defeito é estado global mutável, e reproduzi-lo
faria o resultado de uma partida depender de quais partidas rodaram antes dela, o que quebra a
propriedade sobre a qual o projeto inteiro é construído: uma carreira se repete a partir da semente e
uma partida se resimula sozinha. A distribuição de cada plano isolado continua saindo das mesmas
faixas e probabilidades que a 3.8 publica; só a correlação entre partidas vizinhas se perde, e nenhuma
figura da 3.16 a mede.

**A parte que não é estado global já foi reproduzida.** Dentro de uma partida os dois times tiram do
**mesmo** embaralhamento, em posições fixas e distintas, e por isso não coincidem dentro de um mesmo
pool. Isso é sorteio dentro da partida, não estado entre partidas, e não conflita com a
reprodutibilidade. A garantia vale por pool e não entre pools: o detalhe está no item 42 do
`spec/OPEN-QUESTIONS.md`.

Spec: seção 3.15 item 8, seção 3.8. Resolução registrada em `spec/OPEN-QUESTIONS.md`, item 42.

## Ainda não implementados

Ficam registrados aqui para quando o código chegar nessas partes.

- Três dos quatro botões de tática são inertes. Formação, postura e lado do ataque são escritos e
  nunca lidos. Só a marcação faz algo, e principalmente na taxa de cartões (seção 3.12).
- A força exibida na interface usa divisão inteira e mostra zero com energia abaixo de 100
  (seção 3.15). É defeito de exibição, não do motor.
- A multa rescisória incide em exatamente 1 dos 8 caminhos de venda, o que torna listar um jogador
  estritamente pior que oferecê-lo manualmente (seção 6.9).
- Renovação de 3 anos custa +5% e a de 2 anos custa +15%. Contrato longo é sempre o melhor negócio
  (seção 6.9).
- Prorrogação nunca é simulada. Empate em mata-mata vai direto para uma fórmula abstrata de
  pênaltis (seção 3.10).
- Clubes da IA não têm dinheiro (seção 6.0). Esse é o mais estrutural de todos.
- Os "minutos jogados" que descontam nota não são tempo em campo: são o minuto do último evento em que
  o jogador aparece. Marcar no 1º tempo custa -1,5, ou -2,5 antes do minuto 15; assistir depois do
  minuto 35 do 2º tempo custa -2,5 (seção 3.15, item 14).
- O desconto de pênalti perdido da nota multiplica o contador de **gols contra** em vez do de pênaltis
  perdidos, e por isso quase nunca desconta nada (seção 3.15, item 15).
- Num pênalti interativo perdido, **5 dos 7 desfechos sobem o contador de "no alvo"** e só 3 deles
  creditam uma defesa ao goleiro. Os outros 2 - a bola na trave e o batedor que escorrega - contam
  como chute no alvo sem que o goleiro tenha tocado na bola, então a linha "no alvo" da súmula não é
  exatamente "gols + defesas" quando houve pênalti interativo (seções 3.10 e 3.13).
- Os ramos "+0,2 se o adversário chutou mais de 15" e "+0,3 se chutou mais de 20" da nota do goleiro
  estão atrás do ramo "mais de 10" numa cadeia de senão e nunca são alcançados (seção 3.14).
- O capitão e o "falso 9" da seção 5.6 são derivados pelo original e guardados no time, mas nada os
  lê: a própria seção marca o efeito real dos dois como nenhum, display no caso do capitão, manual e
  sem efeito nenhum no caso do falso 9. O motor não deriva nem guarda os dois por isso, e só o
  batedor de falta/pênalti e o cobrador de escanteio existem no código (seção 5.6).
- Existe no original um caminho que recalcularia o batedor designado a partir de uma lista dada, a
  escalação da partida por exemplo, só quando o designado não estivesse nela. Nada no original chama
  esse caminho, e por isso ele não foi portado (seção 5.6, item 56 de `OPEN-QUESTIONS.md`).

## Não são defeitos

Coisas que parecem defeito, já foram consideradas, e ficam como estão nos dois conjuntos de regras.
Estão aqui para que ninguém gaste um PR tentando corrigi-las.

### Divisores de linha fixos

Os divisores são constantes 5, 5 e 3, e não a quantidade de jogadores encontrada. Escalar quatro
meias em vez de cinco custa um quinto da força de meio-campo, e um atacante isolado rende a própria
força dividida por três.

Isso já foi descrito aqui como defeito. Não é. Some os tamanhos nominais das linhas: 5 na defesa,
5 no meio, 3 no ataque, ou seja **13 jogadores para os 10 de linha que existem**. Todo time está
sempre 3 curto, e **escolher onde ficar curto é a decisão de formação**. Os divisores são o preço
de cada falta: um atacante a menos custa um terço do ataque, um zagueiro a menos custa um quinto da
defesa, então encurtar o ataque é caro e encurtar a defesa é barato.

O 5-2-3 preenche defesa e ataque por inteiro e joga toda a falta no meio-campo, que é justamente o
que decide o duelo de posse: ataque forte, defesa forte, e quase nenhuma bola. O 4-4-2 divide a dor
e termina com o ataque estruturalmente abaixo da defesa. As duas coisas são posições coerentes num
trade-off de verdade.

A alternativa, dividir pela quantidade encontrada, transformaria o ataque na média de quem está lá
na frente. Um atacante atacaria exatamente igual a três, e a formação deixaria de afetar a força de
qualquer linha. Seria um jogo mais pobre.

Spec: seção 3.4. **Mantido em `CLASSIC` e em `MODERN`.**

O que é defeito de verdade e continua corrigido em `MODERN` é o slot 18, logo acima: ali o jogador
não entra em nenhuma conta. Isso não é trade-off, é um jogador que não existe.

### A janela de placar sorteada no goleiro é desperdiçada

As janelas de intervalo e de "correndo atrás" sorteiam um índice qualquer da escalação em campo, e
não um jogador de linha. Se o índice cai no goleiro, a janela morre ali: ninguém sai, ninguém entra
e nenhum sorteio novo é feito. Cerca de uma em onze dessas janelas se perde assim.

Não vira delta do `MODERN`, por três motivos. A seção 3.15 não lista isso entre os defeitos, e a 3.8
escreve como sendo a regra. Corrigir mexeria numa **probabilidade** e não numa regra, que é
exatamente o tipo de mudança que os divisores fixos acima já recusam. E a correção nem é
determinada: uma janela que não pode ser desperdiçada teria de re-sortear ou de sortear entre dez em
vez de onze, e nada na spec diz qual - o `MODERN` estaria inventando uma regra, não removendo um
erro.

Spec: seção 3.8, item 44 do `spec/OPEN-QUESTIONS.md`. **Mantido em `CLASSIC` e em `MODERN`.**

## Como propor uma correção

Um defeito vira delta do `MODERN` quando é claramente não intencional e a correção não muda o
balanceamento de forma imprevisível. Um número que apenas parece mal calibrado é balanceamento, não
defeito, e precisa de discussão antes.

Em nenhum caso a correção entra como `if` no motor. Ela vira campo do `RuleSet`. Ver
`CONTRIBUTING.md`.
