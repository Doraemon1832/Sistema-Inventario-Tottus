/**
 * SISTEMA-INVENTARIO-TOTTUS — Evidencias P1-01/P1-02
 * Ensayo controlado de solicitudes GET con Playwright.
 * Esta plantilla NO mide navegadores reales ni sustituye un generador de carga especializado.
 * NO ejecutar sobre producción. NO usar con POST/PUT/DELETE (HU24-HU26).
 *
 * Variables obligatorias: PERF_URL (endpoint GET real que responde JSON)
 * Opcionales: PERF_HU, PERF_MODE, PERF_SC, PERF_MAX_P95,
 * PERF_STATE_PATH, PERF_DIAGNOSTICO
 *
 * La autenticación se obtiene de la clave tottus_token de localStorage del storageState real.
 */
import { test, expect, request as apiRequest } from '@playwright/test';
import { performance } from 'node:perf_hooks';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve, join } from 'node:path';

const URL_REAL = process.env.PERF_URL;
const HU = process.env.PERF_HU || 'HU20';
const MODO = (process.env.PERF_MODE || 'carga').toLowerCase();
const SESIONES = Number(process.env.PERF_SC || 10);
const META_P95 = Number(process.env.PERF_MAX_P95 || 1200);
const DIAGNOSTICO = process.env.PERF_DIAGNOSTICO === '1';
const STATE = process.env.PERF_STATE_PATH || 'playwright/.auth/tottus.json';
const PAUSA_MS = 200;
const pausa = (ms) => new Promise((r) => setTimeout(r, ms));
const percentile = (xs, q) => xs.length ? [...xs].sort((a,b)=>a-b)[Math.ceil(xs.length*q)-1] : null;
const formatear = (n) => n == null ? null : Number(n.toFixed(2));

function etapas() {
  const minuto = DIAGNOSTICO ? 1000 : 60000;
  switch (MODO) {
    case 'carga': return [{nombre:'carga', sesiones:SESIONES, duracionMs:5*minuto}];
    case 'resistencia': return [{nombre:'resistencia', sesiones:SESIONES, duracionMs:60*minuto}];
    case 'estres': return [1,2,3,4].map(n => ({nombre:`escalon_${n}`, sesiones:SESIONES*n, duracionMs:3*minuto}));
    case 'picos': return [
      {nombre:'base',sesiones:SESIONES,duracionMs:DIAGNOSTICO?1000:30000},
      {nombre:'subida',sesiones:SESIONES*3,duracionMs:10*(DIAGNOSTICO?100:1000)},
      {nombre:'pico',sesiones:SESIONES*3,duracionMs:minuto},
      {nombre:'recuperacion',sesiones:SESIONES,duracionMs:2*minuto},
    ];
    default: throw new Error('PERF_MODE debe ser carga, estres, resistencia o picos');
  }
}

test(`${HU} · ${MODO} · solicitudes GET controladas`, async () => {
  // Timeout de prueba: pruebas largas 60 min + margen para peticiones en curso.
  test.setTimeout(70 * 60 * 1000);
  if (!URL_REAL || !/^https?:\/\//.test(URL_REAL)) throw Error('PERF_URL debe ser la URL completa del endpoint GET REAL.');
  if (!/^HU(20|21|22|23|27)$/.test(HU)) throw Error('Esta plantilla GET solo cubre HU20-HU23 y HU27; escrituras HU24-26 se diseñan aparte.');
  if (!Number.isInteger(SESIONES) || SESIONES < 1 || SESIONES > 20) throw Error('PERF_SC inválido (1 a 20); en estrés se multiplicará hasta x4.');
  if (!Number.isFinite(META_P95) || META_P95 <= 0) throw Error('PERF_MAX_P95 debe ser mayor que cero.');

  const resumen = [];
  const statePath = resolve(STATE);
  if (!existsSync(statePath)) throw Error(`No existe el estado autenticado de Playwright: ${statePath}`);
  const storageState = JSON.parse(await (await import('node:fs/promises')).readFile(statePath, 'utf8'));
  const originState = (storageState.origins || []).find((origin) => origin.origin === new URL(URL_REAL).origin);
  const tokenEntry = originState?.localStorage?.find((entry) => entry.name === 'tottus_token');
  const token = tokenEntry?.value;
  if (typeof token !== 'string' || token.length === 0) {
    throw Error(`El storageState no contiene el token de sesión requerido para el origen ${new URL(URL_REAL).origin}. Renueve el estado mediante el login de la aplicación.`);
  }
  for (const etapa of etapas()) {
    const config = {
      timeout: 15000,
      ignoreHTTPSErrors: false,
      storageState: statePath,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    };
    const contexts = await Promise.all(Array.from({length:etapa.sesiones},()=>apiRequest.newContext(config)));
    const tiempos = [];
    let total=0, fallos=0, unauthorized=0, lastError='';
    const inicio = performance.now();
    const finEsperado = inicio + etapa.duracionMs;
    try {
      await Promise.all(contexts.map(async (ctx) => {
        while(performance.now() < finEsperado) {
          const t0=performance.now();
          try {
            const resp=await ctx.get(URL_REAL,{maxRedirects:0,timeout:15000});
            tiempos.push(performance.now()-t0);
            total++;
            const contentType = resp.headers()['content-type'] || '';
            if(resp.status()===401 || resp.status()===403) unauthorized++;
            if(!resp.ok() || !contentType.includes('json')) {
              fallos++;
              lastError=`HTTP ${resp.status()} (${contentType})`;
            }
            await resp.dispose();
          } catch(e) {
            tiempos.push(performance.now()-t0);
            total++; fallos++;
            lastError = String(e?.message || e).slice(0,160);
          }
          await pausa(PAUSA_MS);
        }
      }));
    } finally {
      await Promise.all(contexts.map(c=>c.dispose()));
    }
    const duracionSeg = (performance.now()-inicio)/1000;
    const fila = {
      hu:HU, modo:MODO, fase:etapa.nombre, diagnostico:DIAGNOSTICO,
      sesiones_concurrentes_simuladas:etapa.sesiones,
      duracion_segundos:formatear(duracionSeg),
      solicitudes:total, fallos, porcentaje_fallos:formatear(100*fallos/Math.max(total,1)),
      p50_ms:formatear(percentile(tiempos,0.50)),
      p95_ms:formatear(percentile(tiempos,0.95)),
      promedio_ms:formatear(tiempos.reduce((a,b)=>a+b,0)/Math.max(total,1)),
      operaciones_exitosas_seg:formatear((total-fallos)/Math.max(duracionSeg,0.001)),
      respuestas_401_403:unauthorized,
      percentiles_validos: unauthorized < total,
      error_mas_reciente:lastError,
      meta_p95_ms:META_P95, fecha:new Date().toISOString(), endpoint:URL_REAL,
    };
    resumen.push(fila);
    console.log('RESULTADO_T3',JSON.stringify(fila));
  }
  const dir=resolve(process.cwd(),'test-results','t3-sprint4');
  mkdirSync(dir,{recursive:true});
  const nombre=`${HU}_${MODO}_${new Date().toISOString().replace(/[:.]/g,'-')}.json`;
  const ruta=join(dir,nombre);
  writeFileSync(ruta,JSON.stringify(resumen,null,2),'utf8');
  console.log('REPORTE_JSON_T3',ruta);
  const rechazadasTotal = resumen.reduce((sum, fila) => sum + fila.respuestas_401_403, 0);
  const solicitudesTotal = resumen.reduce((sum, fila) => sum + fila.solicitudes, 0);
  expect(solicitudesTotal, 'Se obtuvieron solicitudes').toBeGreaterThan(0);
  expect(rechazadasTotal, 'No todas las solicitudes pueden ser rechazadas por autenticación').toBeLessThan(solicitudesTotal);
  expect(resumen.some((fila) => fila.respuestas_401_403 === 0), 'Al menos una fase debe tener respuestas distintas de 401/403 para interpretar percentiles').toBeTruthy();
  // Solo la carga real (no diagnóstico) decide aceptación por umbrales.
  if(MODO==='carga' && !DIAGNOSTICO) {
    const r=resumen[0];
    expect(r.solicitudes,'Se obtuvieron solicitudes').toBeGreaterThan(0);
    expect(r.respuestas_401_403,'La autenticación debe funcionar').toBe(0);
    expect(r.p95_ms,'p95 HTTP, no tiempo de carga visual').toBeLessThanOrEqual(META_P95);
    expect(r.porcentaje_fallos,'Errores inesperados (<1%)').toBeLessThan(1);
  }
});
