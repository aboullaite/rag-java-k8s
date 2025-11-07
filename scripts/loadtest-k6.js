import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

export const options = {
  stages: [
    { duration: '1m', target: 10 },
    { duration: '5m', target: 30 },
    { duration: '1m', target: 0 },
  ],
};

const SUCCESS = new Counter('ask_success');
const ORCHESTRATOR_URL = __ENV.ORCHESTRATOR_URL || 'http://localhost:8080/v1/ask';
const PROMPTS = [
  'Summarize the RAG Java platform architecture.',
  'How does the semantic cache make retrieval faster?',
  'What happens if the LLM becomes unavailable?',
  'Describe the autoscaling approach for the retriever service.',
  'Explain the telemetry pipeline used for observability.',
  'How is Weaviate populated during ingestion?',
  'What are the prerequisites for running the KinD demo?',
];

export default function () {
  const prompt = PROMPTS[Math.floor(Math.random() * PROMPTS.length)];
  const payload = JSON.stringify({ prompt, topK: 5 });
  const headers = { 'Content-Type': 'application/json' };
  const res = http.post(ORCHESTRATOR_URL, payload, { headers, timeout: '120s' });
  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'has citations': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body.citations) && body.citations.length > 0;
      } catch (e) {
        return false;
      }
    },
  });
  if (ok) {
    SUCCESS.add(1);
  }
  sleep(1);
}
