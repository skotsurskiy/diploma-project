// Короткий діагностичний прогін: стале навантаження для зняття docker stats.
// Мета — побачити, який контейнер впирається в стелю CPU (де саме вузьке місце).
import http from 'k6/http';

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: parseInt(__ENV.RATE || '300', 10),
      timeUnit: '1s',
      duration: __ENV.DURATION || '35s',
      preAllocatedVUs: 500,
      maxVUs: 3000,
    },
  },
};

export default function () {
  http.post(
    `${__ENV.BASE_URL || 'http://order-service:8080'}/orders`,
    JSON.stringify({ customer: 'diag', amount: 100.0 }),
    { headers: { 'Content-Type': 'application/json' } },
  );
}
