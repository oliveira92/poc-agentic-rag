import type {
  AdviceResult,
  AdviseRequest,
  ApprovalRecord,
  Citation,
  FeedbackRequest,
  FeedbackResponse,
  GateReport,
  IngestionResult,
  ModelInfo,
  ProblemDetail,
} from './types';

const BASE = '/api/v1';

/** Erro de API carregando o ProblemDetail (RFC 7807) quando disponível. */
export class ApiError extends Error {
  readonly status: number;
  readonly title?: string;

  constructor(status: number, problem?: ProblemDetail) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`);
    this.status = status;
    this.title = problem?.title;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, init);
  if (!res.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await res.json()) as ProblemDetail;
    } catch {
      /* corpo não-JSON: mantém só o status */
    }
    throw new ApiError(res.status, problem);
  }
  return (await res.json()) as T;
}

const json = (body: unknown): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

// ---------- Advisor ----------

export const advise = (componentId: string, req: AdviseRequest) =>
  request<AdviceResult>(`${BASE}/components/${componentId}/advise`, json(req));

export const search = (componentId: string, q: string, k = 5) =>
  request<Citation[]>(
    `${BASE}/components/${componentId}/search?q=${encodeURIComponent(q)}&k=${k}`,
  );

export const sendFeedback = (componentId: string, req: FeedbackRequest) =>
  request<FeedbackResponse>(`${BASE}/components/${componentId}/feedback`, json(req));

// ---------- Ingestão ----------

export const ingest = (componentId: string) =>
  request<IngestionResult>(`${BASE}/components/${componentId}/ingest`, { method: 'POST' });

export const ingestReadme = (componentId: string, markdown: string) =>
  request<IngestionResult>(`${BASE}/components/${componentId}/ingest/readme`, {
    method: 'POST',
    headers: { 'Content-Type': 'text/markdown' },
    body: markdown,
  });

// ---------- Curadoria (HITL) ----------

export const pendingApprovals = () => request<ApprovalRecord[]>(`${BASE}/approvals/pending`);

export const reviewApproval = (
  id: string,
  action: 'approve' | 'reject',
  reviewer: string,
  note: string,
) => request<ApprovalRecord>(`${BASE}/approvals/${id}/${action}`, json({ reviewer, note }));

// ---------- Modelos ----------

export const listModels = () => request<ModelInfo[]>(`${BASE}/models`);

// ---------- Quality gate ----------

/** 200 (PASS) e 422 (FAIL) trazem o mesmo Report — ambos são resultado válido do Portão. */
export async function runQualityGate(componentId: string): Promise<GateReport> {
  const res = await fetch(`${BASE}/quality/${componentId}/gate`, { method: 'POST' });
  if (res.status === 200 || res.status === 422) {
    return (await res.json()) as GateReport;
  }
  let problem: ProblemDetail | undefined;
  try {
    problem = (await res.json()) as ProblemDetail;
  } catch {
    /* ignore */
  }
  throw new ApiError(res.status, problem);
}

// ---------- Observabilidade ----------

export const health = async (): Promise<boolean> => {
  try {
    const res = await fetch('/actuator/health');
    if (!res.ok) return false;
    const body = (await res.json()) as { status?: string };
    return body.status === 'UP';
  } catch {
    return false;
  }
};

export const prometheusText = async (): Promise<string> => {
  const res = await fetch('/actuator/prometheus');
  if (!res.ok) throw new ApiError(res.status);
  return res.text();
};
