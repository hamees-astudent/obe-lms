import { useState } from 'react';
import { Copy, Check } from 'lucide-react';

/**
 * A record's internal UUID, shown compactly and click-to-copy.
 *
 * These ids are what the API and support tooling address records by, but until
 * now they were stored and never displayed — so anything that asked for one
 * (transcript lookup, bug reports) had no way to supply it. Shown small and
 * secondary: the roll number or name stays the human identifier.
 */
export default function CopyableId({ id, label }: { id: string; label?: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(id);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard access denied (insecure context or user setting) — the id is
      // still selectable in the title attribute.
    }
  }

  return (
    <button
      type="button"
      onClick={copy}
      title={copied ? 'Copied' : `Copy ${label ?? 'ID'}: ${id}`}
      className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 font-mono text-xs text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600"
    >
      {copied ? <Check size={11} className="text-green-600" /> : <Copy size={11} />}
      {id.slice(0, 8)}…
    </button>
  );
}
