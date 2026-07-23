# Contributing

## Prerequisites

- JDK 17+ (compile `release=17`)
- Maven Wrapper (`./mvnw`) — do not require a global Maven install

## Development

```bash
git clone https://github.com/acefun29/jswarm.git
cd jswarm
./mvnw -B verify
```

- Keep Java packages under `com.jswarm.*`.
- Maven coordinates use `com.github.acefun29.jswarm` (JitPack era). Do not reintroduce `com.jswarm` as a Maven `groupId`.
- Do not add `spring-boot-maven-plugin` repackage to library modules; only example modules may produce runnable fat jars.
- Prefer file-top one-line Chinese responsibility comments; avoid inline noise in class/method bodies (project convention).

## Pull Requests

- Run `./mvnw -B verify` locally.
- Keep commits focused; use bracket tags in messages, e.g. `[Core] …`, `[Build] …`.
- Do not commit secrets, local `doc/`, or `AGENTS.md`.
