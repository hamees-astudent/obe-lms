import { useEffect, useRef } from 'react';
import { X } from 'lucide-react';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  /** Max width tailwind class, defaults to max-w-lg */
  maxWidth?: string;
}

export default function Modal({ open, onClose, title, children, maxWidth = 'max-w-lg' }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const backdropPressRef = useRef(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open) {
      if (!dialog.open) dialog.showModal();
    } else {
      if (dialog.open) dialog.close();
    }
  }, [open]);

  // Close on backdrop click. The backdrop is the dialog's ::backdrop pseudo-element,
  // so only a press that both starts and ends on the <dialog> itself counts — never one
  // on its children. Coordinates are unreliable here: native <select> popups can paint
  // outside the panel and report click points beyond (or at 0,0 of) the dialog's box.
  function handleMouseDown(e: React.MouseEvent<HTMLDialogElement>) {
    backdropPressRef.current = e.target === dialogRef.current;
  }

  function handleClick(e: React.MouseEvent<HTMLDialogElement>) {
    const startedOnBackdrop = backdropPressRef.current;
    backdropPressRef.current = false;
    if (startedOnBackdrop && e.target === dialogRef.current) onClose();
  }

  if (!open) return null;

  return (
    <dialog
      ref={dialogRef}
      onMouseDown={handleMouseDown}
      onClick={handleClick}
      onClose={onClose}
      className={[
        'w-full rounded-2xl p-0 shadow-xl backdrop:bg-black/40',
        'animate-scale-in',
        maxWidth,
      ].join(' ')}
    >
      <div className="flex flex-col">
        {title && (
          <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
            <h2 className="text-base font-semibold text-gray-900">{title}</h2>
            <button
              onClick={onClose}
              aria-label="Close modal"
              className="rounded-md p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
            >
              <X size={18} />
            </button>
          </div>
        )}
        <div className="p-6">{children}</div>
      </div>
    </dialog>
  );
}
