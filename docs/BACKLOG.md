# Product Backlog — Agentic RAG (Component Advisor)

> Backlog refinado a partir da **Fase 1 entregue** e do roadmap definido.
> Estimativas (Story Points, Fibonacci) e prioridades (MoSCoW) são **propostas para
> refinamento com o time** — o PO facilita, o time estima. Pronto para colar em
> Jira/GitHub Issues (cada `HU-xx` = 1 issue; sub-tarefas = checklist/sub-issues).

## Visão de produto

Reduzir o tempo e o erro de **integrar componentes internos**: um agente que, a partir
das fontes oficiais (API do portal + README), explica **como consumir** um componente,
com respostas **fundamentadas e citadas**, e que aprende com o conhecimento **curado por
humanos**.

**Métrica-norte:** _time-to-first-successful-call_ (tempo do dev até a 1ª chamada bem
sucedida ao componente). Métricas de apoio: % de respostas com citação válida, % de
respostas aprovadas na curadoria, nº de componentes ingeridos.

## Personas

| Persona | Descrição | Dor principal |
|---|---|---|
| **Dev Consumidor** | Desenvolvedor que precisa integrar um componente | Documentação dispersa/desatualizada; tentativa e erro |
| **Owner/Revisor** | Dono ou tech lead do componente que cura o conhecimento | Perguntas repetidas; falta de canal para validar respostas |
| **Platform Engineer** | Opera a plataforma (ingestão, portal, deploy, observabilidade) | Manter a base atualizada e confiável em produção |

---

## Épicos (mapa para o roadmap)

| Épico | Objetivo | Fase | Status |
|---|---|---|---|
| **E0 — Fundação (Fase 1)** | Ingestão + pgvector + memórias + HITL + testes/CI | #1 | ✅ Entregue |
| **E1 — Aconselhamento confiável** | Tornar o `/advise` produtivo e digno de confiança (grounding, citações, guardrails) | #1→#4 | 🎯 Próximo |
| **E2 — Ingestão do portal real** | Sair do mock: dados reais via OpenAPI do portal | #1 | Backlog |
| **E3 — Qualidade de recuperação** | HyDE + busca híbrida + rerank (melhor recall/precisão, PT-BR) | #2 | Backlog |
| **E4 — Governança do conhecimento** | Curadoria HITL com UI, papéis e auditoria | #4 | Backlog |
| **E5 — Operação & Segurança** | AuthN/Z da API, observabilidade Langfuse, custo/latência | transversal | Backlog |

---

## Definição de Pronto (DoR) — para entrar no sprint
- [ ] História no formato **Como/Quero/Para** com valor claro.
- [ ] **Critérios de aceite** testáveis (Gherkin).
- [ ] Dependências e premissas mapeadas; sem bloqueio externo aberto.
- [ ] Fatia **vertical** (entrega valor observável ponta a ponta) e cabe num sprint.
- [ ] Decisão de UX/contrato de API definida quando aplicável.
- [ ] **Estimada** pelo time.

## Definição de Feito (DoD) — ancorada neste projeto
- [ ] Todos os critérios de aceite cobertos por **teste automatizado** (unit e/ou IT Testcontainers).
- [ ] PR revisado e **CI verde** (`mvn verify`).
- [ ] Observabilidade: spans/logs relevantes emitidos (Micrometer → OTLP/Langfuse).
- [ ] **README/docs atualizados**; config e feature flags documentadas.
- [ ] Sem regressão nos fluxos existentes (ingestão, `search`, aprovação).
- [ ] Incremento **demonstrável** (roteiro de demo).

---

## Backlog priorizado (resumo)

| ID | História | Épico | MoSCoW | SP |
|---|---|---|---|---|
| HU-01 | Resposta fundamentada de "como consumir" (`/advise` com LLM real) | E1 | Must | 5 |
| HU-02 | Guardrails de fidelidade (anti-alucinação) | E1 | Must | 5 |
| HU-04 | Ingestão do portal real (OpenAPI) | E2 | Must | 8 |
| HU-03 | Tela de curadoria (aprovação sem `curl`) | E4 | Should | 8 |
| HU-07 | Busca híbrida (denso + full-text PT-BR) | E3 | Should | 8 |
| HU-06 | HyDE (documento hipotético) | E3 | Could | 5 |
| HU-09 | Segurança da API (AuthN/Z) | E5 | Must (pré-prod) | 5 |
| HU-05 | Reingestão incremental/agendada | E2 | Could | 5 |
| HU-10 | Observabilidade Langfuse validada (custo/latência) | E5 | Should | 3 |
| HU-08 | Rerank por fonte / cross-encoder | E3 | Could | 5 |

---

# E1 — Aconselhamento confiável (histórias detalhadas)

### HU-01 — Resposta fundamentada de "como consumir"
**Como** Dev Consumidor, **quero** perguntar em linguagem natural como consumir um
componente e receber um passo a passo fundamentado **com citações às fontes**, **para**
implementar a integração sem vasculhar toda a documentação.

**Valor:** é o núcleo do produto — sem isto, a Fase 1 não gera valor ao usuário final.
**Prioridade:** Must · **SP:** 5 · **Dependências:** E0 (pronto). Requer `ANTHROPIC_API_KEY`.

**Critérios de aceite (Gherkin)**
```gherkin
Cenário: resposta fundamentada com citações
  Dado um componente já ingerido e uma ANTHROPIC_API_KEY válida
  Quando envio POST /advise com uma pergunta pertinente
  Então recebo 200 com "grounded": true, "answer" não-vazio
  E ao menos uma citação, cujos marcadores [n] aparecem no texto da resposta

Cenário: sem base recuperada não inventa
  Dado um componente NÃO ingerido
  Quando pergunto como consumi-lo
  Então "grounded" é false
  E a resposta declara explicitamente que a base não contém o componente
  E não afirma a existência de endpoints/headers específicos

Cenário: continuidade da conversa (memória curta)
  Dado que enviei uma 1ª pergunta com conversationId "c1"
  Quando envio "e para estornar?" com o mesmo conversationId "c1"
  Então a resposta considera o contexto da pergunta anterior
```
**Requisito não-funcional:** p95 de latência do `/advise` ≤ 6 s; custo/tokens por chamada registrado.

**Sub-tarefas**
- [ ] Provisionar `ANTHROPIC_API_KEY` por ambiente via cofre de segredos (não no `.env` versionado).
- [ ] Teste de integração com **ChatModel stubado** validando o envelope (`AdviceResult`), o `conversationId` e a montagem do prompt/contexto.
- [ ] Tratar falhas da LLM (timeout, 429, 5xx) com ret/backoff e mensagem de erro clara (RFC 7807).
- [ ] Garantir que os índices `[n]` do texto correspondem às `citations` (pós-processamento).
- [ ] Roteiro de teste manual E2E com chave real (mock `payments-sdk`).
- [ ] Emitir spans + custo/tokens no Langfuse; atualizar README com exemplo real de `/advise`.

---

### HU-02 — Guardrails de fidelidade (anti-alucinação)
**Como** Dev Consumidor, **quero** que o agente **nunca invente** endpoints, campos ou
headers que não estejam nas fontes, **para** poder confiar na resposta e usar o código com segurança.

**Valor:** confiança é pré-requisito de adoção; uma alucinação queima a credibilidade do produto.
**Prioridade:** Must · **SP:** 5 · **Dependências:** HU-01.

**Critérios de aceite (Gherkin)**
```gherkin
Cenário: pergunta sobre endpoint inexistente
  Dado um contexto que não contém o endpoint X
  Quando pergunto especificamente sobre X
  Então a resposta não afirma que X existe
  E sugere ingerir/validar a informação

Cenário: verificação de groundedness das citações
  Dada uma resposta que referencia "POST /v2/..."
  Quando executo a verificação automática
  Então todo endpoint citado no texto consta das citações recuperadas
  Senão a resposta é marcada como baixa confiança
```
**Métrica:** taxa de respostas com citação inválida = 0 no conjunto de avaliação (_golden set_).

**Sub-tarefas**
- [ ] Endurecer o system prompt de `ComponentAdvisorService` + **testes adversariais**.
- [ ] Pós-verificação: extrair endpoints citados no `answer` e checar contra `citations`; sinalizar `lowConfidence` no `AdviceResult`.
- [ ] Montar **golden set** (perguntas boas + adversariais) e script de eval reproduzível.
- [ ] (Semente da Fase 3) registrar métrica de "groundedness" por resposta.

---

### HU-03 — Tela de curadoria (aprovação HITL sem `curl`)
**Como** Owner/Revisor, **quero** revisar, aprovar ou reprovar respostas pendentes por uma
interface simples que mostre as **fontes usadas**, **para** curar a base sem usar a API crua.

**Valor:** habilita a curadoria por não-devs e destrava o loop de conhecimento em escala.
**Prioridade:** Should · **SP:** 8 · **Dependências:** HU-01. **Épico:** E4.

**Critérios de aceite (Gherkin)**
```gherkin
Cenário: revisar e aprovar
  Dado rascunhos com status PENDING
  Quando abro a tela de curadoria
  Então vejo pergunta, resposta e as citações que a fundamentaram
  E posso Aprovar ou Reprovar informando meu usuário e uma nota

Cenário: efeito da aprovação
  Quando aprovo um rascunho
  Então ele sai da fila
  E passa a ser recuperável com source=LLM_APPROVED
  E ficam registrados revisor e data/hora (auditoria)
```
**Sub-tarefas**
- [ ] **Persistir as citações no rascunho** (hoje `savePending` grava só pergunta+resposta) para o revisor ver as fontes.
- [ ] UI mínima (página estática consumindo `/approvals/*`) ou integração com ferramenta existente.
- [ ] Autenticar o revisor (papel `reviewer`) — depende de **HU-09**.
- [ ] **Bug conhecido:** corrigir inconsistência de fuso entre `created_at` (SQL `now()`) e `reviewed_at` (Java `Instant`) — padronizar em UTC (`TIMESTAMPTZ` ou gravar `Instant` nos dois).
- [ ] Paginação/filtro por componente na fila.

---

# E2 · E3 · E5 — Histórias para refinamento (nível mais leve)

> Estas ficam no topo do backlog seguinte; detalhamento completo (todos os cenários) no
> próximo refinamento, quando entrarem no radar da sprint.

### HU-04 — Ingestão do portal real (OpenAPI) · E2 · Must · SP 8
**Como** Platform Engineer, **quero** ingerir componentes reais a partir do portal, **para**
a base refletir o catálogo real em vez do mock.
**AC (resumo):** com credenciais válidas e um id real, o `RestClientComponentPortalAdapter`
mapeia endpoints/README corretamente; 401/403/404 tratados; roda em perfil `!mock`.
**Sub-tarefas:** obter OpenAPI/contrato do portal · implementar o mapeamento (hoje esqueleto)
· autenticação (token/OAuth client-credentials) · testes com **WireMock** · paginação e rate-limit
· documentar variáveis `COMPONENT_PORTAL_*`.

### HU-05 — Reingestão incremental/agendada · E2 · Could · SP 5
**Como** Platform Engineer, **quero** reingerir componentes de forma agendada/incremental,
**para** manter a base atualizada sem intervenção manual.
**Sub-tarefas:** job agendado · detecção de mudança por versão/hash (já há hash de conteúdo)
· webhook do portal (se existir) · métricas de _freshness_.

### HU-06 — HyDE (documento hipotético) · E3 · Could · SP 5
**Como** Dev Consumidor, **quero** melhores respostas para perguntas vagas, **para** não
precisar formular a pergunta "do jeito da doc".
**AC (resumo):** o sistema gera uma resposta hipotética, embeda-a e recupera por ela;
comparação A/B mostra ganho de recall no golden set.

### HU-07 — Busca híbrida (denso + full-text PT-BR) · E3 · Should · SP 8
**Como** Dev Consumidor, **quero** que termos exatos (nomes de endpoint, headers) sejam
encontrados mesmo quando o embedding falha, **para** ter recuperação robusta.
**Sub-tarefas:** coluna/índice `tsvector` (portuguese) no Postgres · fusão de scores (RRF)
denso+lexical · flag de configuração · avaliação A/B vs. só-denso.

### HU-08 — Rerank por fonte / cross-encoder · E3 · Could · SP 5
Reordenar o top-K priorizando fontes primárias e relevância fina (cross-encoder ou regra
de precedência `PORTAL_API`/`README` > `LLM_APPROVED`).

### HU-09 — Segurança da API (AuthN/Z) · E5 · Must (pré-prod) · SP 5
**Como** Platform Engineer, **quero** proteger os endpoints com autenticação e papéis,
**para** só devs autenticados consultarem e só revisores aprovarem.
**Sub-tarefas:** Spring Security (OAuth2 Resource Server/JWT) · papéis `consumer`/`reviewer`
· proteger `/approvals/*` para `reviewer` · testes de autorização.

### HU-10 — Observabilidade Langfuse validada · E5 · Should · SP 3
**Como** Platform Engineer, **quero** ver no Langfuse cada recuperação e chamada de LLM com
custo/latência, **para** operar e otimizar.
**Sub-tarefas:** validar export OTLP ponta a ponta · dashboards de custo/latência · alertas.

---

## Proposta de Sprint 1

**Objetivo (Sprint Goal):** _"Um desenvolvedor consegue, com dados de pelo menos um
componente real, perguntar como consumi-lo e receber uma resposta confiável, fundamentada e
sem alucinação."_

**Itens candidatos:** HU-01 (5) + HU-02 (5) + HU-04 (8) = **18 SP**
_(ajustar à capacidade do time; se necessário, adiar HU-04 e validar HU-01/HU-02 com o mock)._

**Fora do objetivo (explicitamente):** UI de curadoria (HU-03), Fase 2 (HU-06/07/08).

**Riscos & mitigação**
- _Contrato do portal desconhecido_ → começar HU-04 por um **spike** de 1 dia sobre o OpenAPI.
- _Custo/latência da LLM_ → medir desde HU-01 (Langfuse) e definir orçamento por chamada.
- _Qualidade em PT-BR_ → golden set desde HU-02 para evitar regressão ao mexer em prompts/retrieval.

## Observações do PO (dívidas/gaps já identificados na Fase 1)
1. **Citações não são persistidas no rascunho** de aprovação — endereçado em HU-03.
2. **Inconsistência de fuso** `created_at` × `reviewed_at` — endereçado em HU-03.
3. **Sem autenticação** nos endpoints — endereçado em HU-09 (bloqueante para produção).
4. **Provider Azure OpenAI** (se exigido pela empresa) é troca de _starter_+config — decisão
   de arquitetura a confirmar; não bloqueia HU-01 (o domínio é indiferente ao provider).
