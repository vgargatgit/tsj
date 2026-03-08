// Source inspiration: mitt event emitter shape (https://github.com/developit/mitt).
type Handler<T> = (event: T) => void;

function createEmitter<T>() {
  const handlers: Handler<T>[] = [];
  return {
    on(handler: Handler<T>) {
      handlers.push(handler);
    },
    emit(event: T) {
      for (const handler of handlers) {
        handler(event);
      }
    }
  };
}

const emitter = createEmitter<number>();
let seen = 0;
emitter.on((n) => (seen += n));
emitter.emit(4);
console.log(seen);
