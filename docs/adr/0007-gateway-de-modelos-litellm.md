# ADR-0007 — Gateway de modelos LiteLLM (multi-provedor)

- **Status:** Aceito
- **Data:** 2026-08-04
- **Substitui parcialmente:** [ADR-0006](0006-selecao-de-modelos-anthropic.md) (o catálogo e a
  allow-list continuam valendo; muda **de quem** o app descobre e para onde ele chama)

## Contexto

A PoC falava direto com a Anthropic (`spring-ai-starter-model-anthropic`). Três consequências
disso apareceram na prática:

1. **Mono-provedor.** Toda a governança de modelo (ADR-0006) e o roteamento por risco
   (ADR-0005) só sabiam escolher entre Claudes. Não dava para colocar a rota de FAQ num modelo
   barato de outro vendor, nem comparar a resposta de dois fornecedores no mesmo golden set.
2. **Acoplamento de client.** Cada vendor traz seu SDK, sua hierarquia de exceções e seu
   formato de erro — o `ApiExceptionHandler` já carregava `com.anthropic.errors.*`. Somar um
   segundo vendor somaria um segundo bloco de acoplamento.
3. **Chave de vendor dentro da aplicação.** `ANTHROPIC_API_KEY` vivia no processo do app.
   Comprometer a aplicação entregava a credencial do provedor.

## Decisão

Colocar um **proxy LiteLLM** na frente dos provedores e apontar a aplicação para ele.

- **Client único, API OpenAI-compatível.** Passamos a usar `spring-ai-starter-model-openai`
  com `spring.ai.openai.base-url` apontando para o proxy. É a interface que o LiteLLM expõe —
  não estamos "usando OpenAI", estamos usando o protocolo que o gateway fala.
- **Qual auto-config vale** é decidido por `spring.ai.model.chat` (`openai` | `anthropic`), com
  `spring.ai.model.embedding=transformers` fixado — as duas auto-configs de embedding têm
  `matchIfMissing=true` e brigariam pelo bean com o starter novo no classpath.
- **Aliases, não ids de vendor.** `litellm/config.yaml` publica `model_name` (`claude-sonnet-5`,
  `gpt-4o`, `gemini-2.0-flash`, `llama-3.3-70b`); é isso que o catálogo versiona e o roteamento
  referencia. Trocar o modelo real por trás de um alias vira mudança de config no proxy.
- **Descoberta atrás de uma interface.** `ModelDiscoveryClient` com duas implementações:
  `LiteLlmModelsClient` (padrão — `/model/info`, com fallback `/v1/models`) e
  `AnthropicModelsClient` (`app.models.provider=anthropic`, caminho sem gateway).
- **Credenciais só no proxy.** A app conhece apenas a `LITELLM_MASTER_KEY`. As chaves dos
  vendors são injetadas no container do LiteLLM.
- **Custo por família multi-vendor** (`app.cost.per-family`), com casamento pela chave **mais
  longa** — sem isso, `gpt-4o-mini` seria cobrado ao preço de `gpt-4o` dependendo da ordem do mapa.

## Consequências

- ✅ Roteamento por risco e seleção manual passam a atravessar vendors: a rota barata pode ir
  para um modelo aberto e a rota de risco ficar no Claude, mudando só config.
- ✅ Raio de vazamento menor: a aplicação não guarda credencial de provedor.
- ✅ Um ponto central para rate limit, fallback entre modelos e contabilidade de custo.
- ✅ O caminho antigo continua disponível (`APP_LLM_PROVIDER=anthropic`) para rodar sem infra extra.
- ⚠️ **Novo ponto de falha e novo salto de rede.** O proxy fora do ar derruba o `/advise`.
  Mitigação parcial: `num_retries` no proxy; fallback entre modelos ainda não configurado.
- ⚠️ **Erros chegam traduzidos.** A falha específica do vendor vira erro OpenAI-compatível; o
  `ApiExceptionHandler` trata as duas hierarquias (`com.anthropic.errors.*` e
  `com.openai.errors.*`), mas parte do detalhe original se perde no caminho.
- ⚠️ **Mais uma peça para operar** (imagem, config, healthcheck) — coberta no compose sob o
  perfil `llm`.

## Alternativas consideradas

- **Um starter Spring AI por vendor.** Sem processo extra, mas multiplica SDKs, exceções e
  configuração dentro da app, e deixa a chave de cada provedor no processo do agente.
- **Escrever a abstração à mão** sobre `ChatModel`. Todo o trabalho do gateway (fallback,
  contabilidade, tradução de erro) viraria código nosso para manter.
- **OpenRouter ou gateway gerenciado.** Resolve o mesmo problema sem operar o proxy, mas manda
  todo o tráfego de prompt para um terceiro — inaceitável no cenário de dado sensível que o
  M04 trata.
