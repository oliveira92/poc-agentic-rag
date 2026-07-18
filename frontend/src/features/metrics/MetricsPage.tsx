import { useQuery } from '@tanstack/react-query';
import { prometheusText } from '../../api/client';
import { Badge, EmptyState, RouteBadge, Spinner } from '../../components/ui';
import { labelValues, parsePrometheus, sumOf, type Sample } from '../../lib/prometheus';

/**
 * SLIs do M03 ao vivo (fonte: /actuator/prometheus, meters `rag_*` do RagMetrics).
 * Camada técnica (latência/tokens/erros por modelo e rota) + negócio (custo, CSAT, HITL).
 */
export function MetricsPage() {
  const metrics = useQuery({
    queryKey: ['prometheus'],
    queryFn: async () => parsePrometheus(await prometheusText()),
    refetchInterval: 10_000,
  });

  const s = metrics.data ?? [];
  const models = labelValues(s, 'rag_llm_tokens_total', 'model');
  const routes = new Set([
    ...labelValues(s, 'rag_advise_latency_seconds_count', 'route'),
    ...labelValues(s, 'rag_retrieve_latency_seconds_count', 'route'),
  ]);

  const totalCalls = sumOf(s, 'rag_advise_latency_seconds_count');
  const totalCost = sumOf(s, 'rag_llm_cost_total');
  const totalTokens = sumOf(s, 'rag_llm_tokens_total');
  const csatCount = sumOf(s, 'rag_csat_count');
  const csatAvg = csatCount > 0 ? sumOf(s, 'rag_csat_sum') / csatCount : null;
  const ungrounded = sumOf(s, 'rag_advise_ungrounded_total');
  const lowConf = sumOf(s, 'rag_advise_low_confidence_total');
  const approved = sumOf(s, 'rag_hitl_total', { outcome: 'approved' });
  const rejected = sumOf(s, 'rag_hitl_total', { outcome: 'rejected' });

  return (
    <div className="page">
      <div className="page__head">
        <h1 className="page__title">Métricas M03 ao vivo</h1>
        {metrics.isFetching && <Spinner small />}
        <span className="topbar__spacer" />
        <span className="faint">atualiza a cada 10s · fonte /actuator/prometheus</span>
      </div>

      {metrics.isError && (
        <div className="alert alert--danger">⚠ {String(metrics.error.message)}</div>
      )}

      <div className="grid grid--stats">
        <Stat label="Chamadas /advise" value={fmt(totalCalls)} hint="todas as rotas e modelos" />
        <Stat label="Custo estimado" value={`$${totalCost.toFixed(4)}`} hint="tokens × preço por família" />
        <Stat label="Tokens (in+out)" value={fmt(totalTokens)} hint="o custo mora aqui (A04)" />
        <Stat
          label="CSAT"
          value={csatAvg == null ? '—' : `${(csatAvg * 100).toFixed(0)}%`}
          hint={`${fmt(csatCount)} avaliações 👍/👎`}
        />
        <Stat label="Sem base (ungrounded)" value={fmt(ungrounded)} hint="respostas sem recuperação" />
        <Stat label="Guardrail disparou" value={fmt(lowConf)} hint="endpoints fora das fontes" />
        <Stat label="Curadoria (HITL)" value={`${fmt(approved)} ✓ · ${fmt(rejected)} ✕`} hint="aprovadas · rejeitadas" />
      </div>

      {models.length === 0 && !metrics.isLoading && (
        <div style={{ marginTop: 20 }}>
          <EmptyState
            icon="📈"
            title="Ainda sem chamadas de LLM medidas"
            hint="Faça uma pergunta no Advisor — latência, tokens e custo aparecem aqui."
          />
        </div>
      )}

      {models.length > 0 && (
        <div className="card" style={{ marginTop: 20 }}>
          <h2 className="card__title">Por modelo — o roteamento por custo em números</h2>
          <table className="table">
            <thead>
              <tr>
                <th>modelo</th>
                <th>chamadas</th>
                <th>latência média</th>
                <th>tokens in</th>
                <th>tokens out</th>
                <th>custo</th>
              </tr>
            </thead>
            <tbody>
              {models.map((m) => (
                <ModelRow key={m} samples={s} model={m} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {routes.size > 0 && (
        <div className="card">
          <h2 className="card__title">Por rota de negócio</h2>
          <table className="table">
            <thead>
              <tr>
                <th>rota</th>
                <th>chamadas /advise</th>
                <th>recuperações</th>
                <th>p95 retrieval</th>
                <th>sem base</th>
                <th>guardrail</th>
              </tr>
            </thead>
            <tbody>
              {[...routes].sort().map((r) => (
                <RouteRow key={r} samples={s} route={r} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="stat">
      <div className="stat__label">{label}</div>
      <div className="stat__value">{value}</div>
      {hint && <div className="stat__hint">{hint}</div>}
    </div>
  );
}

function ModelRow({ samples, model }: { samples: Sample[]; model: string }) {
  const calls = sumOf(samples, 'rag_advise_latency_seconds_count', { model });
  const latSum = sumOf(samples, 'rag_advise_latency_seconds_sum', { model });
  const tin = sumOf(samples, 'rag_llm_tokens_total', { model, type: 'input' });
  const tout = sumOf(samples, 'rag_llm_tokens_total', { model, type: 'output' });
  const cost = sumOf(samples, 'rag_llm_cost_total', { model });
  return (
    <tr>
      <td><Badge tone="violet">{model}</Badge></td>
      <td className="mono">{fmt(calls)}</td>
      <td className="mono">{calls > 0 ? `${(latSum / calls).toFixed(1)}s` : '—'}</td>
      <td className="mono">{fmt(tin)}</td>
      <td className="mono">{fmt(tout)}</td>
      <td className="mono">${cost.toFixed(4)}</td>
    </tr>
  );
}

function RouteRow({ samples, route }: { samples: Sample[]; route: string }) {
  const advises = sumOf(samples, 'rag_advise_latency_seconds_count', { route });
  const retrieves = sumOf(samples, 'rag_retrieve_latency_seconds_count', { route });
  const p95 = samples.find(
    (x) =>
      x.name === 'rag_retrieve_latency_seconds' &&
      x.labels.route === route &&
      x.labels.quantile === '0.95',
  )?.value;
  const ungrounded = sumOf(samples, 'rag_advise_ungrounded_total', { route });
  const lowConf = sumOf(samples, 'rag_advise_low_confidence_total', { route });
  return (
    <tr>
      <td><RouteBadge route={route} /></td>
      <td className="mono">{fmt(advises)}</td>
      <td className="mono">{fmt(retrieves)}</td>
      <td className="mono">{p95 != null ? `${(p95 * 1000).toFixed(0)}ms` : '—'}</td>
      <td className="mono">{fmt(ungrounded)}</td>
      <td className="mono">{fmt(lowConf)}</td>
    </tr>
  );
}

const fmt = (n: number) => n.toLocaleString('pt-BR', { maximumFractionDigits: 0 });
