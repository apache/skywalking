# Virtual GenAI

Virtual cache represent the Generative AI service nodes detected by [server agents' plugins](server-agents.md). The performance
metrics of the GenAI operations are also from the GenAI client-side perspective.

For example, an Spring-ai plugin in the Java agent could detect the latency of a chat completion request.
As a result, SkyWalking would show traffic, latency, success rate, and token usage (input/output) powered by backend analysis capabilities in this dashboard.

The GenAI operation span should have
- It is an **Exit** span
- **Span's layer == GENAI**
- Tag key = `gen_ai.provider.name`, value = The Generative AI provider, e.g. openai, anthropic, ollama
- Tag key = `gen_ai.response.model`, value = The name of the GenAI model a response is being made to, e.g. gpt-4o, claude-3-5-sonnet
- Tag key = `gen_ai.usage.input_tokens`, value = The number of tokens used in the GenAI input (prompt)
- Tag key = `gen_ai.usage.output_tokens`, value = The number of tokens used in the GenAI response (completion)
- If the GenAI service is a remote API (e.g. OpenAI), the span's peer would be the network address (IP or domain) of the GenAI server.
