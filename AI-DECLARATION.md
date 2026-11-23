---
version: "1.3.0"
level: copilot
processes:
  design: none
  implementation: pair
  testing: copilot
  documentation: pair
  review: assist
  deployment: none
---

This format is based on [AI-DECLARATION.md](https://ai-declaration.md/en/0.1.1).

## Notes

- All output was reviewed by a human or had human involvement at creation.
- Claude Code and Hermes used as the AI harnesses.
- Models used (in order of highest use):
  - anthropic/claude-opus-4.7
  - anthropic/claude-opus-4.8
  - anthropic/claude-opus-5.5
  - z-ai/glm-5.3
  - moonshotai/kimi-k3.1-pro
  - anthropic/claude-opus-5
  - anthropic/claude-fable-5
- Additional agent/skills used during review:
  - voltagent-qa-sec:code-reviewer
  - voltagent-qa-sec:security-auditor
