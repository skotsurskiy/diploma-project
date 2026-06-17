// Сценарій fault-injection: стале помірне навантаження для демонстрації того,
// як деградація payment впливає на order. Використовується у Розділі 3.2 для
// порівняння «payment здоровий (200мс)» vs «payment гальмує (2000мс)».
//
// Запуск (через контейнер на мережі стенду):
//   docker run --rm --network diploma_default -v ".../load-tests:/scripts" -w /scripts \
//     -e BASE_URL=http://order-service:8080 -e RATE=300 -e DURATION=45s \
//     -e REPORT=3.2.1-payment-healthy grafana/k6 run fault-injection.js

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = parseInt(__ENV.RATE || '300', 10);

const outcomeSuccess = new Counter('outcome_success_201');
const outcomeRejected5xx = new Counter('outcome_rejected_5xx');
const outcomeConnFail = new Counter('outcome_conn_timeout');
const outcomeOther = new Counter('outcome_other');

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: __ENV.DURATION || '45s',
      preAllocatedVUs: 500,
      maxVUs: 5000,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const res = http.post(
    `${BASE_URL}/orders`,
    JSON.stringify({ customer: 'fault', amount: 100.0 }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  if (res.status === 201) {
    outcomeSuccess.add(1);
  } else if (res.status >= 500 && res.status < 600) {
    outcomeRejected5xx.add(1);
  } else if (res.status === 0) {
    outcomeConnFail.add(1);
  } else {
    outcomeOther.add(1);
  }

  check(res, {
    'успішно (HTTP 201)': (r) => r.status === 201,
    'відмова сервера (HTTP 5xx)': (r) => r.status >= 500 && r.status < 600,
    "відмова з'єднання / timeout (status 0)": (r) => r.status === 0,
  });
}

export function handleSummary(data) {
  const name = __ENV.REPORT || 'fault-injection';
  return {
    [`reports/${name}.html`]: htmlReport(data),
    [`reports/${name}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}
