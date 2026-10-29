import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

/**
 * Checks the golden wire files of the repository-level `contract/` folder (written by the backend
 * `WireContract` tests from what the server really serializes) against the frontend types, with the
 * TypeScript compiler: every recorded payload becomes an object literal assigned to the frontend type, so a
 * renamed, missing or extra field, a wrong nullability or an unknown enum value is a type error. Excess
 * property checks are on: the frontend types must name every field the server sends.
 */

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const SRC_DIR = path.resolve(HERE, '..');
export const CONTRACT_DIR = path.resolve(SRC_DIR, '../../contract');
const VIRTUAL_FILE = path.join(HERE, '__wire__.ts');

/** A type of a frontend module, as a type expression usable from `src/contract/`. */
export function t(module: string, name: string): string {
  return `import('../${module}').${name}`;
}

/** How the frontend reads the wire of one game (or of the session layer). */
export interface GameContract {
  /** The folder under `contract/`: the server game type, or `session`. */
  folder: string;
  /** The type of a REST state body and of a message's `state` (a lobby view or the game). */
  view: string;
  /**
   * The union of the event type strings the frontend knows for this game, or null when the frontend types
   * them as plain strings. Checked both ways against `event-types.json` (lifecycle types excepted).
   */
  eventType: string | null;
  /** The type of `eventData` by event type; `*` for the others. Lifecycle types are filled in by the check. */
  eventData: Record<string, string>;
  /** The type of each other REST body, by recorded kind. */
  rest?: Record<string, string>;
  /** Typed test fixtures of the game: their own type errors fail the check too (they must mirror the wire). */
  fixtures?: string[];
}

interface Recorded {
  kind: string;
  payload: unknown;
}

interface Envelope {
  success?: boolean;
  eventType?: string;
  eventData?: unknown;
  message?: unknown;
  state?: unknown;
}

const EMPTY_DATA = 'Record<string, never>';
/** The data of the shared events; START is the session's start (no data) unless a game says otherwise. */
const SHARED_DATA: Record<string, string> = {
  START: EMPTY_DATA,
  CREATE: EMPTY_DATA,
  JOIN: EMPTY_DATA,
  REMOVED: EMPTY_DATA,
  OPPONENT_DISCONNECTED: t('model/SeatLifecycleEvents', 'OpponentDisconnectedEventData'),
  OPPONENT_RECONNECTED: t('model/SeatLifecycleEvents', 'OpponentReconnectedEventData'),
  FORFEIT: t('model/SeatLifecycleEvents', 'ForfeitEventData'),
};

/** The event types of the shared session layer, pinned by the backend in `contract/session/`. */
export function lifecycleEventTypes(): string[] {
  return readJson<string[]>(path.join(CONTRACT_DIR, 'session', 'event-types.json'));
}

function readJson<T>(file: string): T {
  return JSON.parse(fs.readFileSync(file, 'utf8')) as T;
}

/** The scenario files of a folder, without `event-types.json`. */
export function scenarioFiles(folder: string): string[] {
  const dir = path.join(CONTRACT_DIR, folder);
  return fs.readdirSync(dir).filter((f) => f.endsWith('.json') && f !== 'event-types.json').sort();
}

class Source {
  private readonly lines: string[] = [];
  private readonly labels: { line: number; label: string }[] = [];
  private counter = 0;

  add(label: string, type: string, value: unknown): void {
    const literal = JSON.stringify(value, null, 2) ?? 'undefined';
    this.raw(label, `const _${this.counter++}: ${type} = ${literal};`);
  }

  raw(label: string, code: string): void {
    this.labels.push({ line: this.lines.length, label });
    this.lines.push(...code.split('\n'));
  }

  labelAt(line: number): string {
    let found = '?';
    for (const entry of this.labels) {
      if (entry.line <= line) found = entry.label;
      else break;
    }
    return found;
  }

  text(): string {
    return `export {};\n${this.lines.join('\n')}\n`;
  }

  /** Line offset of the generated code: the `export {}` line. */
  static readonly HEADER_LINES = 1;
}

function addScenario(source: Source, game: GameContract, file: string, lifecycle: Set<string>): void {
  const recorded = readJson<Recorded[]>(path.join(CONTRACT_DIR, game.folder, file));
  recorded.forEach((entry, index) => {
    const where = `${game.folder}/${file} #${index} (${entry.kind}`;
    if (entry.kind === 'message') {
      const envelope = entry.payload as Envelope;
      const eventType = envelope.eventType ?? '';
      source.add(`${where} ${eventType} envelope)`,
        '{ success: boolean; eventType: string; eventData: unknown; message: string | null; state: unknown }',
        envelope);
      if (envelope.state !== null && envelope.state !== undefined) {
        source.add(`${where} ${eventType} state)`, game.view, envelope.state);
      }
      if (envelope.success === false) {
        source.add(`${where} ${eventType} error eventData)`, EMPTY_DATA, envelope.eventData);
        return;
      }
      if (game.eventType && !lifecycle.has(eventType)) {
        source.add(`${where} eventType)`, game.eventType, eventType);
      }
      const dataType = game.eventData[eventType] ?? SHARED_DATA[eventType] ?? game.eventData['*'];
      if (!dataType) {
        throw new Error(`${where}): no eventData type for ${eventType} in the contract manifest`);
      }
      source.add(`${where} ${eventType} eventData)`, dataType, envelope.eventData);
    } else if (entry.kind === 'state') {
      source.add(`${where})`, game.view, entry.payload);
    } else {
      const type = game.rest?.[entry.kind];
      if (!type) throw new Error(`${where}): no type for the REST kind ${entry.kind} in the contract manifest`);
      source.add(`${where})`, type, entry.payload);
    }
  });
}

function addEventTypes(source: Source, game: GameContract, lifecycle: string[]): void {
  if (!game.eventType) return;
  const file = path.join(CONTRACT_DIR, game.folder, 'event-types.json');
  if (!fs.existsSync(file)) return;
  const server = readJson<string[]>(file);
  const known = [...new Set([...server, ...lifecycle])].map((s) => JSON.stringify(s)).join(' | ');
  source.raw(`${game.folder}/event-types.json (a server event type the frontend does not know)`,
    `const _types_${game.folder.replace(/\W/g, '_')}: ${game.eventType}[] = ${JSON.stringify(server)};`);
  const id = game.folder.replace(/\W/g, '_');
  source.raw(`${game.folder}/event-types.json (a frontend event type the server never sends)`,
    `const _extra_${id}: never = null as unknown as Exclude<${game.eventType}, ${known}>;`);
}

const OPTIONS: ts.CompilerOptions = {
  strict: true,
  noEmit: true,
  target: ts.ScriptTarget.ES2022,
  module: ts.ModuleKind.ESNext,
  moduleResolution: ts.ModuleResolutionKind.Bundler,
  skipLibCheck: true,
  types: [],
  lib: ['lib.es2022.d.ts', 'lib.dom.d.ts'],
};

/**
 * Type-checks every golden file of the given games; returns one line per problem (empty when the frontend types
 * match the wire). Identical problems are reported once, with the number of payloads that hit it.
 */
export function checkWireContract(games: GameContract[]): string[] {
  const lifecycleTypes = lifecycleEventTypes();
  const lifecycle = new Set(lifecycleTypes);
  const source = new Source();
  for (const game of games) {
    for (const file of scenarioFiles(game.folder)) addScenario(source, game, file, lifecycle);
    addEventTypes(source, game, lifecycleTypes);
  }
  const text = source.text();
  const host = ts.createCompilerHost(OPTIONS);
  const getSourceFile = host.getSourceFile.bind(host);
  host.getSourceFile = (fileName, language, ...rest) => (path.resolve(fileName) === VIRTUAL_FILE
    ? ts.createSourceFile(fileName, text, language)
    : getSourceFile(fileName, language, ...rest));
  const fileExists = host.fileExists.bind(host);
  host.fileExists = (fileName) => path.resolve(fileName) === VIRTUAL_FILE || fileExists(fileName);
  const fixtures = games.flatMap((g) => g.fixtures ?? []).map((f) => path.join(SRC_DIR, f));
  const program = ts.createProgram({ rootNames: [VIRTUAL_FILE, ...fixtures], options: OPTIONS, host });

  const problems = new Map<string, { first: string; count: number }>();
  const report = (key: string, first: string) => {
    const known = problems.get(key);
    if (known) known.count += 1;
    else problems.set(key, { first, count: 1 });
  };
  const virtual = program.getSourceFile(VIRTUAL_FILE)!;
  for (const d of program.getSemanticDiagnostics(virtual)) {
    const message = ts.flattenDiagnosticMessageText(d.messageText, ' ');
    const line = d.start === undefined ? 0 : virtual.getLineAndCharacterOfPosition(d.start).line;
    const label = source.labelAt(line - Source.HEADER_LINES);
    const code = text.split('\n')[line]?.trim() ?? '';
    report(`${label.replace(/ #\d+ /, ' ')}: ${message}`, `${label}: ${message} [at: ${code}]`);
  }
  for (const file of fixtures) {
    const sf = program.getSourceFile(file);
    if (!sf) {
      report(`missing fixture ${file}`, `fixture ${file} not found`);
      continue;
    }
    for (const d of program.getSemanticDiagnostics(sf)) {
      const where = d.start === undefined ? '' : `:${sf.getLineAndCharacterOfPosition(d.start).line + 1}`;
      const message = ts.flattenDiagnosticMessageText(d.messageText, ' ');
      report(`${path.relative(SRC_DIR, file)}${where}: ${message}`, `${path.relative(SRC_DIR, file)}${where}: ${message}`);
    }
  }
  return [...problems.values()].map(({ first, count }) => (count > 1 ? `${first} (x${count})` : first));
}
