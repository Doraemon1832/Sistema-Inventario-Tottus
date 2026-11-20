import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const baseUrl = process.env.TOTTUS_PERF_BASE_URL ?? 'http://127.0.0.1:18080';
const username = process.env.TOTTUS_E2E_USERNAME;
const password = process.env.TOTTUS_E2E_PASSWORD;
const durationMs = Number(process.env.TOTTUS_PERF_DURATION_MS ?? 300_000);
const concurrency = Number(process.env.TOTTUS_PERF_CONCURRENCY ?? 10);
const artifact = resolve(dirname(fileURLToPath(import.meta.url)), '../../../auditoria-calidad/cierre-implementacion/evidencia-rendimiento-hu20-2026-11-14.json');

if (!username || !password || durationMs < 1 || concurrency < 1) {
  throw new Error('Credenciales temporales, duración y concurrencia válidas son obligatorias.');
}

const loginResponse = await fetch(`${baseUrl}/api/auth/login`, {
  method: 'POST', headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ usuario: username, password })
});
if (!loginResponse.ok) throw new Error(`Login de prueba falló: HTTP ${loginResponse.status}`);
const user = await loginResponse.json();
const auth = { authorization: `Bearer ${user.token}` };
const inventoryResponse = await fetch(`${baseUrl}/api/inventario?sedeId=${user.idSede}`, { headers: auth });
if (!inventoryResponse.ok) throw new Error(`Consulta inicial de inventario falló: HTTP ${inventoryResponse.status}`);
const inventory = await inventoryResponse.json();
const productId = inventory.find(item => Number.isInteger(item.idProducto))?.idProducto;

const startedAt = new Date();
const deadline = Date.now() + durationMs;
const latencies = [];
const errors = [];
let requestCount = 0;

async function worker(workerId) {
  let sequence = workerId;
  while (Date.now() < deadline) {
    const page = sequence % 8;
    const size = [10, 20, 50][sequence % 3];
    const params = new URLSearchParams({ sedeId: String(user.idSede), page: String(page), size: String(size) });
    if (sequence % 4 === 1) params.set('tipo', 'ENTRADA');
    if (sequence % 4 === 2) params.set('tipo', 'SALIDA');
    if (productId && sequence % 5 === 3) params.set('productoId', String(productId));
    if (sequence % 3 === 2) {
      params.set('desde', '2020-01-01');
      params.set('hasta', '2030-12-31');
    }
    const url = `${baseUrl}/api/inventario/movimientos?${params}`;
    const start = performance.now();
    try {
      const response = await fetch(url, { headers: auth });
      const body = await response.json();
      const elapsed = performance.now() - start;
      requestCount++;
      latencies.push(elapsed);
      if (!response.ok || !Array.isArray(body.content) || !Number.isFinite(body.totalElements)) {
        errors.push({ status: response.status, elapsedMs: Number(elapsed.toFixed(2)), page, size });
      }
    } catch (error) {
      requestCount++;
      errors.push({ status: 'network-error', message: String(error), elapsedMs: Number((performance.now() - start).toFixed(2)), page, size });
    }
    sequence += concurrency;
  }
}

await Promise.all(Array.from({ length: concurrency }, (_, id) => worker(id)));
latencies.sort((a, b) => a - b);
const percentile = p => latencies.length ? Number(latencies[Math.min(latencies.length - 1, Math.ceil(p * latencies.length) - 1)].toFixed(2)) : null;
const errorRate = requestCount ? errors.length / requestCount * 100 : 100;
const completedAt = new Date();
const report = {
  generatedAt: completedAt.toISOString(),
  scenario: { endpoint: '/api/inventario/movimientos', durationSeconds: Number(((completedAt - startedAt) / 1000).toFixed(2)), targetSeconds: durationMs / 1000, concurrency, requestCount,
    filterMix: 'page 0-7, size 10/20/50; alternating ENTRADA/SALIDA; optional product and 2020-01-01..2030-12-31 date filters',
    environment: { backend: baseUrl, database: 'disposable mysql:8.4 container, schema base + V002 + V003', java: process.env.TOTTUS_PERF_JAVA_VERSION ?? process.env.JAVA_HOME ?? 'Java selected by PATH', node: process.version, operatingSystem: process.env.TOTTUS_PERF_OS ?? `${process.platform} ${process.arch}`, machine: process.env.TOTTUS_PERF_MACHINE ?? 'hardware details not captured' } },
  latencyMs: { p50: percentile(0.5), p95: percentile(0.95), p99: percentile(0.99), min: latencies.length ? Number(latencies[0].toFixed(2)) : null, max: latencies.length ? Number(latencies.at(-1).toFixed(2)) : null },
  errors: { count: errors.length, ratePercent: Number(errorRate.toFixed(4)), samples: errors.slice(0, 25) },
  reference: { p95LimitMs: 1200, errorRateLimitPercent: 1, result: latencies.length && percentile(0.95) <= 1200 && errorRate < 1 ? 'PASS' : 'FAIL' }
};

await mkdir(dirname(artifact), { recursive: true });
await writeFile(artifact, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
console.log(JSON.stringify(report, null, 2));
console.log(`Evidence: ${artifact}`);
if (report.reference.result !== 'PASS') process.exitCode = 1;
