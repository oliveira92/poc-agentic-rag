# Component Advisor UI

Interface de **teste e demonstração** do PoC Agentic RAG (backend Spring em `../`).
React 19 · TypeScript · Vite · TanStack Query. Tema dark alinhado ao deck do M03.

## Rodar

```bash
# 1) backend de pé (na raiz do repo):
#    docker compose up -d postgres
#    java -jar target/agentic-rag-0.1.0-SNAPSHOT.jar --spring.profiles.active=mock

# 2) UI em modo dev (proxy para :8080 — zero CORS):
npm install
npm run dev          # http://localhost:5173
```

`npm run build` gera o bundle em `dist/`; `npm run preview` serve o build (porta 4173, mesmo proxy).

## O que cada aba demonstra (mapa M03)

| Aba | O que mostra | Camada |
|---|---|---|
| **Advisor** | chat com citações `[n]`, badges de rota/modelo, guardrail anti-alucinação, 👍/👎 (CSAT→trace), traceId copiável | 1 · 3 · 4 · 5 |
| **Curadoria** | fila HITL: aprovar → indexa `LLM_APPROVED` / rejeitar → descarta | governança do conhecimento |
| **Quality Gate** | roda "O Portão" (golden set pela recuperação) e mostra PASS/FAIL por caso | 5 |
| **Modelos** | catálogo versionado + modelos reais da conta (`/v1/models`) | 5 (ADR-0006) |
| **Métricas** | SLIs `rag_*` ao vivo: custo/tokens/latência por modelo e rota, CSAT, HITL | 2 · 4 |
| **Ingestão** | ingerir do portal ou com README próprio (idempotente) | — |

O seletor **componente** no topo (default `payments-sdk`) vale para todas as abas.
