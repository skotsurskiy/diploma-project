// Навантажувальний тест Фази 3.1 (Тест 1): baseline-архітектура.
//
// Модель навантаження — OPEN MODEL (ramping-arrival-rate): K6 подає задану
// кількість запитів на секунду НЕЗАЛЕЖНО від часу відповіді сервера. Саме така
// модель коректно відтворює реальний трафік і виявляє каскадну відмову: коли
// сервер не встигає, черга й час відповіді ростуть, а RPS на вході лишається.
//
// Запуск (потрібен встановлений k6 або образ grafana/k6):
//   k6 run load-tests/baseline.js
//   k6 run -e BASE_URL=http://localhost:8080 -e PEAK_RPS=1000 load-tests/baseline.js
//
// Результати: HTML-звіт (summary.html) + JSON (summary.json) у поточній теці.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PEAK_RPS = parseInt(__ENV.PEAK_RPS || '1000', 10);

const errors = new Counter('business_errors');

export const options = {
  scenarios: {
    ramp_to_peak: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 5000,
      stages: [
        { target: 100, duration: '30s' },      // прогрів
        { target: PEAK_RPS, duration: '30s' },  // вихід на пік
        { target: PEAK_RPS, duration: '60s' },  // утримання піку
        { target: 0, duration: '10s' },         // спад
      ],
    },
  },
  thresholds: {
    // SLO дослідження: 99% запитів < 1с, частка помилок < 1%.
    // У baseline ці пороги свідомо НЕ виконуються — це фіксує точку відліку.
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const payload = JSON.stringify({
    customer: `customer-${__VU}`,
    amount: 100.0,
  });
  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(`${BASE_URL}/orders`, payload, params);

  const ok = check(res, {
    'status is 201': (r) => r.status === 201,
  });
  if (!ok) {
    errors.add(1);
  }
}

export function handleSummary(data) {
  return {
    'summary.html': htmlReport(data),
    'summary.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}
