# Experimentos que resolvem lacunas da spec

A regra do projeto e que uma lacuna se resolve por **comportamento observado jogando o original**,
nunca por descompilacao. Este arquivo guarda os experimentos ja desenhados, com o que cada resultado
significa, para que qualquer pessoa possa rodar um deles e trazer a resposta.

Um experimento so entra aqui se as duas hipoteses derem previsoes **diferentes o bastante para
serem vistas**. Se as previsoes ficam a um ponto percentual uma da outra, o experimento nao presta e
e melhor dizer isso do que gastar cem partidas.

## E1. Os divisores de linha sao fixos ou seguem a contagem de jogadores? [RESOLVIDO]

**Resolvido sem rodar, pela equipe de spec em quarentena (ago/2026): os divisores sao FIXOS.**
A leitura direta da logica do original confirmou a coluna "divisores fixos" da tabela abaixo,
incluindo os casos degenerados da 3.4. Registro completo no item 30 de OPEN-QUESTIONS. O
protocolo fica guardado como verificacao independente barata para quem quiser conferir jogando.

Resolve o item 30 e, junto com ele, a duvida mais cara em aberto: se os divisores seguirem a
contagem, **todo agregado de linha do motor esta errado**, e portanto todo resultado de partida.

### As duas hipoteses

| | divisores fixos (o que a 3.4 diz) | divisores por contagem |
|---|---|---|
| meio-campo com 4 meias, todos nota 5,0 | `4 x 5 / 5 = 4,0` | `4 x 5 / 4 = 5,0` |
| meio-campo com 2 meias, todos nota 5,0 | `2 x 5 / 5 = 2,0` | `2 x 5 / 2 = 5,0` |

Ou seja: com divisor fixo, tirar meias enfraquece o meio-campo proporcionalmente. Com divisor por
contagem, a quantidade de meias **nao muda nada** e so a qualidade media conta.

### Por que medir posse e nao gols

O duelo de posse compara meio-campo contra meio-campo e nada mais. Nao passa por ataque, defesa,
finalizador nem conversao, entao mexer no numero de meias mexe na posse e em mais nada. A posse
tambem e exibida como porcentagem ja agregada sobre uns 92 duelos, o que a torna um numero de baixa
variancia: da para ler uma partida e ja saber muita coisa.

Gols seriam a medida errada. A diferenca esperada em gols e pequena perto do ruido de Poisson, e
precisaria de mais de cem partidas por braco para dar tres desvios. Posse resolve em tres a cinco.

### Protocolo

1. Assuma um clube humano e escolha um adversario fixo que jogue 4-4-2. Jogue sempre **em casa**,
   contra o mesmo adversario, na mesma temporada.
2. Braco A: escale **4 meias** (slots 11 a 17), o resto como preferir.
3. Braco B: escale **2 meias**, movendo os outros dois para a defesa. Nao mexa em mais nada.
4. Rode 3 a 5 partidas em cada braco e anote a **porcentagem de posse exibida** em cada uma.
5. Compare as medias dos dois bracos.

### Previsoes

Calculadas a partir da 3.6a com divisor 8 (temporada 1), adversario com meio-campo 4,0, jogando em
casa. Sao previsoes do motor deste projeto sob cada hipotese.

| meias escalados | se os divisores forem fixos | se seguirem a contagem |
|---|---|---|
| 4 | 53,2% | 53,2% |
| 3 | 47,5% | 53,2% |
| 2 | 41,8% | 53,2% |

### Como ler o resultado

- **A posse cai visivelmente ao tirar meias** (algo perto de 53 para 42): os divisores sao fixos, a
  3.4 esta certa e o motor tambem. Nada a fazer alem de fechar o item 30.
- **A posse nao se mexe** (fica perto de 53 nos dois bracos): os divisores seguem a contagem, a 3.4
  esta errada, e `LineAggregates` precisa dividir por `tally.count`. Isso muda todo resultado de
  partida ja produzido e invalida as faixas da 3.16 medidas ate aqui.
- **Qualquer coisa no meio**: registre os numeros crus no item 30 e nao conclua. Uma terceira leitura
  e possivel e vale mais que uma escolha forcada entre as duas.

Onze pontos de diferenca nao se confundem com ruido, entao nao ha necessidade de estatistica aqui.
Se der um resultado ambiguo, o mais provavel e que alguma outra variavel tenha mudado entre os
bracos, e nao que o efeito seja pequeno.
