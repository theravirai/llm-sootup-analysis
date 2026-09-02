# LLM-Augmented Java Program Analysis via SootUp

[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Maven 3.9+](https://img.shields.io/badge/Maven-3.9%2B-C71A36.svg)](https://maven.apache.org/)
[![SootUp](https://img.shields.io/badge/SootUp-3.0.2--SNAPSHOT-blue.svg)](https://github.com/soot-oss/SootUp)

An empirical research project exploring the integration of the **SootUp** static analysis framework to assist Large Language Models (LLMs) with Java program analysis and complex bug localization.

---

## 1. Overview

This repository contains the standalone **SootUp Analyzer** tool alongside experimental prompts, evaluation reports, and empirical evidence across real-world benchmark tasks.

The static analyzer extracts low-level structural facts directly from compiled Java bytecode:
* **Class Hierarchy (`hierarchy`):** Resolves superclasses, subclasses, and implemented interfaces.
* **Call Graph Construction (`callgraph`):** Builds interprocedural call graphs using Class Hierarchy Analysis (CHA) or Rapid Type Analysis (RTA).
* **Method Inspection (`inspect-method`):** Disassembles method bodies into Jimple Intermediate Representation (IR).
* **Allocation Site Search (`find-allocations`):** Scans bytecode for object instantiations (`JNewExpr`).
* **Invocation Site Search (`find-invocations`):** Finds caller locations of specific methods.

---

## 2. Repository Structure

```text
llm-sootup-analysis/
├── sootup_analyzer/                       <-- Standalone SootUp 3.x CLI analyzer
│   ├── pom.xml
│   └── src/main/java/SootUpAnalyzer.java
├── docs/                                  <-- User guide and case study reports
│   ├── 01_SootUp_Analyzer_User_Guide.md   <-- CLI reference and command guide
│   ├── 02_Case_Study_Gson_2158.md         <-- Case study report: Google Gson #2158
│   └── 03_Case_Study_Druid_14136.md       <-- Case study report: Apache Druid #14136
├── prompts/                               <-- Experimental prompts for replication
│   ├── gson_2158/
│   └── druid_14136/
└── screenshots/                           <-- Empirical execution evidence
    ├── gson_2158/
    └── druid_14136/
```

---

## 3. Quickstart: Building & Running

### Prerequisites
* **Java 17+**
* **Apache Maven 3.9+**
* Compiled target project bytecode (`mvn clean compile` in the target repository)

### Build the Analyzer
```bash
cd sootup_analyzer
mvn clean compile
```

### Run an Analysis Query
```bash
mvn exec:java -Dexec.mainClass="SootUpAnalyzer" -Dexec.args="<path-to-target-classes> hierarchy <fully-qualified-class>" -q
```

---

## 4. Documentation Links

* **User Guide & CLI Reference:** [`docs/01_SootUp_Analyzer_User_Guide.md`](docs/01_SootUp_Analyzer_User_Guide.md)
* **Case Study 1 (Google Gson):** [`docs/02_Case_Study_Gson_2158.md`](docs/02_Case_Study_Gson_2158.md)
* **Case Study 2 (Apache Druid):** [`docs/03_Case_Study_Druid_14136.md`](docs/03_Case_Study_Druid_14136.md)
