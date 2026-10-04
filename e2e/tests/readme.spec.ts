import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';

// @trace FR-43
// Node `fs` text checks of apps/oracul-engine/README.md (phase-02 run-modes.md "FR-43 — Comprehensive README").

const APP = new URL('../../', import.meta.url);
const readme = (): string => fs.readFileSync(new URL('README.md', APP), 'utf8');

const SECTIONS = ['Prerequisites', 'Run for real', 'Sign in step by step', 'Run the tests', 'Troubleshooting', 'Stop and reset'];

/** The text of a `## <title>` section up to the next `## ` heading. */
function section(text: string, title: string): string {
  const lines = text.split('\n');
  const start = lines.findIndex((l) => l.trim() === `## ${title}`);
  if (start < 0) return '';
  let end = lines.length;
  for (let i = start + 1; i < lines.length; i++) {
    if (/^## /.test(lines[i])) {
      end = i;
      break;
    }
  }
  return lines.slice(start + 1, end).join('\n');
}


/**
 * Walks the lines of one code block as a shell would, starting in `start`. A top-level `cd` (also inside `&&`, `||`,
 * `;` chains) moves the working directory for every later command; a `( ... )` subshell does not. Returns one problem
 * text per `cd` target or `docker compose -f/--file` file that does not exist relative to the directory it runs in.
 */
export function walkBlock(block: string, start: string, exists: (p: string) => boolean): string[] {
  const problems: string[] = [];
  const cwds = [start]; // the top is the current directory; "(" pushes a copy, ")" pops it
  const run = (segment: string, lineNo: number): void => {
    const tokens = segment.trim().split(/\s+/).filter(Boolean).map((t) => t.replace(/^['"]|['"]$/g, ''));
    if (tokens.length === 0) return;
    const cwd = cwds[cwds.length - 1];
    if (tokens[0] === 'cd') {
      const target = tokens[1];
      const dir = target === undefined ? '' : path.resolve(cwd, target);
      if (target === undefined || !exists(dir)) {
        problems.push(`line ${lineNo}: "${segment.trim()}" - cd target ${target ?? '(home)'} does not exist from ${cwd}`);
      } else {
        cwds[cwds.length - 1] = dir;
      }
      return;
    }
    if (tokens[0] === 'docker' && tokens[1] === 'compose') {
      tokens.forEach((t, i) => {
        const file = t === '-f' || t === '--file' ? tokens[i + 1] : /^--file=/.test(t) ? t.slice(7) : undefined;
        if (file !== undefined && !exists(path.resolve(cwd, file))) {
          problems.push(`line ${lineNo}: "${segment.trim()}" - compose file ${file} does not exist from ${cwd}`);
        }
      });
    }
  };
  block.split('\n').forEach((line, idx) => {
    let current = '';
    const flush = (): void => {
      run(current, idx + 1);
      current = '';
    };
    for (let i = 0; i < line.length; i++) {
      const c = line[i];
      const two = line.slice(i, i + 2);
      if (two === '&&' || two === '||') {
        flush();
        i++;
      } else if (c === ';') {
        flush();
      } else if (c === '(') {
        // "((" or "$((" is arithmetic evaluation in bash and zsh, not a nested subshell: the shell rejects the command
        if (line[i + 1] === '(') {
          problems.push(`line ${idx + 1}: "${line.trim()}" - "((" is an arithmetic expression in bash and zsh, not a subshell`);
        }
        flush();
        cwds.push(cwds[cwds.length - 1]);
      } else if (c === ')') {
        flush();
        if (cwds.length > 1) cwds.pop();
      } else {
        current += c;
      }
    }
    flush();
  });
  return problems;
}

test.describe('FR-43 Comprehensive README', () => {
  test('README.md exists and starts with "# ORACUL"', () => {
    expect(fs.existsSync(new URL('README.md', APP))).toBe(true);
    expect(readme().split('\n')[0].trim()).toBe('# ORACUL');
  });

  test('the first six "##" headings are the six sections, in order', () => {
    const headings = readme().split('\n').filter((l) => /^## /.test(l)).map((l) => l.trim());
    expect(headings.slice(0, 6)).toEqual(SECTIONS.map((s) => `## ${s}`));
  });

  test('Prerequisites names the account, Docker Desktop, the ports, the browser and the test tools', () => {
    const text = section(readme(), 'Prerequisites');
    expect(text).toMatch(/ChatGPT Plus or Pro/);
    expect(text).toMatch(/Docker Desktop/);
    expect(text).toMatch(/Compose v2/);
    expect(text).toContain('4200');
    expect(text).toContain('8080');
    expect(text).toMatch(/same computer/i);
    expect(text).toMatch(/JDK 17/);
    expect(text).toMatch(/Node\.js 22/);
  });

  test('Run for real starts the stack with the plain build-and-up command, no -f flag and no e2e compose file', () => {
    const text = section(readme(), 'Run for real');
    expect(text).toContain('docker compose up -d --build');
    expect(text).toContain('http://localhost:4200');
    expect(text).not.toMatch(/(^|\s)-f(\s|$)/);
    expect(text).not.toContain('docker-compose.e2e.yml');
  });

  test('Sign in step by step walks the whole flow', () => {
    const text = section(readme(), 'Sign in step by step');
    for (const part of ['Continue with ChatGPT', 'Connect your ORACUL to ChatGPT', 'ChatGPT connected', 'GENERATE THE FUTURE', 'STOP']) {
      expect(text, part).toContain(part);
    }
    expect(text).toMatch(/Google/);
    expect(text).toMatch(/memory/i);
    expect(text).toMatch(/restart/i);
  });

  test('Run the tests has the backend, frontend and E2E commands and the E2E database note', () => {
    const text = section(readme(), 'Run the tests');
    for (const cmd of [
      'cd backend && ./gradlew test',
      'cd frontend && npm ci && npm run test:ci',
      'docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build',
      'cd e2e && npm ci && npx playwright install chromium && npx playwright test',
      'docker compose -f docker-compose.yml -f docker-compose.e2e.yml down',
    ]) {
      expect(text, cmd).toContain(cmd);
    }
    expect(text).toContain('db-e2e-data');
    // E2E against the plain stack fails at the first call to the stub
    expect(text).toMatch(/fail/i);
    expect(text).toMatch(/plain/i);
    expect(text).toMatch(/stub/i);
  });

  test('Troubleshooting has the seven entries in order, each with a Cause: and a Fix: line', () => {
    const text = section(readme(), 'Troubleshooting');
    const parts = text.split(/^### /m).slice(1);
    expect(parts).toHaveLength(7);
    const titles: RegExp[] = [
      /invalid_authorize_request/,
      /Your ChatGPT plan is not eligible for ORACUL/,
      /ChatGPT usage limit reached/,
      /port/i,
      /disk/i,
      /ChatGPT registration is no longer valid/,
      /database/i,
    ];
    parts.forEach((entry, i) => {
      const [heading, ...body] = entry.split('\n');
      expect(heading, `entry ${i + 1}`).toMatch(titles[i]);
      const lines = body.map((l) => l.trim());
      expect(lines.some((l) => l.startsWith('Cause:')), `entry ${i + 1} "${heading}" has a Cause: line`).toBe(true);
      expect(lines.some((l) => l.startsWith('Fix:')), `entry ${i + 1} "${heading}" has a Fix: line`).toBe(true);
    });
    expect(parts[3]).toContain('4200');
    expect(parts[3]).toContain('8080');
    expect(parts[5]).toContain('Reset ChatGPT connection');
    expect(parts[6]).toContain('docker compose down -v');
  });

  test('Stop and reset has the three ways', () => {
    const text = section(readme(), 'Stop and reset');
    expect(text).toContain('docker compose down');
    expect(text).toContain('docker compose down -v');
    expect(text).toContain('Reset ChatGPT connection');
  });

  test('the README names the commands and URLs of the contract and uses only Compose v2', () => {
    const text = readme();
    for (const part of [
      'docker compose up -d --build',
      'docker compose down -v',
      './gradlew test',
      'npm run test:ci',
      '-f docker-compose.yml -f docker-compose.e2e.yml',
      'http://127.0.0.1:4200/callback',
      'http://localhost:4200',
      'Connect your ORACUL to ChatGPT',
    ]) {
      expect(text, part).toContain(part);
    }
    expect(text).toMatch(/loopback/i);
    expect(text).not.toContain('docker-compose ');
    expect(text).not.toContain('.env');
    expect(text).not.toContain('export ');
    expect(text).not.toContain('psql');
  });

  test('"API key" appears only in a sentence that says none is needed', () => {
    const sentences = readme().split(/(?<=[.!?])\s+|\n+/);
    for (const s of sentences.filter((x) => /API key/i.test(x))) {
      expect(s, 'a sentence with "API key"').toMatch(/\b(no|not|never|none|without|neither|nor)\b|n't/i);
    }
  });

  test('every command in a code block is copy-paste runnable: no placeholders', () => {
    const blocks = [...readme().matchAll(/```[a-z]*\n([\s\S]*?)```/g)].map((m) => m[1]);
    expect(blocks.length).toBeGreaterThan(0);
    for (const b of blocks) {
      expect(b).not.toMatch(/<[a-z][^>]*>|YOUR_|\.\.\./i);
      expect(b).not.toContain('docker-compose ');
    }
  });

  // Every code block is one copy-paste unit that starts in the app folder ("From this folder").
  test('every cd target and every compose -f file exists when the commands of a code block run one after another', () => {
    const appDir = fileURLToPath(APP);
    const blocks = [...readme().matchAll(/```[a-z]*\n([\s\S]*?)```/g)].map((m) => m[1]);
    const problems = blocks.flatMap((b, i) =>
      walkBlock(b, appDir, (p) => fs.existsSync(p)).map((x) => `block ${i + 1}: ${x}`),
    );
    expect(problems).toEqual([]);
    // the walk is not vacuous: the README does use cd and -f
    expect(blocks.join('\n')).toMatch(/\bcd \w+/);
    expect(blocks.join('\n')).toMatch(/\s-f\s+docker-compose\.yml/);
  });

  test('the block walker follows top-level cd, ignores subshell cd and reports missing targets and files', () => {
    const files = new Set(['/app', '/app/e2e', '/app/backend', '/app/docker-compose.yml', '/app/docker-compose.e2e.yml']);
    const ex = (p: string): boolean => files.has(p);
    // cd e2e persists, so the next -f files are looked up in /app/e2e
    expect(walkBlock('cd e2e && npm ci\ndocker compose -f docker-compose.yml up', '/app', ex)).toHaveLength(1);
    // a subshell cd does not persist
    expect(walkBlock('(cd e2e && npm ci)\ndocker compose -f docker-compose.yml -f docker-compose.e2e.yml up', '/app', ex)).toEqual([]);
    // cd .. brings it back
    expect(walkBlock('cd e2e; cd ..\ndocker compose -f docker-compose.yml down', '/app', ex)).toEqual([]);
    // missing cd target
    expect(walkBlock('cd nowhere && ls', '/app', ex)).toHaveLength(1);
    // a second cd from the first target
    expect(walkBlock('cd backend && ./gradlew test\ncd e2e', '/app', ex)).toHaveLength(1);
    // "((" is arithmetic in bash and zsh
    expect(walkBlock('((cd e2e && npm ci))', '/app', ex).length).toBeGreaterThan(0);
    expect(walkBlock('(cd e2e && npm ci)', '/app', ex)).toEqual([]);
  });

  // Runs every README code block in a real zsh and a real bash, inside a temp copy of the app layout with stub
  // docker/npm/npx/gradlew commands that log their working directory and arguments.
  test('every code block runs in zsh and bash (exit 0) and every command runs in the folder it needs', () => {
    const appDir = fileURLToPath(APP);
    const blocks = [...readme().matchAll(/```([a-z]*)\n([\s\S]*?)```/g)]
      .filter((m) => ['', 'bash', 'sh', 'shell', 'zsh', 'console'].includes(m[1]))
      .map((m) => m[2]);
    expect(blocks.length).toBeGreaterThan(0);

    const root = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'oracul-readme-')));
    try {
      const app = path.join(root, 'app');
      const bin = path.join(root, 'bin');
      const log = path.join(root, 'commands.log');
      for (const d of ['backend', 'frontend', 'e2e']) fs.mkdirSync(path.join(app, d), { recursive: true });
      fs.mkdirSync(bin);
      for (const f of ['docker-compose.yml', 'docker-compose.e2e.yml']) fs.copyFileSync(path.join(appDir, f), path.join(app, f));
      const stub = (name: string, extra = ''): string =>
        `#!/bin/sh\n${extra}printf '%s|%s|%s\\n' "${name}" "$(pwd -P)" "$*" >> "${log}"\n`;
      // docker compose -f <file> must find its files relative to the current folder, like the real one
      const dockerCheck =
        'prev=""\nfor a in "$@"; do\n  if [ "$prev" = "-f" ] || [ "$prev" = "--file" ]; then [ -f "$a" ] || { echo "docker: no such file $a" >&2; exit 14; }; fi\n  prev="$a"\ndone\n';
      fs.writeFileSync(path.join(bin, 'docker'), stub('docker', dockerCheck), { mode: 0o755 });
      fs.writeFileSync(path.join(bin, 'npm'), stub('npm'), { mode: 0o755 });
      fs.writeFileSync(path.join(bin, 'npx'), stub('npx'), { mode: 0o755 });
      fs.writeFileSync(path.join(app, 'backend', 'gradlew'), stub('gradlew'), { mode: 0o755 });

      const expectedDir = (block: string, tool: string, args: string): string => {
        if (tool === 'docker') return app;
        if (tool === 'gradlew') return path.join(app, 'backend');
        if (tool === 'npx') return path.join(app, 'e2e');
        if (args.startsWith('run test:ci')) return path.join(app, 'frontend');
        return path.join(app, /\bcd frontend\b/.test(block) ? 'frontend' : 'e2e'); // npm ci
      };

      for (const shell of ['zsh', 'bash']) {
        blocks.forEach((block, i) => {
          fs.writeFileSync(log, '');
          const r = spawnSync(shell, ['-e', '-c', block], {
            cwd: app,
            encoding: 'utf8',
            env: { ...process.env, PATH: `${bin}${path.delimiter}${process.env.PATH ?? ''}` },
          });
          const label = `${shell} block ${i + 1}`;
          expect(`${label} exit ${r.status}: ${r.stderr}`).toBe(`${label} exit 0: `);
          const lines = fs.readFileSync(log, 'utf8').split('\n').filter(Boolean);
          // each stub-able command of the block ran: one log line per command word at the start of a segment
          const commands = block
            .split('\n')
            .flatMap((l) => l.split(/&&|\|\||;/))
            .map((seg) => seg.replace(/[()]/g, '').trim())
            .filter((seg) => /^(docker|npm|npx|\.\/gradlew)\b/.test(seg));
          expect(lines, `${label} ran ${commands.length} commands`).toHaveLength(commands.length);
          for (const line of lines) {
            const [tool, cwd, args] = line.split('|');
            expect(cwd, `${label}: ${tool} ${args}`).toBe(expectedDir(block, tool, args));
          }
        });
      }
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });
});
