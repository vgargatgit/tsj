// Source inspiration: Redux selector composition patterns.
type State = { todos: { done: boolean }[] };

function selectCompletedCount(state: State): number {
  let count = 0;
  for (const todo of state.todos) {
    if (todo.done) {
      count++;
    }
  }
  return count;
}

console.log(selectCompletedCount({ todos: [{ done: true }, { done: false }, { done: true }] }));
