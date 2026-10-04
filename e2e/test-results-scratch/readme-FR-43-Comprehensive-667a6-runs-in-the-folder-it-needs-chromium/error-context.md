# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: readme.spec.ts >> FR-43 Comprehensive README >> every code block runs in zsh and bash (exit 0) and every command runs in the folder it needs
- Location: tests/readme.spec.ts:258:3

# Error details

```
Error: expect(received).toBe(expected) // Object.is equality

- Expected  - 1
+ Received  + 2

- zsh block 2 exit 0: 
+ zsh block 2 exit 2: zsh:1: bad math expression: operator expected at `backend &&...'
+
```

# Test source

```ts
  200 |       expect(text, part).toContain(part);
  201 |     }
  202 |     expect(text).toMatch(/loopback/i);
  203 |     expect(text).not.toContain('docker-compose ');
  204 |     expect(text).not.toContain('.env');
  205 |     expect(text).not.toContain('export ');
  206 |     expect(text).not.toContain('psql');
  207 |   });
  208 | 
  209 |   test('"API key" appears only in a sentence that says none is needed', () => {
  210 |     const sentences = readme().split(/(?<=[.!?])\s+|\n+/);
  211 |     for (const s of sentences.filter((x) => /API key/i.test(x))) {
  212 |       expect(s, 'a sentence with "API key"').toMatch(/\b(no|not|never|none|without|neither|nor)\b|n't/i);
  213 |     }
  214 |   });
  215 | 
  216 |   test('every command in a code block is copy-paste runnable: no placeholders', () => {
  217 |     const blocks = [...readme().matchAll(/```[a-z]*\n([\s\S]*?)```/g)].map((m) => m[1]);
  218 |     expect(blocks.length).toBeGreaterThan(0);
  219 |     for (const b of blocks) {
  220 |       expect(b).not.toMatch(/<[a-z][^>]*>|YOUR_|\.\.\./i);
  221 |       expect(b).not.toContain('docker-compose ');
  222 |     }
  223 |   });
  224 | 
  225 |   // Every code block is one copy-paste unit that starts in the app folder ("From this folder").
  226 |   test('every cd target and every compose -f file exists when the commands of a code block run one after another', () => {
  227 |     const appDir = fileURLToPath(APP);
  228 |     const blocks = [...readme().matchAll(/```[a-z]*\n([\s\S]*?)```/g)].map((m) => m[1]);
  229 |     const problems = blocks.flatMap((b, i) =>
  230 |       walkBlock(b, appDir, (p) => fs.existsSync(p)).map((x) => `block ${i + 1}: ${x}`),
  231 |     );
  232 |     expect(problems).toEqual([]);
  233 |     // the walk is not vacuous: the README does use cd and -f
  234 |     expect(blocks.join('\n')).toMatch(/\bcd \w+/);
  235 |     expect(blocks.join('\n')).toMatch(/\s-f\s+docker-compose\.yml/);
  236 |   });
  237 | 
  238 |   test('the block walker follows top-level cd, ignores subshell cd and reports missing targets and files', () => {
  239 |     const files = new Set(['/app', '/app/e2e', '/app/backend', '/app/docker-compose.yml', '/app/docker-compose.e2e.yml']);
  240 |     const ex = (p: string): boolean => files.has(p);
  241 |     // cd e2e persists, so the next -f files are looked up in /app/e2e
  242 |     expect(walkBlock('cd e2e && npm ci\ndocker compose -f docker-compose.yml up', '/app', ex)).toHaveLength(1);
  243 |     // a subshell cd does not persist
  244 |     expect(walkBlock('(cd e2e && npm ci)\ndocker compose -f docker-compose.yml -f docker-compose.e2e.yml up', '/app', ex)).toEqual([]);
  245 |     // cd .. brings it back
  246 |     expect(walkBlock('cd e2e; cd ..\ndocker compose -f docker-compose.yml down', '/app', ex)).toEqual([]);
  247 |     // missing cd target
  248 |     expect(walkBlock('cd nowhere && ls', '/app', ex)).toHaveLength(1);
  249 |     // a second cd from the first target
  250 |     expect(walkBlock('cd backend && ./gradlew test\ncd e2e', '/app', ex)).toHaveLength(1);
  251 |     // "((" is arithmetic in bash and zsh
  252 |     expect(walkBlock('((cd e2e && npm ci))', '/app', ex).length).toBeGreaterThan(0);
  253 |     expect(walkBlock('(cd e2e && npm ci)', '/app', ex)).toEqual([]);
  254 |   });
  255 | 
  256 |   // Runs every README code block in a real zsh and a real bash, inside a temp copy of the app layout with stub
  257 |   // docker/npm/npx/gradlew commands that log their working directory and arguments.
  258 |   test('every code block runs in zsh and bash (exit 0) and every command runs in the folder it needs', () => {
  259 |     const appDir = fileURLToPath(APP);
  260 |     const blocks = [...readme().matchAll(/```([a-z]*)\n([\s\S]*?)```/g)]
  261 |       .filter((m) => ['', 'bash', 'sh', 'shell', 'zsh', 'console'].includes(m[1]))
  262 |       .map((m) => m[2]);
  263 |     expect(blocks.length).toBeGreaterThan(0);
  264 | 
  265 |     const root = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'oracul-readme-')));
  266 |     try {
  267 |       const app = path.join(root, 'app');
  268 |       const bin = path.join(root, 'bin');
  269 |       const log = path.join(root, 'commands.log');
  270 |       for (const d of ['backend', 'frontend', 'e2e']) fs.mkdirSync(path.join(app, d), { recursive: true });
  271 |       fs.mkdirSync(bin);
  272 |       for (const f of ['docker-compose.yml', 'docker-compose.e2e.yml']) fs.copyFileSync(path.join(appDir, f), path.join(app, f));
  273 |       const stub = (name: string, extra = ''): string =>
  274 |         `#!/bin/sh\n${extra}printf '%s|%s|%s\\n' "${name}" "$(pwd -P)" "$*" >> "${log}"\n`;
  275 |       // docker compose -f <file> must find its files relative to the current folder, like the real one
  276 |       const dockerCheck =
  277 |         'prev=""\nfor a in "$@"; do\n  if [ "$prev" = "-f" ] || [ "$prev" = "--file" ]; then [ -f "$a" ] || { echo "docker: no such file $a" >&2; exit 14; }; fi\n  prev="$a"\ndone\n';
  278 |       fs.writeFileSync(path.join(bin, 'docker'), stub('docker', dockerCheck), { mode: 0o755 });
  279 |       fs.writeFileSync(path.join(bin, 'npm'), stub('npm'), { mode: 0o755 });
  280 |       fs.writeFileSync(path.join(bin, 'npx'), stub('npx'), { mode: 0o755 });
  281 |       fs.writeFileSync(path.join(app, 'backend', 'gradlew'), stub('gradlew'), { mode: 0o755 });
  282 | 
  283 |       const expectedDir = (block: string, tool: string, args: string): string => {
  284 |         if (tool === 'docker') return app;
  285 |         if (tool === 'gradlew') return path.join(app, 'backend');
  286 |         if (tool === 'npx') return path.join(app, 'e2e');
  287 |         if (args.startsWith('run test:ci')) return path.join(app, 'frontend');
  288 |         return path.join(app, /\bcd frontend\b/.test(block) ? 'frontend' : 'e2e'); // npm ci
  289 |       };
  290 | 
  291 |       for (const shell of ['zsh', 'bash']) {
  292 |         blocks.forEach((block, i) => {
  293 |           fs.writeFileSync(log, '');
  294 |           const r = spawnSync(shell, ['-e', '-c', block], {
  295 |             cwd: app,
  296 |             encoding: 'utf8',
  297 |             env: { ...process.env, PATH: `${bin}${path.delimiter}${process.env.PATH ?? ''}` },
  298 |           });
  299 |           const label = `${shell} block ${i + 1}`;
> 300 |           expect(`${label} exit ${r.status}: ${r.stderr}`).toBe(`${label} exit 0: `);
      |                                                            ^ Error: expect(received).toBe(expected) // Object.is equality
  301 |           const lines = fs.readFileSync(log, 'utf8').split('\n').filter(Boolean);
  302 |           // each stub-able command of the block ran: one log line per command word at the start of a segment
  303 |           const commands = block
  304 |             .split('\n')
  305 |             .flatMap((l) => l.split(/&&|\|\||;/))
  306 |             .map((seg) => seg.replace(/[()]/g, '').trim())
  307 |             .filter((seg) => /^(docker|npm|npx|\.\/gradlew)\b/.test(seg));
  308 |           expect(lines, `${label} ran ${commands.length} commands`).toHaveLength(commands.length);
  309 |           for (const line of lines) {
  310 |             const [tool, cwd, args] = line.split('|');
  311 |             expect(cwd, `${label}: ${tool} ${args}`).toBe(expectedDir(block, tool, args));
  312 |           }
  313 |         });
  314 |       }
  315 |     } finally {
  316 |       fs.rmSync(root, { recursive: true, force: true });
  317 |     }
  318 |   });
  319 | });
  320 | 
```