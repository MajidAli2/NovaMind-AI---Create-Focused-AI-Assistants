# NovaMind AI — Create Focused AI Assistants

NovaMind AI is a Java-based framework for building, configuring, and running focused AI assistants. The project provides a foundation for creating assistant "agents" tailored to specific tasks (e.g., research assistant, coding coach, productivity buddy) with configurable prompts, memory, and integration points for language model providers.

> Note: This repository is implemented in Java (100% Java). The README below describes typical usage, configuration, and extension points. If you want the README tailored to the actual code (examples, package names, exact CLI commands), share the repository files or let me read specific files and I’ll adapt it.

## Table of contents
- [Key features](#key-features)
- [Tech stack](#tech-stack)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Creating an assistant](#creating-an-assistant)
- [Running the assistant](#running-the-assistant)
- [Extensibility](#extensibility)
- [Security & data privacy](#security--data-privacy)
- [Development & contribution](#development--contribution)
- [License](#license)

## Key features
- Create multiple focused assistants with distinct personas and task definitions
- Configurable prompt templates and context windows
- Memory layers (short-term session context, optional persistent memory)
- Pluggable language model providers (OpenAI, Anthropic, local LLMs — adapt connectors)
- Simple REST/CLI interface for querying assistants
- Hooks for adding tools, web-search, or external APIs

## Tech stack
- Language: Java (requires Java 17+ recommended)
- Build: Maven or Gradle (project contains whichever build file is present)
- Optional: Dockerfile for containerized runs (if included in repo)
- Integrations: REST (Spring Boot or similar), modular provider adapters

## Quick start (local)
1. Prerequisites
   - Java 17+ installed
   - Maven or Gradle (depending on project)
   - Model API key(s) if using a hosted LLM provider (e.g., OpenAI)

2. Build
   - Maven: mvn clean package
   - Gradle: ./gradlew build

3. Run
   - java -jar target/novamind-ai-<version>.jar
   - Or run from your IDE

4. Configure environment variables (example)
   - OPENAI_API_KEY=sk-...
   - NOVAMIND_CONFIG_PATH=config/assistants.json

(Replace example commands with the project’s real build/run commands if different.)

## Configuration
NovaMind AI uses configuration files / properties to define assistants and provider settings. Typical configuration items:
- assistants: list of assistant definitions (id, name, persona, system prompt, tools allowed)
- provider: provider name and credentials (OpenAI API key, endpoint, model name, temperature)
- memory: short-term vs persistent memory settings (size, vector store config)
- server: REST port, CORS, authentication (optional)

Example assistant definition (JSON)
```json
{
  "id": "researcher",
  "name": "Research Assistant",
  "systemPrompt": "You are a concise research assistant. Provide sources when possible.",
  "maxContextTokens": 3000
}
```

## Creating an assistant
1. Add an assistant entry to the configuration file or via the admin API.
2. Provide a system prompt / persona and allowed tools.
3. Optionally attach persistent memory settings or a document store.

## Running & Querying
- REST endpoints (examples — adapt to actual project):
  - POST /assistants/{id}/chat — send a user message, receive assistant reply
  - GET /assistants — list available assistants
  - POST /admin/assistants — create/update assistant config (auth required)
- CLI: A CLI runner may exist to chat locally:
  - java -jar novamind-ai.jar chat --assistant researcher

## Extensibility
- Provider adapters: Implement the provider interface to add new LLM connectors (OpenAI, Anthropic, local LLM servers).
- Tooling hooks: Register external tools (search, calculators, code runners) that assistants can call.
- Storage: Swap or add vector stores / DB backends for persistent memory.

## Security & data privacy
- Keep API keys and secrets out of code — use environment variables or secret manager.
- Review data retention for persistent memory; provide options to redact or expire stored user data.
- Consider adding authentication and authorization for admin endpoints and assistant management.

## Development & contribution
- Fork the repository and open pull requests for features or fixes.
- Follow the repository's code style and test guidelines.
- When contributing integrations (providers/tools), include unit tests and examples.

If you'd like:
- I can generate example Java classes for a provider adapter, REST controller, or a sample assistant config.
- I can inspect the repository and produce a README that exactly matches available modules, build commands, and example usage.

## License
Specify the repository license (e.g., MIT, Apache-2.0). If there's a LICENSE file in the repo, include it as-is. If you want, I can add a default license template.

---

If you want a README tailored precisely to the code in the repository (specific build commands, package names, examples), let me read the repository files (or paste key files). I can then produce a finalized README.md ready to commit.
