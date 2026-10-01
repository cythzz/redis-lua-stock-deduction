import http from "k6/http";
import { check } from "k6";

export const options = {
  scenarios: {
    flash_sale: {
      executor: "constant-arrival-rate",
      rate: 100,
      timeUnit: "1s",
      duration: "30s",
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.02"],
    http_req_duration: ["p(95)<500"],
  },
};

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";

export function setup() {
  const response = http.post(`${baseUrl}/api/inventory/SKU-K6/initialize?stock=1000`);
  check(response, { "inventory initialized": (result) => result.status === 200 });
}

export default function () {
  const requestId = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const response = http.post(
    `${baseUrl}/api/orders`,
    JSON.stringify({ sku: "SKU-K6", quantity: 1 }),
    {
      headers: {
        "Content-Type": "application/json",
        "X-Request-Id": requestId,
        "X-Client-Id": `vu-${__VU}`,
      },
    },
  );
  check(response, {
    "accepted or sold out": (result) => [202, 409, 429].includes(result.status),
  });
}
