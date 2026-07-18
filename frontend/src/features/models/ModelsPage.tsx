import { useQuery } from '@tanstack/react-query';
import { listModels } from '../../api/client';
import { Badge, EmptyState, Spinner } from '../../components/ui';

/**
 * Catálogo de modelos (ADR-0006): allow-list versionada (source=catalog) + descoberta ao
 * vivo dos modelos reais da conta Anthropic (source=anthropic). Qualquer um destes ids pode
 * ser usado no seletor de modelo do Advisor.
 */
export function ModelsPage() {
  const models = useQuery({ queryKey: ['models'], queryFn: listModels });

  return (
    <div className="page">
      <div className="page__head">
        <h1 className="page__title">Modelos Anthropic disponíveis</h1>
        {models.data && <Badge tone="violet">{models.data.length} modelo(s)</Badge>}
      </div>
      <p className="page__desc">
        <strong>catalog</strong> = allow-list curada e versionada (governança de modelo);{' '}
        <strong>anthropic</strong> = descoberto ao vivo na conta via <code>/v1/models</code> (os
        ids exatos). O Advisor aceita qualquer um destes no campo <code>model</code> — fora da
        lista, a API responde 400.
      </p>

      {models.isLoading && <Spinner />}
      {models.isError && (
        <div className="alert alert--danger">⚠ {String(models.error.message)}</div>
      )}
      {models.data?.length === 0 && (
        <EmptyState icon="🧠" title="Nenhum modelo disponível" hint="Verifique o catálogo em app.models e a chave da conta." />
      )}

      {models.data && models.data.length > 0 && (
        <div className="card">
          <table className="table">
            <thead>
              <tr>
                <th>id (exato)</th>
                <th>nome</th>
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
