import { useEffect, useState } from "react";

const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";

export default function App() {
  const [tools, setTools] = useState([]);
  const [question, setQuestion] = useState("");
  const [loading, setLoading] = useState(false);
  const [history, setHistory] = useState([]); // [{question, answer, toolCalls}]
  const [error, setError] = useState("");

  useEffect(() => {
    fetch(`${API_BASE}/api/tools`)
      .then((res) => res.json())
      .then(setTools)
      .catch(() => setError("Couldn't reach the agent backend. Is it running on :8080?"));
  }, []);

  async function handleAsk(e) {
    e.preventDefault();
    if (!question.trim() || loading) return;

    setLoading(true);
    setError("");
    const askedQuestion = question;
    setQuestion("");

    try {
      const res = await fetch(`${API_BASE}/api/agent/ask`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ question: askedQuestion }),
      });
      if (!res.ok) throw new Error(`Request failed: ${res.status}`);
      const data = await res.json();
      setHistory((prev) => [
        ...prev,
        { question: askedQuestion, answer: data.answer, toolCalls: data.toolCalls || [] },
      ]);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="app">
      <header>
        <h1>MCP Tool-Calling Agent</h1>
        <p className="subtitle">Ask a question. The agent picks tools, calls them via MCP, then answers.</p>
      </header>

      <section className="tools-panel">
        <h2>Available tools</h2>
        <div className="tool-chips">
          {tools.length === 0 && <span className="muted">Loading tools…</span>}
          {tools.map((t) => (
            <span key={t.name} className="chip" title={t.description}>
              {t.name}
            </span>
          ))}
        </div>
      </section>

      {error && <div className="error-banner">{error}</div>}

      <section className="conversation">
        {history.map((turn, i) => (
          <div key={i} className="turn">
            <div className="bubble user">{turn.question}</div>

            {turn.toolCalls.length > 0 && (
              <div className="trace">
                {turn.toolCalls.map((call) => (
                  <div key={call.step} className="trace-step">
                    <div className="trace-header">
                      <span className="step-badge">{call.step}</span>
                      <span className="tool-name">{call.tool}</span>
                    </div>
                    <div className="trace-body">
                      <div>
                        <span className="label">input</span>
                        <code>{JSON.stringify(call.input)}</code>
                      </div>
                      <div>
                        <span className="label">output</span>
                        <code>{call.output}</code>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}

            <div className="bubble answer">{turn.answer}</div>
          </div>
        ))}
        {loading && <div className="bubble answer muted">Thinking…</div>}
      </section>

      <form className="ask-form" onSubmit={handleAsk}>
        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="e.g. What's the weather in Chennai, and how many engineers joined this year?"
          disabled={loading}
        />
        <button type="submit" disabled={loading}>
          Ask
        </button>
      </form>
    </div>
  );
}
