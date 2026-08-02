---
name: "markserv"
description: "Start (or restart) markserv to preview README/OpenSpec Markdown at localhost:8642"
allowed-tools: Bash(npx markserv:*), Bash(rm:*), Bash(curl:*), Bash(taskkill:*)
category: Tooling
tags: [docs, markdown, preview]
---

Start `markserv` so the user can browse this repo's OpenSpec proposals and specs GitHub-style at http://localhost:8642.

**Scoped to `./openspec`, not the project root.** markserv has no ignore flag and no `.gitignore` awareness (confirmed by reading its source: it recursively watches whatever directory it's given via the `livereload` package, with no filtering option). Pointed at the project root, it walks `data/` (hundreds of files, the personal photo/export archive) and `target/` (Maven build output) — thousands of irrelevant files — and can take 60+ seconds just to start responding. Scoped to `./openspec` (~80 files), it starts in a couple of seconds. This means root `README.md` is not browsable through markserv — open it directly in an editor or on GitHub instead.

**Known failure mode:** markserv dies with `EBUSY ... watch bash.exe.stackdump` if Git Bash has crashed and left that file in the repo root. Since there's no ignore flag, the fix is always: delete the stackdump file, *then* restart — restarting without deleting it fails again immediately.

Steps:

1. From the project root, delete any leftover crash file: `rm -f bash.exe.stackdump`
2. Check whether something is already listening on 8642 (`curl -s -o /dev/null -w "%{http_code}" http://localhost:8642/` — a `200` means it's already running; no need to restart it, just tell the user the URL). If it's not responding, continue.
3. Start it in the background, scoped to `./openspec`, output redirected to a fixed log path so the user can tail it themselves:
   `nohup npx markserv -p 8642 --browser false ./openspec > "C:\Users\<user>\AppData\Local\Temp\endomondo-markserv.log" 2>&1 &` then `disown` (substitute the real Windows username; use `$USERPROFILE`/`whoami` if unsure, or reuse whatever path this project's app log already uses if one exists). The `--browser false` flag is required — markserv defaults to auto-opening a browser tab on start, which is what the next step's note is guarding against. **Must be the long form `--browser`, not `-b`**: this installed version's CLI has a bug where `-b` is actually aliased to `livereloadport`, not `browser` (confirmed by reading `lib/cli-defs.js` vs. the help text, which incorrectly documents `-b` as the browser flag) — `-b false` silently does nothing to browser launch.
4. Confirm it actually came up with a `curl` check to http://localhost:8642/, and tell the user:
   - the browse URL (http://localhost:8642)
   - the log file path
   - that README.md isn't served this way anymore (scoped to openspec/ for startup speed)
   **Do not open a browser tab for it.** Just report the URL in chat and let the user open it themselves.
5. If the user reports it crashed again later, re-run from step 1 (delete the stackdump first — don't just restart).
