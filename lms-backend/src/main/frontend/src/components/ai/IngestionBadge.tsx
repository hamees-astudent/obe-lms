import Badge from '@/components/ui/Badge';
import type { IngestionStatus } from '@/types/api';

/** Whether a document has been read and indexed for the study assistant and generator. */
export default function IngestionBadge({ status, error }: { status: IngestionStatus; error?: string }) {
  switch (status) {
    case 'READY': return <Badge variant="success">Indexed</Badge>;
    case 'PROCESSING': return <Badge variant="info">Indexing…</Badge>;
    case 'PENDING': return <Badge variant="info">Queued</Badge>;
    case 'UNSUPPORTED': return <span title={error}><Badge variant="default">Not indexable</Badge></span>;
    default: return <span title={error}><Badge variant="danger">Index failed</Badge></span>;
  }
}
