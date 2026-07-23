# Releasing

Canonical repository: https://github.com/acefun29/jswarm

## Coordinates

| Field | Value |
|-------|-------|
| groupId | `com.github.acefun29.jswarm` |
| version / git tag | same string, e.g. `1.0.0` (**no** `v` prefix) |

## Checklist

1. Ensure `origin` points at `https://github.com/acefun29/jswarm.git` (not only Gitee).
2. Set `<revision>` in the root POM to the release version.
3. `./mvnw -B verify`
4. Push `main`, then `git tag -a 1.0.0 -m "1.0.0"` and `git push origin 1.0.0`.
5. Create a GitHub Release for that tag; attach SBOM + sha256 for Release assets when available.
6. Trigger / wait for https://jitpack.io/#acefun29/jswarm Look up of the tag.
7. Consumer smoke: empty project with only `jitpack.io`, resolve `jswarm-core` and one adapter (transitive deps must resolve).

## Rollback

- Do not delete published tags or Releases.
- Ship a patch tag (e.g. `1.0.1`) with fixes.

## Supply chain honesty

SBOM / sha256 / attestations on GitHub Release assets do **not** cryptographically cover jars downloaded from JitPack.
