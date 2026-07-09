---
version: "1.0.7"
level: pair
processes:
  design: none
  implementation: pair
  testing: pair
  documentation: pair
  review: pair
  deployment: none
---

This format is based on [AI-DECLARATION.md](https://ai-declaration.md/en/0.1.1).

## Notes

- All output was reviewed by a human or had human involvement at creation.
- Claude Code was used as the AI harness.
- Models used (in order of highest use):
  - anthropic/claude-opus-4.7
  - anthropic/claude-opus-4.8
- Additional Claude Code subagents used during review:
  - voltagrnt-qa-sec:code-reviewer
  - voltagent-qa-sec:security-auditor
