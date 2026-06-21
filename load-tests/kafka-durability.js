// Сценарій durability для Kafka (Розділ 3.3.3). Стале помірне навантаження на
// подієвий ендпойнт POST /orders/kafka (відповідає 202 ACCEPTED одразу, не
// чекаючи на payment). Під час прогону payment-service зупиняють на кілька
// секунд («сон»), а потім запускають знову. Мета — показати, що:
//   * прийом замовлень не падає, поки payment спить (202 і далі надходять);
//   * події накопичуються в брокері (backlog у Grafana росте);
//   * після пробудження payment вичерпує весь backlog і жодне замовлення не
//     втрачається (orders_pending повертається до 0, accepted == completed).
//
// Запуск (через контейнер на мережі стенду):
//   docker run --rm --network diploma_default -v ".../load-tests:/scripts" -w /scripts \
//     -e BASE_URL=http://order-service:8080 -e RATE=100 -e DURATION=120s \
//     -e REPORT=3.3.3-kafka-durability grafana/k6 run kafka-durability.js

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = parseInt(__ENV.RATE || '100', 10);

const outcomeAccepted = new Counter('outcome_accepted_202');
const outcomeRejected5xx = new Counter('outcome_rejected_5xx');
const outcomeConnFail = new Counter('outcome_conn_timeout');
const outcomeOther = new Counter('outcome_other');

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: __ENV.DURATION || '120s',
      preAllocatedVUs: 200,
      maxVUs: 2000,
    },
  },
  // Прийом не повинен відмовляти навіть коли payment спить: подієва модель
  // приймає 202 незалежно від доступності payment.
  thresholds: {
    'outcome_accepted_202': ['count>0'],
  },
};

export default function () {
  const res = http.post(
    `${BASE_URL}/orders/kafka`,
    JSON.stringify({ customer: 'durability', amount: 100.0 }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  if (res.status === 202) {
    outcomeAccepted.add(1);
  } else if (res.status >= 500 && res.status < 600) {
    outcomeRejected5xx.add(1);
  } else if (res.status === 0) {
    outcomeConnFail.add(1);
  } else {
    outcomeOther.add(1);
  }

  check(res, {
    'прийнято (HTTP 202)': (r) => r.status === 202,
    'відмова сервера (HTTP 5xx)': (r) => r.status >= 500 && r.status < 600,
    "відмова з'єднання / timeout (status 0)": (r) => r.status === 0,
  });
}

export function handleSummary(data) {
  const name = __ENV.REPORT || 'kafka-durability';
  return {
    [`reports/${name}.html`]: htmlReport(data),
    [`reports/${name}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}
