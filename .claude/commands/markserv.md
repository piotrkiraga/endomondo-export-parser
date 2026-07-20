---
name: "markserv"
description: "Start (or restart) markserv to preview README/OpenSpec Markdown at localhost:8642"
allowed-tools: Bash(npx markserv:*), Bash(rm:*), Bash(curl:*), Bash(taskkill:*)
category: Tooling
tags: [docs, markdown, preview]
---

Start `markserv` so the user can browse this repo's Markdown (README, `openspec/` proposals and specs) GitHub-style at http://localhost:8642.

**Known failure mode:** markserv dies with `EBUSY ... watch bash.exe.stackdump` if Git Bash has crashed and left that file in the repo root. It has no ignore flag, so the fix is always: delete the stackdump file, *then* restart — restarting without deleting it fails again immediately.

Steps:

1. From the project root, delete any leftover crash file: `rm -f bash.exe.stackdump`
2. Check whether something is already listening on 8642 (`curl -s -o /dev/null -w "%{http_code}" http://localhost:8642/` — a `200` means it's already running; no need to restart it, just tell the user the URL). If it's not responding, continue.
3. Start it in the background, output redirected to a fixed log path so the user can tail it themselves:
   `nohup npx markserv -p 8642 . > "C:\Users\<user>\AppData\Local\Temp\endomondo-markserv.log" 2>&1 &` then `disown` (substitute the real Windows username; use `$USERPROFILE`/`whoami` if unsure, or reuse whatever path this project's app log already uses if one exists).
4. Confirm it actually came up with a `curl` check to http://localhost:8642/, and tell the user:
   - the browse URL (http://localhost:8642)
   - the log file path
5. If the user reports it crashed again later, re-run from step 1 (delete the stackdump first — don't just restart).

Don't scope markserv to a subdirectory unless the user asks; they've previously wanted the project root so the README is reachable too.
