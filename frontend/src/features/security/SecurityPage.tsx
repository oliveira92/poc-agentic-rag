import { useMutation, useQuery } from '@tanstack/react-query';
import { evaluateSecurity, securityControls } from '../../api/client';
import type {
  GuardAction,
  SecurityCaseResult,
  SecurityEvaluationReport,
  SecurityMetrics,
} from '../../api/types';
import { Badge, EmptyState, Spinner } from '../../components/ui';
import { useToast } from '../../lib/toast';

/**
 * Evidência do M04 em uma tela: quais controles existem, o que cada um faz em cada estágio,
 * e o que o dataset da A05 diz sobre eles — linha de base × protegida, com denominador.
 *
 * <p>Os dois números que importam ficam lado a lado de propósito: eficácia (ataques barrados)
 * e custo (legítimos barrados). Mostrar só o primeiro é o jeito clássico de fazer um controle
 * ruim parecer bom.
 */
export function SecurityPage() {
  const { toast } = useToast();
  const controls = useQuery({ queryKey: ['security-controls'], queryFn: securityControls });

  const evaluation = useMutation({
    mutationFn: evaluateSecurity,
    onError: (err) => toast(`Falha ao rodar o dataset: ${err.message}`, 'error'),
  });

  const reports: SecurityEvaluationReport[] = evaluation.data ?? [];
  const status = controls.data?.status;

  return (
    <div className="page">
      <div className="page__head">
        <h1 className="page__title">Segurança — controles fora do system prompt</h1>
        <span className="topbar__spacer" />
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => evaluation.mutate()}
          disabled={evaluation.isPending}
        >
          {evaluation.isPending ? <Spinner small /> : '🔒'} Rodar os datasets
        </button>
      </div>
      <p className="page__desc">
        Todo controle desta tela roda em <strong>Java</strong>, antes ou depois do modelo — nada
        depende de o modelo obedecer a uma instrução. Uma pergunta bloqueada não vira token, não
        vira embedding e não aparece em log de prompt. O dataset roda em{' '}
        <strong>dois domínios</strong> (advisor de componentes e atendimento em seguros) com o
        mesmo código: é isso que mostra que os controles não dependem do assunto.
      </p>

      {controls.isLoading && <Spinner />}
      {controls.isError && (
        <div className="alert alert--danger">⚠ {String(controls.error.message)}</div>
      )}

      {status && (
        <div className="card">
          <h2 className="card__title">Estado da camada</h2>
          <div className="msg-ai__meta">
            <Badge tone={status.enabled ? 'green' : 'rose'}>
              {status.enabled ? 'controles ativos' : 'DESLIGADOS (linha de base)'}
            </Badge>
            <Badge
              tone={status.failMode === 'closed' ? 'green' : 'amber'}
              title="o que acontece quando um controle falha internamente"
            >
              fail-{status.failMode}
            </Badge>
            <Badge tone="violet" title="quem decide se a pergunta cabe no conteúdo ingerido">
              juiz de escopo: {status.scopeJudge}
            </Badge>
            <Badge tone="neutral">{status.controls} controles</Badge>
            <Badge tone="neutral">pergunta ≤ {status.maxQuestionChars} chars</Badge>
          </div>
        </div>
      )}

      {controls.data && controls.data.controls.length > 0 && (
        <div className="card" style={{ marginTop: 14 }}>
          <h2 className="card__title">Controles implementados</h2>
          <table className="table">
            <thead>
              <tr>
                <th>id</th>
                <th>categoria</th>
                <th>ingestão</th>
                <th>entrada</th>
                <th>saída</th>
                <th>o que procura</th>
                <th>onde está</th>
              </tr>
            </thead>
            <tbody>
              {controls.data.controls.map((c) => (
                <tr key={c.id}>
                  <td className="mono">{c.id}</td>
                  <td className="mono">{c.category.toLowerCase()}</td>
                  <td><ActionCell action={c.actions.INGESTION} /></td>
                  <td><ActionCell action={c.actions.INPUT} /></td>
                  <td><ActionCell action={c.actions.OUTPUT} /></td>
                  <td className="muted">{c.description}</td>
                  <td className="mono faint">{c.implementation}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="faint" style={{ marginTop: 10 }}>
            <strong>MASK</strong> ofusca e segue; <strong>BLOCK</strong> interrompe. A mesma
            categoria tem ação diferente por estágio: um CPF na pergunta é mascarado (o titular
            continua sendo atendido), o mesmo CPF entrando na base é recusado — ali o erro
            ficaria indexado para sempre.
          </p>
        </div>
      )}

      {reports.length === 0 && !evaluation.isPending && (
        <div style={{ marginTop: 14 }}>
          <EmptyState
            icon="🧪"
            title="Datasets ainda não executados"
            hint="Dois cenários: o domínio desta PoC (payments-sdk) e o de atendimento em seguros da A05 — os mesmos controles, sem regra específica de domínio."
          />
        </div>
      )}

      {reports.map((r) => (
        <EvaluationResult key={r.scenarioId} report={r} />
      ))}
    </div>
  );
}

function ActionCell({ action }: { action?: GuardAction }) {
  if (!action) return <span className="muted">—</span>;
  return <Badge tone={action === 'BLOCK' ? 'rose' : 'amber'}>{action}</Badge>;
}

function EvaluationResult({ report }: { report: SecurityEvaluationReport }) {
  const p = report.protectedRun;
  const clean = p.legitimateBlocked === 0 && p.badAllowed === 0;

  return (
    <>
      <div
        className={`gate-banner ${clean ? 'gate-banner--pass' : 'gate-banner--fail'}`}
        style={{ marginTop: 22 }}
      >
        {clean
          ? `✓ ${report.scenario} — barrou todo ataque sem derrubar caso legítimo`
          : `✕ ${report.scenario} — falso positivo ou ataque não detectado`}
        <span className="gate-banner__stats">
          <span className="mono">{report.scenarioId}</span>
          <span>juiz: {report.judge}</span>
        </span>
      </div>

      <div className="card" style={{ marginTop: 14 }}>
        <h2 className="card__title">
          Métricas — linha de base × protegida · <span className="mono">{report.scenarioId}</span>
        </h2>
        <table className="table">
          <thead>
            <tr>
              <th>métrica</th>
              <th>linha de base</th>
              <th>protegida</th>
            </tr>
          </thead>
          <tbody>
            <MetricRow
              label="Ataques barrados (recall)"
              base={fraction(report.baseline.badBlocked, report.baseline.badCases)}
              prot={fraction(p.badBlocked, p.badCases)}
            />
            <MetricRow
              label="Legítimos barrados (falso positivo)"
              base={fraction(report.baseline.legitimateBlocked, report.baseline.legitimateCases)}
              prot={fraction(p.legitimateBlocked, p.legitimateCases)}
            />
            <MetricRow
              label="Legítimos atendidos com ofuscação"
              base={`${report.baseline.legitimateMasked}/${report.baseline.legitimateCases}`}
              prot={`${p.legitimateMasked}/${p.legitimateCases}`}
            />
            <MetricRow
              label="Precisão dos bloqueios"
              base={pct(precision(report.baseline))}
              prot={pct(precision(p))}
            />
            <MetricRow
              label={`Acurácia (n=${p.totalCases})`}
              base={pct(accuracy(report.baseline))}
              prot={pct(accuracy(p))}
            />
          </tbody>
        </table>
        <p className="faint" style={{ marginTop: 10 }}>
          A linha de base não é um experimento: é a definição de "sem controle nada é barrado".
          Está aqui para dar contraste e para deixar explícito o denominador de cada taxa —
          percentual sem denominador esconde o tamanho da amostra.
        </p>
      </div>

      <div className="card" style={{ marginTop: 14 }}>
        <h2 className="card__title">
          Caso a caso · <span className="mono">{report.scenarioId}</span>
        </h2>
        <table className="table">
          <thead>
            <tr>
              <th>id</th>
              <th>tipo</th>
              <th>pergunta</th>
              <th>base</th>
              <th>protegida</th>
              <th>controles</th>
              <th>resultado</th>
            </tr>
          </thead>
          <tbody>
            {report.cases.map((c) => (
              <CaseRow key={c.id} c={c} />
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function CaseRow({ c }: { c: SecurityCaseResult }) {
  const tone =
    c.type === 'legitimo' ? 'teal' : c.type === 'abuso' ? 'rose' : 'amber';
  return (
    <tr title={c.note}>
      <td className="mono">{c.id}</td>
      <td><Badge tone={tone}>{c.type}</Badge></td>
      <td className="muted">{c.question}</td>
      <td><Badge tone="neutral">{c.baselineAction}</Badge></td>
      <td><ActionCell action={c.action === 'ALLOW' ? undefined : c.action} /></td>
      <td className="mono faint">{c.controls.length > 0 ? c.controls.join(', ') : '—'}</td>
      <td>
        {c.outcome === 'TP' || c.outcome === 'TN' ? (
          <span className="check">{c.outcome}</span>
        ) : (
          <span className="cross">{c.outcome}</span>
        )}
      </td>
    </tr>
  );
}

function MetricRow({ label, base, prot }: { label: string; base: string; prot: string }) {
  return (
    <tr>
      <td>{label}</td>
      <td className="mono muted">{base}</td>
      <td className="mono">{prot}</td>
    </tr>
  );
}

const fraction = (n: number, d: number) =>
  `${n}/${d} (${pct(d === 0 ? 0 : n / d)})`;

const pct = (v: number) => `${Math.round(v * 100)}%`;

const precision = (m: SecurityMetrics) => {
  const emitted = m.badBlocked + m.legitimateBlocked;
  return emitted === 0 ? 0 : m.badBlocked / emitted;
};

const accuracy = (m: SecurityMetrics) =>
  m.totalCases === 0 ? 0 : (m.badBlocked + m.legitimateAllowed) / m.totalCases;
