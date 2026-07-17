# ADR-0004 — Quality gate de recuperação no CI (golden set versionado)

- **Status:** Aceito
- **Data:** 2026-07-17
- **Camada M03:** 5 (Governança) · 3 (Qualidade)

## Contexto

*"No notebook funciona; em produção, quem segura?"* (A05). Uma mudança bem-intencionada no
prompt/recuperação pode derrubar a fundamentação e só descobrimos pelo cliente reclamando.
Falta o "teste de regressão" da IA: avaliar **antes** de subir ("O Portão").

## Decisão

Versionar a tríade **`qualidade = f(prompt, golden, modelo)`** junto e rodar um **quality gate**
no CI ([`quality-gate.yml`](../../.github/workflows/quality-gate.yml)) nos PRs que tocam
prompt/golden/modelo/recuperação. O gate roda o **golden set**
([`golden/payments-sdk.csv`](../../src/main/resources/golden/payments-sdk.csv), casos
fácil/médio/armadilha) pela **recuperação** e decide com
[`QualityGate`](../../src/main/java/com/example/agenticrag/quality/QualityGate.java):
**toda rota de risco alto precisa passar** + taxa geral ≥ limite. Falha → `/quality/{id}/gate`
responde **HTTP 422** → o job do CI reprova e **bloqueia o merge**.

Foco em **recuperação** (não geração): é a causa raiz da maioria das alucinações
("groundedness baixo? olhe o retrieval antes de culpar o modelo") e roda **sem chave de LLM** e
sem efeito colateral — barato o suficiente para todo PR.

## Consequências

- ✅ Regressão de recuperação é barrada antes do cliente; a decisão do gate é testada por unidade.
- ✅ Armadilhas (over-retrieval fora de escopo) são toleradas por `min-pass-rate`, mantendo o
  gate verde num sistema saudável, mas visíveis no relatório.
- ⚠️ O gate não avalia *geração*/groundedness por LLM (isso fica para amostragem em produção, A03).

## Alternativas consideradas

- **Gate gerando resposta + juiz-LLM no CI:** exige chave, custo e persistiria rascunhos
  (efeito colateral). Adiado para amostragem em produção.
- **Sem gate (só review humano):** não escala e deixa passar regressão silenciosa.
