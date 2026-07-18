import { useMutation } from '@tanstack/react-query';
import { runQualityGate } from '../../api/client';
import type { GateReport } from '../../api/types';
import { useComponentId } from '../../App';
import { EmptyState, RiskBadge, Spinner } from '../../components/ui';
import { useToast } from '../../lib/toast';

/**
 * "O Portão" (A05): roda o golden set pela recuperação e decide PASS/FAIL. Toda rota de
 * risco alto precisa passar; a taxa geral precisa ficar acima do limite. É o mesmo gate que
 * o CI usa para bloquear merge — aqui sob demanda, para teste e demonstração.
 */
export function QualityPage() {
  const componentId = useComponentId();
  const { toast } = useToast();

  const gate = useMutation({
    mutationFn: () => runQualityGate(componentId),
    onError: (err) => toast(`Falha ao rodar o gate: ${err.message}`, 'error'),
  });

  const report: GateReport | undefined = gate.data;

  return (
    <div className="page">
      <div className="page__head">
        <h1 className="page__title">Quality Gate — “O Portão”</h1>
        <span className="topbar__spacer" />
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => gate.mutate()}
          disabled={gate.isPending}
        >
          {gate.isPending ? <Spinner small /> : '🛡️'} Rodar o Portão
        </button>
      </div>
      <p className="page__desc">
        Roda o <strong>golden set</strong> de <code>{componentId}</code> (casos fácil · médio ·
        armadilha) pela recuperação — sem gastar LLM. Regra: toda rota de risco{' '}
        <strong>HIGH</strong> precisa passar + taxa geral acima do limite. No CI, reprovar aqui{' '}
        <strong>bloqueia o merge</strong>.
      </p>

      {!report && !gate.isPending && (
        <EmptyState
          icon="🛡️"
          title="Portão ainda não executado"
          hint="Ingira o componente antes (aba Ingestão) e clique em Rodar o Portão."
        />
      )}

      {report && (
        <>
          <div className={`gate-banner ${report.gatePassed ? 'gate-banner--pass' : 'gate-banner--fail'}`}>
            {report.gatePassed ? '✓ GATE PASSOU — deploy liberado' : '✕ GATE REPROVOU — merge bloqueado'}
            <span className="gate-banner__stats">
              <span>{report.passed}/{report.total} casos</span>
              <span>hit rate {(report.hitRate * 100).toFixed(0)}%</span>
            </span>
          </div>

          <div className="card" style={{ marginTop: 14 }}>
            <h2 className="card__title">Evidência por caso do golden set</h2>
            <table className="table">
              <thead>
                <tr>
                  <th>caso</th>
                  <th>risco</th>
                  <th>retrieval</th>
                  <th>limite</th>
                  <th>resultado</th>
                </tr>
              </thead>
              <tbody>
                {report.cases.map((c) => (
                  <tr key={c.id}>
                    <td className="mono">{c.id}</td>
                    <td><RiskBadge risk={c.risk} /></td>
                    <td>
                      {c.retrievalHit ? (
                        <span className="check">✓ fonte esperada no top-K</span>
                      ) : (
                        <span className="cross">✕ não recuperou o esperado</span>
                      )}
                    </td>
                    <td className="mono">{c.threshold.toFixed(2)}</td>
                    <td>{c.passed ? <span className="check">PASS</span> : <span className="cross">FAIL</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="faint" style={{ marginTop: 10 }}>
              Armadilhas passam quando o sistema <em>não</em> recupera nada com confiança (não dá
              margem para inventar). Reprovações de armadilha LOW são toleradas pela taxa geral,
              mas ficam visíveis aqui.
            </p>
          </div>
        </>
      )}
    </div>
  );
}
