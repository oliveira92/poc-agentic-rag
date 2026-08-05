# ADR-0008 — Guardrails fora do system prompt (entrada · saída · ingestão)

- **Status:** Aceito
- **Data:** 2026-08-04
- **Entregável:** M04 — ver [SEGURANCA-M04.md](../SEGURANCA-M04.md)

## Contexto

O system prompt do `/advise` já dizia "responda apenas com base no contexto" e "não invente
endpoints". Isso é uma instrução, não um controle: quem a interpreta é o mesmo modelo que o
atacante está tentando convencer, e a capacidade que faz o modelo seguir o desenvolvedor é a
mesma que o faz seguir quem escreveu "ignore as instruções anteriores".

Três riscos do threat model exigiam controle de verdade:

- **R1 — conteúdo fora de escopo entrando na base.** O RAG aceitava qualquer README. Uma receita
  de bolo indexada volta como "fonte" com citação e disputa espaço no top-K com a fonte primária.
- **R2 — perguntas fora do conteúdo ingerido.** Sem noção de escopo, o agente responde sobre
  qualquer coisa — e o que ele responde fora do domínio não tem base para estar certo.
- **R3 — dado sensível na ingestão.** Segredo ou PII que vira embedding é permanente: o RAG passa
  a distribuí-lo sob demanda, com aparência de fonte confiável.

## Decisão

Uma camada de controles **em Java**, fora do prompt, em três estágios, com a política em config.

- **Detectores determinísticos** (`security/detector/`): PII com dígito verificador, segredos por
  formato conhecido + atribuição genérica, injeção de prompt, URL, binário por magic bytes,
  dado de terceiro. Cada um só **encontra**; não decide.
- **Quem decide é a política** (`app.security.actions.<estágio>.<categoria>`), resolvida pelo
  `GuardEngine`. O mesmo detector de PII mascara na pergunta e bloqueia na ingestão sem duplicar
  código, e a decisão fica auditável em YAML.
- **Três ações**, e a do meio é o ponto: `ALLOW`, **`MASK`**, `BLOCK`. Bloquear toda pergunta com
  dado pessoal derrubaria o caso de uso mais comum — o dev colando o payload real, com PII de
  cliente dentro, para perguntar por que deu 400 (L-05). Mascarar atende o requisito "não enviar
  dado sensível ao modelo" **e** responde a pessoa.
- **Juiz de escopo** atrás de interface: `LexicalScopeJudge` (padrão, determinístico, roda no CI
  sem chave) e `LlmScopeJudge` (generaliza para formulações não previstas). O vocabulário do
  escopo vem do **registro de ingestão**, não de uma lista fixa — o escopo acompanha a base.
- **Um controle que não é filtro de texto:** `ResourceOwnershipDetector` (SEC-10). "Qual o status
  da cobrança chg_9f2a1c?" é uma frase impecável e é abuso se a cobrança não for de quem
  perguntou. Recebe `SecuritySubject` em vez de só texto, e **se declara inerte** quando não há
  identidade propagada — a lacuna fica registrada, não escondida.
- **Fail-safe:** `app.security.fail-mode=closed` — detector que estoura e juiz indisponível viram
  bloqueio, não liberação.
- **Erro genérico e igual para todos os controles** (HTTP 422). Dizer qual padrão casou entrega
  ao atacante um oráculo para iterar até passar.

## Consequências

- ✅ Controle testável: **31 casos em dois domínios** rodam no CI contra a política **de
  produção** (o teste lê `application.yml`, não uma cópia). Resultado atual: `componentes` 10/10
  ataques barrados e 0/7 falsos positivos; `a05-seguros` 8/8 e 0/6. Passar nos dois com o mesmo
  código é o que separa "a política foi ajustada até os casos passarem" de "os controles não
  dependem do domínio".
- ✅ Endurecer a política (MASK → BLOCK) não exige recompilar nem tocar no prompt.
- ✅ O inventário de `GET /api/v1/security/controls` é montado dos beans reais — não descola do código.
- ⚠️ **Listas de padrão têm teto.** Injeção e "fora de escopo" reformulados de forma inédita
  passam. Mitigação prevista: o juiz LLM em série na rota de risco.
- ⚠️ **Controle calibrado num domínio erra em outro.** Levar o dataset para o domínio real da PoC
  expôs dois controles que barravam trabalho legítimo — o SEC-06 recusava perguntas sobre
  **schema** ("o payload inclui o e-mail do cliente?") e o SEC-05 recusava **todo** link, num
  agente cujos usuários colam link o tempo todo. Ambos ganharam precisão (verbo de obtenção;
  allow-list de domínios internos) sem perder recall no outro cenário — mas a lição fica: sem
  dataset do domínio real, o número mede pouco.
- ⚠️ **Falso positivo é custo real.** Um README legítimo com um trecho parecido com credencial é
  recusado inteiro. Preferimos recusar: reingerir é barato, despoluir índice não.
- ⚠️ **Latência e superfície** adicionais em todo `/advise` (medidas em `rag.guard.latency`).

## Alternativas consideradas

- **Só instrução no system prompt.** Custo zero e é exatamente o que o M04 pede para não fazer:
  não é testável, não é auditável e cede à primeira formulação convincente.
- **Um serviço de guardrail de terceiro** (Llama Guard, Presidio, moderação do vendor). Melhor
  cobertura semântica pronta, mas manda o texto — inclusive o dado sensível — para fora, que é
  justamente o que R3 quer evitar, e some com a explicabilidade que a auditoria pede.
- **Controle só na saída.** Mais barato (um ponto único), mas gasta token com a chamada que não
  deveria existir e deixa a pergunta hostil aparecer nos logs de prompt.
