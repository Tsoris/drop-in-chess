import { useLayoutEffect, useRef } from 'react';

export type PromotionPiece = 'q' | 'r' | 'b' | 'n';
const choices: { piece: PromotionPiece; name: string; white: string; black: string }[] = [
  { piece: 'q', name: 'Queen', white: '\u2655', black: '\u265B' },
  { piece: 'r', name: 'Rook', white: '\u2656', black: '\u265C' },
  { piece: 'b', name: 'Bishop', white: '\u2657', black: '\u265D' },
  { piece: 'n', name: 'Knight', white: '\u2658', black: '\u265E' },
];

export default function PromotionPicker({ color, onChoose, onCancel }: {
  color: 'w' | 'b'; onChoose: (piece: PromotionPiece) => void; onCancel: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useLayoutEffect(() => {
    const element = dialog.current;
    const board = element?.parentElement;
    if (!element || !board) return;
    function positionPicker() {
      if (!element || !board) return;
      const bounds = board.getBoundingClientRect();
      element.style.left = `${bounds.left + bounds.width / 2}px`;
      element.style.top = `${bounds.top + bounds.height / 2}px`;
      element.style.width = `${Math.min(340, Math.max(0, bounds.width - 24))}px`;
    }
    positionPicker();
    element.showModal();
    const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(positionPicker) : null;
    observer?.observe(board);
    window.addEventListener('resize', positionPicker);
    window.addEventListener('scroll', positionPicker, true);
    return () => {
      observer?.disconnect();
      window.removeEventListener('resize', positionPicker);
      window.removeEventListener('scroll', positionPicker, true);
      element.close();
    };
  }, []);
  return <dialog ref={dialog} className="promotion-picker" aria-labelledby="promotion-title"
    onCancel={event => { event.preventDefault(); onCancel(); }}
    onClick={event => {
      if (event.target !== event.currentTarget) return;
      const bounds = event.currentTarget.getBoundingClientRect();
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) onCancel();
    }}>
    <h2 id="promotion-title">Promote your pawn</h2>

    <div className="promotion-choices">
      {choices.map(choice => <button type="button" key={choice.piece} onClick={() => onChoose(choice.piece)}>
        <span aria-hidden="true">{color === 'w' ? choice.white : choice.black}</span>{choice.name}
      </button>)}
    </div>
    <button type="button" className="promotion-cancel" onClick={onCancel}>Cancel</button>
  </dialog>;
}
