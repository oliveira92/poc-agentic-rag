/** Tipos espelhando os records Java da API (fonte: com.example.agenticrag.*). */

export interface Citation {
  index: number;
  source: 'PORTAL_API' | 'README' | 'LLM_APPROVED' | string;
  ref: string;
  score: number | null;
  snippet: string;
}

export interface AdviceResult {
  componentId: string;
  conversationId: string;
  answer: string;
  citations: Citation[];
  grounded: boolean;
  approvalId: string;
  traceId: string | null;
  route: string;
  model: string;
  lowConfidence: boolean;
  unsupportedEndpoints: string[];
}

export interface AdviseRequest {
  question: string;
  conversationId?: string;
  /** Id de GET /api/v1/models; ausente = roteamento por risco decide. */
  model?: string;
}

export interface IngestionResult {
  componentId: string;
  endpointsIndexed: number;
  readmeChunks: number;
  totalDocuments: number;
  skipped: boolean;
}

export interface ApprovalRecord {
  id: string;
  componentId: string;
  question: string;
  answer: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewer: string | null;
  reviewNote: string | null;
  createdAt: string;
  reviewedAt: string | null;
  vectorDocId: string | null;
}

export interface ModelInfo {
  id: string;
  label: string;
  tier: 'forte' | 'rapido' | 'default' | null;
  description: string | null;
  source: 'catalog' | 'anthropic';
}

export type RiskTier = 'LOW' | 'MEDIUM' | 'HIGH';

export interface GateCaseEvaluation {
  id: string;
  risk: RiskTier;
  retrievalHit: boolean;
  groundedness: { score: number; reason: string; judge: string } | null;
  threshold: number;
  passed: boolean;
}

export interface GateReport {
  total: number;
  passed: number;
  hitRate: number;
  avgGroundedness: number;
  gatePassed: boolean;
  cases: GateCaseEvaluation[];
}

export interface FeedbackRequest {
  value: 0 | 1;
  route?: string;
  traceId?: string;
  comment?: string;
}

export interface FeedbackResponse {
  componentId: string;
  route: string;
  recorded: boolean;
}

/** RFC 7807 — como o ApiExceptionHandler responde erros. */
export interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
}
