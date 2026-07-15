# Civilization V — Useful Commands

Run these from `CivilizationGame` (the folder with `pom.xml`).

## Build + Run (do this every time after editing code)
```bash
mvn -q -o compile exec:java
```

## Just compile (check for errors without running)
```bash
mvn -q -o compile
```

## Just run (only works if already compiled, i.e. target/classes exists)
```bash
mvn -q -o exec:java
```

## Clean rebuild (if things get weird / stale classes)
```bash
mvn -q -o clean compile exec:java
```

## Run with full error output (for debugging crashes)
```bash
mvn -q -o compile exec:java -e
```

---
Note: `exec:java` alone fails with `ClassNotFoundException: sut.civilization.Civilization`
if the project hasn't been compiled yet (no `target/classes` folder). Always compile first.
