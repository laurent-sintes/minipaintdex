export function nextSuggestionIndex(current: number, count: number, direction: 1 | -1) {
  if (!count) return -1;
  if (current < 0) return direction === 1 ? 0 : count - 1;
  return (current + direction + count) % count;
}
