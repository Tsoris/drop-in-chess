import { useLayoutEffect, useRef } from 'react';
import type { PieceRenderObject } from 'react-chessboard';

export type PromotionPiece = 'q' | 'r' | 'b' | 'n';
const choices: { piece: PromotionPiece; name: string }[] = [
  { piece: 'q', name: 'Queen' },
  { piece: 'r', name: 'Rook' },
  { piece: 'b', name: 'Bishop' },
  { piece: 'n', name: 'Knight' },
];

export default function PromotionPicker({ color, pieces, onChoose, onCancel }: {
  color: 'w' | 'b'; pieces: PieceRenderObject; onChoose: (piece: PromotionPiece) => void; onCancel: () => void;
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
    <h2 id="promotion-title">Promotion</h2>

    <div className="promotion-choices">
      {choices.map(choice => {
        const PieceIcon = pieces[`${color}${choice.piece.toUpperCase()}`];
        return <button type="button" key={choice.piece} aria-label={choice.name} onClick={() => onChoose(choice.piece)}>
          <span aria-hidden="true"><PieceIcon /></span>
        </button>;
      })}
    </div>
  </dialog>;
}
