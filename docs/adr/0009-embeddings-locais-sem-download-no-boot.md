# ADR-0009 — Embeddings pelo gateway, com caminho local offline como fallback

- **Status:** Aceito
- **Data:** 2026-08-05
- **Substitui parcialmente:** a configuração `spring.ai.embedding.transformer.*` da Fase 1

## Contexto

A subida da aplicação estava demorando de forma inaceitável, e o diagnóstico apontava para o
HuggingFace. Medindo, o problema é mais específico — e a conclusão muda o que se deve fazer.

**O que foi medido (2026-08-05, nesta máquina):**

| Medida | Valor |
|---|---|
| Banda até o HuggingFace | **~200 KB/s** |
| Banda até o Maven Central (controle) | ~145 KB/s |
| Modelo fp32 + tokenizer | 448 MB + 17 MB = **465 MB** |
| Tempo do download completo | **~39 minutos** |

O primeiro número parece acusar o HuggingFace; o segundo desmente. **O link inteiro está lento** —
o Maven Central não vai melhor. Trocar de fornecedor ou de mirror não resolveria nada, e essa é a
razão de esta ADR não fazer isso.

O que torna o problema recorrente é onde o arquivo é guardado. O Spring AI baixa para
`${java.io.tmpdir}/spring-ai-onnx-generative`. Esse diretório:

- é **purgado pelo macOS** periodicamente e no reboot;
- **não existe** em contêiner nem em runner de CI.

Ou seja, o que se acreditava ser "baixa uma vez" era, na prática, **baixar de novo a cada boot
frio**. Foi assim que se confirmou: no início desta análise o diretório de cache simplesmente
não existia mais. E, como o download acontece dentro da inicialização do contexto Spring, sem
barra de progresso e sem retomada, o sintoma que chega ao desenvolvedor não é "estou baixando
465 MB" — é "a aplicação travou".

## Decisão

**O embedding passa pelo gateway LiteLLM que já existe** (ADR-0007), e o modelo local vira o
caminho de fallback — offline, sem chave, para CI, testes e para quem não pode mandar texto para
fora. Nenhuma das duas coisas é trocar o HuggingFace de lugar.

### 1. Gateway como padrão

`spring.ai.model.embedding=openai` apontando para o proxy. Não há modelo dentro do processo:
nada para baixar, nada para carregar em memória, e o boot deixa de ter qualquer relação com o
tamanho de um arquivo. `dimensions: 384` mantém a coluna do pgvector — `text-embedding-3-*`
suporta encurtar o vetor (Matryoshka), então **não houve migração de schema**.

Duas propriedades vêm de graça e não eram o objetivo inicial:

- **é o caminho de melhor qualidade.** O peso local que cabe no disco é quantizado, e a
  quantização custa recall medido — §"O custo do caminho local" abaixo;
- **a chave do vendor continua só no proxy**, que é a mesma garantia do chat (ADR-0007). A app
  não ganhou nenhuma credencial nova.

Publicar o embedding no mesmo gateway também é o que torna a troca de provedor uma questão de
três linhas em `litellm/config.yaml` — inclusive para um **servidor local** (Ollama, TEI,
Infinity) quando o texto não puder sair da rede. A aplicação não muda.

### 2. Caminho local (`APP_EMBEDDING_PROVIDER=transformers`): o que foi consertado

O fallback não podia continuar como estava, porque era exatamente ele que causava o sintoma
original. Três correções:

**Cache persistente, fora do `tmp`.** `app.embedding.cache-dir` aponta para
`~/.cache/agentic-rag/onnx`. Sobrevive a reboot, é compartilhado entre clones e é montável como
volume em contêiner.

**Peso int8 em vez de fp32.** 112 MB em vez de 448 MB, mesmas 384 dimensões e mesmo tokenizer.

**Download como passo explícito.** `scripts/prefetch-embeddings.sh`: progresso, `curl -C -`
(retoma de onde parou), `--retry`, SHA-256 e idempotência; `--verify` só confere, para CI. A
aplicação resolve **arquivo primeiro, rede depois** (`LocalOnnxEmbeddingConfig`) e diz no log
qual caminho usou. Com `require-local=true` — ligado no CI — cache frio vira **erro com o comando
de correção**, não um boot de onze minutos.

O CI usa este caminho de propósito: o padrão da aplicação exigiria uma chave de vendor no
pipeline e uma chamada de rede por embedding, duas coisas que um CI não deve ter.

### 3. O custo do caminho local, medido

`QuantizationFidelityTest` carrega os dois pesos lado a lado. **int8 não é de graça:**

| Medida | int8 vs fp32 |
|---|---|
| Fidelidade do vetor (cosseno, mesmo texto) | média **0,994**, pior caso **0,990** |
| Pares do domínio recuperados corretamente | **2/3** (fp32: 3/3) |
| Mesmo texto em lote × sozinho | int8 **0,989** · fp32 **1,000** |

O par perdido é "rate limit por token no payments-sdk", que deixa de recuperar "limite de
requisições por minuto por credencial". O teste **fixa** esse número em vez de escondê-lo: exige
a maioria e registra no log o que caiu.

A última linha desmente a explicação intuitiva e merece registro: a divergência entre embedar em
lote (ingestão) e sozinho (busca) **não é padding** — o fp32 dá 1,000. É da quantização dinâmica,
cujas escalas de ativação são calculadas a partir do tensor em processamento.

Nada disso aparece no caminho padrão, porque lá não há quantização. É o argumento mais concreto a
favor da inversão.

## Resultado medido

Bytes de rede no boot, e tempo até `Started AgenticRagApplication` com o health respondendo `UP`:

| Cenário | Rede no boot | Boot |
|---|---|---|
| Antes — cache frio (o caso recorrente, não o excepcional) | **465 MB** | **~39 min** |
| **Depois — gateway (padrão)** | **0 bytes** | **3,3 s** |
| Depois — fallback local int8, cache quente | 0 bytes | 7,8 s |
| Prefetch do fallback, uma vez na vida da máquina | 129 MB | ~11 min |

## Consequências

- ✅ **Boot de ~39 min (cache frio) para 3,3 s**, sem nenhum byte de rede e sem modelo em memória.
- ✅ Sem quantização no caminho padrão: o recall perdido no int8 não existe aqui.
- ✅ Trocar de provedor de embedding — inclusive para um servidor local — é config no gateway.
- ✅ O fallback local continua íntegro e é o que roda no CI: offline, keyless, determinístico.
- ✅ Nenhuma migração de schema: `dimensions: 384` preserva a coluna do pgvector.
- ⚠️ **O padrão passou a exigir o gateway de pé e uma chave.** A app não sobe menos sem ele
  (boot é o mesmo), mas ingestão e busca falham até o proxy responder. Quem quer o comportamento
  antigo — offline, sem chave — usa `APP_EMBEDDING_PROVIDER=transformers`.
- ⚠️ **Passou a haver custo por token de embedding e latência de rede na ingestão.** Em troca,
  saiu o custo de 112 MB de RAM por instância e o de manter o arquivo distribuído.
- ⚠️ **O texto ingerido agora sai da máquina** para virar vetor. Era uma propriedade real do
  caminho local e foi perdida no padrão. Para bases que não podem sair, o gateway aponta para um
  embedding server interno — ou usa-se o fallback.
- ⚠️ **A troca exige reingestão.** `model-id` passou a `text-embedding-3-small-384`; ele entra no
  hash de idempotência e força o re-embed sozinho.

## Alternativas consideradas

- **Trocar o HuggingFace por outro mirror.** Foi a hipótese inicial e a medição a descartou: o
  Maven Central, no mesmo momento, entregava 145 KB/s. O gargalo é o link, não a origem.
- **Modelo menor (MiniLM inglês, 90 MB).** Resolveria o tamanho e reintroduziria o bug que o
  projeto já corrigiu: a base é em PT-BR e a consulta "estornar" saía do top-3. Rejeitado.
- **Versionar o modelo no repositório (Git LFS).** Acaba com o download no boot, mas põe 112 MB
  de binário no histórico e transfere o problema para o `git clone`, que passa a ser lento para
  todo mundo, inclusive quem não vai rodar a aplicação.
- **Embutir o modelo na imagem Docker.** Boa para produção e já contemplada (basta montar o
  diretório de cache como camada/volume), mas não ajuda quem roda `spring-boot:run` na máquina,
  que é o fluxo da PoC.
- **Só modelo local, sem gateway** (a decisão anterior). Roda offline e sem chave, mas paga a
  quantização em recall, 112 MB de RAM por instância e a distribuição do arquivo para toda
  máquina que sobe o projeto. Mantido como fallback justamente porque o offline importa em
  alguns contextos — mas não como padrão.
