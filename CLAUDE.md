# Git / PR / OpenSpec workflow

`develop` and `master` both require a PR with a green `build` CI check; no direct pushes, squash-merge only. One branch per OpenSpec change (or otherwise-scoped unit of work), named after the change — unrelated work never rides along on the same branch, since a squash merge collapses it into one commit and it becomes invisible in history.

**Fold `openspec archive` into the same branch/PR as the implementation — never a separate follow-up branch.** `openspec archive <name>` only touches files in the repo (moves the change folder under `openspec/changes/archive/`, syncs its delta specs into `openspec/specs/`); it has no dependency on the implementation already being merged. Sequence on one branch:

1. Implement the change, verify it (tests green, live-checked where applicable)
2. `openspec archive <name>` (non-interactive: `echo y | openspec archive <name>`, to answer its "Proceed with spec updates?" prompt)
3. Commit the implementation and the archive (one commit or several, same branch)
4. Push, open one PR, wait for green, merge once

This assumes changes are worked strictly sequentially — archiving pre-merge isn't safe if another in-flight branch is concurrently editing the same `openspec/specs/` file.
