# Support

## Supported matrix

| Item | Status |
|------|--------|
| JDK | 17+ (`release=17`) |
| LangChain4j adapter | 1.15.1 |
| Spring AI adapter | 2.0.0 + Spring Boot 4.0.7 |
| Publish channel | JitPack (`com.github.acefun29.jswarm`) |

See `adr/[ADR]module-boundaries-and-public-api.md` for the public capability table. Do not treat unimplemented APIs as stable.

## Not supported (no commitment)

- JPMS `module-info`
- Spring AOT / native image
- Full real LLM provider matrix in CI
- Maven Central / GPG / GitHub Packages (deferred)
- Cryptographic signing of JitPack consumer jars

## Getting help

- Issues: https://github.com/acefun29/jswarm/issues
- Discussions / questions: open an issue with the `question` label
