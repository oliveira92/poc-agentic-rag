import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { pendingApprovals, reviewApproval } from '../../api/client';
import { Badge, EmptyState, Spinner } from '../../components/ui';
import { useToast } from '../../lib/toast';

/**
 * Curadoria humana (HITL): só o que for APROVADO entra na base como LLM_APPROVED
 * (com precedência menor que as fontes primárias — salvaguarda anti-eco).
 */
export function ApprovalsPage() {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [reviewer, setReviewer] = useState('');
  const [note, setNote] = useState('');

  const pending = useQuery({ queryKey: ['approvals'], queryFn: pendingApprovals });

  const review = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'approve' | 'reject' }) =>
      reviewApproval(id, action, reviewer || 'ui', note),
    onSuccess: (rec) => {
      toast(
        rec.status === 'APPROVED'
          ? 'Aprovado — indexado na base como LLM_APPROVED'
          : 'Rejeitado — descartado sem indexar',
        rec.status === 'APPROVED' ? 'success' : 'info',
      );
      void queryClient.invalidateQueries({ queryKey: ['approvals'] });
    },
    onError: (err) => toast(`Falha na revisão: ${err.message}`, 'error'),
  });

  return (
    <div className="page">
      <div className="page__head">
        <h1 className="page__title">Fila de curadoria</h1>
        {pending.data && <Badge tone="violet">{pending.data.length} pendente(s)</Badge>}
        <span className="topbar__spacer" />
        <input
          className="input input--sm"
          style={{ width: 140 }}
          placeholder="revisor"
          value={reviewer}
          onChange={(e) => setReviewer(e.target.value)}
        />
        <input
          className="input input--sm"
          style={{ width: 220 }}
          placeholder="nota da revisão (opcional)"
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
      </div>
      <p className="page__desc">
        Cada resposta do Advisor vira um rascunho aqui. <strong>Aprovar</strong> indexa como{' '}
        <code>LLM_APPROVED</code> (recuperável, com precedência menor que as fontes primárias);{' '}
        <strong>rejeitar</strong> descarta. É o loop que faz a base aprender com curadoria.
      </p>

      {pending.isLoading && <Spinner />}
      {pending.isError && (
        <div className="alert alert--danger">⚠ {String(pending.error.message)}</div>
      )}
      {pending.data?.length === 0 && (
        <EmptyState
          icon="🗂️"
          title="Nenhum rascunho pendente"
          hint="Faça uma pergunta no Advisor — a resposta chega aqui para revisão."
        />
      )}

      {pending.data?.map((rec) => (
        <div key={rec.id} className="card">
          <div className="row" style={{ marginBottom: 10 }}>
            <Badge tone="neutral">{rec.componentId}</Badge>
            <span className="faint mono">{rec.id.slice(0, 8)}…</span>
            <span className="faint">{new Date(rec.createdAt).toLocaleString('pt-BR')}</span>
            <span className="topbar__spacer" />
            <button
              type="button"
              className="btn btn--success"
              disabled={review.isPending}
              onClick={() => review.mutate({ id: rec.id, action: 'approve' })}
            >
              ✓ Aprovar
            </button>
            <button
              type="button"
              className="btn btn--danger"
              disabled={review.isPending}
              onClick={() => review.mutate({ id: rec.id, action: 'reject' })}
            >
              ✕ Rejeitar
            </button>
          </div>
          <div style={{ fontWeight: 650, marginBottom: 8 }}>“{rec.question}”</div>
          <div className="md approval-answer">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{rec.answer}</ReactMarkdown>
          </div>
        </div>
      ))}
    </div>
  );
}
