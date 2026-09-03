# How to Generate Condition A & Condition B Prompts

This guide documents the exact prompts/instructions used to generate the evaluation prompts for **Condition A (Baseline)** and **Condition B (SootUp-Augmented)** across benchmark tasks.

---

## 1. Generating Condition A (Baseline - Source Code Only)

To generate a Condition A prompt for any task, the instruction given to the AI assistant is:

```text
Generate a Condition A prompt for task `<TASK_ID>` (e.g., `google__gson-2158`) in repository `<REPO_PATH>`. 
Include the original issue description and reproduction code, and ask the LLM to identify the exact file and method causing the bug based purely on the source code.
```

### Resulting Prompt Structure:
* **Context:** Bug report description + reproduction snippet.
* **Goal:** Identify the exact file and method containing the bug.
* **Condition:** Source code only (no static analysis facts injected).

---

## 2. Generating Condition B (SootUp-Augmented Static Analysis)

To generate a Condition B prompt for any task, the instruction given to the AI assistant is:

```text
Generate a Condition B prompt for task `<TASK_ID>` (e.g., `google__gson-2158`) in repository `<REPO_PATH>`. 
Use `SootUpAnalyzer.java` on the compiled bytecode (`target/classes`) to extract static analysis facts (class hierarchy, Jimple method inspection, allocation sites, and invocations) related to the bug, and inject those facts into the prompt to assist the LLM in localizing the bug.
```

### Workflow Executed by the Assistant:
1. **Compile Bytecode:** Runs `mvn compile` in the target repository to generate `.class` files.
2. **Execute SootUp Analyzer:** Runs `SootUpAnalyzer` CLI queries against `target/classes`:
   * `hierarchy <class>` → Discovers all subclasses and hidden anonymous classes (e.g., `Gson$1`, `Gson$2`).
   * `inspect-method <class> <method>` → Extracts Jimple IR, line numbers, and outer class links.
   * `find-allocations <class> [scope]` → Identifies which method instantiates the target class (`JNewExpr`).
   * `find-invocations <class> <method> [scope]` → Traces callers and interprocedural callsites.
3. **Prune & Format:** Trims verbose bytecode boilerplate while preserving high-signal semantic facts.
4. **Assemble Prompt:** Injects the extracted facts into the `### Static Analysis Context (SootUp Generic Extractor)` section of the prompt.

---

## 3. Directory Structure of Prompts

```text
prompts/
├── README.md                           <-- Instructions on generating prompts
├── gson_2158/
│   ├── condition_a_prompt.md           <-- Baseline prompt (Source code only)
│   └── condition_b_prompt.md           <-- SootUp-augmented prompt + CLI reproducibility commands
└── druid_14136/
    ├── condition_a_prompt.md           <-- Baseline prompt (Source code only)
    └── condition_b_prompt.md           <-- SootUp-augmented prompt + CLI reproducibility commands
```
