# MCP Tool-Calling Agent

An agent that answers questions by deciding, on its own, which external
tools to call — a MySQL database, a document search, a weather API — through
**MCP (Model Context Protocol)** instead of hardcoded if/else integrations
for each service.

## What is MCP, in plain terms

Normally, if you want an LLM to check your database *and* the weather, you
write custom code for each: one function to run SQL, another to call a
weather API, and you glue both into your prompt logic by hand. Every new
tool means more hardcoded plumbing.

MCP standardizes that connection. A server exposes a list of tools (name,
description, input schema) over one interface. Any MCP-compatible client —
this agent, or Claude Desktop, or anything else — can ask "what tools do you
have?" and "run this tool with these arguments" using the *same* protocol,
regardless of what the tool actually does underneath. Add a fourth tool to
the server tomorrow, and the client doesn't change at all — it just sees one
more entry in the tool list.

This project has two independent Spring Boot apps talking MCP to each other:

```
[React frontend] → [agent-backend :8080] --MCP (SSE)--> [mcp-server :8081] → MySQL / docs / weather API
                          │
                          └──> Groq (LLaMA) for tool-call decisions
```

## How a request flows

1. You ask a question on the frontend.
2. `agent-backend` sends it to Groq along with the list of tools it got from
   `mcp-server` (fetched once at startup via MCP's `tools/list`).
3. Groq's response either asks for a tool call or gives a final answer.
4. If it asks for a tool call, `agent-backend` calls `mcp-server` via MCP's
   `tools/call`, gets the result, and feeds it back to Groq as a `tool`
   message. This repeats until Groq is satisfied (capped at 5 rounds).
5. Every tool call — name, input, output — is recorded and returned to the
   frontend alongside the answer, so you can see the agent's reasoning trail.

## Tools exposed by `mcp-server`

| Tool | What it does | Safety notes |
|---|---|---|
| `query_database` | Runs a read-only SELECT against MySQL | App-level check blocks non-SELECT/DDL keywords; DB user (`mcp_readonly`) only has SELECT grants |
| `search_documents` | Keyword search over `.txt` files in `mcp-server/src/main/resources/docs` | In-memory, no external calls |
| `get_weather` | Current weather for a city via Open-Meteo (free, no API key) | Rate-limit this yourself if you demo it a lot |

## Running it

### Option A — Docker Compose (easiest)

```bash
export GROQ_API_KEY=your_key_here
docker compose up --build
```

- Frontend: http://localhost:5173
- Agent backend: http://localhost:8080
- MCP server: http://localhost:8081
- MySQL: localhost:3306 (root password `rootpass`, seeded automatically)

### Option B — run locally without Docker

```bash
# 1. MySQL — create the DB and read-only user
mysql -u root -p < db/init.sql

# 2. mcp-server
cd mcp-server
mvn spring-boot:run

# 3. agent-backend (in a new terminal)
cd agent-backend
export GROQ_API_KEY=your_key_here
mvn spring-boot:run

# 4. frontend (in a new terminal)
cd frontend
npm install
npm run dev
```

Get a free Groq API key at https://console.groq.com — the free tier is
enough for demoing this.

## Test cases to show multi-tool chaining

Ask these on the frontend to demonstrate the trace UI:

1. **Single tool:** "How many employees are in Engineering?" → one
   `query_database` call.
2. **Single tool:** "What's the weather in Chennai right now?" → one
   `get_weather` call.
3. **Multi-tool chaining:** "What's the weather in Chennai, and how many
   people joined the Engineering team this year?" → two tool calls in one
   turn (`get_weather` + `query_database`), both shown in the trace, before
   the model combines them into one answer.
4. **Document search:** "How often are database credentials rotated?" →
   one `search_documents` call against `onboarding.txt`.

## Known rough edges (be upfront about these in a viva/interview)

- **MCP Java SDK and Spring AI's MCP starters are both under active
  development in 2026** — artifact IDs and annotation names have shifted
  across releases. If a build fails on a dependency, check
  `docs.spring.io/spring-ai/reference` and Maven Central for the current
  version before assuming the logic is wrong.
- No auth on the endpoints yet — fine for a demo/portfolio project, not for
  anything real. Add Spring Security + a JWT filter if you want to show that
  off separately.
- `query_database`'s SQL-injection guard is a basic keyword blocklist, not a
  proper SQL parser. It's there mainly to back up the DB-level read-only
  grant, not to replace it — that ordering (permissions first, code checks
  second) is worth calling out if asked about it in an interview.
- Tool call loop is capped at 5 rounds to avoid runaway costs; tune
  `groq.max-tool-iterations` if you add tools that need deeper chains.

## Why this is worth having on your resume

MCP shipped in late 2024 and only really picked up in the Java/Spring
ecosystem through 2025–26 — most campus placements haven't seen a candidate
who's actually wired one up end-to-end, as opposed to just reading about it.
Be ready to explain, in one line: *why MCP instead of hardcoding each
integration* (standard interface, tools become swappable/composable without
touching client code) — that's the actual point interviewers will probe.
