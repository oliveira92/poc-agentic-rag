import type { ReactNode } from 'react';
import type { RiskTier } from '../api/types';

/** Chip/badge com tom semântico. */
export function Badge({
  tone = 'neutral',
  children,
  title,
}: {
  tone?: 'neutral' | 'violet' | 'cyan' | 'teal' | 'blue' | 'rose' | 'amber' | 'green';
  children: ReactNode;
  title?: string;
}) {
  return (
    <span className={`badge badge--${tone}`} title={title}>
      {children}
    </span>
  );
}

/** Rota de negócio → cor (faq=teal, integracao=blue, risco=rose). */
export function RouteBadge({ route }: { route: string }) {
  const tone = route === 'risco' ? 'rose' : route === 'faq' ? 'teal' : 'blue';
  return <Badge tone={tone}>{route}</Badge>;
}

export function RiskBadge({ risk }: { risk: RiskTier }) {
  const tone = risk === 'HIGH' ? 'rose' : risk === 'LOW' ? 'teal' : 'blue';
  return <Badge tone={tone}>{risk}</Badge>;
}

/** Fonte da citação → cor (precedência: PORTAL_API/README > LLM_APPROVED). */
export function SourceBadge({ source }: { source: string }) {
  const tone =
    source === 'PORTAL_API' ? 'violet' : source === 'README' ? 'cyan' : 'amber';
  return <Badge tone={tone}>{source}</Badge>;
}

export function Spinner({ small = false }: { small?: boolean }) {
  return <span className={small ? 'spinner spinner--sm' : 'spinner'} aria-label="carregando" />;
}

export function EmptyState({ icon, title, hint }: { icon: string; title: string; hint?: string }) {
  return (
    <div className="empty">
      <div className="empty__icon">{icon}</div>
      <div className="empty__title">{title}</div>
      {hint && <div className="empty__hint">{hint}</div>}
    </div>
  );
}

/** Barra de score (similaridade 0..1) das citações. */
export function ScoreBar({ score }: { score: number | null }) {
  if (score == null) return <span className="muted">—</span>;
  const pct = Math.max(0, Math.min(1, score)) * 100;
  return (
    <span className="scorebar" title={`similaridade ${score.toFixed(3)}`}>
      <span className="scorebar__track">
        <span className="scorebar__fill" style={{ width: `${pct}%` }} />
      </span>
      <span className="scorebar__num">{score.toFixed(2)}</span>
    </span>
  );
}

/** Botão de copiar (traceId, ids…). */
export function CopyButton({ text, label }: { text: string; label?: string }) {
  return (
    <button
      type="button"
      className="btn btn--ghost btn--xs"
      onClick={() => void navigator.clipboard.writeText(text)}
      title={`Copiar ${label ?? text}`}
    >
      ⧉ {label ?? 'copiar'}
    </button>
  );
}
