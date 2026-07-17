# ADR-0006 — Seleção de modelos Anthropic (catálogo versionado + descoberta ao vivo)

- **Status:** Aceito
- **Data:** 2026-07-17
- **Camada M03:** 5 (Governança) · 2 (Observabilidade técnica)

## Contexto

O `/advise` usava o modelo resolvido pelo **roteamento por risco** (ADR-0005). Faltava permitir
que o cliente **escolha explicitamente** outro modelo Anthropic por chamada — para comparar
qualidade/custo entre modelos, testar um modelo novo, ou forçar o forte numa pergunta específica.

Restrições do domínio (já registradas no projeto): o id do modelo precisa ser **exato para a
conta** (`GET /v1/models`), e "modelo" faz parte da tríade versionada `qualidade = f(prompt,
golden, modelo)` (A05). Aceitar qualquer string do cliente seria um risco de governança e de
erro 400 do provedor.

## Decisão

Introduzir uma **seleção de modelo com allow-list versionada + descoberta ao vivo**:

- **Catálogo** (`app.models.catalog`, `ModelsProperties`): allow-list curada e **versionada em
  config** — a fonte de governança do que o app aceita. Binding por construtor (record).
- **Descoberta ao vivo** (`AnthropicModelsClient` → `GET /v1/models`): lista os modelos **reais
  da conta** (ids exatos), com cache/TTL, opcional (`live-sync`) e **não-fatal** (sem chave/rede,
  cai no catálogo). Exposta em `GET /api/v1/models`.
- **Seleção por chamada:** campo opcional `model` no `POST /advise`. Se informado, é **validado**
  (`ModelCatalogService.resolve`) e tem **precedência** sobre o roteamento; se ausente, o
  `RouteClassifier` decide (comportamento anterior inalterado). Id inválido → **HTTP 400**
  (`UnknownModelException`, RFC 7807) — distinto do 404 de `IllegalArgumentException`.
- **Válvula de escape:** `app.models.allow-any=true` desliga a validação para contas com ids
  fora do catálogo.
- **Observabilidade:** o modelo efetivo vira dimensão de métrica/trace, com a tag
  `rag.model_source = requested | route` para distinguir seleção manual de roteamento.

## Consequências

- ✅ Escolha de modelo por chamada, mantendo o roteamento por risco como default.
- ✅ Governança preservada: só ids aceitos passam; a lista aceita fica versionada e auditável.
- ✅ Descoberta dos ids exatos da conta sem sair do app (o material pede "id EXATO da conta").
- ✅ Custo/qualidade por modelo comparáveis nas métricas (`rag.llm.tokens/cost{model}`).
- ⚠️ O catálogo default precisa refletir os ids reais da conta (ou ligar `live-sync`/`allow-any`).

## Alternativas consideradas

- **Aceitar qualquer string de modelo:** simples, mas sem governança e sujeito a 400 do provedor.
- **Só descoberta ao vivo (sem catálogo):** dependeria de rede/chave para validar toda chamada e
  perderia o versionamento do "modelo" da tríade de qualidade.
- **Trocar só via config global:** não permite escolha por chamada nem A/B entre modelos.
