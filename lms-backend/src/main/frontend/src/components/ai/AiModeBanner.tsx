import { Cpu } from 'lucide-react';
import { useAiStatus } from '@/lib/ai';

/**
 * Says when the local language model is down and the NLP features are running
 * on classical methods only, so weaker output is not a mystery.
 */
export default function AiModeBanner({ what }: { what: string }) {
  const status = useAiStatus();
  if (!status.data || status.data.mode === 'LLM') return null;
  return (
    <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
      <Cpu size={14} className="mt-0.5 flex-shrink-0" />
      <span>
        Basic mode: the local language model is not running, so {what} uses classical text
        analysis only. Results stay grounded in the course material but read less naturally.
      </span>
    </div>
  );
}
