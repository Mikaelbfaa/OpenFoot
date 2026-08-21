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

### A IA nunca é punida por defesa quebrada

As regras anti-exploit acima só valem quando há clube humano na partida.

Spec: seções 3.6b e 3.6c. Mantido.

## Nunca reproduzido, em nenhum conjunto de regras

### Pools de minutos de substituição estáticos e compartilhados

O item 8 da seção 3.15 registra que os pools de minutos de substituição do original são
estáticos e compartilhados, reembaralhados por partida, de modo que partidas consecutivas sorteiam
minutos correlacionados. É um defeito nomeado como qualquer outro desta página, mas ao contrário de
todos os outros ele não sai reproduzido sob `CLASSIC` nem corrigido sob `MODERN`: `substitutionPlan`
sorteia um plano novo por time e por partida, de um gerador derivado só da semente daquela partida, e
nada é compartilhado entre partidas.

É uma divergência deliberada, não uma omissão. O defeito é estado global mutável, e reproduzi-lo
faria o resultado de uma partida depender de quais partidas rodaram antes dela, o que quebra a
propriedade sobre a qual o projeto inteiro é construído: uma carreira se repete a partir da semente e
uma partida se resimula sozinha. A distribuição de cada plano isolado continua saindo das mesmas
faixas e probabilidades que a 3.8 publica; só a correlação entre partidas vizinhas se perde, e nenhuma
figura da 3.16 a mede.

**Atenção a uma parte que não é estado global e deve ser reproduzida.** Dentro de uma partida, os dois
times tiram do **mesmo** embaralhamento, em posições fixas e distintas: os minutos de rotina e de
"correndo atrás" do mandante e do visitante **nunca coincidem**. Um plano sorteado por time
independentemente deixa os dois lados colidirem, e a colisão tem efeito, porque a janela do visitante
é engolida pela do mandante no mesmo minuto (item 11 da 3.15). Isso é sorteio dentro da partida, não
estado entre partidas, e não conflita com a reprodutibilidade.

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
- **A janela de substituição do visitante é engolida pela do mandante.** As duas são avaliadas na
  mesma passagem, mandante primeiro; se o mandante trocou de fato, a do visitante nem é examinada.
  No intervalo, onde os dois lados são sempre avaliados juntos, isso vale para toda partida em que
  o mandante trocou no intervalo (seção 3.15 item 11).
- **A trava "não tire quem acabou de entrar" só protege o mandante.** No sorteio das janelas de
  placar, a lista consultada é sempre a de quem entrou pelo mandante, para os dois times. O
  visitante pode sacar num minuto o reserva que pôs em campo no minuto anterior (seção 3.15 item 12).
- **As janelas de placar desperdiçam a troca quando o sorteio cai no goleiro.** O índice é sorteado
  sobre a escalação inteira e não há nova tentativa: ~1 em 11 janelas de intervalo ou de "correndo
  atrás" não produz troca nenhuma (seção 3.8).
- **Os contadores que derrubam o limiar do cartão contam tentativas, não eventos.** Eles sobem mesmo
  quando o grupo de risco sorteado está vazio, então a sobrescrita da lesão - a mais violenta das
  três - dispara a partir da primeira tentativa de lesão, tenha ela lesionado alguém ou não
  (seção 3.8).
- **A perda permanente de força por lesão depois dos 35 tem piso 0, não 1.** O piso só é aplicado
  quando o resultado fica negativo; uma força que cai exatamente em 0 fica em 0 (seção 3.8).
- **Uma lesão de duração 0 tira o jogador da partida e não registra lesão nenhuma.** Acontece com
  jogadores de até 20 anos quando o termo de sorteio sai zero (seção 3.8).

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

## Como propor uma correção

Um defeito vira delta do `MODERN` quando é claramente não intencional e a correção não muda o
balanceamento de forma imprevisível. Um número que apenas parece mal calibrado é balanceamento, não
defeito, e precisa de discussão antes.

Em nenhum caso a correção entra como `if` no motor. Ela vira campo do `RuleSet`. Ver
`CONTRIBUTING.md`.
