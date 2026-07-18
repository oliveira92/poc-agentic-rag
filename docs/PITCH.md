# Pitch de 5 minutos — Component Advisor (roteiro da demo, dirigido pela UI)

> Segue a estrutura do professor ("Roteiro que se conta sozinho"): **Contexto → Pergunta fácil
> → Pergunta de risco → Armadilha → Langfuse**. A frase-tese fecha tudo:
> **"Não vendo a IA — vendo o controle sobre ela."**
>
> A demo roda na **interface gráfica** (`frontend/`, localhost:5173) — sem curl na frente da
> banca. Adaptação do caso: onde o roteiro original diz "sinistro/jurídico" (Porto), aqui a
> rota de risco é **pagamento/estorno/idempotência** — mesmo conceito, outro domínio.

---

## Antes do pitch (30 min antes — NÃO pule)

```bash
# 1) Infra
docker compose up -d postgres
docker compose --profile observability up -d          # Langfuse (web/worker/clickhouse/redis/minio)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3000/api/public/health   # tem que dar 200

# 2) Backend (⚠ se mexeu em application.yml, rode `mvn package` antes — o jar embute a config)
java -jar target/agentic-rag-0.1.0-SNAPSHOT.jar --spring.profiles.active=mock
curl -s localhost:8080/actuator/health                 # {"status":"UP"}

# 3) UI
cd frontend && npm run dev                             # http://localhost:5173
```

**Na UI (nesta ordem):**

1. Aba **Ingestão** → botão *"Ingerir payments-sdk"* (idempotente — pode repetir).
2. Aba **Quality Gate** → *"Rodar o Portão"* → deixe o banner verde **GATE PASSOU** pronto
   (vai ser o pano de fundo do fecho).
3. Aba **Advisor** → **pré-execute as duas perguntas lentas** (20–30s cada — não gaste isso
   dentro dos 5 min):
   - *"Como faço para estornar parcialmente uma cobrança e garantir idempotência no reenvio?"*
     → confira os badges `risco` + `claude-sonnet-5`.
   - *"O payments-sdk envia notificações por WhatsApp ao cliente?"* (a armadilha)
     → na resposta, clique **👎** (a UI grava o CSAT já amarrado ao trace).
4. Em cada uma das duas respostas, clique **⧉ trace** para copiar o traceId e **anote os dois
   num papel** (para achar rápido no Langfuse).
5. **⚠ NÃO recarregue a página da UI depois disso** — o histórico do chat vive no navegador.
6. Abas do navegador prontas: **[A]** UI no Advisor · **[B]** Langfuse (localhost:3000,
   projeto aberto, lista de traces filtrada pela última hora).

---

## O pitch (5:00)

### Cena 1 · Contexto — 0:00–0:30

*Na tela: a UI aberta no Advisor (com o histórico pré-executado mais acima, fora de vista).*

**Fala (decore, é 1 frase + a tese):**

> "Este é o **Component Advisor**: um agente que ensina desenvolvedores a consumir os
> componentes internos da empresa — ele lê a documentação oficial, responde 'como implementar'
> **com as fontes citadas**, e um humano aprova o que entra na base.
> O que eu vou mostrar em 5 minutos **não é a IA — é o controle sobre ela**: cada resposta é
> medida, roteada por risco e auditável."

---

### Cena 2 · Pergunta fácil → responde ancorado, rota barata — 0:30–1:30

**Ação (AO VIVO — essa é rápida, ~5s):** digite no chat e envie:

> **O que é o payments-sdk?**

**O que apontar na tela (3 coisas, nesta ordem):**
1. Os badges no topo da resposta: **`faq`** e **`claude-haiku…`** → *"pergunta simples caiu
   na rota barata: um modelo rápido, que custa uma fração do forte. A decisão foi do sistema,
   não minha."*
2. O badge **`ancorada`** + os marcadores `[1] [2]` no texto → *"cada afirmação aponta a fonte
   de onde saiu — isso é groundedness: resposta apoiada em documento, não em achismo."*
3. Clique em **"6 fontes citadas"** → as citações abrem com origem e score → *"e as fontes são
   auditáveis, com o quão relevante cada uma foi."*

**Fala de fecho da cena:**
> "Essa chamada custou **décimos de centavo** e voltou em 5 segundos — porque o sistema sabe
> que aqui o risco é baixo."

---

### Cena 3 · Pergunta de risco → roteia pro forte — 1:30–2:30

**Ação:** role o chat para cima até a pergunta de estorno **pré-executada**.

**O que apontar:**
1. Badges **`risco`** + **`claude-sonnet-5`** → *"estorno é dinheiro. Dinheiro é rota de
   risco: o sistema trocou sozinho para o modelo forte — e essa rota exige fundamentação
   mínima de 90%."*
2. A resposta: passo a passo com `Idempotency-Key`, tudo citando `[n]`.
3. A ausência de alerta amarelo → *"e um verificador automático conferiu: todo endpoint citado
   existe de verdade nas fontes — é o guardrail anti-alucinação."*

**Fala de fecho da cena (o número que convence):**
> "Medi as duas rotas: a fácil custou **15× menos** e voltou **7× mais rápido**. O forte fica
> **só onde errar custa caro** — é assim que o custo cai sem perder qualidade no que importa."

*(Se sobrar fôlego: 5s na aba **Métricas** — a tabela "Por modelo" mostra exatamente essa
diferença de custo/latência, medida, não estimada.)*

---

### Cena 4 · Armadilha → o sistema não inventa, e o 👎 fica registrado — 2:30–3:30

**Ação:** role até a pergunta do WhatsApp **pré-executada**.

**O que apontar:**
1. A resposta **diz que a documentação não cobre isso** → *"pergunta-armadilha: WhatsApp não
   existe nesse componente. A IA mais perigosa é a que inventa com confiança — a nossa
   responde 'não está nas fontes'."*
2. O **👎 já marcado** na resposta → *"e quando mesmo assim a resposta desagrada, o usuário
   clica no polegar — esse 👎 não se perde: ele fica amarrado à chamada exata, pelo traceId.
   Vocês vão ver isso agora."*

*(Gancho perfeito para a cena 5.)*

---

### Cena 5 · Langfuse → abre o trace: as 5 camadas ali — 3:30–4:40

**Ação:** alt-tab para o Langfuse (localhost:3000) → **Traces** → localize o trace da pergunta
de risco (pelo horário ou colando o `traceId` anotado) → **abra**.

**Roteiro dentro do Langfuse (aponte na ordem, ~15s cada):**

1. **A lista de traces** → *"cada pergunta ao agente vira um rastro completo aqui — sem isso,
   debugar IA é chute".*
2. **O waterfall do trace** (spans aninhados: requisição → busca no banco vetorial → chamada do
   modelo) → *"dá para ver onde foi o tempo: a busca levou milissegundos; o tempo está no
   modelo. Isso é a camada de **observabilidade**".*
3. **No span do modelo: tokens de entrada/saída e o modelo usado**
   (`gen_ai.request.model`, `gen_ai.usage.*`) → *"tokens são o custo. Latência + tokens são a
   camada **técnica** — é daqui que saíram os números de 15× e 7×".*
4. **No span raiz: as tags `rag.route=risco`, `rag.risk=HIGH`** → *"a decisão de roteamento
   ficou gravada — camada de **governança**: dá para auditar por que o modelo forte foi usado".*
5. **Eventos no trace: `rag.low_confidence` (quando o guardrail dispara) e
   `user_feedback.negative` no trace da armadilha** → *"a camada de **qualidade** e a de
   **negócio**: o guardrail e o 👎 do usuário grudados na chamada exata que os causou. Quando o
   CSAT cair, eu não tenho uma média — eu tenho **as conversas** que o derrubaram".*

**Fala de fecho da cena:**
> "As **cinco camadas** do módulo estão neste único trace: observar, medir, qualidade, negócio
> e governança. Uma tela, história completa."

---

### Fecho — 4:40–5:00

**Ação:** alt-tab de volta para a UI, aba **Quality Gate** (o banner verde já está lá).

**Fala final (decore):**

> "E antes de qualquer mudança chegar em produção, este **portão roda no CI**: um gabarito de
> 13 perguntas — fáceis, médias e armadilhas — que **bloqueia o merge** se a qualidade
> regredir. Prompt, gabarito e modelo são versionados juntos.
> Ou seja: **não estou vendendo a IA. Estou vendendo o controle sobre ela.** Obrigado."

---

## Plano B (se algo falhar na hora)

| Problema | Saída |
|---|---|
| UI fora do ar | Volte ao terminal: os mesmos fluxos via `curl` (comandos no [README](../README.md#uso--passo-a-passo-exemplos-reais)) |
| Langfuse fora do ar | Cena 5 vira a aba **Métricas** da UI — custo/latência/CSAT por modelo e rota ao vivo; diga que o trace detalhado vive no Langfuse |
| `/advise` lento/instável ao vivo | Pule a cena 2 ao vivo: use uma 3ª pergunta fácil **pré-executada** e narre por cima |
| Sem chave Anthropic | Aba **Quality Gate** (não usa LLM) + `search` via curl — recuperação com scores |

## Cola de bolso (leve impressa)

1. Component Advisor: ensina a consumir componentes internos, **com fontes citadas**.
2. Fácil → rota `faq` → **Haiku** (barato). Risco → rota `risco` → **Sonnet** (forte).
3. Números: **15× mais barato, 7× mais rápido** na rota fácil.
4. Armadilha: **não inventa** — diz que não está nas fontes. 👎 vira evento no trace.
5. Langfuse: **um trace = as 5 camadas** (observar · técnica · qualidade · negócio · governança).
6. Fecho na aba Quality Gate: 13 perguntas-gabarito **bloqueiam merge** se a qualidade cair.
7. Tese: **"Não vendo a IA — vendo o controle sobre ela."**
8. ⚠ Não recarregar a página da UI (perde o histórico) · traceIds anotados no papel.
