import { useQuery } from '@tanstack/react-query';
import { createContext, useContext, useState } from 'react';
import { health } from './api/client';
import { AdvisorPage } from './features/advisor/AdvisorPage';
import { ApprovalsPage } from './features/approvals/ApprovalsPage';
import { IngestPage } from './features/ingest/IngestPage';
import { MetricsPage } from './features/metrics/MetricsPage';
import { ModelsPage } from './features/models/ModelsPage';
import { QualityPage } from './features/quality/QualityPage';

/** Componente-alvo global (ex.: payments-sdk) — usado por Advisor, Gate e Ingestão. */
const ComponentCtx = createContext<string>('payments-sdk');
export const useComponentId = () => useContext(ComponentCtx);

type Page = 'advisor' | 'approvals' | 'quality' | 'models' | 'metrics' | 'ingest';

const NAV: { id: Page; label: string; icon: string; title: string }[] = [
  { id: 'advisor', label: 'Advisor', icon: '💬', title: 'Advisor — pergunte como consumir' },
  { id: 'approvals', label: 'Curadoria', icon: '✅', title: 'Curadoria humana (HITL)' },
  { id: 'quality', label: 'Quality Gate', icon: '🛡️', title: 'Quality Gate — O Portão' },
  { id: 'models', label: 'Modelos', icon: '🧠', title: 'Modelos Anthropic disponíveis' },
  { id: 'metrics', label: 'Métricas', icon: '📈', title: 'Métricas M03 (SLIs · custo · CSAT)' },
  { id: 'ingest', label: 'Ingestão', icon: '📥', title: 'Ingestão de componentes' },
];

export function App() {
  const [page, setPage] = useState<Page>('advisor');
  const [componentId, setComponentId] = useState('payments-sdk');

  const { data: up } = useQuery({
    queryKey: ['health'],
    queryFn: health,
    refetchInterval: 15_000,
  });

  const active = NAV.find((n) => n.id === page)!;

  return (
    <ComponentCtx.Provider value={componentId}>
      <div className="shell">
        <aside className="sidebar">
          <div className="logo">
            <div className="logo__mark">Ai</div>
            <div>
              <div className="logo__name">Component Advisor</div>
              <div className="logo__sub">Agentic RAG · M03</div>
            </div>
          </div>

          <nav className="nav">
            {NAV.map((n) => (
              <button
                key={n.id}
                type="button"
                className={`nav__item ${page === n.id ? 'active' : ''}`}
                onClick={() => setPage(n.id)}
              >
                <span className="nav__icon">{n.icon}</span>
                {n.label}
              </button>
            ))}
          </nav>

          <div className="sidebar__footer">
            <span className={`dot ${up === undefined ? '' : up ? 'dot--up' : 'dot--down'}`} />
            {up === undefined ? 'verificando…' : up ? 'API saudável' : 'API fora do ar'}
          </div>
        </aside>

        <div className="main">
          <header className="topbar">
            <span className="topbar__title">{active.title}</span>
            <span className="topbar__spacer" />
            <label htmlFor="component-id">componente</label>
            <input
              id="component-id"
              className="input input--sm mono"
              style={{ width: 170 }}
              value={componentId}
              onChange={(e) => setComponentId(e.target.value.trim())}
              spellCheck={false}
            />
          </header>

          {page === 'advisor' && <AdvisorPage />}
          {page === 'approvals' && (
            <div className="content"><ApprovalsPage /></div>
          )}
          {page === 'quality' && (
            <div className="content"><QualityPage /></div>
          )}
          {page === 'models' && (
            <div className="content"><ModelsPage /></div>
          )}
          {page === 'metrics' && (
            <div className="content"><MetricsPage /></div>
          )}
          {page === 'ingest' && (
            <div className="content"><IngestPage /></div>
          )}
        </div>
      </div>
    </ComponentCtx.Provider>
  );
}
