import { useQuery } from '@tanstack/react-query';
import { listModels } from '../../api/client';
import { Badge, EmptyState, Spinner } from '../../components/ui';

/**
 * Catálogo de modelos (ADR-0006 + ADR-0007): allow-list versionada (source=catalog) +
 * descoberta ao vivo no gateway LiteLLM (source=litellm), que publica modelos de vários
 * vendors. Qualquer um destes ids pode ser usado no seletor de modelo do Advisor.
 */
export function ModelsPage() {
  const models = useQuery({ queryKey: ['models'], queryFn: listModels });

  const providers = new Set(
    (models.data ?? []).map((m) => m.provider).filter((p): p is string => Boolean(p)),
  );

  return (
    <div className="page">
      <div className="page__head">
        <h1 className="page__title">Modelos disponíveis</h1>
        {models.data && <Badge tone="violet">{models.data.length} modelo(s)</Badge>}
        {providers.size > 0 && <Badge tone="cyan">{providers.size} provedor(es)</Badge>}
      </div>
      <p className="page__desc">
        A aplicação fala com um <strong>gateway LiteLLM</strong>, não com um vendor: o mesmo
        cliente alcança Claude, GPT, Gemini ou modelo aberto, e trocar de modelo vira config.{' '}
        <strong>catalog</strong> = allow-list curada e versionada (governança de modelo);{' '}
        <strong>litellm</strong> = descoberto ao vivo no proxy. Fora da lista, a API responde 400.
        As chaves dos vendors ficam <strong>só no proxy</strong> — a app nunca as vê.
      </p>

      {models.isLoading && <Spinner />}
      {models.isError && (
        <div className="alert alert--danger">⚠ {String(models.error.message)}</div>
      )}
      {models.data?.length === 0 && (
        <EmptyState icon="🧠" title="Nenhum modelo disponível" hint="Verifique o catálogo em app.models e se o proxy LiteLLM está de pé (docker compose --profile llm up -d litellm)." />
      )}

      {models.data && models.data.length > 0 && (
        <div className="card">
          <table className="table">
            <thead>
              <tr>
                <th>id (alias no gateway)</th>
                <th>nome</th>
                <th>provedor</th>
                <th>classe</th>
                <th>origem</th>
                <th>quando usar</th>
              </tr>
            </thead>
            <tbody>
              {models.data.map((m) => (
                <tr key={m.id}>
                  <td className="mono">{m.id}</td>
                  <td>{m.label}</td>
                  <td>
                    {m.provider ? (
                      <Badge tone="cyan">{m.provider}</Badge>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                  <td>
                    {m.tier ? (
                      <Badge tone={m.tier === 'forte' ? 'rose' : m.tier === 'rapido' ? 'teal' : 'blue'}>
                        {m.tier}
                      </Badge>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                  <td>
                    <Badge tone={m.source === 'catalog' ? 'violet' : 'cyan'}>{m.source}</Badge>
                  </td>
                  <td className="muted">{m.description ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
